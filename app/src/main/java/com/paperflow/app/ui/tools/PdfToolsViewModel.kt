package com.paperflow.app.ui.tools

import android.content.Context
import android.net.Uri
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
import kotlinx.coroutines.withContext
import java.io.File

data class ToolsUiState(
    val isProcessing: Boolean = false,
    val resultMessage: String? = null,
    val resultFile: File? = null,
    val error: String? = null,
    val importedDocumentId: String? = null,
)

class PdfToolsViewModel(
    private val appContext: Context,
    private val repository: DocumentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ToolsUiState())
    val uiState: StateFlow<ToolsUiState> = _uiState.asStateFlow()

    init {
        PdfTools.init(appContext)
    }

    fun mergePdfs(uris: List<Uri>) {
        runTool("Merging PDFs...") {
            val files = uris.map { uri -> copyUriToTemp(uri, "merge_input_${uris.indexOf(uri)}.pdf") }
            val output = PdfTools.createToolOutputFile(appContext, "merged_${System.currentTimeMillis()}.pdf")
            PdfTools.mergePdfs(files, output)
            "Merged ${files.size} PDFs" to output
        }
    }

    fun splitPdf(uri: Uri) {
        runTool("Splitting PDF...") {
            val file = copyUriToTemp(uri, "split_input.pdf")
            val outDir = PdfTools.createToolOutputDir(appContext, "split_${System.currentTimeMillis()}")
            val results = PdfTools.splitPdf(file, outDir)
            "Split into ${results.size} files" to results.first()
        }
    }

    fun compressPdf(uri: Uri, qualityPercent: Int = 60) {
        runTool("Compressing PDF at ${qualityPercent}% quality...") {
            val file = copyUriToTemp(uri, "compress_input.pdf")
            val output = PdfTools.createToolOutputFile(appContext, "compressed_${System.currentTimeMillis()}.pdf")
            PdfTools.compressPdf(file, output, qualityPercent)
            val originalSize = file.length()
            val compressedSize = output.length()
            val savings = ((1 - compressedSize.toFloat() / originalSize) * 100).toInt().coerceAtLeast(0)
            val origMb = String.format("%.1f", originalSize / (1024f * 1024f))
            val compMb = String.format("%.1f", compressedSize / (1024f * 1024f))
            "Compressed: ${origMb}MB → ${compMb}MB (${savings}% smaller)" to output
        }
    }

    fun extractImages(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = ToolsUiState(isProcessing = true, resultMessage = "Extracting images...")
            try {
                val file = withContext(Dispatchers.IO) { copyUriToTemp(uri, "extract_input.pdf") }
                val outDir = PdfTools.createToolOutputDir(appContext, "extracted_${System.currentTimeMillis()}")
                val results = withContext(Dispatchers.IO) { PdfTools.pdfToImages(file, outDir) }
                
                // For images, we can directly create a new document in the app
                val docTitle = "Extracted Images ${System.currentTimeMillis()}"
                val doc = repository.createDocument(docTitle)
                results.forEachIndexed { index, imageFile ->
                    val appPageFile = File(appContext.filesDir, "page_${doc.id}_${System.currentTimeMillis()}_$index.jpg")
                    imageFile.copyTo(appPageFile)
                    repository.addPage(doc.id, appPageFile.absolutePath, index)
                }
                
                _uiState.value = ToolsUiState(
                    resultMessage = "Extracted ${results.size} images to a new document!",
                    importedDocumentId = doc.id
                )
            } catch (e: Exception) {
                _uiState.value = ToolsUiState(error = e.message ?: "Operation failed")
            }
        }
    }

    fun importToApp() {
        val file = _uiState.value.resultFile ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, resultMessage = "Importing to app...")
            try {
                val doc = PdfImporter.importPdfAsDocument(file, repository)
                if (doc != null) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        resultMessage = "Imported successfully!",
                        importedDocumentId = doc.id
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        error = "Failed to import document"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    error = e.message ?: "Failed to import document"
                )
            }
        }
    }

    fun clearResult() {
        _uiState.value = ToolsUiState()
    }

    fun selectPdfForEditing(uri: android.net.Uri, onReady: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = ToolsUiState(isProcessing = true, resultMessage = "Preparing document...")
            try {
                val file = withContext(Dispatchers.IO) {
                    copyUriToTemp(uri, "edit_input_${System.currentTimeMillis()}.pdf")
                }
                _uiState.value = ToolsUiState(isProcessing = false)
                onReady(file.absolutePath)
            } catch (e: Exception) {
                _uiState.value = ToolsUiState(error = e.message ?: "Failed to open PDF")
            }
        }
    }

    private fun runTool(loadingMsg: String, block: suspend () -> Pair<String, File>) {
        viewModelScope.launch {
            _uiState.value = ToolsUiState(isProcessing = true, resultMessage = loadingMsg)
            try {
                val (msg, file) = withContext(Dispatchers.IO) { block() }
                _uiState.value = ToolsUiState(resultMessage = msg, resultFile = file)
            } catch (e: Exception) {
                _uiState.value = ToolsUiState(error = e.message ?: "Operation failed")
            }
        }
    }

    private fun copyUriToTemp(uri: Uri, name: String): File {
        val temp = File(appContext.cacheDir, "pdf_tools_input")
        if (!temp.exists()) temp.mkdirs()
        val file = File(temp, name)
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return file
    }
}
