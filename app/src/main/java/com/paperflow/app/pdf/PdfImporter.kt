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
            
            // Save source PDF into document folder for instant, zero-loss vector reading
            val docDir = repository.getDocumentDir(document.id)
            val docSourcePdf = File(docDir, "source.pdf")
            file.copyTo(docSourcePdf, overwrite = true)

            for (i in 0 until pageCount) {
                val page = pdfRenderer.openPage(i)
                // 300 DPI, 2560px max — safe for Android heap without OOM fallback
                val densityDpi = 300
                var width = (page.width * densityDpi / 72.0).toInt()
                var height = (page.height * densityDpi / 72.0).toInt()
                val maxDim = maxOf(width, height)
                if (maxDim > 2560) {
                    val scale = 2560.0 / maxDim
                    width = (width * scale).toInt()
                    height = (height * scale).toInt()
                }

                val bitmap = com.paperflow.app.util.BitmapUtils.safeCreateAndRenderBitmap(page, width, height, Bitmap.Config.ARGB_8888)
                if (bitmap != null) {
                    val pageImgFile = File(docDir, "page_${i}.png")
                    pageImgFile.outputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    bitmap.recycle()
                    repository.addPage(document.id, pageImgFile.absolutePath, i)
                }
                page.close()

                // Aggressive memory cleanup after each page to prevent OOM on multi-page PDFs
                System.gc()
            }

            val updatedPages = repository.getPagesSync(document.id)
            if (updatedPages.isNotEmpty()) {
                repository.updateDocument(document.copy(thumbnailPath = updatedPages.first().imagePath, pageCount = updatedPages.size))
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
