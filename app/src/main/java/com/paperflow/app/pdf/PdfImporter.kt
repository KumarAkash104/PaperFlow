package com.paperflow.app.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.paperflow.app.data.model.DocumentEntity
import com.paperflow.app.data.repository.DocumentRepository
import com.paperflow.app.util.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfImporter {

    /**
     * Imports a PDF file into the app as a new Document.
     * Renders each page of the PDF into a high-quality Bitmap, saves it, and adds it to the document.
     */
    suspend fun importPdfAsDocument(
        file: File,
        repository: DocumentRepository,
        titlePrefix: String = "Imported"
    ): DocumentEntity? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null

        val document = repository.createDocument("$titlePrefix ${file.name}")
        
        var fd: ParcelFileDescriptor? = null
        var pdfRenderer: PdfRenderer? = null
        
        try {
            fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fd)

            val pageCount = pdfRenderer.pageCount
            for (i in 0 until pageCount) {
                val page = pdfRenderer.openPage(i)
                
                // Render at a higher resolution (e.g., 2x standard) for good editing quality
                // Standard A4 is 595x842 pts. Let's render at ~1500px width, clamped to a safe maximum of 2800px.
                val densityDpi = 300 // Simulate 300 dpi
                val rawWidth = (page.width * densityDpi / 72.0).toInt()
                val rawHeight = (page.height * densityDpi / 72.0).toInt()
                var width = rawWidth
                var height = rawHeight
                val maxDim = maxOf(width, height)
                if (maxDim > 2800) {
                    val scale = 2800.0 / maxDim
                    width = (width * scale).toInt()
                    height = (height * scale).toInt()
                }

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                // Fill with white background (PDFs are often transparent)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                // Save bitmap to file
                val imageFile = repository.createScanImageFile()
                ImageProcessor.saveBitmap(bitmap, imageFile, 95)
                bitmap.recycle()

                // Add page to document
                repository.addPage(document.id, imageFile.absolutePath, i)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        } finally {
            pdfRenderer?.close()
            fd?.close()
        }

        return@withContext document
    }
}
