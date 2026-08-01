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
        
        // Try at full requested size first, then one fallback at 75%
        for (attempt in 0..1) {
            var bitmap: Bitmap? = null
            try {
                bitmap = Bitmap.createBitmap(w, h, config)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bitmap
            } catch (e: Throwable) {
                Log.w("BitmapUtils", "OOM creating bitmap ${w}x${h} (attempt $attempt), scaling down...")
                bitmap?.recycle()
                bitmap = null
                Runtime.getRuntime().gc()
                Thread.sleep(50)
                // Scale down to 75% but never below 1600px on the longest side
                val fallbackScale = 0.75f
                w = maxOf(1600, (width * fallbackScale).toInt())
                h = maxOf(1, (height * fallbackScale).toInt())
                if (w > h) {
                    h = maxOf(1, (w.toFloat() * height / width).toInt())
                } else {
                    w = maxOf(1, (h.toFloat() * width / height).toInt())
                }
            }
        }
        return null
    }
}
