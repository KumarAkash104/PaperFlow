package com.paperflow.app.ui.camera

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperflow.app.data.repository.DocumentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.FileOutputStream
import com.paperflow.app.util.ImageProcessor

data class CapturedPage(
    val uri: Uri,
    val filePath: String,
    val isFromCamera: Boolean = false,
)

data class CameraUiState(
    val documentId: String? = null,
    val capturedPages: List<CapturedPage> = emptyList(),
    val flashEnabled: Boolean = false,
    val isCapturing: Boolean = false,
    val statusText: String = "Ready to scan",
    val scanQuality: Int = 95,  // JPEG quality: 95=High, 75=Medium, 50=Low
)

class CameraViewModel(
    private val repository: DocumentRepository,
    private val photoRepository: com.paperflow.app.data.repository.PhotoRepository,
    private val existingDocumentId: String?,
    val cameraMode: String = "scan",
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    init {
        if (cameraMode == "photo") {
            _uiState.value = _uiState.value.copy(statusText = "Ready to take photo")
        } else {
            if (existingDocumentId != null && existingDocumentId != "new") {
                _uiState.value = _uiState.value.copy(documentId = existingDocumentId)
                // Load existing pages
                viewModelScope.launch {
                    val pages = repository.getPagesSync(existingDocumentId)
                    val captured = pages.map { page ->
                        CapturedPage(
                            uri = Uri.parse("file://${page.imagePath}"),
                            filePath = page.imagePath,
                        )
                    }
                    _uiState.value = _uiState.value.copy(capturedPages = captured)
                }
            }
        }
    }

    fun toggleFlash() {
        _uiState.value = _uiState.value.copy(flashEnabled = !_uiState.value.flashEnabled)
    }

    fun setScanQuality(quality: Int) {
        _uiState.value = _uiState.value.copy(scanQuality = quality)
    }

    fun onCaptureStart() {
        _uiState.value = _uiState.value.copy(isCapturing = true, statusText = "Capturing...")
    }

    fun onCaptured(uri: Uri, filePath: String) {
        val page = CapturedPage(uri = uri, filePath = filePath, isFromCamera = true)
        val updated = _uiState.value.capturedPages + page
        _uiState.value = _uiState.value.copy(
            capturedPages = updated,
            isCapturing = false,
            statusText = if (cameraMode == "photo")
                "${updated.size} photo${if (updated.size > 1) "s" else ""} captured"
            else
                "${updated.size} page${if (updated.size > 1) "s" else ""} scanned",
        )
    }

    fun importFromGallery(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCapturing = true, statusText = "Importing...")
            val newPages = mutableListOf<CapturedPage>()
            
            withContext(Dispatchers.IO) {
                for (uri in uris) {
                    val file = if (cameraMode == "photo") {
                        photoRepository.createPhotoImageFile()
                    } else {
                        repository.createScanImageFile()
                    }
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            file.outputStream().use { output -> input.copyTo(output) }
                        }
                        newPages.add(CapturedPage(Uri.fromFile(file), file.absolutePath, isFromCamera = false))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            
            val updated = _uiState.value.capturedPages + newPages
            _uiState.value = _uiState.value.copy(
                capturedPages = updated,
                isCapturing = false,
                statusText = if (cameraMode == "photo")
                    "${updated.size} photo${if (updated.size > 1) "s" else ""} captured"
                else
                    "${updated.size} page${if (updated.size > 1) "s" else ""} scanned",
            )
        }
    }

    fun onCaptureError(message: String) {
        _uiState.value = _uiState.value.copy(
            isCapturing = false,
            statusText = "Failed: $message",
        )
    }

    fun removePage(page: CapturedPage) {
        val pages = _uiState.value.capturedPages
        if (!pages.contains(page)) return
        java.io.File(page.filePath).delete()
        val updated = pages.filter { it != page }
        _uiState.value = _uiState.value.copy(
            capturedPages = updated,
            statusText = if (updated.isEmpty()) {
                if (cameraMode == "photo") "Ready to take photo" else "Ready to scan"
            } else {
                if (cameraMode == "photo")
                    "${updated.size} photo${if (updated.size > 1) "s" else ""} captured"
                else
                    "${updated.size} page${if (updated.size > 1) "s" else ""} scanned"
            },
        )
    }

    fun createImageFile(): java.io.File = if (cameraMode == "photo") {
        photoRepository.createPhotoImageFile()
    } else {
        repository.createScanImageFile()
    }

    /** Save all captured pages as a new document, returns document ID. */
    suspend fun saveDocument(): String {
        val docId = _uiState.value.documentId
        val doc = if (docId != null) {
            repository.getDocument(docId) ?: repository.createDocument()
        } else {
            repository.createDocument()
        }

        // Store the new docId in state so subsequent saves update the SAME document
        _uiState.value = _uiState.value.copy(documentId = doc.id)

        val existingCount = if (docId != null) {
            repository.getPagesSync(doc.id).size
        } else {
            0
        }

        withContext(Dispatchers.IO) {
            _uiState.value.capturedPages.forEachIndexed { index, page ->
                // Only add truly new pages (those not already in DB for existing docs)
                if (docId == null || index >= existingCount) {
                    val file = java.io.File(page.filePath)
                    if (file.exists() && page.isFromCamera) {
                        try {
                            val bitmap = ImageProcessor.loadBitmap(file.absolutePath)
                            if (bitmap != null) {
                                FileOutputStream(file).use { fos ->
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, _uiState.value.scanQuality, fos)
                                }
                                bitmap.recycle()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    repository.addPage(doc.id, page.filePath, existingCount + index)
                }
            }
        }
        return doc.id
    }

    suspend fun saveAndGetPageId(tappedPage: CapturedPage): String? {
        val docId = saveDocument()
        val pages = repository.getPagesSync(docId)
        return pages.find { it.imagePath == tappedPage.filePath }?.id
    }

    suspend fun savePhotos(): String {
        withContext(Dispatchers.IO) {
            _uiState.value.capturedPages.forEachIndexed { index, page ->
                val file = java.io.File(page.filePath)
                if (file.exists()) {
                    var width = 0
                    var height = 0
                    
                    if (page.isFromCamera) {
                        try {
                            val bitmap = ImageProcessor.loadBitmap(file.absolutePath)
                            if (bitmap != null) {
                                FileOutputStream(file).use { fos ->
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, _uiState.value.scanQuality, fos)
                                }
                                width = bitmap.width
                                height = bitmap.height
                                bitmap.recycle()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    
                    if (width == 0 || height == 0) {
                        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
                        width = options.outWidth
                        height = options.outHeight
                    }
                    
                    photoRepository.addPhoto(
                        title = "Photo_${System.currentTimeMillis()}_${index + 1}",
                        imagePath = file.absolutePath,
                        width = width,
                        height = height,
                        fileSize = file.length(),
                        originalPath = file.absolutePath
                    )
                }
            }
        }
        return "photos_saved"
    }

    suspend fun saveAndGetPhotoId(tappedPage: CapturedPage): String? {
        savePhotos()
        return withContext(Dispatchers.IO) {
            val photos = photoRepository.getAllPhotosSync()
            photos.find { it.imagePath == tappedPage.filePath }?.id
        }
    }
}
