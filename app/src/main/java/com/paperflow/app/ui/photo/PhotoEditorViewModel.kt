package com.paperflow.app.ui.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperflow.app.data.model.PhotoEntity
import com.paperflow.app.data.repository.PhotoRepository
import com.paperflow.app.util.AdjustmentValues
import com.paperflow.app.util.ImageFilters
import com.paperflow.app.util.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class PhotoEditorUiState(
    val photo: PhotoEntity? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val currentFilter: ImageFilters.Filter = ImageFilters.Filter.ORIGINAL,
    val filterIntensity: Float = 1f,
    val rotationAngle: Float = 0f, // total rotation
    val fineRotationAngle: Float = 0f, // slider -45 to 45
    val isFlippedHorizontally: Boolean = false,
    val isFlippedVertically: Boolean = false,
    val editorMode: PhotoEditorMode = PhotoEditorMode.FILTERS,
    val error: String? = null,
    val saveSuccess: Boolean = false
)

enum class PhotoEditorMode {
    FILTERS, CROP, ROTATE, ADJUST
}

data class HistoryState(
    val displayBitmap: Bitmap,
    val originalBitmap: Bitmap,
    val previewBitmap: Bitmap,
    val currentFilter: ImageFilters.Filter,
    val filterIntensity: Float,
    val rotationAngle: Float,
    val fineRotationAngle: Float,
    val isFlippedHorizontally: Boolean,
    val isFlippedVertically: Boolean,
    val adjustmentValues: AdjustmentValues
)

class PhotoEditorViewModel(
    private val appContext: Context,
    private val photoRepository: PhotoRepository,
    private val documentRepository: com.paperflow.app.data.repository.DocumentRepository,
    private val sharedPrefs: android.content.SharedPreferences,
    private val photoId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhotoEditorUiState())
    val uiState: StateFlow<PhotoEditorUiState> = _uiState.asStateFlow()

    private var originalBitmap: Bitmap? = null
    private var previewBitmap: Bitmap? = null
    
    // The current display/work bitmap (having crop/rotation/filters applied)
    private val _displayBitmap = MutableStateFlow<Bitmap?>(null)
    val displayBitmap: StateFlow<Bitmap?> = _displayBitmap.asStateFlow()

    // Undo/Redo Stacks using references to save memory and safeRecycle to prevent leaks
    private val _undoStack = kotlin.collections.ArrayDeque<HistoryState>()
    private val _redoStack = kotlin.collections.ArrayDeque<HistoryState>()
    
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    // Filter previews cache flow
    private val _filterPreviews = MutableStateFlow<Map<ImageFilters.Filter, Bitmap>>(emptyMap())
    val filterPreviews: StateFlow<Map<ImageFilters.Filter, Bitmap>> = _filterPreviews.asStateFlow()

    // Adjustment values
    private val _adjustmentValues = MutableStateFlow(AdjustmentValues())
    val adjustmentValues: StateFlow<AdjustmentValues> = _adjustmentValues.asStateFlow()

    private var isDraggingSlider = false
    private var isDraggingAdjustment = false
    private var isDraggingIntensity = false

    init {
        loadPhoto()
    }

    private fun loadPhoto() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val photo = photoRepository.getPhoto(photoId)
                if (photo == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Photo not found")
                    return@launch
                }

                // Setup backup of the original unedited photo file if it doesn't exist yet
                val photoFile = File(photo.imagePath)
                val origPath = photo.originalPath ?: (photo.imagePath + "_original")
                val origFile = File(origPath)
                withContext(Dispatchers.IO) {
                    if (!origFile.exists() && photoFile.exists()) {
                        photoFile.copyTo(origFile, overwrite = true)
                    }
                }

                if (photo.originalPath != origFile.absolutePath) {
                    photoRepository.updatePhoto(photo.copy(originalPath = origFile.absolutePath))
                }

                val bitmap = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(photo.imagePath)
                }

                if (bitmap == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Failed to load image file")
                    return@launch
                }

                originalBitmap = bitmap
                previewBitmap = createPreviewBitmap(bitmap)
                
                // Copy preview bitmap for display initially
                val initialDisplay = previewBitmap?.copy(Bitmap.Config.ARGB_8888, true)
                _displayBitmap.value = initialDisplay
                _uiState.value = _uiState.value.copy(photo = photo, isLoading = false)
                
                generateFilterPreviews()
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Failed to load photo")
            }
        }
    }

    private fun createPreviewBitmap(src: Bitmap): Bitmap {
        val maxDim = 1500f
        val w = src.width.toFloat()
        val h = src.height.toFloat()
        if (w <= maxDim && h <= maxDim) {
            return src.copy(Bitmap.Config.ARGB_8888, true)
        }
        val scale = minOf(maxDim / w, maxDim / h)
        return Bitmap.createScaledBitmap(src, (w * scale).toInt(), (h * scale).toInt(), true)
    }

    fun setEditorMode(mode: PhotoEditorMode) {
        _uiState.value = _uiState.value.copy(editorMode = mode)
    }

    fun selectFilter(filter: ImageFilters.Filter) {
        if (_uiState.value.currentFilter == filter) return
        pushUndoState()
        _uiState.value = _uiState.value.copy(currentFilter = filter, filterIntensity = 1f)
        reprocessAll()
    }

    fun updateFilterIntensity(intensity: Float) {
        if (!isDraggingIntensity) {
            pushUndoState()
            isDraggingIntensity = true
        }
        _uiState.value = _uiState.value.copy(filterIntensity = intensity)
        reprocessAll()
    }

    fun commitFilterIntensity() {
        isDraggingIntensity = false
    }

    fun rotate90(clockwise: Boolean) {
        pushUndoState()
        val delta = if (clockwise) 90f else -90f
        val newAngle = (_uiState.value.rotationAngle + delta) % 360f
        _uiState.value = _uiState.value.copy(rotationAngle = newAngle)
        reprocessAll()
    }

    fun updateFineRotation(angle: Float) {
        if (!isDraggingSlider) {
            pushUndoState()
            isDraggingSlider = true
        }
        _uiState.value = _uiState.value.copy(fineRotationAngle = angle)
        reprocessAll()
    }

    fun commitFineRotation() {
        isDraggingSlider = false
    }

    fun flipHorizontal() {
        pushUndoState()
        _uiState.value = _uiState.value.copy(isFlippedHorizontally = !_uiState.value.isFlippedHorizontally)
        reprocessAll()
    }

    fun flipVertical() {
        pushUndoState()
        _uiState.value = _uiState.value.copy(isFlippedVertically = !_uiState.value.isFlippedVertically)
        reprocessAll()
    }

    fun updateAdjustment(newValues: AdjustmentValues) {
        if (!isDraggingAdjustment) {
            pushUndoState()
            isDraggingAdjustment = true
        }
        _adjustmentValues.value = newValues
        reprocessAll()
    }

    fun commitAdjustment() {
        isDraggingAdjustment = false
    }

    private fun reprocessAll() {
        val src = previewBitmap ?: return
        val filter = _uiState.value.currentFilter
        val intensity = _uiState.value.filterIntensity
        val adj = _adjustmentValues.value
        _uiState.value = _uiState.value.copy(isLoading = true)

        viewModelScope.launch {
            try {
                val processed = withContext(Dispatchers.Default) {
                    processPipeline(src, filter, intensity, adj, _uiState.value)
                }
                
                val old = _displayBitmap.value
                _displayBitmap.value = processed
                safeRecycle(old)
                
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Processing error")
            }
        }
    }

    private fun processPipeline(
        src: Bitmap,
        filter: ImageFilters.Filter,
        intensity: Float,
        adj: AdjustmentValues,
        state: PhotoEditorUiState
    ): Bitmap {
        var temp = src.copy(Bitmap.Config.ARGB_8888, true)
        
        // 1. Filter with Intensity Blending
        if (filter != ImageFilters.Filter.ORIGINAL) {
            val filtered = ImageFilters.applyFilter(temp, filter)
            if (intensity < 1f) {
                val blended = ImageProcessor.blend(temp, filtered, intensity)
                filtered.recycle()
                temp.recycle()
                temp = blended
            } else {
                temp.recycle()
                temp = filtered
            }
        }
        
        // 2. Adjustments
        if (adj != AdjustmentValues()) {
            val adjusted = ImageProcessor.applyAdjustments(temp, adj)
            if (adjusted !== temp) temp.recycle()
            temp = adjusted
        }
        
        // 3. Geometry
        val matrix = Matrix()
        val totalRotation = state.rotationAngle + state.fineRotationAngle
        if (totalRotation != 0f) {
            matrix.postRotate(totalRotation)
        }
        
        val sx = if (state.isFlippedHorizontally) -1f else 1f
        val sy = if (state.isFlippedVertically) -1f else 1f
        if (sx != 1f || sy != 1f) {
            matrix.postScale(sx, sy)
        }
        
        val finalBmp = Bitmap.createBitmap(temp, 0, 0, temp.width, temp.height, matrix, true)
        if (finalBmp !== temp) temp.recycle()
        return finalBmp
    }

    fun applyCrop(left: Float, top: Float, right: Float, bottom: Float) {
        val orig = originalBitmap ?: return
        
        pushUndoState()
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val fullTransformed = processPipeline(orig, _uiState.value.currentFilter, _uiState.value.filterIntensity, _adjustmentValues.value, _uiState.value)
                
                val x1 = (left * fullTransformed.width).toInt().coerceIn(0, fullTransformed.width - 1)
                val y1 = (top * fullTransformed.height).toInt().coerceIn(0, fullTransformed.height - 1)
                val x2 = (right * fullTransformed.width).toInt().coerceIn(1, fullTransformed.width)
                val y2 = (bottom * fullTransformed.height).toInt().coerceIn(1, fullTransformed.height)
                val w = (x2 - x1).coerceAtLeast(1)
                val h = (y2 - y1).coerceAtLeast(1)
                
                val newOrig = Bitmap.createBitmap(fullTransformed, x1, y1, w, h)
                if (fullTransformed !== orig) fullTransformed.recycle()
                
                val newPrev = createPreviewBitmap(newOrig)
                
                withContext(Dispatchers.Main) {
                    val oldOriginal = originalBitmap
                    val oldPreview = previewBitmap
                    val oldDisplay = _displayBitmap.value
                    
                    originalBitmap = newOrig
                    previewBitmap = newPrev
                    
                    // Reset geometry and filters since they are now baked in
                    _uiState.value = _uiState.value.copy(
                        rotationAngle = 0f,
                        fineRotationAngle = 0f,
                        isFlippedHorizontally = false,
                        isFlippedVertically = false,
                        currentFilter = ImageFilters.Filter.ORIGINAL,
                        filterIntensity = 1f,
                        isLoading = false
                    )
                    _adjustmentValues.value = AdjustmentValues()
                    
                    _displayBitmap.value = previewBitmap?.copy(Bitmap.Config.ARGB_8888, true)
                    
                    safeRecycle(oldOriginal)
                    safeRecycle(oldPreview)
                    safeRecycle(oldDisplay)
                    
                    generateFilterPreviews()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Failed to crop image")
                }
            }
        }
    }

    private fun pushUndoState() {
        val currentDisplay = _displayBitmap.value ?: return
        val currentOriginal = originalBitmap ?: return
        val currentPreview = previewBitmap ?: return
        
        val state = HistoryState(
            displayBitmap = currentDisplay.copy(Bitmap.Config.ARGB_8888, true),
            originalBitmap = currentOriginal.copy(Bitmap.Config.ARGB_8888, true),
            previewBitmap = currentPreview.copy(Bitmap.Config.ARGB_8888, true),
            currentFilter = _uiState.value.currentFilter,
            filterIntensity = _uiState.value.filterIntensity,
            rotationAngle = _uiState.value.rotationAngle,
            fineRotationAngle = _uiState.value.fineRotationAngle,
            isFlippedHorizontally = _uiState.value.isFlippedHorizontally,
            isFlippedVertically = _uiState.value.isFlippedVertically,
            adjustmentValues = _adjustmentValues.value
        )
        _undoStack.addLast(state)
        
        if (_undoStack.size > 8) {
            val oldest = _undoStack.removeFirst()
            safeRecycle(oldest.displayBitmap)
            safeRecycle(oldest.originalBitmap)
            safeRecycle(oldest.previewBitmap)
        }
        _canUndo.value = true
        
        // Clear redo stack on new action
        clearRedoStack()
    }

    fun hasUnsavedChanges(): Boolean {
        return _undoStack.isNotEmpty()
    }

    fun undo() {
        if (_undoStack.isEmpty()) return
        
        // Push current state to redo stack
        val currentDisplay = _displayBitmap.value
        val currentOriginal = originalBitmap
        val currentPreview = previewBitmap
        if (currentDisplay != null && currentOriginal != null && currentPreview != null) {
            val currentState = HistoryState(
                displayBitmap = currentDisplay.copy(Bitmap.Config.ARGB_8888, true),
                originalBitmap = currentOriginal.copy(Bitmap.Config.ARGB_8888, true),
                previewBitmap = currentPreview.copy(Bitmap.Config.ARGB_8888, true),
                currentFilter = _uiState.value.currentFilter,
                filterIntensity = _uiState.value.filterIntensity,
                rotationAngle = _uiState.value.rotationAngle,
                fineRotationAngle = _uiState.value.fineRotationAngle,
                isFlippedHorizontally = _uiState.value.isFlippedHorizontally,
                isFlippedVertically = _uiState.value.isFlippedVertically,
                adjustmentValues = _adjustmentValues.value
            )
            _redoStack.addLast(currentState)
            _canRedo.value = true
        }

        val lastState = _undoStack.removeLast()
        _canUndo.value = _undoStack.isNotEmpty()
        
        val oldDisplay = _displayBitmap.value
        val oldOriginal = originalBitmap
        val oldPreview = previewBitmap
        
        _displayBitmap.value = lastState.displayBitmap
        originalBitmap = lastState.originalBitmap
        previewBitmap = lastState.previewBitmap
        
        _uiState.value = _uiState.value.copy(
            currentFilter = lastState.currentFilter,
            filterIntensity = lastState.filterIntensity,
            rotationAngle = lastState.rotationAngle,
            fineRotationAngle = lastState.fineRotationAngle,
            isFlippedHorizontally = lastState.isFlippedHorizontally,
            isFlippedVertically = lastState.isFlippedVertically
        )
        _adjustmentValues.value = lastState.adjustmentValues
        
        safeRecycle(oldDisplay)
        safeRecycle(oldOriginal)
        safeRecycle(oldPreview)
        
        generateFilterPreviews()
    }

    fun redo() {
        if (_redoStack.isEmpty()) return
        
        // Push current state to undo stack
        val currentDisplay = _displayBitmap.value
        val currentOriginal = originalBitmap
        val currentPreview = previewBitmap
        if (currentDisplay != null && currentOriginal != null && currentPreview != null) {
            val currentState = HistoryState(
                displayBitmap = currentDisplay.copy(Bitmap.Config.ARGB_8888, true),
                originalBitmap = currentOriginal.copy(Bitmap.Config.ARGB_8888, true),
                previewBitmap = currentPreview.copy(Bitmap.Config.ARGB_8888, true),
                currentFilter = _uiState.value.currentFilter,
                filterIntensity = _uiState.value.filterIntensity,
                rotationAngle = _uiState.value.rotationAngle,
                fineRotationAngle = _uiState.value.fineRotationAngle,
                isFlippedHorizontally = _uiState.value.isFlippedHorizontally,
                isFlippedVertically = _uiState.value.isFlippedVertically,
                adjustmentValues = _adjustmentValues.value
            )
            _undoStack.addLast(currentState)
            _canUndo.value = true
        }

        val nextState = _redoStack.removeLast()
        _canRedo.value = _redoStack.isNotEmpty()
        
        val oldDisplay = _displayBitmap.value
        val oldOriginal = originalBitmap
        val oldPreview = previewBitmap
        
        _displayBitmap.value = nextState.displayBitmap
        originalBitmap = nextState.originalBitmap
        previewBitmap = nextState.previewBitmap
        
        _uiState.value = _uiState.value.copy(
            currentFilter = nextState.currentFilter,
            filterIntensity = nextState.filterIntensity,
            rotationAngle = nextState.rotationAngle,
            fineRotationAngle = nextState.fineRotationAngle,
            isFlippedHorizontally = nextState.isFlippedHorizontally,
            isFlippedVertically = nextState.isFlippedVertically
        )
        _adjustmentValues.value = nextState.adjustmentValues
        
        safeRecycle(oldDisplay)
        safeRecycle(oldOriginal)
        safeRecycle(oldPreview)
        
        generateFilterPreviews()
    }

    fun revertToOriginal() {
        val photo = _uiState.value.photo ?: return
        val origPath = photo.originalPath ?: (photo.imagePath + "_original")
        val origFile = File(origPath)
        if (!origFile.exists()) {
            _uiState.value = _uiState.value.copy(error = "Original backup file not found")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(origFile.absolutePath)
                }
                if (bitmap != null) {
                    clearUndoStack()
                    clearRedoStack()
                    
                    val oldDisplay = _displayBitmap.value
                    val oldOriginal = originalBitmap
                    val oldPreview = previewBitmap
                    
                    originalBitmap = bitmap
                    previewBitmap = createPreviewBitmap(bitmap)
                    
                    _displayBitmap.value = previewBitmap?.copy(Bitmap.Config.ARGB_8888, true)
                    
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        rotationAngle = 0f,
                        fineRotationAngle = 0f,
                        isFlippedHorizontally = false,
                        isFlippedVertically = false,
                        currentFilter = ImageFilters.Filter.ORIGINAL,
                        filterIntensity = 1f
                    )
                    _adjustmentValues.value = AdjustmentValues()
                    
                    safeRecycle(oldDisplay)
                    safeRecycle(oldOriginal)
                    safeRecycle(oldPreview)
                    
                    generateFilterPreviews()
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Failed to load original file")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Failed to revert: ${e.message}")
            }
        }
    }

    private fun clearUndoStack() {
        val bitmapsToRecycle = mutableListOf<Bitmap>()
        while (_undoStack.isNotEmpty()) {
            val state = _undoStack.removeFirst()
            bitmapsToRecycle.add(state.displayBitmap)
            bitmapsToRecycle.add(state.originalBitmap)
            bitmapsToRecycle.add(state.previewBitmap)
        }
        _canUndo.value = false
        bitmapsToRecycle.forEach { safeRecycle(it) }
    }

    private fun clearRedoStack() {
        val bitmapsToRecycle = mutableListOf<Bitmap>()
        while (_redoStack.isNotEmpty()) {
            val state = _redoStack.removeFirst()
            bitmapsToRecycle.add(state.displayBitmap)
            bitmapsToRecycle.add(state.originalBitmap)
            bitmapsToRecycle.add(state.previewBitmap)
        }
        _canRedo.value = false
        bitmapsToRecycle.forEach { safeRecycle(it) }
    }

    private fun safeRecycle(bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) return
        if (bitmap == _displayBitmap.value || bitmap == originalBitmap || bitmap == previewBitmap) return
        val inStack = _undoStack.any { it.displayBitmap == bitmap || it.originalBitmap == bitmap || it.previewBitmap == bitmap }
        val inRedoStack = _redoStack.any { it.displayBitmap == bitmap || it.originalBitmap == bitmap || it.previewBitmap == bitmap }
        if (!inStack && !inRedoStack) {
            bitmap.recycle()
        }
    }

    private fun generateFilterPreviews() {
        val src = previewBitmap ?: return
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val thumbWidth = 120
                val thumbHeight = 120
                val thumb = Bitmap.createScaledBitmap(src, thumbWidth, thumbHeight, true)
                
                val previews = ImageFilters.Filter.values().associateWith { filter ->
                    ImageFilters.applyFilter(thumb, filter)
                }
                
                if (thumb !== src) {
                    thumb.recycle()
                }
                
                val oldPreviews = _filterPreviews.value
                withContext(Dispatchers.Main) {
                    _filterPreviews.value = previews
                    oldPreviews.values.forEach { it.recycle() }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveEdits() {
        val orig = originalBitmap ?: return
        val photo = _uiState.value.photo ?: return
        
        _uiState.value = _uiState.value.copy(isSaving = true)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) {
                    // Process pipeline on high-res original
                    val finalBitmap = processPipeline(orig, _uiState.value.currentFilter, _uiState.value.filterIntensity, _adjustmentValues.value, _uiState.value)
                    
                    val file = File(photo.imagePath)
                    FileOutputStream(file).use { fos ->
                        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                    }

                    photoRepository.updatePhoto(
                        photo.copy(
                            width = finalBitmap.width,
                            height = finalBitmap.height,
                            fileSize = file.length(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    
                    if (finalBitmap !== orig) {
                        finalBitmap.recycle()
                    }
                }
                _uiState.value = _uiState.value.copy(isSaving = false, saveSuccess = true)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.message ?: "Failed to save photo edits")
            }
        }
    }

    fun saveEditsAndNavigate(onSaved: () -> Unit) {
        val orig = originalBitmap ?: return
        val photo = _uiState.value.photo ?: return
        
        _uiState.value = _uiState.value.copy(isSaving = true)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) {
                    val finalBitmap = processPipeline(orig, _uiState.value.currentFilter, _uiState.value.filterIntensity, _adjustmentValues.value, _uiState.value)
                    
                    val file = File(photo.imagePath)
                    FileOutputStream(file).use { fos ->
                        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                    }

                    photoRepository.updatePhoto(
                        photo.copy(
                            width = finalBitmap.width,
                            height = finalBitmap.height,
                            fileSize = file.length(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    if (finalBitmap !== orig) finalBitmap.recycle()
                }
                _uiState.value = _uiState.value.copy(isSaving = false)
                onSaved()
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.message ?: "Failed to save photo edits")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun resetSuccess() {
        _uiState.value = _uiState.value.copy(saveSuccess = false)
    }

    override fun onCleared() {
        super.onCleared()
        originalBitmap = null
        previewBitmap = null
        _displayBitmap.value = null
        clearUndoStack()
        clearRedoStack()
        val previews = _filterPreviews.value
        _filterPreviews.value = emptyMap()
        previews.values.forEach { it.recycle() }
    }
}
