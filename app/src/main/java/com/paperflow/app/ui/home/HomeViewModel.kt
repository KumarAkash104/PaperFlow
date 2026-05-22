package com.paperflow.app.ui.home

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperflow.app.data.model.DocumentEntity
import com.paperflow.app.data.model.PageEntity
import com.paperflow.app.data.repository.DocumentRepository
import com.paperflow.app.pdf.PdfImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.paperflow.app.data.model.PhotoEntity
import com.paperflow.app.data.repository.PhotoRepository
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.PdfDocument
import android.graphics.Paint
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.FileOutputStream

enum class SortOption {
    DATE_DESC, DATE_ASC, NAME_ASC, PAGE_COUNT
}

enum class DocumentTransferMode(val label: String) {
    COPY("Copy"),
    MOVE("Move"),
}

class HomeViewModel(
    private val repository: DocumentRepository,
    private val photoRepository: PhotoRepository,
    private val sharedPrefs: SharedPreferences
) : ViewModel() {

    private val _activeTab = MutableStateFlow(sharedPrefs.getString("active_tab", "Documents") ?: "Documents")
    val activeTab: StateFlow<String> = _activeTab

    fun setActiveTab(tab: String) {
        _activeTab.value = tab
        sharedPrefs.edit().putString("active_tab", tab).apply()
    }

    private val _isGridView = MutableStateFlow(sharedPrefs.getBoolean("is_grid_view", true))
    val isGridView: StateFlow<Boolean> = _isGridView

    private val _sortOption = MutableStateFlow(SortOption.DATE_DESC)
    val sortOption: StateFlow<SortOption> = _sortOption

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedDocumentIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedDocumentIds: StateFlow<Set<String>> = _selectedDocumentIds

    private val _selectedPhotoIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedPhotoIds: StateFlow<Set<String>> = _selectedPhotoIds

    private val _isPhotoSelectionMode = MutableStateFlow(false)
    val isPhotoSelectionMode: StateFlow<Boolean> = _isPhotoSelectionMode

    private val _isTransferring = MutableStateFlow(false)
    val isTransferring: StateFlow<Boolean> = _isTransferring

    private val _transferMessage = MutableStateFlow<String?>(null)
    val transferMessage: StateFlow<String?> = _transferMessage

    private val _transferError = MutableStateFlow<String?>(null)
    val transferError: StateFlow<String?> = _transferError

    private val _activeTransferAction = MutableStateFlow<DocumentTransferMode?>(null)
    val activeTransferAction: StateFlow<DocumentTransferMode?> = _activeTransferAction

    @OptIn(ExperimentalCoroutinesApi::class)
    val documents: StateFlow<List<DocumentEntity>> = kotlinx.coroutines.flow.combine(
        _searchQuery.flatMapLatest { query ->
            if (query.isBlank()) repository.getAllDocuments()
            else repository.searchDocuments(query)
        },
        _sortOption
    ) { docs, sortOpt ->
        when (sortOpt) {
            SortOption.DATE_DESC -> docs.sortedByDescending { it.updatedAt }
            SortOption.DATE_ASC -> docs.sortedBy { it.updatedAt }
            SortOption.NAME_ASC -> docs.sortedBy { it.title.lowercase() }
            SortOption.PAGE_COUNT -> docs.sortedByDescending { it.pageCount }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val photos: StateFlow<List<PhotoEntity>> = kotlinx.coroutines.flow.combine(
        _searchQuery.flatMapLatest { query ->
            if (query.isBlank()) photoRepository.getAllPhotos()
            else photoRepository.searchPhotos(query)
        },
        _sortOption
    ) { pts, sortOpt ->
        when (sortOpt) {
            SortOption.DATE_DESC -> pts.sortedByDescending { it.updatedAt }
            SortOption.DATE_ASC -> pts.sortedBy { it.updatedAt }
            SortOption.NAME_ASC -> pts.sortedBy { it.title.lowercase() }
            SortOption.PAGE_COUNT -> pts.sortedByDescending { it.fileSize }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun toggleViewStyle() {
        val newValue = !_isGridView.value
        _isGridView.value = newValue
        sharedPrefs.edit().putBoolean("is_grid_view", newValue).apply()
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun startTransferAction(action: DocumentTransferMode) {
        _activeTransferAction.value = action
        _selectedDocumentIds.value = emptySet()
    }

    fun cancelTransferAction() {
        _activeTransferAction.value = null
        _selectedDocumentIds.value = emptySet()
    }

    fun toggleSelection(documentId: String) {
        val selected = _selectedDocumentIds.value
        _selectedDocumentIds.value = if (documentId in selected) {
            selected - documentId
        } else {
            selected + documentId
        }
    }

    fun clearSelection() {
        _selectedDocumentIds.value = emptySet()
    }

    fun selectAll(documentIds: List<String>) {
        _selectedDocumentIds.value = documentIds.toSet()
    }

    fun clearTransferStatus() {
        _transferMessage.value = null
        _transferError.value = null
    }

    fun transferSelectedDocuments(
        targetDocumentId: String,
        onComplete: () -> Unit = {},
    ) {
        val mode = _activeTransferAction.value ?: return
        val sourceIds = _selectedDocumentIds.value.toList()
        if (sourceIds.isEmpty() || targetDocumentId in sourceIds) return

        viewModelScope.launch {
            _isTransferring.value = true
            _transferError.value = null
            _transferMessage.value = null
            try {
                val pageCount = withContext(Dispatchers.IO) {
                    when (mode) {
                        DocumentTransferMode.COPY ->
                            repository.copyDocumentsInto(sourceIds, targetDocumentId)
                        DocumentTransferMode.MOVE ->
                            repository.moveDocumentsInto(sourceIds, targetDocumentId)
                    }
                }
                val action = when (mode) {
                    DocumentTransferMode.COPY -> "copied"
                    DocumentTransferMode.MOVE -> "moved"
                }
                _transferMessage.value = "$pageCount page${if (pageCount == 1) "" else "s"} $action"
                _activeTransferAction.value = null
                clearSelection()
                onComplete()
            } catch (e: Exception) {
                _transferError.value = e.message ?: "${mode.label} failed"
            } finally {
                _isTransferring.value = false
            }
        }
    }

    fun deleteDocument(id: String) {
        viewModelScope.launch {
            repository.deleteDocument(id)
            _selectedDocumentIds.value = _selectedDocumentIds.value - id
        }
    }

    fun renameDocument(id: String, newTitle: String) {
        viewModelScope.launch {
            repository.renameDocument(id, newTitle)
        }
    }

    fun deletePhoto(id: String) {
        viewModelScope.launch {
            photoRepository.deletePhoto(id)
        }
    }

    fun renamePhoto(id: String, newTitle: String) {
        viewModelScope.launch {
            photoRepository.renamePhoto(id, newTitle)
        }
    }

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting

    fun importPdf(context: Context, uri: Uri, onImported: (String) -> Unit) {
        viewModelScope.launch {
            _isImporting.value = true
            try {
                val tempFile = withContext(Dispatchers.IO) {
                    val file = File(context.cacheDir, "temp_import.pdf")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    file
                }
                
                val doc = PdfImporter.importPdfAsDocument(tempFile, repository)
                if (doc != null) {
                    onImported(doc.id)
                }
                tempFile.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun importImages(context: Context, uris: List<Uri>, onImported: (String) -> Unit) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isImporting.value = true
            try {
                withContext(Dispatchers.IO) {
                    uris.forEachIndexed { index, uri ->
                        val title = "Photo_${System.currentTimeMillis() / 1000}_${index + 1}"
                        photoRepository.importPhotoFromUri(uri, title)
                    }
                }
                withContext(Dispatchers.Main) {
                    onImported("") // Triggers refresh
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isImporting.value = false
            }
        }
    }

    // PDF -> Photo Conversion: Renders PDF page to image and saves to photo tab
    fun convertPdfToPhoto(context: Context, pdfFile: File) {
        viewModelScope.launch {
            _isImporting.value = true
            try {
                withContext(Dispatchers.IO) {
                    val fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    val pdfRenderer = PdfRenderer(fd)
                    val count = pdfRenderer.pageCount
                    for (i in 0 until count) {
                        val page = pdfRenderer.openPage(i)
                        
                        val width = page.width * 2
                        val height = page.height * 2
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()

                        val imageFile = photoRepository.createPhotoImageFile()
                        FileOutputStream(imageFile).use { fos ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                        }

                        photoRepository.addPhoto(
                            title = "Converted_${pdfFile.nameWithoutExtension}_Page_${i + 1}",
                            imagePath = imageFile.absolutePath,
                            width = bitmap.width,
                            height = bitmap.height,
                            fileSize = imageFile.length(),
                            originalPath = imageFile.absolutePath
                        )
                        bitmap.recycle()
                    }
                    pdfRenderer.close()
                    fd.close()
                }
                setActiveTab("Photos")
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun convertDocumentToPhotos(documentId: String) {
        viewModelScope.launch {
            _isImporting.value = true
            try {
                withContext(Dispatchers.IO) {
                    val pages = repository.getPagesSync(documentId)
                    pages.forEachIndexed { index, page ->
                        val srcFile = File(page.imagePath)
                        if (srcFile.exists()) {
                            val destFile = photoRepository.createPhotoImageFile()
                            srcFile.copyTo(destFile, overwrite = true)
                            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(destFile.absolutePath, options)
                            val width = options.outWidth
                            val height = options.outHeight
                            
                            photoRepository.addPhoto(
                                title = "Converted_${page.id.take(6)}_${index + 1}",
                                imagePath = destFile.absolutePath,
                                width = width,
                                height = height,
                                fileSize = destFile.length(),
                                originalPath = destFile.absolutePath
                            )
                        }
                    }
                }
                setActiveTab("Photos")
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isImporting.value = false
            }
        }
    }

    // Photo -> PDF Conversion: Wraps image inside a single-page PDF and saves to documents
    fun convertPhotoToPdf(context: Context, photo: PhotoEntity) {
        viewModelScope.launch {
            _isImporting.value = true
            try {
                withContext(Dispatchers.IO) {
                    val bitmap = com.paperflow.app.util.ImageProcessor.loadBitmap(photo.imagePath) ?: return@withContext
                    
                    val pdfDocument = PdfDocument()
                    val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    
                    val canvas = page.canvas
                    val paint = Paint()
                    canvas.drawBitmap(bitmap, 0f, 0f, paint)
                    
                    pdfDocument.finishPage(page)
                    
                    val pdfFile = File(context.cacheDir, "Converted_${photo.title}.pdf")
                    FileOutputStream(pdfFile).use { fos ->
                        pdfDocument.writeTo(fos)
                    }
                    pdfDocument.close()
                    bitmap.recycle()

                    // Import to Document Repository
                    PdfImporter.importPdfAsDocument(pdfFile, repository, "PhotoToPdf")
                    pdfFile.delete()
                }
                setActiveTab("Documents")
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun togglePhotoSelection(photoId: String) {
        val selected = _selectedPhotoIds.value
        _selectedPhotoIds.value = if (photoId in selected) {
            selected - photoId
        } else {
            selected + photoId
        }
        if (_selectedPhotoIds.value.isEmpty()) {
            _isPhotoSelectionMode.value = false
        }
    }

    fun startPhotoSelection(photoId: String) {
        _isPhotoSelectionMode.value = true
        _selectedPhotoIds.value = setOf(photoId)
    }

    fun cancelPhotoSelection() {
        _isPhotoSelectionMode.value = false
        _selectedPhotoIds.value = emptySet()
    }

    fun selectAllPhotos(photoIds: List<String>) {
        _isPhotoSelectionMode.value = true
        _selectedPhotoIds.value = photoIds.toSet()
    }

    fun deleteSelectedPhotos() {
        val selected = _selectedPhotoIds.value.toList()
        if (selected.isEmpty()) return
        viewModelScope.launch {
            selected.forEach { id ->
                photoRepository.deletePhoto(id)
            }
            cancelPhotoSelection()
        }
    }

    fun convertSelectedPhotosToPdf(context: Context, onDocumentImported: (String) -> Unit) {
        val selectedIds = _selectedPhotoIds.value.toList()
        if (selectedIds.isEmpty()) return
        
        viewModelScope.launch {
            _isImporting.value = true
            try {
                val docId = withContext(Dispatchers.IO) {
                    val pdfDocument = PdfDocument()
                    val allPhotos = photoRepository.getAllPhotosSync()
                    val selectedPhotos = allPhotos.filter { it.id in selectedIds }
                    
                    if (selectedPhotos.isEmpty()) return@withContext null
                    
                    selectedPhotos.forEachIndexed { index, photo ->
                        val bitmap = com.paperflow.app.util.ImageProcessor.loadBitmap(photo.imagePath)
                        if (bitmap != null) {
                            val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                            val page = pdfDocument.startPage(pageInfo)
                            val canvas = page.canvas
                            val paint = Paint()
                            canvas.drawBitmap(bitmap, 0f, 0f, paint)
                            pdfDocument.finishPage(page)
                            bitmap.recycle()
                        }
                    }
                    
                    val tempPdfFile = File(context.cacheDir, "MergedPhotos_${System.currentTimeMillis()}.pdf")
                    FileOutputStream(tempPdfFile).use { fos ->
                        pdfDocument.writeTo(fos)
                    }
                    pdfDocument.close()
                    
                    val doc = PdfImporter.importPdfAsDocument(tempPdfFile, repository, "PhotosToPdf")
                    tempPdfFile.delete()
                    doc?.id
                }
                
                if (docId != null) {
                    cancelPhotoSelection()
                    setActiveTab("Documents")
                    onDocumentImported(docId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun savePhotoToGallery(context: Context, photo: PhotoEntity, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val srcFile = File(photo.imagePath)
                    if (!srcFile.exists()) return@withContext false

                    val contentResolver = context.contentResolver
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, photo.title.ifEmpty { "Photo_${System.currentTimeMillis()}" } + ".jpg")
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/PaperFlow")
                            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                    }

                    val imageUri = contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (imageUri != null) {
                        contentResolver.openOutputStream(imageUri).use { outStream ->
                            if (outStream != null) {
                                srcFile.inputStream().use { inStream ->
                                    inStream.copyTo(outStream)
                                }
                            }
                        }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            contentValues.clear()
                            contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                            contentResolver.update(imageUri, contentValues, null, null)
                        } else {
                            android.media.MediaScannerConnection.scanFile(
                                context,
                                arrayOf(srcFile.absolutePath),
                                arrayOf("image/jpeg"),
                                null
                            )
                        }
                        true
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
            onResult(success)
        }
    }
}
