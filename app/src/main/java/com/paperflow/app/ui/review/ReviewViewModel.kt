package com.paperflow.app.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperflow.app.data.model.PageEntity
import com.paperflow.app.data.repository.DocumentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ReviewSelectionType(val label: String) {
    COPY("Copy"),
    MOVE("Move"),
    CONVERT("Convert")
}

data class ReviewUiState(
    val documentId: String = "",
    val documentTitle: String = "",
    val allDocuments: List<com.paperflow.app.data.model.DocumentEntity> = emptyList(),
    val pages: List<PageEntity> = emptyList(),
    val isSaving: Boolean = false,
    val activeSelectionType: ReviewSelectionType? = null,
    val selectedPageIds: Set<String> = emptySet(),
    val isTransferring: Boolean = false,
    val transferMessage: String? = null,
    val transferError: String? = null,
)

class ReviewViewModel(
    private val repository: DocumentRepository,
    private val photoRepository: com.paperflow.app.data.repository.PhotoRepository,
    private val sharedPrefs: android.content.SharedPreferences,
    private val documentId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewUiState(documentId = documentId))
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    init {
        loadDocument()
    }

    fun startSelectionType(type: ReviewSelectionType) {
        _uiState.value = _uiState.value.copy(
            activeSelectionType = type,
            selectedPageIds = emptySet()
        )
    }

    fun cancelSelectionType() {
        _uiState.value = _uiState.value.copy(
            activeSelectionType = null,
            selectedPageIds = emptySet()
        )
    }

    fun toggleSelection(pageId: String) {
        val current = _uiState.value.selectedPageIds
        val newSelection = if (pageId in current) {
            current - pageId
        } else {
            current + pageId
        }
        _uiState.value = _uiState.value.copy(selectedPageIds = newSelection)
    }

    fun selectAll() {
        val allIds = _uiState.value.pages.map { it.id }.toSet()
        if (_uiState.value.selectedPageIds == allIds) {
            _uiState.value = _uiState.value.copy(selectedPageIds = emptySet())
        } else {
            _uiState.value = _uiState.value.copy(selectedPageIds = allIds)
        }
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedPageIds = emptySet())
    }

    fun clearTransferStatus() {
        _uiState.value = _uiState.value.copy(transferMessage = null, transferError = null)
    }

    fun transferSelectedPages(
        targetDocumentId: String,
        onComplete: () -> Unit = {}
    ) {
        val type = _uiState.value.activeSelectionType ?: return
        if (type == ReviewSelectionType.CONVERT) return
        val mode = if (type == ReviewSelectionType.COPY) {
            com.paperflow.app.ui.home.DocumentTransferMode.COPY
        } else {
            com.paperflow.app.ui.home.DocumentTransferMode.MOVE
        }
        val pageIds = _uiState.value.selectedPageIds.toList()
        if (pageIds.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTransferring = true, transferError = null, transferMessage = null)
            try {
                val count = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    when (mode) {
                        com.paperflow.app.ui.home.DocumentTransferMode.COPY ->
                            repository.copyPagesInto(pageIds, targetDocumentId)
                        com.paperflow.app.ui.home.DocumentTransferMode.MOVE ->
                            repository.movePagesInto(pageIds, targetDocumentId)
                    }
                }
                val action = if (mode == com.paperflow.app.ui.home.DocumentTransferMode.COPY) "copied" else "moved"
                _uiState.value = _uiState.value.copy(
                    transferMessage = "$count page${if (count == 1) "" else "s"} $action",
                    selectedPageIds = emptySet(),
                    activeSelectionType = null,
                    isTransferring = false
                )
                onComplete()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    transferError = e.message ?: "Transfer failed",
                    isTransferring = false
                )
            }
        }
    }

    fun convertSelectedPagesToPhotos(onComplete: () -> Unit = {}) {
        val selectedIds = _uiState.value.selectedPageIds
        val pages = _uiState.value.pages.filter { it.id in selectedIds }
        if (pages.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTransferring = true, transferError = null, transferMessage = null)
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    pages.forEachIndexed { index, page ->
                        val srcFile = java.io.File(page.imagePath)
                        if (srcFile.exists()) {
                            val destFile = photoRepository.createPhotoImageFile()
                            srcFile.copyTo(destFile)
                            
                            val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            android.graphics.BitmapFactory.decodeFile(destFile.absolutePath, options)
                            
                            photoRepository.addPhoto(
                                title = "Converted_${page.id.take(6)}_${index + 1}",
                                imagePath = destFile.absolutePath,
                                width = options.outWidth,
                                height = options.outHeight,
                                fileSize = destFile.length(),
                                originalPath = destFile.absolutePath
                            )
                        }
                    }
                }
                sharedPrefs.edit().putString("active_tab", "Photos").apply()
                _uiState.value = _uiState.value.copy(
                    transferMessage = "${pages.size} page${if (pages.size == 1) "" else "s"} converted to Photos",
                    selectedPageIds = emptySet(),
                    activeSelectionType = null,
                    isTransferring = false
                )
                onComplete()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    transferError = e.message ?: "Conversion failed",
                    isTransferring = false
                )
            }
        }
    }

    fun convertDocumentToPhotos(onComplete: () -> Unit = {}) {
        val pages = _uiState.value.pages
        if (pages.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTransferring = true, transferError = null, transferMessage = null)
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    pages.forEachIndexed { index, page ->
                        val srcFile = java.io.File(page.imagePath)
                        if (srcFile.exists()) {
                            val destFile = photoRepository.createPhotoImageFile()
                            srcFile.copyTo(destFile)
                            
                            val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            android.graphics.BitmapFactory.decodeFile(destFile.absolutePath, options)
                            
                            photoRepository.addPhoto(
                                title = "Converted_${page.id.take(6)}_${index + 1}",
                                imagePath = destFile.absolutePath,
                                width = options.outWidth,
                                height = options.outHeight,
                                fileSize = destFile.length(),
                                originalPath = destFile.absolutePath
                            )
                        }
                    }
                }
                sharedPrefs.edit().putString("active_tab", "Photos").apply()
                _uiState.value = _uiState.value.copy(
                    transferMessage = "${pages.size} page${if (pages.size == 1) "" else "s"} converted to Photos",
                    selectedPageIds = emptySet(),
                    activeSelectionType = null,
                    isTransferring = false
                )
                onComplete()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    transferError = e.message ?: "Conversion failed",
                    isTransferring = false
                )
            }
        }
    }

    private fun loadDocument() {
        viewModelScope.launch {
            val doc = repository.getDocument(documentId) ?: return@launch
            _uiState.value = _uiState.value.copy(documentTitle = doc.title)
        }
        viewModelScope.launch {
            repository.getAllDocuments().collect { docs ->
                _uiState.value = _uiState.value.copy(allDocuments = docs)
            }
        }
        viewModelScope.launch {
            repository.getPages(documentId).collect { pages ->
                _uiState.value = _uiState.value.copy(pages = pages)
            }
        }
    }

    fun deletePage(pageId: String) {
        viewModelScope.launch {
            repository.deletePage(pageId)
        }
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        val pages = _uiState.value.pages.toMutableList()
        if (fromIndex < 0 || toIndex < 0 || fromIndex >= pages.size || toIndex >= pages.size) return
        val moved = pages.removeAt(fromIndex)
        pages.add(toIndex, moved)
        _uiState.value = _uiState.value.copy(pages = pages)
        // Persist new order
        viewModelScope.launch {
            repository.reorderPages(documentId, pages.map { it.id })
        }
    }

    fun updateTitle(newTitle: String) {
        _uiState.value = _uiState.value.copy(documentTitle = newTitle)
        viewModelScope.launch {
            repository.renameDocument(documentId, newTitle)
        }
    }
}
