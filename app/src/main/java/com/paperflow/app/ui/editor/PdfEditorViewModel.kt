package com.paperflow.app.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperflow.app.data.repository.DocumentRepository
import com.paperflow.app.pdf.OverlayType
import com.paperflow.app.pdf.PdfEditOverlay
import com.paperflow.app.pdf.PdfImporter
import com.paperflow.app.pdf.PdfTextBlock
import com.paperflow.app.pdf.PdfTextExtractor
import com.paperflow.app.pdf.PdfTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class PdfPageMeta(
    val index: Int,
    val width: Float,
    val height: Float,
    val rotation: Int = 0
)

data class PdfEditorUiState(
    val isProcessing: Boolean = false,
    val pdfTitle: String = "",
    val totalPageCount: Int = 0,
    val activePageIndex: Int = 0,
    val pagesMeta: List<PdfPageMeta> = emptyList(),
    val activePageBlocks: List<PdfTextBlock> = emptyList(),
    val overlays: List<PdfEditOverlay> = emptyList(),
    val selectedOverlayId: String? = null,
    val isSaving: Boolean = false,
    val savedFilePath: String? = null,
    val error: String? = null
)

class PdfEditorViewModel(
    private val appContext: Context,
    private val pdfPath: String,
    private val repository: DocumentRepository? = null,
    private val initialPageIndex: Int = 0
) : ViewModel() {

    private val _uiState = MutableStateFlow(PdfEditorUiState())
    val uiState: StateFlow<PdfEditorUiState> = _uiState.asStateFlow()

    private val undoStack = mutableListOf<List<PdfEditOverlay>>()
    private val redoStack = mutableListOf<List<PdfEditOverlay>>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private fun pushState() {
        undoStack.add(_uiState.value.overlays.toList())
        if (undoStack.size > 30) undoStack.removeAt(0)
        redoStack.clear()
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = false
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack.add(_uiState.value.overlays.toList())
            val prevState = undoStack.removeAt(undoStack.lastIndex)
            _uiState.value = _uiState.value.copy(
                overlays = prevState,
                selectedOverlayId = null
            )
            _canUndo.value = undoStack.isNotEmpty()
            _canRedo.value = redoStack.isNotEmpty()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack.add(_uiState.value.overlays.toList())
            val nextState = redoStack.removeAt(redoStack.lastIndex)
            _uiState.value = _uiState.value.copy(
                overlays = nextState,
                selectedOverlayId = null
            )
            _canUndo.value = undoStack.isNotEmpty()
            _canRedo.value = redoStack.isNotEmpty()
        }
    }

    private val highResCache = LinkedHashMap<Int, String>(5, 0.75f, true)
    private val thumbCache = ConcurrentHashMap<Int, String>()
    private val pageBlocksCache = ConcurrentHashMap<Int, List<PdfTextBlock>>()
    private val renderMutex = Mutex()
    private var activeDocumentId: String? = null

    private val cacheDir by lazy {
        File(appContext.cacheDir, "pdf_editor_cache").also { if (!it.exists()) it.mkdirs() }
    }

    init {
        loadPdfQuick()
    }

    /** Instant load: read page count & dimensions in < 50ms without batch rendering all pages. */
    private fun loadPdfQuick() {
        viewModelScope.launch {
            _uiState.value = PdfEditorUiState(isProcessing = true, pdfTitle = File(pdfPath).name)
            try {
                val file = File(pdfPath)
                if (!file.exists()) {
                    _uiState.value = _uiState.value.copy(isProcessing = false, error = "File does not exist")
                    return@launch
                }

                PdfTools.init(appContext)

                val metaList = withContext(Dispatchers.IO) {
                    readPageMetaList(file)
                }

                val targetPage = initialPageIndex.coerceIn(0, (metaList.size - 1).coerceAtLeast(0))

                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    totalPageCount = metaList.size,
                    pagesMeta = metaList,
                    activePageIndex = targetPage
                )

                // Lazily load text for target page
                loadActivePageText(targetPage)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    error = e.message ?: "Failed to load PDF"
                )
            }
        }
    }

    private fun readPageMetaList(file: File): List<PdfPageMeta> {
        val list = mutableListOf<PdfPageMeta>()
        var fd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(fd)
            for (i in 0 until renderer.pageCount) {
                val p = renderer.openPage(i)
                list.add(PdfPageMeta(index = i, width = p.width.toFloat(), height = p.height.toFloat()))
                p.close()
            }
        } finally {
            renderer?.close()
            fd?.close()
        }
        return list
    }

    fun setActivePage(pageIndex: Int) {
        val total = _uiState.value.totalPageCount
        if (pageIndex < 0 || pageIndex >= total) return
        _uiState.value = _uiState.value.copy(activePageIndex = pageIndex, selectedOverlayId = null)
        loadActivePageText(pageIndex)
    }

    private fun loadActivePageText(pageIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val cached = pageBlocksCache[pageIndex]
            if (cached != null) {
                _uiState.value = _uiState.value.copy(activePageBlocks = cached)
                return@launch
            }

            val file = File(pdfPath)
            if (!file.exists()) return@launch
            val blocks = PdfTextExtractor.extractTextFromPage(file, pageIndex)
            pageBlocksCache[pageIndex] = blocks

            if (_uiState.value.activePageIndex == pageIndex) {
                _uiState.value = _uiState.value.copy(activePageBlocks = blocks)
            }
        }
    }

    suspend fun getPageHighResPath(pageIndex: Int): String? {
        return withContext(Dispatchers.IO) {
            renderMutex.withLock {
                highResCache[pageIndex]?.let { path ->
                    if (File(path).exists()) return@withContext path
                }
                while (highResCache.size >= 5) {
                    val oldest = highResCache.entries.first()
                    try { File(oldest.value).delete() } catch (_: Exception) {}
                    highResCache.remove(oldest.key)
                }
                val rendered = renderPageBitmap(pageIndex, densityDpi = 300, maxDim = 3200)
                if (rendered != null) highResCache[pageIndex] = rendered
                rendered
            }
        }
    }

    suspend fun getPageThumbnailPath(pageIndex: Int): String? {
        return withContext(Dispatchers.IO) {
            thumbCache[pageIndex]?.let { path ->
                if (File(path).exists()) return@withContext path
            }
            val rendered = renderPageBitmap(pageIndex, densityDpi = 100, maxDim = 600)
            if (rendered != null) thumbCache[pageIndex] = rendered
            rendered
        }
    }

    private fun renderPageBitmap(pageIndex: Int, densityDpi: Int, maxDim: Int): String? {
        val file = File(pdfPath)
        if (!file.exists()) return null

        var fd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(fd)
            if (pageIndex >= renderer.pageCount) return null

            val page = renderer.openPage(pageIndex)
            var targetWidth = (page.width * densityDpi / 72.0).toInt()
            var targetHeight = (page.height * densityDpi / 72.0).toInt()
            val m = maxOf(targetWidth, targetHeight)
            if (m > maxDim) {
                val s = maxDim.toDouble() / m
                targetWidth = (targetWidth * s).toInt()
                targetHeight = (targetHeight * s).toInt()
            }

            val bitmap = com.paperflow.app.util.BitmapUtils.safeCreateAndRenderBitmap(
                page, targetWidth, targetHeight, Bitmap.Config.ARGB_8888
            ) ?: run {
                page.close()
                return null
            }

            val outFile = File(cacheDir, "p_${file.name.hashCode()}_${densityDpi}_$pageIndex.png")
            outFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
            page.close()
            return outFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            renderer?.close()
            fd?.close()
        }
    }

    fun handleTextTapped(pageIndex: Int, block: PdfTextBlock) {
        val pageMeta = _uiState.value.pagesMeta.find { it.index == pageIndex } ?: return
        val xFraction = block.x / pageMeta.width
        val yFraction = block.y / pageMeta.height
        val widthFraction = block.width / pageMeta.width
        val heightFraction = block.height / pageMeta.height

        val existing = _uiState.value.overlays.find {
            it.pageIndex == pageIndex && it.originalText == block.text && Math.abs(it.xFraction - xFraction) < 0.02f
        }
        if (existing != null) {
            _uiState.value = _uiState.value.copy(selectedOverlayId = existing.id)
            return
        }

        val fontCategory = when {
            block.fontName.contains("serif", ignoreCase = true) || block.fontName.contains("times", ignoreCase = true) -> "Serif"
            block.fontName.contains("monospace", ignoreCase = true) || block.fontName.contains("courier", ignoreCase = true) -> "Monospace"
            else -> "Sans"
        }

        val newOverlay = PdfEditOverlay(
            id = UUID.randomUUID().toString(),
            pageIndex = pageIndex,
            type = OverlayType.TEXT_EDIT,
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

        pushState()
        _uiState.value = _uiState.value.copy(
            overlays = _uiState.value.overlays + newOverlay,
            selectedOverlayId = newOverlay.id
        )
    }

    fun addCustomTextOverlay(pageIndex: Int, xFraction: Float = 0.15f, yFraction: Float = 0.3f) {
        val newOverlay = PdfEditOverlay(
            id = UUID.randomUUID().toString(),
            pageIndex = pageIndex,
            type = OverlayType.CUSTOM_TEXT,
            text = "Tap to type text",
            xFraction = xFraction,
            yFraction = yFraction,
            widthFraction = 0.35f,
            heightFraction = 0.05f,
            fontSize = 14f,
            color = android.graphics.Color.BLACK,
            fontName = "Sans",
            whiteout = false
        )
        pushState()
        _uiState.value = _uiState.value.copy(
            overlays = _uiState.value.overlays + newOverlay,
            selectedOverlayId = newOverlay.id
        )
    }

    fun addWhiteoutMaskOverlay(pageIndex: Int, xFraction: Float = 0.2f, yFraction: Float = 0.4f) {
        val newOverlay = PdfEditOverlay(
            id = UUID.randomUUID().toString(),
            pageIndex = pageIndex,
            type = OverlayType.WHITEOUT_MASK,
            text = "",
            xFraction = xFraction,
            yFraction = yFraction,
            widthFraction = 0.4f,
            heightFraction = 0.08f,
            whiteout = true,
            color = android.graphics.Color.WHITE
        )
        pushState()
        _uiState.value = _uiState.value.copy(
            overlays = _uiState.value.overlays + newOverlay,
            selectedOverlayId = newOverlay.id
        )
    }

    fun addImageOrSignatureOverlay(pageIndex: Int, imagePath: String, isSignature: Boolean = true) {
        val newOverlay = PdfEditOverlay(
            id = UUID.randomUUID().toString(),
            pageIndex = pageIndex,
            type = if (isSignature) OverlayType.SIGNATURE else OverlayType.IMAGE_STAMP,
            text = "",
            xFraction = 0.3f,
            yFraction = 0.4f,
            widthFraction = 0.35f,
            heightFraction = 0.12f,
            imagePath = imagePath,
            whiteout = false
        )
        pushState()
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

    fun deleteSelectedOverlay() {
        val selectedId = _uiState.value.selectedOverlayId ?: return
        pushState()
        _uiState.value = _uiState.value.copy(
            overlays = _uiState.value.overlays.filter { it.id != selectedId },
            selectedOverlayId = null
        )
    }

    fun selectOverlay(id: String?) {
        _uiState.value = _uiState.value.copy(selectedOverlayId = id)
    }

    fun rotatePage(pageIndex: Int) {
        val pages = _uiState.value.pagesMeta.toMutableList()
        if (pageIndex in pages.indices) {
            val current = pages[pageIndex]
            val newRotation = (current.rotation + 90) % 360
            pages[pageIndex] = current.copy(rotation = newRotation)
            _uiState.value = _uiState.value.copy(pagesMeta = pages)
        }
    }

    fun deletePage(pageIndex: Int) {
        val pages = _uiState.value.pagesMeta.toMutableList()
        if (pages.size > 1 && pageIndex in pages.indices) {
            pages.removeAt(pageIndex)
            val updated = pages.mapIndexed { idx, p -> p.copy(index = idx) }
            val newActive = pageIndex.coerceAtMost(updated.size - 1)
            _uiState.value = _uiState.value.copy(
                totalPageCount = updated.size,
                pagesMeta = updated,
                activePageIndex = newActive,
                selectedOverlayId = null
            )
            loadActivePageText(newActive)
        }
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
                    output.copyTo(file, overwrite = true)

                    if (repository != null) {
                        try {
                            val existingDocId = activeDocumentId
                            val editedPageIndices = overlays.map { it.pageIndex }.distinct().ifEmpty { listOf(_uiState.value.activePageIndex) }

                            if (existingDocId != null) {
                                val docDir = repository.getDocumentDir(existingDocId)
                                val docSourcePdf = File(docDir, "source.pdf")
                                file.copyTo(docSourcePdf, overwrite = true)

                                val pages = repository.getPagesSync(existingDocId).sortedBy { it.pageIndex }
                                for (idx in editedPageIndices) {
                                    val pageEntity = pages.find { it.pageIndex == idx }
                                    if (pageEntity != null) {
                                        val renderedPath = renderPageBitmap(idx, densityDpi = 350, maxDim = 3200)
                                        if (renderedPath != null) {
                                            File(renderedPath).copyTo(File(pageEntity.imagePath), overwrite = true)
                                        }
                                    }
                                }
                            } else {
                                val imported = PdfImporter.importPdfAsDocument(file, repository, "Edited")
                                if (imported != null) {
                                    activeDocumentId = imported.id
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // Invalidate render cache
                    try {
                        if (cacheDir.exists()) {
                            cacheDir.listFiles()?.forEach { it.delete() }
                        }
                    } catch (_: Exception) {}
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
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (cacheDir.exists()) cacheDir.deleteRecursively()
            } catch (_: Exception) {}
        }
    }
}
