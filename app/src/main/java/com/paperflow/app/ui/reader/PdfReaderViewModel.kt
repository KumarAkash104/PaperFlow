package com.paperflow.app.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperflow.app.data.repository.DocumentRepository
import com.paperflow.app.pdf.PdfImporter
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

/** Scroll modes for the reader. */
enum class ScrollMode {
    VERTICAL,   // Continuous scroll (all pages stacked)
    HORIZONTAL  // Page-by-page horizontal swipe
}

/**
 * Reading theme presets inspired by Kindle Paperwhite experience.
 */
enum class ReadingTheme(
    val displayName: String,
    val bgColor: Long,
    val textTint: Long,
    val uiSurface: Long,
    val uiOnSurface: Long,
    val warmth: Float
) {
    PAPER(
        displayName = "Paper",
        bgColor = 0xFFF5F0E8,
        textTint = 0xFF3B3228,
        uiSurface = 0xFFE8E0D4,
        uiOnSurface = 0xFF3B3228,
        warmth = 0.3f
    ),
    SEPIA(
        displayName = "Sepia",
        bgColor = 0xFFECDFC8,
        textTint = 0xFF5B4636,
        uiSurface = 0xFFDDD0B8,
        uiOnSurface = 0xFF5B4636,
        warmth = 0.6f
    ),
    IVORY(
        displayName = "Ivory",
        bgColor = 0xFFFFFFF0,
        textTint = 0xFF2C2C2C,
        uiSurface = 0xFFF0F0E4,
        uiOnSurface = 0xFF2C2C2C,
        warmth = 0.15f
    ),
    DARK(
        displayName = "Night",
        bgColor = 0xFF1A1A1A,
        textTint = 0xFFCCCCC0,
        uiSurface = 0xFF242424,
        uiOnSurface = 0xFFCCCCC0,
        warmth = 0.0f
    ),
    WARM_DARK(
        displayName = "Warm Night",
        bgColor = 0xFF2A2018,
        textTint = 0xFFD4C4A8,
        uiSurface = 0xFF342818,
        uiOnSurface = 0xFFD4C4A8,
        warmth = 0.5f
    ),
}

data class PdfReaderUiState(
    val isLoading: Boolean = true,
    val pdfTitle: String = "",
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val scrollMode: ScrollMode = ScrollMode.VERTICAL,
    val theme: ReadingTheme = ReadingTheme.PAPER,
    val brightness: Float = 1.0f,
    val contrast: Float = 1.0f,
    val showControls: Boolean = false,
    val isFullscreen: Boolean = false,
    val savedToDocuments: Boolean = false,
    val error: String? = null
)

class PdfReaderViewModel(
    private val appContext: Context,
    private val pdfPath: String,
    private val repository: DocumentRepository? = null,
    private val imagePagePaths: List<String>? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(PdfReaderUiState())
    val uiState: StateFlow<PdfReaderUiState> = _uiState.asStateFlow()

    // True when we're displaying pre-existing images (no PDF rendering needed)
    private val isImageMode = !imagePagePaths.isNullOrEmpty()

    // Lazy page rendering: only render pages as they become visible
    private val pageCache = LinkedHashMap<Int, String>(10, 0.75f, true)
    private val renderMutex = Mutex()
    private val cacheDir by lazy {
        File(appContext.cacheDir, "pdf_reader_cache").also { if (!it.exists()) it.mkdirs() }
    }

    init {
        if (isImageMode) {
            loadImagePages()
        } else {
            loadPdf()
        }
    }

    /** Fast path: images already exist on disk, no rendering needed. */
    private fun loadImagePages() {
        val paths = imagePagePaths ?: return
        _uiState.value = PdfReaderUiState(
            isLoading = false,
            pdfTitle = File(pdfPath).nameWithoutExtension,
            pageCount = paths.size,
        )
    }

    private fun loadPdf() {
        viewModelScope.launch {
            _uiState.value = PdfReaderUiState(
                isLoading = true,
                pdfTitle = File(pdfPath).nameWithoutExtension
            )
            try {
                PdfTools.init(appContext)
                val count = withContext(Dispatchers.IO) { getPageCount() }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    pageCount = count,
                )
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load PDF"
                )
            }
        }
    }

    private fun getPageCount(): Int {
        val file = File(pdfPath)
        var fd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(fd)
            return renderer.pageCount
        } finally {
            renderer?.close()
            fd?.close()
        }
    }

    /**
     * Get the image path for a page, rendering it lazily if not cached.
     * In image mode, returns the pre-existing image path directly (zero-cost).
     * In PDF mode, renders the page to a high-quality cached JPEG.
     */
    suspend fun getPageImagePath(pageIndex: Int): String? {
        // Image mode: instant — no rendering, no caching, no JPEG re-compression
        if (isImageMode) {
            val paths = imagePagePaths ?: return null
            return paths.getOrNull(pageIndex)
        }

        // PDF mode: render on demand
        return withContext(Dispatchers.IO) {
            renderMutex.withLock {
                // Return from cache if available
                pageCache[pageIndex]?.let { path ->
                    if (File(path).exists()) return@withContext path
                }

                // Evict old pages if cache is too large (keep max 8 pages)
                while (pageCache.size >= 8) {
                    val oldest = pageCache.entries.first()
                    try { File(oldest.value).delete() } catch (_: Exception) {}
                    pageCache.remove(oldest.key)
                }

                // Render the page
                val path = renderPage(pageIndex)
                if (path != null) {
                    pageCache[pageIndex] = path
                }
                path
            }
        }
    }

    private fun renderPage(pageIndex: Int): String? {
        val file = File(pdfPath)
        if (!file.exists()) return null

        var fd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(fd)

            if (pageIndex >= renderer.pageCount) return null

            val page = renderer.openPage(pageIndex)

            // Render at 300 DPI for crystal clear text, capped at 4000px max dimension
            val densityDpi = 300
            var targetWidth = (page.width * densityDpi / 72.0).toInt()
            var targetHeight = (page.height * densityDpi / 72.0).toInt()
            val maxDim = maxOf(targetWidth, targetHeight)
            if (maxDim > 4000) {
                val scale = 4000.0 / maxDim
                targetWidth = (targetWidth * scale).toInt()
                targetHeight = (targetHeight * scale).toInt()
            }

            val bitmap = com.paperflow.app.util.BitmapUtils.safeCreateAndRenderBitmap(page, targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            if (bitmap == null) {
                page.close()
                return null
            }

            // Save at 95% quality for minimal artifacts on text
            val pageFile = File(cacheDir, "reader_p${pageIndex}.jpg")
            pageFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            bitmap.recycle()
            page.close()
            return pageFile.absolutePath
        } finally {
            renderer?.close()
            fd?.close()
        }
    }

    fun saveToDocuments() {
        if (repository == null) return
        if (_uiState.value.savedToDocuments) return
        viewModelScope.launch {
            try {
                val file = File(pdfPath)
                if (!file.exists()) return@launch
                withContext(Dispatchers.IO) {
                    PdfImporter.importPdfAsDocument(file, repository, "Reading")
                }
                _uiState.value = _uiState.value.copy(savedToDocuments = true)
            } catch (_: Exception) {}
        }
    }

    fun setCurrentPage(page: Int) {
        val max = (_uiState.value.pageCount - 1).coerceAtLeast(0)
        _uiState.value = _uiState.value.copy(currentPage = page.coerceIn(0, max))
    }

    fun setTheme(theme: ReadingTheme) {
        _uiState.value = _uiState.value.copy(theme = theme)
    }

    fun setBrightness(brightness: Float) {
        _uiState.value = _uiState.value.copy(brightness = brightness.coerceIn(0.3f, 1.0f))
    }

    fun setContrast(contrast: Float) {
        _uiState.value = _uiState.value.copy(contrast = contrast.coerceIn(0.5f, 2.0f))
    }

    fun toggleControls() {
        _uiState.value = _uiState.value.copy(showControls = !_uiState.value.showControls)
    }

    fun hideControls() {
        _uiState.value = _uiState.value.copy(showControls = false)
    }

    fun toggleFullscreen() {
        _uiState.value = _uiState.value.copy(
            isFullscreen = !_uiState.value.isFullscreen,
            showControls = false
        )
    }

    fun toggleScrollMode() {
        val newMode = if (_uiState.value.scrollMode == ScrollMode.VERTICAL)
            ScrollMode.HORIZONTAL else ScrollMode.VERTICAL
        _uiState.value = _uiState.value.copy(scrollMode = newMode)
    }

    override fun onCleared() {
        super.onCleared()
        // Use a standalone scope since viewModelScope is already cancelled
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                if (cacheDir.exists()) cacheDir.deleteRecursively()
            } catch (_: Exception) {}
        }
    }
}
