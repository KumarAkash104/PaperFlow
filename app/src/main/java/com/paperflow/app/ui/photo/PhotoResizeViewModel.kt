package com.paperflow.app.ui.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
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
                currentSourceBitmap?.recycle()
                currentSourceBitmap = null

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
                var rawBitmap = BitmapFactory.decodeStream(stream2, null, decodeOpts)
                stream2?.close()

                if (rawBitmap == null) {
                    _uiState.value = _uiState.value.copy(isProcessing = false, error = "Failed to load image")
                    return@launch
                }

                val bitmap = applyExifRotation(rawBitmap, uri)
                currentSourceBitmap = bitmap
                
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
                    originalWidth = bitmap.width,
                    originalHeight = bitmap.height,
                    originalSize = fileSize,
                    inputWidth = bitmap.width.toString(),
                    inputHeight = bitmap.height.toString(),
                    resizedWidth = bitmap.width,
                    resizedHeight = bitmap.height,
                    resizedSize = fileSize
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(isProcessing = false, error = e.message ?: "Failed to load image")
            }
        }
    }

    private fun applyExifRotation(bitmap: Bitmap, uri: Uri): Bitmap {
        return try {
            appContext.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                val matrix = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
                    else -> return bitmap
                }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated !== bitmap) bitmap.recycle()
                rotated
            } ?: bitmap
        } catch (_: Exception) {
            bitmap
        }
    }

    fun onWidthChanged(widthStr: String) {
        val oldState = _uiState.value
        val w = widthStr.toIntOrNull()
        if (oldState.lockAspectRatio && w != null && w > 0 && oldState.originalWidth > 0) {
            val ratio = oldState.originalHeight.toFloat() / oldState.originalWidth.toFloat()
            val h = (w * ratio).toInt().coerceAtLeast(1)
            _uiState.value = oldState.copy(inputWidth = widthStr, inputHeight = h.toString())
        } else {
            _uiState.value = oldState.copy(inputWidth = widthStr)
        }
    }

    fun onHeightChanged(heightStr: String) {
        val oldState = _uiState.value
        val h = heightStr.toIntOrNull()
        if (oldState.lockAspectRatio && h != null && h > 0 && oldState.originalHeight > 0) {
            val ratio = oldState.originalWidth.toFloat() / oldState.originalHeight.toFloat()
            val w = (h * ratio).toInt().coerceAtLeast(1)
            _uiState.value = oldState.copy(inputHeight = heightStr, inputWidth = w.toString())
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
                val targetW = (state.inputWidth.toIntOrNull() ?: state.originalWidth).coerceAtLeast(1)
                val targetH = (state.inputHeight.toIntOrNull() ?: state.originalHeight).coerceAtLeast(1)
                val targetSizeKb = state.inputTargetSizeKb.toIntOrNull()

                withContext(Dispatchers.IO) {
                    var currentW = targetW
                    var currentH = targetH
                    var scaledBitmap = Bitmap.createScaledBitmap(bitmap, currentW, currentH, true)
                    var resultBytes = ByteArray(0)

                    if (targetSizeKb != null && targetSizeKb > 0) {
                        val maxBytes = targetSizeKb * 1024

                        // Tier 1: Try binary search quality (10..95) on the target dimensions
                        var low = 10
                        var high = 95
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
                                low = mid + 1
                            } else {
                                high = mid - 1
                            }
                        }

                        if (bestBytes.isNotEmpty()) {
                            resultBytes = bestBytes
                        } else {
                            // Tier 2: Quality=10 is still too large. Iteratively downscale dimensions until under target KB.
                            var curScaleBmp = scaledBitmap
                            var q = 15
                            while (currentW > 50 && currentH > 50) {
                                currentW = (currentW * 0.85f).toInt().coerceAtLeast(50)
                                currentH = (currentH * 0.85f).toInt().coerceAtLeast(50)
                                val nextBmp = Bitmap.createScaledBitmap(curScaleBmp, currentW, currentH, true)
                                if (curScaleBmp !== scaledBitmap && curScaleBmp !== bitmap) {
                                    curScaleBmp.recycle()
                                }
                                curScaleBmp = nextBmp

                                val outStream = ByteArrayOutputStream()
                                curScaleBmp.compress(Bitmap.CompressFormat.JPEG, q, outStream)
                                val bytes = outStream.toByteArray()
                                if (bytes.size <= maxBytes) {
                                    resultBytes = bytes
                                    break
                                }
                            }
                            if (resultBytes.isEmpty()) {
                                val outStream = ByteArrayOutputStream()
                                curScaleBmp.compress(Bitmap.CompressFormat.JPEG, 10, outStream)
                                resultBytes = outStream.toByteArray()
                            }
                            if (curScaleBmp !== scaledBitmap && curScaleBmp !== bitmap) {
                                curScaleBmp.recycle()
                            }
                        }
                    } else {
                        val outStream = ByteArrayOutputStream()
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outStream)
                        resultBytes = outStream.toByteArray()
                    }

                    // Save preview
                    val previewFile = File(appContext.cacheDir, "resized_preview.jpg")
                    FileOutputStream(previewFile).use { fos ->
                        fos.write(resultBytes)
                    }

                    if (scaledBitmap !== bitmap) {
                        scaledBitmap.recycle()
                    }

                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            isProcessing = false,
                            resizedWidth = currentW,
                            resizedHeight = currentH,
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
        currentSourceBitmap?.recycle()
        currentSourceBitmap = null
    }
}

