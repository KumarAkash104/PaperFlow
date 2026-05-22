package com.paperflow.app.pdf

import android.graphics.Color
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.io.IOException
import java.io.Writer

data class PdfTextBlock(
    val text: String,
    val x: Float,          // PDF points relative to top-left of page (xDirAdj)
    val y: Float,          // PDF points relative to top-left of page (yDirAdj - height)
    val width: Float,
    val height: Float,
    val fontSize: Float,
    val isBold: Boolean,
    val isItalic: Boolean,
    val color: Int,        // RGB Color
    val fontName: String,
    val pageIndex: Int
)

class PdfTextExtractor(private val pageIdx: Int) : PDFTextStripper() {
    val blocks = mutableListOf<PdfTextBlock>()
    private var pageHeight = 0f
    private var pageWidth = 0f

    init {
        sortByPosition = true
        startPage = pageIdx + 1
        endPage = pageIdx + 1
    }

    override fun startPage(page: PDPage) {
        val mediaBox = page.mediaBox
        pageWidth = mediaBox.width
        pageHeight = mediaBox.height
    }

    @Throws(IOException::class)
    override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
        if (text == null || textPositions == null || textPositions.isEmpty()) return

        var currentWordChars = mutableListOf<TextPosition>()

        for (tp in textPositions) {
            val unicode = tp.unicode
            if (unicode == null || unicode.isEmpty()) continue

            if (currentWordChars.isEmpty()) {
                if (unicode != " ") {
                    currentWordChars.add(tp)
                }
            } else {
                val last = currentWordChars.last()
                val gap = tp.xDirAdj - (last.xDirAdj + last.widthDirAdj)
                val sameY = Math.abs(tp.yDirAdj - last.yDirAdj) < 2f
                val sameFont = tp.font.name == last.font.name && tp.fontSizeInPt == last.fontSizeInPt
                
                // standard space is about 0.3 to 0.4 of font size
                val maxGap = tp.fontSizeInPt * 0.4f

                if (sameY && sameFont && gap >= 0f && gap < maxGap && unicode != " ") {
                    currentWordChars.add(tp)
                } else {
                    // Flush word block
                    val block = createBlockFromChars(currentWordChars)
                    if (block != null) {
                        blocks.add(block)
                    }
                    currentWordChars = mutableListOf()
                    if (unicode != " ") {
                        currentWordChars.add(tp)
                    }
                }
            }
        }

        if (currentWordChars.isNotEmpty()) {
            val block = createBlockFromChars(currentWordChars)
            if (block != null) {
                blocks.add(block)
            }
        }
    }

    private fun createBlockFromChars(chars: List<TextPosition>): PdfTextBlock? {
        if (chars.isEmpty()) return null
        val text = chars.joinToString("") { it.unicode ?: "" }.trim()
        if (text.isEmpty()) return null

        val first = chars.first()
        val last = chars.last()

        val x = first.xDirAdj
        // In PDFBox, yDirAdj is the text baseline, and heightDir is the height of character.
        // We want the bounding box top to be baseline - height.
        val height = chars.maxOf { it.heightDir }
        val y = chars.minOf { it.yDirAdj - it.heightDir }
        val width = (last.xDirAdj + last.widthDirAdj) - first.xDirAdj

        val font = first.font
        val fontName = font.name ?: "Helvetica"
        val fontSize = first.fontSizeInPt

        val isBold = fontName.contains("Bold", ignoreCase = true) || fontName.contains("Black", ignoreCase = true)
        val isItalic = fontName.contains("Italic", ignoreCase = true) || fontName.contains("Oblique", ignoreCase = true)

        val color = try {
            val gs = graphicsState
            val pdColor = gs.nonStrokingColor
            val rgbComponents = pdColor.colorSpace.toRGB(pdColor.components)
            val r = (rgbComponents[0] * 255).toInt().coerceIn(0, 255)
            val g = (rgbComponents[1] * 255).toInt().coerceIn(0, 255)
            val b = (rgbComponents[2] * 255).toInt().coerceIn(0, 255)
            Color.rgb(r, g, b)
        } catch (e: Exception) {
            Color.BLACK
        }

        return PdfTextBlock(
            text = text,
            x = x,
            y = y,
            width = width,
            height = height,
            fontSize = fontSize,
            isBold = isBold,
            isItalic = isItalic,
            color = color,
            fontName = fontName,
            pageIndex = pageIdx
        )
    }

    private object NullWriter : Writer() {
        override fun write(cbuf: CharArray, off: Int, len: Int) {}
        override fun flush() {}
        override fun close() {}
    }

    companion object {
        fun extractTextFromPdf(file: File): List<PdfTextBlock> {
            val document = PDDocument.load(file)
            val allBlocks = mutableListOf<PdfTextBlock>()
            try {
                val pageCount = document.numberOfPages
                for (i in 0 until pageCount) {
                    val extractor = PdfTextExtractor(i)
                    extractor.writeText(document, NullWriter)
                    allBlocks.addAll(extractor.blocks)
                }
            } finally {
                document.close()
            }
            return allBlocks
        }
    }
}
