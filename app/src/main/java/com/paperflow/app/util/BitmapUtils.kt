package com.paperflow.app.util

import android.graphics.Bitmap
import android.util.Log

object BitmapUtils {
    /**
     * Safely creates and renders a Bitmap from a PdfRenderer.Page, scaling down if an OutOfMemoryError occurs.
     */
    fun safeCreateAndRenderBitmap(
        page: android.graphics.pdf.PdfRenderer.Page,
        width: Int,
        height: Int,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888
    ): Bitmap? {
        var w = maxOf(1, width)
        var h = maxOf(1, height)
        var scale = 1.0f
        
        while (scale >= 0.1f) {
            var bitmap: Bitmap? = null
            try {
                bitmap = Bitmap.createBitmap(w, h, config)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bitmap
            } catch (e: Throwable) {
                Log.w("BitmapUtils", "Error creating/rendering bitmap ${w}x${h}, scaling down...")
                bitmap?.recycle()
                scale *= 0.8f
                w = maxOf(1, (width * scale).toInt())
                h = maxOf(1, (height * scale).toInt())
                // Try to hint GC
                System.gc()
            }
        }
        return null
    }
}
