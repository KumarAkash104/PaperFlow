package com.paperflow.app.pdf

enum class OverlayType {
    TEXT_EDIT,
    CUSTOM_TEXT,
    WHITEOUT_MASK,
    SIGNATURE,
    IMAGE_STAMP
}

data class PdfEditOverlay(
    val id: String,
    val pageIndex: Int,
    val type: OverlayType = OverlayType.TEXT_EDIT,
    val originalText: String? = null,
    val text: String = "",
    val xFraction: Float,         // Left coordinate (0..1) relative to page top-left
    val yFraction: Float,         // Top coordinate (0..1) relative to page top-left
    val widthFraction: Float,     // Width (0..1) relative to page width
    val heightFraction: Float,    // Height (0..1) relative to page height
    val fontSize: Float = 14f,    // Font size in points
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val color: Int = android.graphics.Color.BLACK, // RGB Int color
    val fontName: String = "Sans", // "Sans", "Serif", "Monospace"
    val whiteout: Boolean = true,  // Whether to erase original text underneath
    val imagePath: String? = null,  // For signature or image stamp overlays
    val rotationDegrees: Int = 0
)
