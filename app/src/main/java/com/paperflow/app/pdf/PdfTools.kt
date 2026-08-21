package com.paperflow.app.pdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import java.io.File
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.paperflow.app.pdf.PdfEditOverlay

object PdfTools {

    private var initialized = false

    fun init(context: Context) {
        if (!initialized) {
            PDFBoxResourceLoader.init(context.applicationContext)
            initialized = true
        }
    }

    /** Merge multiple PDF files into one. */
    fun mergePdfs(inputFiles: List<File>, outputFile: File): File {
        val merger = PDFMergerUtility()
        merger.destinationFileName = outputFile.absolutePath
        inputFiles.forEach { merger.addSource(it) }
        merger.mergeDocuments(null)
        return outputFile
    }

    /** Split a PDF into individual pages or ranges. */
    fun splitPdf(inputFile: File, outputDir: File, ranges: List<IntRange>? = null): List<File> {
        if (!outputDir.exists()) outputDir.mkdirs()
        val doc = PDDocument.load(inputFile)
        val results = mutableListOf<File>()
        try {
            val splitRanges = ranges ?: (0 until doc.numberOfPages).map { it..it }

            splitRanges.forEachIndexed { idx, range ->
                val newDoc = PDDocument()
                try {
                    for (pageIdx in range) {
                        if (pageIdx < doc.numberOfPages) {
                            newDoc.addPage(doc.getPage(pageIdx))
                        }
                    }
                    val outFile = File(outputDir, "split_${idx + 1}.pdf")
                    newDoc.save(outFile)
                    results.add(outFile)
                } finally {
                    newDoc.close()
                }
            }
        } finally {
            doc.close()
        }
        return results
    }

    /** Rearrange pages in a PDF given new order (0-indexed). */
    fun rearrangePages(inputFile: File, outputFile: File, newOrder: List<Int>): File {
        val doc = PDDocument.load(inputFile)
        try {
            val newDoc = PDDocument()
            try {
                newOrder.forEach { pageIdx ->
                    if (pageIdx < doc.numberOfPages) {
                        newDoc.importPage(doc.getPage(pageIdx))
                    }
                }
                newDoc.save(outputFile)
            } finally {
                newDoc.close()
            }
        } finally {
            doc.close()
        }
        return outputFile
    }

    /** Delete specific pages from a PDF (0-indexed). */
    fun deletePages(inputFile: File, outputFile: File, pagesToDelete: Set<Int>): File {
        val doc = PDDocument.load(inputFile)
        try {
            val newDoc = PDDocument()
            try {
                for (i in 0 until doc.numberOfPages) {
                    if (i !in pagesToDelete) {
                        newDoc.importPage(doc.getPage(i))
                    }
                }
                newDoc.save(outputFile)
            } finally {
                newDoc.close()
            }
        } finally {
            doc.close()
        }
        return outputFile
    }

    /** Get page count of a PDF file. */
    fun getPageCount(file: File): Int {
        val doc = PDDocument.load(file)
        return try {
            doc.numberOfPages
        } finally {
            doc.close()
        }
    }

    /**
     * Compress a PDF by re-rendering pages as JPEG images at the given quality.
     * @param qualityPercent JPEG quality 10-100. Lower = smaller file, less quality.
     */
    /**
     * Compress a PDF with adaptive DPI and resolution scaling.
     * @param qualityPercent Compression level (10-100). Lower = smaller file size.
     */
    fun compressPdf(inputFile: File, outputFile: File, qualityPercent: Int = 60): File {
        val quality = qualityPercent.coerceIn(10, 100)

        // Calculate adaptive DPI and max dimension based on target quality
        val targetDpi = (72 + (quality.toFloat() / 100f) * 78f).toInt().coerceIn(80, 150)
        val targetMaxDim = (900 + (quality.toFloat() / 100f) * 700f).toInt().coerceIn(800, 1600)

        fun performCompressPass(dpi: Int, maxDimLimit: Int, jpegQualityPct: Int): Boolean {
            var fd: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            val doc = PDDocument()
            return try {
                fd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(fd)

                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)

                    var bmpWidth = (page.width * dpi / 72.0).toInt()
                    var bmpHeight = (page.height * dpi / 72.0).toInt()
                    val maxDim = maxOf(bmpWidth, bmpHeight)
                    if (maxDim > maxDimLimit) {
                        val scale = maxDimLimit.toDouble() / maxDim
                        bmpWidth = (bmpWidth * scale).toInt()
                        bmpHeight = (bmpHeight * scale).toInt()
                    }
                    val bitmap = com.paperflow.app.util.BitmapUtils.safeCreateAndRenderBitmap(
                        page, bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888
                    )
                    if (bitmap == null) {
                        page.close()
                        continue
                    }

                    val mediaBox = PDRectangle(page.width.toFloat(), page.height.toFloat())
                    page.close()
                    val pdfPage = PDPage(mediaBox)
                    doc.addPage(pdfPage)

                    val pdImage = JPEGFactory.createFromImage(doc, bitmap, jpegQualityPct.toFloat() / 100f)
                    bitmap.recycle()

                    val contentStream = PDPageContentStream(doc, pdfPage)
                    contentStream.drawImage(pdImage, 0f, 0f, mediaBox.width, mediaBox.height)
                    contentStream.close()
                }

                doc.save(outputFile)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            } finally {
                doc.close()
                renderer?.close()
                fd?.close()
            }
        }

        // Pass 1: Adaptive compression
        val pass1Success = performCompressPass(targetDpi, targetMaxDim, quality)

        // Safety Guard: If compressed result is LARGER than original or failed, perform aggressive Pass 2
        if (!pass1Success || outputFile.length() >= inputFile.length()) {
            val pass2Success = performCompressPass(dpi = 85, maxDimLimit = 950, jpegQualityPct = (quality - 15).coerceAtLeast(20))
            if (!pass2Success || outputFile.length() >= inputFile.length()) {
                // If file size cannot be reduced further, preserve original input file so size NEVER increases!
                inputFile.copyTo(outputFile, overwrite = true)
            }
        }

        return outputFile
    }

    /** Import images as a PDF. */
    fun imagesToPdf(context: Context, imagePaths: List<String>, outputFile: File): File {
        init(context)
        // Delegate to the existing PdfExporter for image->PDF
        return PdfExporter.exportToPdf(
            context, imagePaths, PageSize.A4, ExportQuality.BALANCED, outputFile,
        )
    }

    /** Convert a PDF into a folder of images. */
    fun pdfToImages(inputFile: File, outputDir: File): List<File> {
        if (!outputDir.exists()) outputDir.mkdirs()
        val results = mutableListOf<File>()
        
        var descriptor: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            descriptor = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(descriptor)
            
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val densityDpi = 300
                var bmpWidth = (page.width * densityDpi / 72.0).toInt()
                var bmpHeight = (page.height * densityDpi / 72.0).toInt()
                val maxDim = maxOf(bmpWidth, bmpHeight)
                if (maxDim > 3500) {
                    val scale = 3500.0 / maxDim
                    bmpWidth = (bmpWidth * scale).toInt()
                    bmpHeight = (bmpHeight * scale).toInt()
                }
                val bitmap = com.paperflow.app.util.BitmapUtils.safeCreateAndRenderBitmap(page, bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
                if (bitmap == null) {
                    page.close()
                    continue
                }
                
                val outFile = File(outputDir, "page_${i + 1}.jpg")
                outFile.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                bitmap.recycle()
                page.close()
                results.add(outFile)
            }
        } finally {
            renderer?.close()
            descriptor?.close()
        }
        return results
    }

    /**
     * Add a diagonal text watermark across all pages in a PDF document.
     */
    fun watermarkPdf(
        inputFile: File,
        outputFile: File,
        watermarkText: String,
        opacity: Float = 0.35f,
        fontSize: Float = 48f
    ): File {
        val doc = PDDocument.load(inputFile)
        try {
            val font = PDType1Font.HELVETICA_BOLD
            val safeText = sanitizeWinAnsiText(font, watermarkText)

            for (page in doc.pages) {
                val mediaBox = page.mediaBox
                val width = mediaBox.width
                val height = mediaBox.height

                val contentStream = PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true
                )

                try {
                    val extGState = com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState().apply {
                        nonStrokingAlphaConstant = opacity.coerceIn(0.05f, 1.0f)
                    }
                    contentStream.setGraphicsStateParameters(extGState)

                    val textWidth = (font.getStringWidth(safeText) / 1000f) * fontSize
                    val centerX = width / 2f
                    val centerY = height / 2f

                    contentStream.beginText()
                    contentStream.setFont(font, fontSize)
                    contentStream.setNonStrokingColor(180, 180, 180)

                    // Rotate 45 degrees around center
                    val matrix = com.tom_roush.pdfbox.util.Matrix.getRotateInstance(
                        Math.toRadians(45.0),
                        centerX - (textWidth / 2f * 0.707f),
                        centerY - (textWidth / 2f * 0.707f)
                    )
                    contentStream.setTextMatrix(matrix)
                    contentStream.showText(safeText)
                    contentStream.endText()
                } finally {
                    contentStream.close()
                }
            }
            doc.save(outputFile)
        } finally {
            doc.close()
        }
        return outputFile
    }

    /**
     * Encrypt / Password protect a PDF with a user password.
     */
    fun protectPdf(
        inputFile: File,
        outputFile: File,
        userPassword: String,
        ownerPassword: String = userPassword
    ): File {
        val doc = PDDocument.load(inputFile)
        try {
            val permissions = com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission()
            val policy = com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy(
                ownerPassword, userPassword, permissions
            )
            policy.encryptionKeyLength = 128
            doc.protect(policy)
            doc.save(outputFile)
        } finally {
            doc.close()
        }
        return outputFile
    }

    /**
     * Unlock / Remove password protection from an encrypted PDF.
     */
    fun unlockPdf(
        inputFile: File,
        outputFile: File,
        password: String
    ): File {
        val doc = PDDocument.load(inputFile, password)
        try {
            doc.isAllSecurityToBeRemoved = true
            doc.save(outputFile)
        } finally {
            doc.close()
        }
        return outputFile
    }

    fun createToolOutputFile(context: Context, name: String): File {
        val dir = File(context.cacheDir, "pdf_tools")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, name)
    }

    fun createToolOutputDir(context: Context, name: String): File {
        val dir = File(context.cacheDir, "pdf_tools/$name")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Save text overlays and whiteouts natively into the PDF document as vector items. */
    fun savePdfEdits(inputFile: File, outputFile: File, overlays: List<PdfEditOverlay>) {
        val doc = PDDocument.load(inputFile)
        try {
            val pageCount = doc.numberOfPages
            val overlaysByPage = overlays.groupBy { it.pageIndex }

            for ((pageIdx, pageOverlays) in overlaysByPage) {
                if (pageIdx >= pageCount) continue
                val page = doc.getPage(pageIdx)
                val mediaBox = page.mediaBox
                val pageWidth = mediaBox.width
                val pageHeight = mediaBox.height

                // Append edits natively
                val contentStream = PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true
                )
                
                for (overlay in pageOverlays) {
                    val absX = overlay.xFraction * pageWidth
                    val absY = pageHeight - (overlay.yFraction * pageHeight) - (overlay.heightFraction * pageHeight)
                    val absW = overlay.widthFraction * pageWidth
                    val absH = overlay.heightFraction * pageHeight

                    // 1. Vector Whiteout / Mask
                    if (overlay.whiteout) {
                        val c = android.graphics.Color.valueOf(if (overlay.type == OverlayType.WHITEOUT_MASK) overlay.color else android.graphics.Color.WHITE)
                        contentStream.setNonStrokingColor(c.red(), c.green(), c.blue())
                        contentStream.addRect(absX - 1.5f, absY - 1.5f, absW + 3f, absH + 3f)
                        contentStream.fill()
                    }

                    // 2. Vector Text
                    if (overlay.text.isNotEmpty()) {
                        try {
                            contentStream.beginText()
                            val font = getStandardFont(overlay.fontName, overlay.isBold, overlay.isItalic)
                            contentStream.setFont(font, overlay.fontSize)

                            val color = android.graphics.Color.valueOf(overlay.color)
                            contentStream.setNonStrokingColor(color.red(), color.green(), color.blue())

                            val safeText = sanitizeWinAnsiText(font, overlay.text)
                            // Position text baseline (approx absY + 15% of height for perfect alignment)
                            contentStream.newLineAtOffset(absX, absY + (absH * 0.15f))
                            contentStream.showText(safeText)
                            contentStream.endText()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // 3. Signature & Image Overlay
                    if (overlay.imagePath != null && File(overlay.imagePath).exists()) {
                        try {
                            val imgFile = File(overlay.imagePath)
                            val pdImage = com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject.createFromFileByExtension(imgFile, doc)
                            contentStream.drawImage(pdImage, absX, absY, absW, absH)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                contentStream.close()
            }
            doc.save(outputFile)
        } finally {
            doc.close()
        }
    }

    private fun sanitizeWinAnsiText(font: PDType1Font, text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            try {
                font.encode(ch.toString())
                sb.append(ch)
            } catch (_: Exception) {
                sb.append('?')
            }
        }
        return sb.toString()
    }

    private fun getStandardFont(fontCategory: String, isBold: Boolean, isItalic: Boolean): PDType1Font {
        val cat = fontCategory.lowercase()
        return when {
            cat.contains("serif") || cat.contains("times") -> {
                when {
                    isBold && isItalic -> PDType1Font.TIMES_BOLD_ITALIC
                    isBold -> PDType1Font.TIMES_BOLD
                    isItalic -> PDType1Font.TIMES_ITALIC
                    else -> PDType1Font.TIMES_ROMAN
                }
            }
            cat.contains("monospace") || cat.contains("courier") -> {
                when {
                    isBold && isItalic -> PDType1Font.COURIER_BOLD_OBLIQUE
                    isBold -> PDType1Font.COURIER_BOLD
                    isItalic -> PDType1Font.COURIER_OBLIQUE
                    else -> PDType1Font.COURIER
                }
            }
            else -> { // Default to Helvetica (Sans-serif)
                when {
                    isBold && isItalic -> PDType1Font.HELVETICA_BOLD_OBLIQUE
                    isBold -> PDType1Font.HELVETICA_BOLD
                    isItalic -> PDType1Font.HELVETICA_OBLIQUE
                    else -> PDType1Font.HELVETICA
                }
            }
        }
    }
}
