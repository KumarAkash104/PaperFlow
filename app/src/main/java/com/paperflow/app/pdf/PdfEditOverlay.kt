package com.paperflow.app.pdf

data class PdfEditOverlay(
    val id: String,
    val pageIndex: Int,
    val originalText: String?,
    val text: String,
    val xFraction: Float,         // Left coordinate (0..1) relative to page top-left
    val yFraction: Float,         // Top coordinate (0..1) relative to page top-left
    val widthFraction: Float,     // Width (0..1) relative to page width
    val heightFraction: Float,    // Height (0..1) relative to page height
    val fontSize: Float,          // Font size in points
    val isBold: Boolean,
    val isItalic: Boolean,
    val color: Int,               // RGB Int color
    val fontName: String,         // "Sans", "Serif", "Monospace" (maps to Helvetica, Times, Courier)
    val whiteout: Boolean = true  // Whether to erase original text underneath
)
