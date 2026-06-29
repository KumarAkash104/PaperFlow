package com.paperflow.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// ── Filter types ──

enum class FilterType(val label: String) {
    ORIGINAL("Original"),
    AUTO_ENHANCE("Auto"),
    MAGIC_COLOR("Magic"),
    HIGH_CONTRAST("Hi-Contrast"),
    LIGHTEN("Lighten"),
    SHARPEN("Sharpen"),
    VIVID("Vivid"),
    INVERT("Invert"),
}

// ── Adjustment values ──

data class AdjustmentValues(
    val brightness: Float = 0f,   // -100..100
    val contrast: Float = 0f,     // -100..100
    val saturation: Float = 0f,   // -100..100
    val sharpness: Float = 0f,    // 0..100
    val warmth: Float = 0f,       // -100..100
    val exposure: Float = 0f,     // -100..100
)

// ── Editor mode ──

enum class EditorMode(val label: String) {
    FILTER("Filter"),
    ADJUST("Adjust"),
    CROP("Crop"),
    ROTATE("Rotate"),
}

// ── Image processor ──

object ImageProcessor {

    fun loadBitmap(path: String, maxDim: Int = 0): Bitmap? {
        val raw = if (maxDim > 0) {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            var sample = 1
            while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) {
                sample *= 2
            }
            opts.inJustDecodeBounds = false
            opts.inSampleSize = sample
            BitmapFactory.decodeFile(path, opts)
        } else {
            BitmapFactory.decodeFile(path)
        } ?: return null

        // Apply EXIF orientation so camera photos display correctly
        return applyExifRotation(raw, path)
    }

    private fun applyExifRotation(bitmap: Bitmap, path: String): Bitmap {
        return try {
            val exif = ExifInterface(path)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postRotate(90f); matrix.preScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postRotate(270f); matrix.preScale(-1f, 1f)
                }
                else -> return bitmap // No rotation needed
            }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            rotated
        } catch (_: Exception) {
            bitmap
        }
    }

    // ── Auto edge detection ──

    fun autoDetectEdges(bitmap: Bitmap): List<PointF> {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()

        // Downscale for speed and noise reduction
        val maxDim = 200
        val scale = maxDim.toFloat() / max(bitmap.width, bitmap.height)
        val sw = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val sh = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bitmap, sw, sh, true)

        val pixels = IntArray(sw * sh)
        small.getPixels(pixels, 0, sw, 0, 0, sw, sh)

        // 1. Convert to grayscale and apply a basic 3x3 box blur
        val gray = IntArray(sw * sh)
        val blurred = IntArray(sw * sh)
        for (y in 0 until sh) {
            for (x in 0 until sw) {
                val idx = y * sw + x
                val p = pixels[idx]
                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)
                gray[idx] = ((0.299 * r + 0.587 * g + 0.114 * b).toInt()).coerceIn(0, 255)
            }
        }

        // Simple 3x3 box blur to smooth out text and texture
        for (y in 0 until sh) {
            for (x in 0 until sw) {
                val idx = y * sw + x
                if (x == 0 || x == sw - 1 || y == 0 || y == sh - 1) {
                    blurred[idx] = gray[idx]
                    continue
                }
                var sum = 0
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        sum += gray[idx + ky * sw + kx]
                    }
                }
                blurred[idx] = sum / 9
            }
        }

        // 2. Otsu's Thresholding
        val hist = IntArray(256)
        for (g in blurred) {
            hist[g]++
        }
        var total = sw * sh
        var sumVal = 0f
        for (t in 0..255) {
            sumVal += t * hist[t]
        }
        var sumB = 0f
        var wB = 0
        var wF = 0
        var varMax = 0f
        var threshold = 128
        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0) continue
            wF = total - wB
            if (wF == 0) break
            sumB += t * hist[t]
            val mB = sumB / wB
            val mF = (sumVal - sumB) / wF
            val varBetween = wB.toFloat() * wF.toFloat() * (mB - mF) * (mB - mF)
            if (varBetween > varMax) {
                varMax = varBetween
                threshold = t
            }
        }

        // Binarize: 1 for bright (document candidate), 0 for dark (background)
        var binary = IntArray(sw * sh)
        for (i in blurred.indices) {
            binary[i] = if (blurred[i] >= threshold) 1 else 0
        }

        // 3. Morphological Operations to clean up noise and fill text holes
        // We do morphological closing (dilate then erode) to fill holes in the text
        fun dilate(src: IntArray): IntArray {
            val dst = src.clone()
            for (y in 1 until sh - 1) {
                for (x in 1 until sw - 1) {
                    val idx = y * sw + x
                    if (src[idx] == 0) {
                        if (src[idx - 1] == 1 || src[idx + 1] == 1 ||
                            src[idx - sw] == 1 || src[idx + sw] == 1 ||
                            src[idx - sw - 1] == 1 || src[idx - sw + 1] == 1 ||
                            src[idx + sw - 1] == 1 || src[idx + sw + 1] == 1) {
                            dst[idx] = 1
                        }
                    }
                }
            }
            return dst
        }

        fun erode(src: IntArray): IntArray {
            val dst = src.clone()
            for (y in 1 until sh - 1) {
                for (x in 1 until sw - 1) {
                    val idx = y * sw + x
                    if (src[idx] == 1) {
                        if (src[idx - 1] == 0 || src[idx + 1] == 0 ||
                            src[idx - sw] == 0 || src[idx + sw] == 0 ||
                            src[idx - sw - 1] == 0 || src[idx - sw + 1] == 0 ||
                            src[idx + sw - 1] == 0 || src[idx + sw + 1] == 0) {
                            dst[idx] = 0
                        }
                    }
                }
            }
            return dst
        }

        // Closing (dilate twice, erode twice) to fill larger text and table gaps
        binary = dilate(binary)
        binary = dilate(binary)
        binary = erode(binary)
        binary = erode(binary)

        // Opening (erode once, dilate once) to remove small noise speckles outside
        binary = erode(binary)
        binary = dilate(binary)

        // 4. Connected Component Analysis to find the largest contiguous paper blob
        // We use BFS with a BitSet
        val visited = java.util.BitSet(sw * sh)
        var largestComponent = listOf<Int>()
        
        for (y in 0 until sh) {
            for (x in 0 until sw) {
                val idx = y * sw + x
                if (binary[idx] == 1 && !visited.get(idx)) {
                    val comp = mutableListOf<Int>()
                    val queue = java.util.ArrayDeque<Int>()
                    queue.add(idx)
                    visited.set(idx)
                    
                    while (queue.isNotEmpty()) {
                        val curr = queue.poll()!!
                        comp.add(curr)
                        
                        val cx = curr % sw
                        val cy = curr / sw
                        
                        // Check 4 neighbors
                        if (cx > 0) {
                            val n = curr - 1
                            if (binary[n] == 1 && !visited.get(n)) {
                                visited.set(n)
                                queue.add(n)
                            }
                        }
                        if (cx < sw - 1) {
                            val n = curr + 1
                            if (binary[n] == 1 && !visited.get(n)) {
                                visited.set(n)
                                queue.add(n)
                            }
                        }
                        if (cy > 0) {
                            val n = curr - sw
                            if (binary[n] == 1 && !visited.get(n)) {
                                visited.set(n)
                                queue.add(n)
                            }
                        }
                        if (cy < sh - 1) {
                            val n = curr + sw
                            if (binary[n] == 1 && !visited.get(n)) {
                                visited.set(n)
                                queue.add(n)
                            }
                        }
                    }
                    if (comp.size > largestComponent.size) {
                        largestComponent = comp
                    }
                }
            }
        }

        // Default crop points: 8% inset (since we have padding in the UI, handles are easy to grab)
        val defaultTL = PointF(bitmap.width * 0.08f, bitmap.height * 0.08f)
        val defaultTR = PointF(bitmap.width * 0.92f, bitmap.height * 0.08f)
        val defaultBR = PointF(bitmap.width * 0.92f, bitmap.height * 0.92f)
        val defaultBL = PointF(bitmap.width * 0.08f, bitmap.height * 0.92f)
        val fallbackCorners = listOf(defaultTL, defaultTR, defaultBR, defaultBL)

        // If the largest component is too small (e.g. less than 10% of image), use default inset
        if (largestComponent.size < (sw * sh) * 0.10) {
            return fallbackCorners
        }

        // 5. Extract the 4 extreme corner coordinates of this component
        var minSum = Float.MAX_VALUE
        var maxSum = -Float.MAX_VALUE
        var minDiff = Float.MAX_VALUE
        var maxDiff = -Float.MAX_VALUE

        var tl = PointF(0f, 0f)
        var tr = PointF(0f, 0f)
        var br = PointF(0f, 0f)
        var bl = PointF(0f, 0f)

        for (idx in largestComponent) {
            val px = (idx % sw).toFloat()
            val py = (idx / sw).toFloat()

            val sum = px + py
            val diff = px - py

            if (sum < minSum) {
                minSum = sum
                tl = PointF(px, py)
            }
            if (sum > maxSum) {
                maxSum = sum
                br = PointF(px, py)
            }
            if (diff > maxDiff) {
                maxDiff = diff
                tr = PointF(px, py)
            }
            if (diff < minDiff) {
                minDiff = diff
                bl = PointF(px, py)
            }
        }

        // Ensure the corners are scaled back to the original bitmap size
        val detectedCorners = listOf(
            PointF(tl.x / scale, tl.y / scale),
            PointF(tr.x / scale, tr.y / scale),
            PointF(br.x / scale, br.y / scale),
            PointF(bl.x / scale, bl.y / scale)
        )

        // Validate the shape: corners must be reasonably distinct and form a valid quadrilateral
        val d1 = hypot((detectedCorners[0].x - detectedCorners[1].x).toDouble(), (detectedCorners[0].y - detectedCorners[1].y).toDouble())
        val d2 = hypot((detectedCorners[1].x - detectedCorners[2].x).toDouble(), (detectedCorners[1].y - detectedCorners[2].y).toDouble())
        if (d1 < bitmap.width * 0.15 || d2 < bitmap.height * 0.15) {
            return fallbackCorners
        }

        return detectedCorners
    }

    fun saveBitmap(bitmap: Bitmap, file: File, quality: Int = 98) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
    }

    // ── Filters ──

    fun applyFilter(src: Bitmap, filter: FilterType): Bitmap = when (filter) {
        FilterType.ORIGINAL -> src.copy(Bitmap.Config.ARGB_8888, false)
        FilterType.AUTO_ENHANCE -> autoEnhance(src)
        FilterType.MAGIC_COLOR -> magicColor(src)
        FilterType.HIGH_CONTRAST -> highContrast(src)
        FilterType.LIGHTEN -> withColorMatrix(src) {
            setScale(1.3f, 1.3f, 1.3f, 1f)
            val cm2 = contrastMatrix(1.15f)
            postConcat(cm2)
        }
        FilterType.SHARPEN -> sharpen(src)
        FilterType.VIVID -> vivid(src)
        FilterType.INVERT -> withColorMatrix(src) {
            set(floatArrayOf(
                -1f,0f,0f,0f,255f,
                0f,-1f,0f,0f,255f,
                0f,0f,-1f,0f,255f,
                0f,0f,0f,1f,0f,
            ))
        }
    }

    /**
     * Blend two bitmaps: result = src1 * (1 - alpha) + src2 * alpha
     */
    fun blend(src1: Bitmap, src2: Bitmap, alpha: Float): Bitmap {
        val w = src1.width; val h = src1.height
        val p1 = IntArray(w * h)
        val p2 = IntArray(w * h)
        src1.getPixels(p1, 0, w, 0, 0, w, h)
        src2.getPixels(p2, 0, w, 0, 0, w, h)
        val a1 = 1f - alpha
        for (i in p1.indices) {
            val r = (Color.red(p1[i]) * a1 + Color.red(p2[i]) * alpha).toInt().coerceIn(0, 255)
            val g = (Color.green(p1[i]) * a1 + Color.green(p2[i]) * alpha).toInt().coerceIn(0, 255)
            val b = (Color.blue(p1[i]) * a1 + Color.blue(p2[i]) * alpha).toInt().coerceIn(0, 255)
            p1[i] = Color.argb(255, r, g, b)
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(p1, 0, w, 0, 0, w, h)
        return result
    }

    // ── Adjustments ──

    fun applyAdjustments(src: Bitmap, adj: AdjustmentValues): Bitmap {
        if (adj == AdjustmentValues()) return src.copy(Bitmap.Config.ARGB_8888, false)
        val cm = ColorMatrix()
        // Brightness
        if (adj.brightness != 0f) {
            val v = adj.brightness * 2.55f
            val bm = ColorMatrix(floatArrayOf(
                1f,0f,0f,0f,v, 0f,1f,0f,0f,v, 0f,0f,1f,0f,v, 0f,0f,0f,1f,0f,
            ))
            cm.postConcat(bm)
        }
        // Contrast
        if (adj.contrast != 0f) {
            val scale = 1f + adj.contrast / 100f
            cm.postConcat(contrastMatrix(scale))
        }
        // Saturation
        if (adj.saturation != 0f) {
            val sm = ColorMatrix()
            sm.setSaturation(1f + adj.saturation / 100f)
            cm.postConcat(sm)
        }
        // Warmth
        if (adj.warmth != 0f) {
            val w = adj.warmth * 0.1f
            val wm = ColorMatrix(floatArrayOf(
                1f+w*0.02f,0f,0f,0f,w, 0f,1f,0f,0f,0f, 0f,0f,1f-w*0.02f,0f,-w, 0f,0f,0f,1f,0f,
            ))
            cm.postConcat(wm)
        }
        // Exposure
        if (adj.exposure != 0f) {
            val e = 1f + adj.exposure / 100f
            val em = ColorMatrix(floatArrayOf(
                e,0f,0f,0f,0f, 0f,e,0f,0f,0f, 0f,0f,e,0f,0f, 0f,0f,0f,1f,0f,
            ))
            cm.postConcat(em)
        }
        var result = withColorMatrix(src, cm)
        // Sharpness (needs pixel ops)
        if (adj.sharpness > 0f) {
            val amount = adj.sharpness / 100f
            result = sharpenWithAmount(result, amount)
        }
        return result
    }

    // ── Rotation ──

    fun rotate(src: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return src.copy(Bitmap.Config.ARGB_8888, false)
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    fun flipH(src: Bitmap): Bitmap {
        val m = Matrix().apply { preScale(-1f, 1f) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    fun flipV(src: Bitmap): Bitmap {
        val m = Matrix().apply { preScale(1f, -1f) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    // ── Perspective crop ──

    fun perspectiveCrop(src: Bitmap, corners: List<PointF>): Bitmap {
        // corners: TL, TR, BR, BL in bitmap coordinates
        val w = max(
            hypot((corners[1].x - corners[0].x).toDouble(), (corners[1].y - corners[0].y).toDouble()),
            hypot((corners[2].x - corners[3].x).toDouble(), (corners[2].y - corners[3].y).toDouble()),
        ).toInt().coerceAtLeast(1)
        val h = max(
            hypot((corners[3].x - corners[0].x).toDouble(), (corners[3].y - corners[0].y).toDouble()),
            hypot((corners[2].x - corners[1].x).toDouble(), (corners[2].y - corners[1].y).toDouble()),
        ).toInt().coerceAtLeast(1)

        val srcPts = floatArrayOf(
            corners[0].x, corners[0].y,
            corners[1].x, corners[1].y,
            corners[2].x, corners[2].y,
            corners[3].x, corners[3].y,
        )
        val dstPts = floatArrayOf(
            0f, 0f, w.toFloat(), 0f, w.toFloat(), h.toFloat(), 0f, h.toFloat(),
        )
        val matrix = Matrix()
        matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(src, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return output
    }

    // ── Internal helpers ──

    private fun withColorMatrix(src: Bitmap, block: ColorMatrix.() -> Unit): Bitmap {
        val cm = ColorMatrix().apply(block)
        return withColorMatrix(src, cm)
    }

    private fun withColorMatrix(src: Bitmap, cm: ColorMatrix): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
        return result
    }

    private fun contrastMatrix(scale: Float): ColorMatrix {
        val t = 128f * (1f - scale)
        return ColorMatrix(floatArrayOf(
            scale,0f,0f,0f,t, 0f,scale,0f,0f,t, 0f,0f,scale,0f,t, 0f,0f,0f,1f,0f,
        ))
    }

    /**
     * Document scan filter: CamScanner-style.
     * Whitens the background, darkens ink, boosts contrast.
     * Great for making handwritten/printed text legible.
     */
    private fun documentScan(src: Bitmap): Bitmap {
        try {
            val w = src.width; val h = src.height
            val pixels = IntArray(w * h)
            src.getPixels(pixels, 0, w, 0, 0, w, h)

            // Compute grayscale in-place
            for (i in pixels.indices) {
                val p = pixels[i]
                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)
                pixels[i] = ((0.299 * r + 0.587 * g + 0.114 * b).toInt()).coerceIn(0, 255)
            }

            // Local adaptive background estimation using block averaging
            val blockSize = max(w, h) / 48
            // Build integral image
            val integral = LongArray(w * h)
            for (y in 0 until h) {
                var rowSum = 0L
                for (x in 0 until w) {
                    rowSum += pixels[y * w + x]
                    integral[y * w + x] = rowSum + if (y > 0) integral[(y - 1) * w + x] else 0
                }
            }

            for (y in 0 until h) {
                for (x in 0 until w) {
                    val x0 = max(0, x - blockSize)
                    val y0 = max(0, y - blockSize)
                    val x1 = min(w - 1, x + blockSize)
                    val y1 = min(h - 1, y + blockSize)
                    val count = (x1 - x0 + 1) * (y1 - y0 + 1)
                    var sum = integral[y1 * w + x1]
                    if (x0 > 0) sum -= integral[y1 * w + x0 - 1]
                    if (y0 > 0) sum -= integral[(y0 - 1) * w + x1]
                    if (x0 > 0 && y0 > 0) sum += integral[(y0 - 1) * w + x0 - 1]
                    val localBg = (sum / count).toInt()

                    // Normalize: map pixel so that localBg → 255 (white)
                    val lum = pixels[y * w + x]
                    val ratio = if (localBg > 10) (lum.toFloat() / localBg) else (lum / 128f)
                    val newLum = (ratio * 255f).toInt().coerceIn(0, 255)

                    // Boost contrast of normalized value
                    val boosted = ((newLum - 128) * 1.6f + 128).toInt().coerceIn(0, 255)
                    pixels[y * w + x] = Color.argb(255, boosted, boosted, boosted)
                }
            }

            val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            result.setPixels(pixels, 0, w, 0, 0, w, h)
            // Light sharpen pass to make text crisp
            return sharpenWithAmount(result, 0.4f)
        } catch (e: OutOfMemoryError) {
            android.util.Log.e("ImageProcessor", "OOM in documentScan, returning original bitmap", e)
            return src
        }
    }

    /**
     * Whiteboard filter: cleans up whiteboard photos.
     * Removes uneven lighting and makes background pure white.
     */
    private fun whiteboard(src: Bitmap): Bitmap {
        val cm = ColorMatrix()
        cm.setSaturation(0.2f) // Nearly desaturate
        cm.postConcat(contrastMatrix(1.6f))
        // Brighten substantially
        val bm = ColorMatrix(floatArrayOf(
            1f,0f,0f,0f,50f,
            0f,1f,0f,0f,50f,
            0f,0f,1f,0f,50f,
            0f,0f,0f,1f,0f,
        ))
        cm.postConcat(bm)
        return withColorMatrix(src, cm)
    }

    /**
     * Vivid filter: saturated, vibrant colors with subtle warmth.
     */
    private fun vivid(src: Bitmap): Bitmap {
        val cm = ColorMatrix()
        cm.setSaturation(1.6f)
        cm.postConcat(contrastMatrix(1.2f))
        // Slight warmth
        val warm = ColorMatrix(floatArrayOf(
            1.02f,0f,0f,0f,5f,
            0f,1f,0f,0f,0f,
            0f,0f,0.98f,0f,-3f,
            0f,0f,0f,1f,0f,
        ))
        cm.postConcat(warm)
        return withColorMatrix(src, cm)
    }

    private fun autoEnhance(src: Bitmap): Bitmap {
        val pixels = IntArray(src.width * src.height)
        src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
        var minR = 255; var maxR = 0; var minG = 255; var maxG = 0; var minB = 255; var maxB = 0
        for (p in pixels) {
            val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p)
            if (r < minR) minR = r; if (r > maxR) maxR = r
            if (g < minG) minG = g; if (g > maxG) maxG = g
            if (b < minB) minB = b; if (b > maxB) maxB = b
        }
        val rangeR = (maxR - minR).coerceAtLeast(1).toFloat()
        val rangeG = (maxG - minG).coerceAtLeast(1).toFloat()
        val rangeB = (maxB - minB).coerceAtLeast(1).toFloat()
        val cm = ColorMatrix(floatArrayOf(
            255f/rangeR,0f,0f,0f,-minR*255f/rangeR,
            0f,255f/rangeG,0f,0f,-minG*255f/rangeG,
            0f,0f,255f/rangeB,0f,-minB*255f/rangeB,
            0f,0f,0f,1f,0f,
        ))
        cm.postConcat(contrastMatrix(1.1f))
        return withColorMatrix(src, cm)
    }

    private fun magicColor(src: Bitmap): Bitmap {
        try {
            val w = src.width; val h = src.height
            val pixels = IntArray(w * h)
            src.getPixels(pixels, 0, w, 0, 0, w, h)

            val scale = 4
            val sw = w / scale + 1
            val sh = h / scale + 1
            val integral = IntArray(sw * sh)

            for (y in 0 until h step scale) {
                var rowSum = 0
                for (x in 0 until w step scale) {
                    val p = pixels[y * w + x]
                    val r = android.graphics.Color.red(p)
                    val g = android.graphics.Color.green(p)
                    val b = android.graphics.Color.blue(p)
                    val grayVal = ((0.299f * r + 0.587f * g + 0.114f * b).toInt()).coerceIn(0, 255)
                    rowSum += grayVal
                    
                    val sy = y / scale
                    val sx = x / scale
                    integral[sy * sw + sx] = rowSum + if (sy > 0) integral[(sy - 1) * sw + sx] else 0
                }
            }

            val sBlockSize = max(sw, sh) / 12

            // Pre-compute local background into a lookup array
            val localBgArray = IntArray(sw * sh)
            for (sy in 0 until sh) {
                for (sx in 0 until sw) {
                    val sx0 = max(0, sx - sBlockSize)
                    val sy0 = max(0, sy - sBlockSize)
                    val sx1 = min(sw - 1, sx + sBlockSize)
                    val sy1 = min(sh - 1, sy + sBlockSize)
                    val count = (sx1 - sx0 + 1) * (sy1 - sy0 + 1)
                    var sum = integral[sy1 * sw + sx1]
                    if (sx0 > 0) sum -= integral[sy1 * sw + sx0 - 1]
                    if (sy0 > 0) sum -= integral[(sy0 - 1) * sw + sx1]
                    if (sx0 > 0 && sy0 > 0) sum += integral[(sy0 - 1) * sw + sx0 - 1]
                    localBgArray[sy * sw + sx] = (sum / count).coerceAtLeast(1)
                }
            }

            for (y in 0 until h) {
                for (x in 0 until w) {
                    val sx = x / scale
                    val sy = y / scale
                    val localBg = localBgArray[sy * sw + sx]

                    val idx = y * w + x
                    val p = pixels[idx]
                    val r = Color.red(p)
                    val g = Color.green(p)
                    val b = Color.blue(p)

                    var ratioR = r.toFloat() / localBg
                    var ratioG = g.toFloat() / localBg
                    var ratioB = b.toFloat() / localBg

                    ratioR = ratioR * ratioR * ratioR
                    ratioG = ratioG * ratioG * ratioG
                    ratioB = ratioB * ratioB * ratioB

                    val nr = ratioR * 255f
                    val ng = ratioG * 255f
                    val nb = ratioB * 255f

                    val lum = nr * 0.299f + ng * 0.587f + nb * 0.114f
                    val satFactor = 1.6f
                    var cr = lum + (nr - lum) * satFactor
                    var cg = lum + (ng - lum) * satFactor
                    var cb = lum + (nb - lum) * satFactor

                    cr = ((cr - 128f) * 1.5f + 128f)
                    cg = ((cg - 128f) * 1.5f + 128f)
                    cb = ((cb - 128f) * 1.5f + 128f)

                    val ri = cr.toInt().coerceIn(0, 255)
                    val gi = cg.toInt().coerceIn(0, 255)
                    val bi = cb.toInt().coerceIn(0, 255)
                    pixels[idx] = (255 shl 24) or (ri shl 16) or (gi shl 8) or bi
                }
            }

            val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            result.setPixels(pixels, 0, w, 0, 0, w, h)
            return sharpenWithAmount(result, 0.5f)
        } catch (e: OutOfMemoryError) {
            android.util.Log.e("ImageProcessor", "OOM in magicColor, returning original bitmap", e)
            return src
        }
    }

    private fun kindlePaperwhite(src: Bitmap): Bitmap {
        try {
            val w = src.width; val h = src.height
            val pixels = IntArray(w * h)
            src.getPixels(pixels, 0, w, 0, 0, w, h)

            val gray = IntArray(w * h)
            for (i in pixels.indices) {
                val r = Color.red(pixels[i])
                val g = Color.green(pixels[i])
                val b = Color.blue(pixels[i])
                gray[i] = ((0.299f * r + 0.587f * g + 0.114f * b).toInt()).coerceIn(0, 255)
            }

            val blockSize = max(w, h) / 48
            val integral = LongArray(w * h)
            for (y in 0 until h) {
                var rowSum = 0L
                for (x in 0 until w) {
                    rowSum += gray[y * w + x]
                    integral[y * w + x] = rowSum + if (y > 0) integral[(y - 1) * w + x] else 0
                }
            }

            val out = IntArray(w * h)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val x0 = max(0, x - blockSize)
                    val y0 = max(0, y - blockSize)
                    val x1 = min(w - 1, x + blockSize)
                    val y1 = min(h - 1, y + blockSize)
                    val count = (x1 - x0 + 1) * (y1 - y0 + 1)
                    
                    var sum = integral[y1 * w + x1]
                    if (x0 > 0) sum -= integral[y1 * w + x0 - 1]
                    if (y0 > 0) sum -= integral[(y0 - 1) * w + x1]
                    if (x0 > 0 && y0 > 0) sum += integral[(y0 - 1) * w + x0 - 1]
                    val localBg = (sum / count).toInt().coerceAtLeast(1)

                    val lum = gray[y * w + x]
                    var ratio = lum.toFloat() / localBg
                    
                    ratio = Math.pow(ratio.toDouble(), 4.0).toFloat()
                    
                    var v = (ratio * 255f).coerceIn(0f, 255f)
                    v = ((v - 128f) * 2.0f + 128f).coerceIn(0f, 255f)
                    
                    val fgColor = 30f
                    val bgColor = 235f
                    
                    val finalColor = fgColor + (v / 255f) * (bgColor - fgColor)
                    val c = finalColor.toInt().coerceIn(0, 255)
                    
                    out[y * w + x] = Color.argb(255, c, c, c)
                }
            }

            val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            result.setPixels(out, 0, w, 0, 0, w, h)
            return sharpenWithAmount(result, 0.4f)
        } catch (e: OutOfMemoryError) {
            android.util.Log.e("ImageProcessor", "OOM in kindlePaperwhite, returning original bitmap", e)
            return src
        }
    }

    private fun highContrast(src: Bitmap): Bitmap {
        val cm = ColorMatrix()
        cm.setSaturation(1.2f)
        cm.postConcat(contrastMatrix(1.8f))
        return withColorMatrix(src, cm)
    }

    private fun noShadow(src: Bitmap): Bitmap {
        // Brighten shadows: gamma correction approximation
        val cm = ColorMatrix()
        cm.setScale(1.2f, 1.2f, 1.2f, 1f)
        cm.postConcat(contrastMatrix(1.3f))
        val bm = ColorMatrix(floatArrayOf(1f,0f,0f,0f,30f, 0f,1f,0f,0f,30f, 0f,0f,1f,0f,30f, 0f,0f,0f,1f,0f))
        cm.postConcat(bm)
        return withColorMatrix(src, cm)
    }

    private fun threshold(src: Bitmap, thresh: Int): Bitmap {
        val gray = withColorMatrix(src) { setSaturation(0f) }
        val pixels = IntArray(gray.width * gray.height)
        gray.getPixels(pixels, 0, gray.width, 0, 0, gray.width, gray.height)
        for (i in pixels.indices) {
            val lum = Color.red(pixels[i])
            pixels[i] = if (lum > thresh) Color.WHITE else Color.BLACK
        }
        val result = Bitmap.createBitmap(gray.width, gray.height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, gray.width, 0, 0, gray.width, gray.height)
        return result
    }

    private fun sharpen(src: Bitmap): Bitmap = sharpenWithAmount(src, 0.7f)

    private fun sharpenWithAmount(src: Bitmap, amount: Float): Bitmap {
        val w = src.width; val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        val center = 1f + 4f * amount
        val side = -amount
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val c = pixels[idx]; val t = pixels[idx - w]; val b = pixels[idx + w]
                val l = pixels[idx - 1]; val r = pixels[idx + 1]
                val nr = (Color.red(c) * center + Color.red(t) * side + Color.red(b) * side + Color.red(l) * side + Color.red(r) * side).toInt().coerceIn(0, 255)
                val ng = (Color.green(c) * center + Color.green(t) * side + Color.green(b) * side + Color.green(l) * side + Color.green(r) * side).toInt().coerceIn(0, 255)
                val nb = (Color.blue(c) * center + Color.blue(t) * side + Color.blue(b) * side + Color.blue(l) * side + Color.blue(r) * side).toInt().coerceIn(0, 255)
                out[idx] = Color.argb(Color.alpha(c), nr, ng, nb)
            }
        }
        // Copy edges
        for (x in 0 until w) { out[x] = pixels[x]; out[(h - 1) * w + x] = pixels[(h - 1) * w + x] }
        for (y in 0 until h) { out[y * w] = pixels[y * w]; out[y * w + w - 1] = pixels[y * w + w - 1] }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }
}
