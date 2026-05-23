package com.paperflow.app.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperflow.app.pdf.PdfEditOverlay
import com.paperflow.app.pdf.PdfImporter
import com.paperflow.app.pdf.PdfTextBlock
import com.paperflow.app.pdf.PdfTextExtractor
import com.paperflow.app.pdf.PdfTools
import com.paperflow.app.data.repository.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class PdfPageData(
    val index: Int,
    val imagePath: String,
    val width: Float,
    val height: Float
)

data class PdfEditorUiState(
    val isProcessing: Boolean = false,
    val pdfTitle: String = "",
    val pages: List<PdfPageData> = emptyList(),
    val extractedBlocks: List<PdfTextBlock> = emptyList(),
    val overlays: List<PdfEditOverlay> = emptyList(),
    val selectedOverlayId: String? = null,
    val isSaving: Boolean = false,
    val savedFilePath: String? = null,
    val error: String? = null
)

class PdfEditorViewModel(
    private val appContext: Context,
    private val pdfPath: String,
    private val repository: DocumentRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(PdfEditorUiState())
    val uiState: StateFlow<PdfEditorUiState> = _uiState.asStateFlow()

    init {
        loadPdf()
    }

    private fun loadPdf() {
        viewModelScope.launch {
            _uiState.value = PdfEditorUiState(isProcessing = true, pdfTitle = File(pdfPath).name)
            try {
                val file = File(pdfPath)
                if (!file.exists()) {
                    _uiState.value = _uiState.value.copy(isProcessing = false, error = "File does not exist")
                    return@launch
                }

                // Initialize PDFBox
                PdfTools.init(appContext)

                // 1. Render and cache pages
                val pages = withContext(Dispatchers.IO) {
                    renderAndCachePages(file)
                }

                // 2. Extract text blocks in the background
                val blocks = withContext(Dispatchers.IO) {
                    PdfTextExtractor.extractTextFromPdf(file)
                }

                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    pages = pages,
                    extractedBlocks = blocks
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    error = e.message ?: "Failed to load PDF"
                )
            }
        }
    }

    private fun renderAndCachePages(file: File): List<PdfPageData> {
        val results = mutableListOf<PdfPageData>()
        var fd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(fd)
            val cacheDir = File(appContext.cacheDir, "pdf_editor_cache")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                
                // render at high-res (300 dpi, max 3500px)
                val densityDpi = 300
                var targetWidth = (page.width * densityDpi / 72.0).toInt()
                var targetHeight = (page.height * densityDpi / 72.0).toInt()
                val maxDim = maxOf(targetWidth, targetHeight)
                if (maxDim > 3500) {
                    val scale = 3500.0 / maxDim
                    targetWidth = (targetWidth * scale).toInt()
                    targetHeight = (targetHeight * scale).toInt()
                }

                val bitmap = com.paperflow.app.util.BitmapUtils.safeCreateAndRenderBitmap(page, targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                if (bitmap == null) {
                    page.close()
                    continue
                }

                val pageFile = File(cacheDir, "page_${file.name.hashCode()}_$i.jpg")
                pageFile.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                bitmap.recycle()

                results.add(
                    PdfPageData(
                        index = i,
                        imagePath = pageFile.absolutePath,
                        width = page.width.toFloat(),
                        height = page.height.toFloat()
                    )
                )
                page.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            renderer?.close()
            fd?.close()
        }
        return results
    }

    fun handleTextTapped(pageIndex: Int, block: PdfTextBlock) {
        val page = _uiState.value.pages.find { it.index == pageIndex } ?: return

        // Check if an overlay already exists for this block
        val existing = _uiState.value.overlays.find { it.pageIndex == pageIndex && it.originalText == block.text && Math.abs(it.xFraction - (block.x / page.width)) < 0.01f }
        if (existing != null) {
            _uiState.value = _uiState.value.copy(selectedOverlayId = existing.id)
            return
        }

        // Map original block coordinates to fractions relative to page
        val xFraction = block.x / page.width
        val yFraction = block.y / page.height
        val widthFraction = block.width / page.width
        val heightFraction = block.height / page.height

        // Determine font category
        val fontCategory = when {
            block.fontName.contains("serif", ignoreCase = true) || block.fontName.contains("times", ignoreCase = true) -> "Serif"
            block.fontName.contains("monospace", ignoreCase = true) || block.fontName.contains("courier", ignoreCase = true) -> "Monospace"
            else -> "Sans"
        }

        val newOverlay = PdfEditOverlay(
            id = UUID.randomUUID().toString(),
            pageIndex = pageIndex,
            originalText = block.text,
            text = block.text,
            xFraction = xFraction,
            yFraction = yFraction,
            widthFraction = widthFraction,
            heightFraction = heightFraction,
            fontSize = block.fontSize,
            isBold = block.isBold,
            isItalic = block.isItalic,
            color = block.color,
            fontName = fontCategory,
            whiteout = true
        )

        _uiState.value = _uiState.value.copy(
            overlays = _uiState.value.overlays + newOverlay,
            selectedOverlayId = newOverlay.id
        )
    }

    fun addCustomTextOverlay(pageIndex: Int, xFraction: Float, yFraction: Float) {
        val newOverlay = PdfEditOverlay(
            id = UUID.randomUUID().toString(),
            pageIndex = pageIndex,
            originalText = null,
            text = "Tap to type",
            xFraction = xFraction,
            yFraction = yFraction,
            widthFraction = 0.2f, // default initial width
            heightFraction = 0.04f, // default initial height
            fontSize = 12f,
            isBold = false,
            isItalic = false,
            color = android.graphics.Color.BLACK,
            fontName = "Sans",
            whiteout = false // new text box doesn't whiteout anything
        )

        _uiState.value = _uiState.value.copy(
            overlays = _uiState.value.overlays + newOverlay,
            selectedOverlayId = newOverlay.id
        )
    }

    fun updateSelectedOverlay(updateBlock: (PdfEditOverlay) -> PdfEditOverlay) {
        val selectedId = _uiState.value.selectedOverlayId ?: return
        _uiState.value = _uiState.value.copy(
            overlays = _uiState.value.overlays.map {
                if (it.id == selectedId) updateBlock(it) else it
            }
        )
    }

    fun updateOverlayById(updateBlock: (PdfEditOverlay) -> PdfEditOverlay) {
        _uiState.value = _uiState.value.copy(
            overlays = _uiState.value.overlays.map { updateBlock(it) }
        )
    }

    fun deleteSelectedOverlay() {
        val selectedId = _uiState.value.selectedOverlayId ?: return
        _uiState.value = _uiState.value.copy(
            overlays = _uiState.value.overlays.filter { it.id != selectedId },
            selectedOverlayId = null
        )
    }

    fun selectOverlay(id: String?) {
        _uiState.value = _uiState.value.copy(selectedOverlayId = id)
    }

    fun saveEdits() {
        viewModelScope.launch {
            val overlays = _uiState.value.overlays
            _uiState.value = _uiState.value.copy(isSaving = true)
            try {
                val file = File(pdfPath)
                val output = File(appContext.cacheDir, "edited_${System.currentTimeMillis()}_${file.name}")
                
                withContext(Dispatchers.IO) {
                    PdfTools.savePdfEdits(file, output, overlays)
                }

                // Overwrite the original PDF file with the edited one
                withContext(Dispatchers.IO) {
                    output.copyTo(file, overwrite = true)
                }

                // Save to documents
                if (repository != null) {
                    withContext(Dispatchers.IO) {
                        try {
                            PdfImporter.importPdfAsDocument(file, repository, "Edited")
                        } catch (_: Exception) {}
                    }
                }

                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    savedFilePath = file.absolutePath
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = e.message ?: "Failed to save edits"
                )
            }
        }
    }

    fun clearSavedState() {
        _uiState.value = _uiState.value.copy(savedFilePath = null)
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up page cache
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cacheDir = File(appContext.cacheDir, "pdf_editor_cache")
                if (cacheDir.exists()) cacheDir.deleteRecursively()
            } catch (_: Exception) {}
        }
    }
}
