package com.paperflow.app.ui.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperflow.app.data.repository.DocumentRepository
import com.paperflow.app.pdf.ExportQuality
import com.paperflow.app.pdf.PageSize
import com.paperflow.app.pdf.PdfExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ExportUiState(
    val documentId: String = "",
    val documentTitle: String = "",
    val pageCount: Int = 0,
    val format: ExportFormat = ExportFormat.PDF,
    val pageSize: PageSize = PageSize.A4,
    val quality: ExportQuality = ExportQuality.BALANCED,
    val isExporting: Boolean = false,
    val exportedFile: File? = null,
    val exportedFiles: List<File> = emptyList(),
    val error: String? = null,
)

enum class ExportFormat(val label: String, val ext: String) {
    PDF("PDF", "pdf"),
    JPG("JPG Images", "jpg"),
}

class ExportViewModel(
    private val repository: DocumentRepository,
    private val documentId: String,
    private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState(documentId = documentId))
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        val prefs = appContext.getSharedPreferences("export_prefs", Context.MODE_PRIVATE)
        val defaultPageSizeStr = prefs.getString("default_page_size", PageSize.A4.name)
        val defaultQualityStr = prefs.getString("default_quality", ExportQuality.BALANCED.name)
        
        val defaultPageSize = PageSize.entries.find { it.name == defaultPageSizeStr } ?: PageSize.A4
        val defaultQuality = ExportQuality.entries.find { it.name == defaultQualityStr } ?: ExportQuality.BALANCED

        viewModelScope.launch {
            val doc = repository.getDocument(documentId) ?: return@launch
            val pages = repository.getPagesSync(documentId)
            _uiState.value = _uiState.value.copy(
                documentTitle = doc.title,
                pageCount = pages.size,
                pageSize = defaultPageSize,
                quality = defaultQuality,
            )
        }
    }

    fun setFormat(format: ExportFormat) {
        _uiState.value = _uiState.value.copy(format = format)
    }

    fun setPageSize(size: PageSize) {
        _uiState.value = _uiState.value.copy(pageSize = size)
        appContext.getSharedPreferences("export_prefs", Context.MODE_PRIVATE).edit().putString("default_page_size", size.name).apply()
    }

    fun setQuality(quality: ExportQuality) {
        _uiState.value = _uiState.value.copy(quality = quality)
        appContext.getSharedPreferences("export_prefs", Context.MODE_PRIVATE).edit().putString("default_quality", quality.name).apply()
    }

    fun export() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, error = null)
            try {
                val pages = repository.getPagesSync(documentId)
                val paths = pages.map { it.imagePath }
                val state = _uiState.value
                val title = state.documentTitle.ifBlank { "scan" }
                    .replace(Regex("[^a-zA-Z0-9_\\- ]"), "")
                    .replace(" ", "_")

                val (mainFile, allFiles) = withContext(Dispatchers.IO) {
                    when (state.format) {
                        ExportFormat.PDF -> {
                            val outFile = PdfExporter.createExportFile(appContext, title, "pdf")
                            val exported = PdfExporter.exportToPdf(
                                appContext, paths, state.pageSize, state.quality, outFile,
                            )
                            exported to listOf(exported)
                        }
                        ExportFormat.JPG -> {
                            val dir = File(appContext.cacheDir, "exports/$title")
                            val files = PdfExporter.exportToImages(paths, state.quality, dir)
                            if (files.isEmpty()) throw Exception("No pages to export")
                            files.first() to files
                        }
                    }
                }
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportedFile = mainFile,
                    exportedFiles = allFiles,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    error = e.message ?: "Export failed",
                )
            }
        }
    }

    fun shareFile(context: Context) {
        val files = _uiState.value.exportedFiles.ifEmpty {
            listOfNotNull(_uiState.value.exportedFile)
        }
        if (files.isEmpty()) return

        if (files.size == 1) {
            val file = files.first()
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (_uiState.value.format == ExportFormat.PDF) "application/pdf" else "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share via"))
        } else {
            val uris = ArrayList(files.map { file ->
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
            })
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/jpeg"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share images via"))
        }
    }
}
