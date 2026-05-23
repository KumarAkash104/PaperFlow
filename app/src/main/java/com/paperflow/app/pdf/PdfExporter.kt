package com.paperflow.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.Rect
import com.paperflow.app.util.ImageProcessor
import java.io.File
import java.io.FileOutputStream

enum class PageSize(val label: String, val widthPt: Int, val heightPt: Int) {
    AUTO("Auto", 0, 0),
    A4("A4", 595, 842),
    LETTER("Letter", 612, 792),
    LEGAL("Legal", 612, 1008),
}

enum class ExportQuality(val label: String, val jpegQuality: Int) {
    SMALL("Small", 50),
    BALANCED("Balanced", 75),
    HIGH("High Quality", 95),
}

object PdfExporter {

    fun exportToPdf(
        context: Context,
        imagePaths: List<String>,
        pageSize: PageSize = PageSize.A4,
        quality: ExportQuality = ExportQuality.BALANCED,
        outputFile: File,
    ): File {
        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        imagePaths.forEachIndexed { index, path ->
            val bitmap = ImageProcessor.loadBitmap(path, 3000) ?: return@forEachIndexed

            val (pageW, pageH) = if (pageSize == PageSize.AUTO) {
                // Use image aspect ratio, scale to ~A4 width
                val scale = 595f / bitmap.width
                (595) to (bitmap.height * scale).toInt()
            } else {
                pageSize.widthPt to pageSize.heightPt
            }

            val densityScale = 300f / 72f
            val pdfW = (pageW * densityScale).toInt()
            val pdfH = (pageH * densityScale).toInt()

            val pageInfo = PdfDocument.PageInfo.Builder(pdfW, pdfH, index + 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            canvas.scale(densityScale, densityScale)

            // Fit image within page with margins
            val margin = if (pageSize == PageSize.AUTO) 0 else 20
            val drawW = pageW - margin * 2
            val drawH = pageH - margin * 2

            val imgAspect = bitmap.width.toFloat() / bitmap.height
            val pageAspect = drawW.toFloat() / drawH

            val (dstW, dstH) = if (imgAspect > pageAspect) {
                drawW to (drawW / imgAspect).toInt()
            } else {
                (drawH * imgAspect).toInt() to drawH
            }

            val left = margin + (drawW - dstW) / 2
            val top = margin + (drawH - dstH) / 2

            canvas.drawBitmap(
                bitmap,
                null,
                Rect(left, top, left + dstW, top + dstH),
                paint,
            )

            // Watermark in bottom-right corner
            val wmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(40, 128, 128, 128)
                textSize = 10f
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.SANS_SERIF,
                    android.graphics.Typeface.NORMAL,
                )
            }
            val wmText = "PaperFlow"
            val wmWidth = wmPaint.measureText(wmText)
            canvas.drawText(wmText, pageW - wmWidth - 12f, pageH - 12f, wmPaint)

            document.finishPage(page)
        }

        FileOutputStream(outputFile).use { document.writeTo(it) }
        document.close()
        return outputFile
    }

    fun exportToImages(
        imagePaths: List<String>,
        quality: ExportQuality,
        outputDir: File,
    ): List<File> {
        if (!outputDir.exists()) outputDir.mkdirs()
        return imagePaths.mapIndexed { index, path ->
            val bitmap = ImageProcessor.loadBitmap(path) ?: return@mapIndexed null
            val outFile = File(outputDir, "page_${index + 1}.jpg")
            ImageProcessor.saveBitmap(bitmap, outFile, quality.jpegQuality)
            outFile
        }.filterNotNull()
    }

    fun createExportFile(context: Context, name: String, extension: String): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${name}.$extension")
    }
}
