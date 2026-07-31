package com.paperflow.app.ui.editor

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperflow.app.data.repository.DocumentRepository
import com.paperflow.app.util.AdjustmentValues
import com.paperflow.app.util.EditorMode
import com.paperflow.app.util.FilterType
import com.paperflow.app.util.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class EditorUiState(
    val pageId: String = "",
    val documentId: String? = null,
    val documentPages: List<com.paperflow.app.data.model.PageEntity> = emptyList(),
    val imagePath: String = "",
    val originalBitmap: Bitmap? = null,
    val baseBitmap: Bitmap? = null,      // after rotation/crop
    val previewBitmap: Bitmap? = null,   // after filter + adjustments
    val mode: EditorMode = EditorMode.FILTER,
    val selectedFilter: FilterType = FilterType.ORIGINAL,
    val filterIntensity: Int = 100,      // 0..100 blend with original
    val adjustments: AdjustmentValues = AdjustmentValues(),
    val cropCorners: List<PointF>? = null,
    val rotationDegrees: Int = 0,
    val isProcessing: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
)

class PageEditorViewModel(
    private val repository: DocumentRepository,
    private val pageId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState(pageId = pageId))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var previewJob: Job? = null
    private val previewSize = 1600

    init {
        loadPage()
    }

    private fun loadPage(targetPageId: String = pageId) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessing = true, 
                pageId = targetPageId,
                previewBitmap = null,
                originalBitmap = null,
                baseBitmap = null
            )
            val (page, pages) = withContext(Dispatchers.IO) {
                val p = repository.getPage(targetPageId)
                if (p != null) {
                    val docPages = repository.getPagesSync(p.documentId)
                    p to docPages
                } else {
                    null to emptyList()
                }
            }
            if (page == null) return@launch

            val pageFile = File(page.imagePath)
            val origFile = File(page.imagePath + "_original")
            
            withContext(Dispatchers.IO) {
                if (!origFile.exists() && pageFile.exists()) {
                    pageFile.copyTo(origFile)
                }
            }

            val bitmap = withContext(Dispatchers.IO) {
                ImageProcessor.loadBitmap(page.imagePath, previewSize)
            } ?: return@launch

            _uiState.value = _uiState.value.copy(
                documentId = page.documentId,
                documentPages = pages,
                imagePath = page.imagePath,
                originalBitmap = bitmap,
                baseBitmap = bitmap,
                previewBitmap = bitmap,
                isProcessing = false,
                cropCorners = listOf(
                    PointF(bitmap.width * 0.08f, bitmap.height * 0.08f),
                    PointF(bitmap.width * 0.92f, bitmap.height * 0.08f),
                    PointF(bitmap.width * 0.92f, bitmap.height * 0.92f),
                    PointF(bitmap.width * 0.08f, bitmap.height * 0.92f),
                ),
            )
        }
    }

    fun setMode(mode: EditorMode) {
        _uiState.value = _uiState.value.copy(mode = mode)
    }

    fun setFilter(filter: FilterType) {
        _uiState.value = _uiState.value.copy(
            selectedFilter = filter,
            filterIntensity = 100,
        )
        updatePreview()
    }

    fun setFilterIntensity(intensity: Int) {
        _uiState.value = _uiState.value.copy(filterIntensity = intensity.coerceIn(0, 100))
        updatePreviewDebounced()
    }

    fun setAdjustments(adj: AdjustmentValues) {
        _uiState.value = _uiState.value.copy(adjustments = adj)
        updatePreviewDebounced()
    }

    fun setCropCorners(corners: List<PointF>) {
        _uiState.value = _uiState.value.copy(cropCorners = corners)
    }

    fun applyCrop() {
        val state = _uiState.value
        val base = state.baseBitmap ?: return
        val corners = state.cropCorners ?: return
        viewModelScope.launch {
            _uiState.value = state.copy(isProcessing = true)
            val cropped = withContext(Dispatchers.Default) {
                ImageProcessor.perspectiveCrop(base, corners)
            }
            _uiState.value = _uiState.value.copy(
                baseBitmap = cropped,
                isProcessing = false,
                mode = EditorMode.FILTER,
                cropCorners = listOf(
                    PointF(cropped.width * 0.08f, cropped.height * 0.08f),
                    PointF(cropped.width * 0.92f, cropped.height * 0.08f),
                    PointF(cropped.width * 0.92f, cropped.height * 0.92f),
                    PointF(cropped.width * 0.08f, cropped.height * 0.92f),
                ),
            )
            updatePreview()
        }
    }

    fun autoDetectEdges() {
        val base = _uiState.value.baseBitmap ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            val detected = withContext(Dispatchers.Default) {
                ImageProcessor.autoDetectEdges(base)
            }
            _uiState.value = _uiState.value.copy(
                cropCorners = detected,
                isProcessing = false,
            )
        }
    }

    fun rotateRight() {
        val base = _uiState.value.baseBitmap ?: return
        viewModelScope.launch {
            val rotated = withContext(Dispatchers.Default) {
                ImageProcessor.rotate(base, 90)
            }
            _uiState.value = _uiState.value.copy(
                baseBitmap = rotated,
                rotationDegrees = (_uiState.value.rotationDegrees + 90) % 360,
                cropCorners = listOf(
                    PointF(rotated.width * 0.08f, rotated.height * 0.08f),
                    PointF(rotated.width * 0.92f, rotated.height * 0.08f),
                    PointF(rotated.width * 0.92f, rotated.height * 0.92f),
                    PointF(rotated.width * 0.08f, rotated.height * 0.92f),
                ),
            )
            updatePreview()
        }
    }

    fun rotateLeft() {
        val base = _uiState.value.baseBitmap ?: return
        viewModelScope.launch {
            val rotated = withContext(Dispatchers.Default) {
                ImageProcessor.rotate(base, -90)
            }
            _uiState.value = _uiState.value.copy(
                baseBitmap = rotated,
                rotationDegrees = (_uiState.value.rotationDegrees + 270) % 360,
                cropCorners = listOf(
                    PointF(rotated.width * 0.08f, rotated.height * 0.08f),
                    PointF(rotated.width * 0.92f, rotated.height * 0.08f),
                    PointF(rotated.width * 0.92f, rotated.height * 0.92f),
                    PointF(rotated.width * 0.08f, rotated.height * 0.92f),
                ),
            )
            updatePreview()
        }
    }

    fun flipHorizontal() {
        val base = _uiState.value.baseBitmap ?: return
        viewModelScope.launch {
            val flipped = withContext(Dispatchers.Default) { ImageProcessor.flipH(base) }
            _uiState.value = _uiState.value.copy(baseBitmap = flipped)
            updatePreview()
        }
    }

    fun flipVertical() {
        val base = _uiState.value.baseBitmap ?: return
        viewModelScope.launch {
            val flipped = withContext(Dispatchers.Default) { ImageProcessor.flipV(base) }
            _uiState.value = _uiState.value.copy(baseBitmap = flipped)
            updatePreview()
        }
    }

    fun resetAll() {
        val orig = _uiState.value.originalBitmap ?: return
        _uiState.value = _uiState.value.copy(
            baseBitmap = orig,
            selectedFilter = FilterType.ORIGINAL,
            filterIntensity = 100,
            adjustments = AdjustmentValues(),
            rotationDegrees = 0,
            previewBitmap = orig,
            cropCorners = listOf(
                PointF(orig.width * 0.08f, orig.height * 0.08f),
                PointF(orig.width * 0.92f, orig.height * 0.08f),
                PointF(orig.width * 0.92f, orig.height * 0.92f),
                PointF(orig.width * 0.08f, orig.height * 0.92f),
            ),
        )
    }

    fun save() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            withContext(Dispatchers.IO) {
                // Save the exact preview bitmap (which contains all crops, rotations, filters, and adjustments)
                val finalBitmap = _uiState.value.previewBitmap ?: return@withContext
                ImageProcessor.saveBitmap(finalBitmap, File(_uiState.value.imagePath))
            }
            _uiState.value = _uiState.value.copy(isSaving = false, isSaved = true)
        }
    }

    fun switchPage(newPageId: String) {
        if (newPageId == _uiState.value.pageId) return
        
        viewModelScope.launch {
            // Auto-save current page
            _uiState.value = _uiState.value.copy(isSaving = true)
            withContext(Dispatchers.IO) {
                val finalBitmap = _uiState.value.previewBitmap
                if (finalBitmap != null) {
                    ImageProcessor.saveBitmap(finalBitmap, File(_uiState.value.imagePath))
                }
            }
            
            // Unset saved state so we don't pop backstack, and load new page
            _uiState.value = _uiState.value.copy(isSaving = false, isSaved = false)
            loadPage(newPageId)
        }
    }

    fun revertToOriginal() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            withContext(Dispatchers.IO) {
                val origFile = File(_uiState.value.imagePath + "_original")
                if (origFile.exists()) {
                    val pageFile = File(_uiState.value.imagePath)
                    origFile.copyTo(pageFile, overwrite = true)
                }
            }
            loadPage(_uiState.value.pageId)
        }
    }

    fun saveBeforeNavigate(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            withContext(Dispatchers.IO) {
                val finalBitmap = _uiState.value.previewBitmap
                if (finalBitmap != null) {
                    ImageProcessor.saveBitmap(finalBitmap, File(_uiState.value.imagePath))
                }
            }
            _uiState.value = _uiState.value.copy(isSaving = false)
            onSuccess()
        }
    }

    fun refresh() {
        val currentPageId = _uiState.value.pageId
        if (currentPageId.isNotEmpty()) {
            loadPage(currentPageId)
        }
    }

    private fun updatePreview() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val state = _uiState.value
            val base = state.baseBitmap ?: return@launch
            _uiState.value = state.copy(isProcessing = true)
            val result = withContext(Dispatchers.Default) {
                var bmp = if (state.selectedFilter != FilterType.ORIGINAL) {
                    val filtered = ImageProcessor.applyFilter(base, state.selectedFilter)
                    if (state.filterIntensity < 100) {
                        ImageProcessor.blend(base, filtered, state.filterIntensity / 100f)
                    } else {
                        filtered
                    }
                } else {
                    base.copy(Bitmap.Config.ARGB_8888, false)
                }
                if (state.adjustments != AdjustmentValues()) {
                    bmp = ImageProcessor.applyAdjustments(bmp, state.adjustments)
                }
                bmp
            }
            _uiState.value = _uiState.value.copy(previewBitmap = result, isProcessing = false)
        }
    }

    private fun updatePreviewDebounced() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(150)
            updatePreview()
        }
    }
}
