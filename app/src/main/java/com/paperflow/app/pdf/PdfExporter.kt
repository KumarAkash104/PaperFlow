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
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context.applicationContext)
        val document = com.tom_roush.pdfbox.pdmodel.PDDocument()

        imagePaths.forEachIndexed { index, path ->
            try {
                // Load image to get dimensions for AUTO page size
                val bitmap = ImageProcessor.loadBitmap(path)
                if (bitmap == null) return@forEachIndexed

                // Determine page dimensions
                val pageRect = when (pageSize) {
                    PageSize.AUTO -> {
                        // Fit page to image at 72 DPI (points = pixels at 72 DPI)
                        val imgW = bitmap.width.toFloat()
                        val imgH = bitmap.height.toFloat()
                        // Cap to A4 max dimensions but preserve aspect ratio
                        val maxW = 595f; val maxH = 842f
                        val scale = minOf(maxW / imgW, maxH / imgH, 1f)
                        com.tom_roush.pdfbox.pdmodel.common.PDRectangle(imgW * scale, imgH * scale)
                    }
                    PageSize.A4 -> com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4
                    PageSize.LETTER -> com.tom_roush.pdfbox.pdmodel.common.PDRectangle.LETTER
                    PageSize.LEGAL -> com.tom_roush.pdfbox.pdmodel.common.PDRectangle.LEGAL
                }

                val page = com.tom_roush.pdfbox.pdmodel.PDPage(pageRect)
                document.addPage(page)

                // Encode bitmap with the specified quality
                val pdImage = com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory.createFromImage(
                    document, bitmap, quality.jpegQuality.toFloat() / 100f
                )
                bitmap.recycle()

                val contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(document, page)

                val pw = page.mediaBox.width
                val ph = page.mediaBox.height

                val margin = if (pageSize == PageSize.AUTO) 0f else 20f
                val drawW = pw - margin * 2
                val drawH = ph - margin * 2

                val imgW = pdImage.width.toFloat()
                val imgH = pdImage.height.toFloat()

                val imgAspect = imgW / imgH
                val pageAspect = drawW / drawH

                val (dstW, dstH) = if (imgAspect > pageAspect) {
                    drawW to (drawW / imgAspect)
                } else {
                    (drawH * imgAspect) to drawH
                }

                val left = margin + (drawW - dstW) / 2
                val top = margin + (drawH - dstH) / 2

                contentStream.drawImage(pdImage, left, top, dstW, dstH)
                contentStream.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        document.save(outputFile)
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
