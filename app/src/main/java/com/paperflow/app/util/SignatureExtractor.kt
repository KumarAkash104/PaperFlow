package com.paperflow.app.util

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

/**
 * Extracts signature ink from a photo by removing the paper background.
 * Works best with dark ink on light paper.
 *
 * Algorithm:
 * 1. Convert to grayscale
 * 2. Compute local adaptive threshold (mean of neighbourhood)
 * 3. Pixels darker than (local_mean - sensitivity) → ink (opaque)
 * 4. Everything else → transparent
 * 5. Crop to bounding box of non-transparent pixels
 */
object SignatureExtractor {

    /**
     * @param src          The source photo bitmap
     * @param sensitivity  How aggressively to detect ink (0..100). Higher = more ink detected.
     *                     Default 30 works well for black ink on white paper.
     * @return Bitmap with transparent background containing only the signature strokes
     */
    fun extract(src: Bitmap, sensitivity: Int = 20): Bitmap {
        val w = src.width
        val h = src.height

        // Work on a manageable size for speed — higher = better quality
        val maxDim = 2500
        val scale = if (max(w, h) > maxDim) maxDim.toFloat() / max(w, h) else 1f
        val workW = (w * scale).toInt().coerceAtLeast(1)
        val workH = (h * scale).toInt().coerceAtLeast(1)
        val work = if (scale < 1f) Bitmap.createScaledBitmap(src, workW, workH, true) else src

        val pixels = IntArray(workW * workH)
        work.getPixels(pixels, 0, workW, 0, 0, workW, workH)

        // Step 1: Convert to grayscale luminance
        val gray = IntArray(workW * workH)
        for (i in pixels.indices) {
            val r = Color.red(pixels[i])
            val g = Color.green(pixels[i])
            val b = Color.blue(pixels[i])
            gray[i] = ((0.299 * r + 0.587 * g + 0.114 * b).toInt()).coerceIn(0, 255)
        }

        // Step 2: Compute integral image for fast local mean
        val integral = LongArray(workW * workH)
        for (y in 0 until workH) {
            var rowSum = 0L
            for (x in 0 until workW) {
                rowSum += gray[y * workW + x]
                integral[y * workW + x] = rowSum + if (y > 0) integral[(y - 1) * workW + x] else 0
            }
        }

        // Step 3: Adaptive threshold using local mean
        val blockRadius = max(workW, workH) / 16 // neighbourhood size
        val threshold = sensitivity.toFloat()
        val output = IntArray(workW * workH)

        for (y in 0 until workH) {
            for (x in 0 until workW) {
                val x0 = max(0, x - blockRadius)
                val y0 = max(0, y - blockRadius)
                val x1 = min(workW - 1, x + blockRadius)
                val y1 = min(workH - 1, y + blockRadius)

                val count = (x1 - x0 + 1) * (y1 - y0 + 1)
                var sum = integral[y1 * workW + x1]
                if (x0 > 0) sum -= integral[y1 * workW + x0 - 1]
                if (y0 > 0) sum -= integral[(y0 - 1) * workW + x1]
                if (x0 > 0 && y0 > 0) sum += integral[(y0 - 1) * workW + x0 - 1]

                val localMean = sum.toFloat() / count
                val pixelVal = gray[y * workW + x]

                // Pixel is ink if it's significantly darker than its neighbourhood (soft transparency)
                val diff = localMean - pixelVal
                if (diff > threshold) {
                    val transitionWidth = 25f
                    val alpha = (((diff - threshold) / transitionWidth) * 255f).toInt().coerceIn(0, 255)
                    if (alpha > 0) {
                        val origColor = pixels[y * workW + x]
                        output[y * workW + x] = Color.argb(
                            alpha,
                            Color.red(origColor),
                            Color.green(origColor),
                            Color.blue(origColor),
                        )
                    } else {
                        output[y * workW + x] = Color.TRANSPARENT
                    }
                } else {
                    output[y * workW + x] = Color.TRANSPARENT
                }
            }
        }

        // Step 4: Find bounding box of non-transparent pixels
        var minX = workW; var maxX = 0; var minY = workH; var maxY = 0
        for (y in 0 until workH) {
            for (x in 0 until workW) {
                if (Color.alpha(output[y * workW + x]) > 0) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        // If nothing was found, return a small transparent bitmap
        if (minX > maxX || minY > maxY) {
            return Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
        }

        // Add small padding around the bounding box
        val pad = max(blockRadius / 2, 8)
        minX = max(0, minX - pad)
        minY = max(0, minY - pad)
        maxX = min(workW - 1, maxX + pad)
        maxY = min(workH - 1, maxY + pad)

        val cropW = maxX - minX + 1
        val cropH = maxY - minY + 1

        // Step 5: Create cropped result
        val result = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
        val croppedPixels = IntArray(cropW * cropH)
        for (y in 0 until cropH) {
            for (x in 0 until cropW) {
                croppedPixels[y * cropW + x] = output[(minY + y) * workW + (minX + x)]
            }
        }
        result.setPixels(croppedPixels, 0, cropW, 0, 0, cropW, cropH)
        return result
    }
}
