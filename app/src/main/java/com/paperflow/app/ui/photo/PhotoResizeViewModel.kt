package com.paperflow.app.ui.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperflow.app.data.repository.PhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

data class PhotoResizeUiState(
    val originalUri: Uri? = null,
    val isProcessing: Boolean = false,
    val inputWidth: String = "",
    val inputHeight: String = "",
    val inputTargetSizeKb: String = "",
    val lockAspectRatio: Boolean = true,
    val originalWidth: Int = 0,
    val originalHeight: Int = 0,
    val originalSize: Long = 0, // bytes
    val resizedWidth: Int = 0,
    val resizedHeight: Int = 0,
    val resizedSize: Long = 0, // bytes
    val previewPath: String? = null,
    val resizeSuccess: Boolean = false,
    val error: String? = null
)

class PhotoResizeViewModel(
    private val appContext: Context,
    private val photoRepository: PhotoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhotoResizeUiState())
    val uiState: StateFlow<PhotoResizeUiState> = _uiState.asStateFlow()

    private var currentSourceBitmap: Bitmap? = null

    fun selectPhoto(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, error = null)
            try {
                val stream = appContext.contentResolver.openInputStream(uri)
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, options)
                stream?.close()

                // Calculate sample size for OOM protection
                var sampleSize = 1
                val maxDim = 4000
                while (options.outWidth / sampleSize > maxDim || options.outHeight / sampleSize > maxDim) {
                    sampleSize *= 2
                }

                val stream2 = appContext.contentResolver.openInputStream(uri)
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val bitmap = BitmapFactory.decodeStream(stream2, null, decodeOpts)
                stream2?.close()

                if (bitmap == null) {
                    _uiState.value = _uiState.value.copy(isProcessing = false, error = "Failed to load image")
                    return@launch
                }

                currentSourceBitmap = bitmap
                
                // Get file size if it's a file uri
                var fileSize: Long = 0
                try {
                    appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                        fileSize = it.length
                    }
                } catch (e: Exception) {
                    fileSize = bitmap.byteCount.toLong()
                }

                _uiState.value = _uiState.value.copy(
                    originalUri = uri,
                    isProcessing = false,
                    originalWidth = options.outWidth,
                    originalHeight = options.outHeight,
                    originalSize = fileSize,
                    inputWidth = options.outWidth.toString(),
                    inputHeight = options.outHeight.toString(),
                    resizedWidth = options.outWidth,
                    resizedHeight = options.outHeight,
                    resizedSize = fileSize
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(isProcessing = false, error = e.message ?: "Failed to load image")
            }
        }
    }

    fun onWidthChanged(widthStr: String) {
        val oldState = _uiState.value
        if (oldState.lockAspectRatio && widthStr.isNotEmpty() && oldState.originalWidth > 0) {
            try {
                val w = widthStr.toInt()
                val ratio = oldState.originalHeight.toFloat() / oldState.originalWidth.toFloat()
                val h = (w * ratio).toInt()
                _uiState.value = oldState.copy(inputWidth = widthStr, inputHeight = h.toString())
            } catch (_: Exception) {
                _uiState.value = oldState.copy(inputWidth = widthStr)
            }
        } else {
            _uiState.value = oldState.copy(inputWidth = widthStr)
        }
    }

    fun onHeightChanged(heightStr: String) {
        val oldState = _uiState.value
        if (oldState.lockAspectRatio && heightStr.isNotEmpty() && oldState.originalHeight > 0) {
            try {
                val h = heightStr.toInt()
                val ratio = oldState.originalWidth.toFloat() / oldState.originalHeight.toFloat()
                val w = (h * ratio).toInt()
                _uiState.value = oldState.copy(inputHeight = heightStr, inputWidth = w.toString())
            } catch (_: Exception) {
                _uiState.value = oldState.copy(inputHeight = heightStr)
            }
        } else {
            _uiState.value = oldState.copy(inputHeight = heightStr)
        }
    }

    fun onTargetSizeChanged(sizeKbStr: String) {
        _uiState.value = _uiState.value.copy(inputTargetSizeKb = sizeKbStr)
    }

    fun toggleLockAspectRatio() {
        _uiState.value = _uiState.value.copy(lockAspectRatio = !_uiState.value.lockAspectRatio)
    }

    fun performResizeAndCompress() {
        val bitmap = currentSourceBitmap ?: return
        val state = _uiState.value
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, error = null)
            try {
                val targetW = state.inputWidth.toIntOrNull() ?: state.originalWidth
                val targetH = state.inputHeight.toIntOrNull() ?: state.originalHeight
                val targetSizeKb = state.inputTargetSizeKb.toIntOrNull()

                withContext(Dispatchers.IO) {
                    // Step 1: Scale the bitmap
                    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                    
                    // Step 2: Quality Compression Search
                    var quality = 90
                    var resultBytes = ByteArray(0)
                    
                    if (targetSizeKb != null && targetSizeKb > 0) {
                        val maxBytes = targetSizeKb * 1024
                        
                        // Binary search for JPEG quality
                        var low = 5
                        var high = 100
                        var bestQuality = 90
                        var bestBytes = ByteArray(0)

                        while (low <= high) {
                            val mid = (low + high) / 2
                            val outStream = ByteArrayOutputStream()
                            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, mid, outStream)
                            val bytes = outStream.toByteArray()
                            
                            if (bytes.size <= maxBytes) {
                                bestQuality = mid
                                bestBytes = bytes
                                low = mid + 1 // try higher quality
                            } else {
                                high = mid - 1 // must lower quality
                            }
                        }
                        
                        quality = bestQuality
                        resultBytes = if (bestBytes.isNotEmpty()) bestBytes else {
                            // Fallback if target size is impossibly small
                            val outStream = ByteArrayOutputStream()
                            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 5, outStream)
                            outStream.toByteArray()
                        }
                    } else {
                        // Standard compression
                        val outStream = ByteArrayOutputStream()
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outStream)
                        resultBytes = outStream.toByteArray()
                    }

                    // Save preview
                    val previewFile = File(appContext.cacheDir, "resized_preview.jpg")
                    FileOutputStream(previewFile).use { fos ->
                        fos.write(resultBytes)
                    }

                    if (scaledBitmap != bitmap) {
                        scaledBitmap.recycle()
                    }

                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            isProcessing = false,
                            resizedWidth = targetW,
                            resizedHeight = targetH,
                            resizedSize = resultBytes.size.toLong(),
                            previewPath = previewFile.absolutePath
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(isProcessing = false, error = e.message ?: "Failed to resize image")
            }
        }
    }

    fun saveResizedPhoto(title: String) {
        val previewPath = _uiState.value.previewPath ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            try {
                withContext(Dispatchers.IO) {
                    val finalFile = photoRepository.createPhotoImageFile()
                    File(previewPath).copyTo(finalFile, overwrite = true)
                    
                    photoRepository.addPhoto(
                        title = if (title.isBlank()) "Resized Photo" else title,
                        imagePath = finalFile.absolutePath,
                        width = _uiState.value.resizedWidth,
                        height = _uiState.value.resizedHeight,
                        fileSize = finalFile.length(),
                        originalPath = finalFile.absolutePath
                    )
                }
                _uiState.value = _uiState.value.copy(isProcessing = false, resizeSuccess = true)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(isProcessing = false, error = e.message ?: "Failed to save photo")
            }
        }
    }

    fun resetSuccess() {
        _uiState.value = _uiState.value.copy(resizeSuccess = false)
    }

    override fun onCleared() {
        super.onCleared()
        currentSourceBitmap = null
    }
}
