package com.paperflow.app.util

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure-Kotlin document edge detector for camera preview.
 * Uses a simplified Hough-line approach for more stable detection than contour-based methods.
 *
 * Algorithm:
 * 1. Downscale to ~240px for speed
 * 2. Grayscale + Gaussian blur (5x5)
 * 3. Canny-style dual-threshold edge detection
 * 4. Hough line accumulator to find dominant lines
 * 5. Cluster lines into 4 groups (top, bottom, left, right)
 * 6. Intersect line pairs to find 4 corners
 */
object EdgeDetector {

    // Temporal smoothing: keep last N detections and average
    private var lastCorners: List<Offset>? = null
    private var smoothedCorners: List<Offset>? = null
    private var noDetectCount = 0
    private const val MAX_NO_DETECT = 5  // frames without detection before clearing

    /**
     * Detect document edges in a bitmap.
     * @param bitmap Source camera frame
     * @param previewWidth Width of the preview display (for scaling output)
     * @param previewHeight Height of the preview display (for scaling output)
     * @return List of 4 corner Offsets in preview coordinates, or null if no document found
     */
    fun detectEdges(
        bitmap: Bitmap,
        previewWidth: Int,
        previewHeight: Int,
    ): List<Offset>? {
        // Step 1: Downscale for performance
        val targetSize = 240
        val scale = targetSize.toFloat() / max(bitmap.width, bitmap.height)
        val w = (bitmap.width * scale).toInt().coerceAtLeast(2)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(2)
        val small = Bitmap.createScaledBitmap(bitmap, w, h, true)

        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        if (small !== bitmap) small.recycle()

        // Step 2: Grayscale
        val gray = IntArray(w * h)
        for (i in pixels.indices) {
            val r = (pixels[i] shr 16) and 0xFF
            val g = (pixels[i] shr 8) and 0xFF
            val b = pixels[i] and 0xFF
            gray[i] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }

        // Step 3: Gaussian blur 5x5
        val blurred = gaussianBlur5x5(gray, w, h)

        // Step 4: Sobel edge detection + non-maximum suppression
        val edgeMag = IntArray(w * h)
        val edgeDir = IntArray(w * h) // direction: 0=horiz, 1=diag45, 2=vert, 3=diag135
        var maxMag = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val gx = -blurred[(y - 1) * w + (x - 1)] - 2 * blurred[y * w + (x - 1)] - blurred[(y + 1) * w + (x - 1)] +
                        blurred[(y - 1) * w + (x + 1)] + 2 * blurred[y * w + (x + 1)] + blurred[(y + 1) * w + (x + 1)]
                val gy = -blurred[(y - 1) * w + (x - 1)] - 2 * blurred[(y - 1) * w + x] - blurred[(y - 1) * w + (x + 1)] +
                        blurred[(y + 1) * w + (x - 1)] + 2 * blurred[(y + 1) * w + x] + blurred[(y + 1) * w + (x + 1)]
                val mag = abs(gx) + abs(gy)
                edgeMag[y * w + x] = mag
                if (mag > maxMag) maxMag = mag

                // Quantize direction to 4 buckets
                val angle = kotlin.math.atan2(gy.toDouble(), gx.toDouble())
                val deg = Math.toDegrees(angle).let { if (it < 0) it + 180 else it }
                edgeDir[y * w + x] = when {
                    deg < 22.5 || deg >= 157.5 -> 0  // horizontal
                    deg < 67.5 -> 1                    // 45 degrees
                    deg < 112.5 -> 2                   // vertical
                    else -> 3                           // 135 degrees
                }
            }
        }

        if (maxMag == 0) {
            return handleNoDetection()
        }

        // Canny dual-threshold
        val highThresh = (maxMag * 0.25).toInt()
        val lowThresh = (highThresh * 0.4).toInt()

        // Non-maximum suppression + hysteresis
        val edges = BooleanArray(w * h)
        for (y in 2 until h - 2) {
            for (x in 2 until w - 2) {
                val mag = edgeMag[y * w + x]
                if (mag < lowThresh) continue

                // Non-maximum suppression
                val dir = edgeDir[y * w + x]
                val (n1, n2) = when (dir) {
                    0 -> (edgeMag[y * w + (x - 1)]) to (edgeMag[y * w + (x + 1)])
                    1 -> (edgeMag[(y - 1) * w + (x + 1)]) to (edgeMag[(y + 1) * w + (x - 1)])
                    2 -> (edgeMag[(y - 1) * w + x]) to (edgeMag[(y + 1) * w + x])
                    else -> (edgeMag[(y - 1) * w + (x - 1)]) to (edgeMag[(y + 1) * w + (x + 1)])
                }
                if (mag >= n1 && mag >= n2 && mag >= highThresh) {
                    edges[y * w + x] = true
                }
            }
        }

        // Step 5: Hough line detection
        val lines = houghLines(edges, w, h, threshold = 30)
        if (lines.size < 4) {
            return handleNoDetection()
        }

        // Step 6: Cluster lines into 4 groups (top, bottom, left, right)
        val quad = findQuadrilateral(lines, w, h) ?: return handleNoDetection()

        // Validate quad area (10-95% of frame)
        val quadArea = polygonArea(quad)
        val frameArea = (w * h).toFloat()
        if (quadArea < frameArea * 0.08f || quadArea > frameArea * 0.96f) {
            return handleNoDetection()
        }

        // Scale to preview coordinates
        val scaleX = previewWidth.toFloat() / w
        val scaleY = previewHeight.toFloat() / h
        val result = quad.map { (px, py) ->
            Offset(px * scaleX, py * scaleY)
        }

        // Temporal smoothing
        return smoothResult(result)
    }

    // ── Gaussian Blur 5x5 ──
    private fun gaussianBlur5x5(src: IntArray, w: Int, h: Int): IntArray {
        val kernel = intArrayOf(1, 4, 6, 4, 1)
        val kernelSum = 256 // 16 * 16
        val temp = IntArray(w * h)
        val dst = IntArray(w * h)

        // Horizontal pass
        for (y in 0 until h) {
            for (x in 2 until w - 2) {
                var sum = 0
                for (k in -2..2) {
                    sum += src[y * w + (x + k)] * kernel[k + 2]
                }
                temp[y * w + x] = sum / 16
            }
        }

        // Vertical pass
        for (y in 2 until h - 2) {
            for (x in 2 until w - 2) {
                var sum = 0
                for (k in -2..2) {
                    sum += temp[(y + k) * w + x] * kernel[k + 2]
                }
                dst[y * w + x] = sum / 16
            }
        }
        return dst
    }

    // ── Hough Line Detection ──
    data class HoughLine(val rho: Float, val theta: Float)

    private fun houghLines(edges: BooleanArray, w: Int, h: Int, threshold: Int): List<HoughLine> {
        val maxRho = sqrt((w * w + h * h).toDouble()).toInt() + 1
        val thetaSteps = 180
        val accumulator = IntArray(2 * maxRho * thetaSteps)

        // Pre-compute sin/cos
        val cosTable = FloatArray(thetaSteps)
        val sinTable = FloatArray(thetaSteps)
        for (t in 0 until thetaSteps) {
            val theta = Math.toRadians(t.toDouble())
            cosTable[t] = cos(theta).toFloat()
            sinTable[t] = sin(theta).toFloat()
        }

        // Accumulate votes
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (!edges[y * w + x]) continue
                for (t in 0 until thetaSteps) {
                    val rho = (x * cosTable[t] + y * sinTable[t]).roundToInt()
                    val rhoIdx = rho + maxRho
                    if (rhoIdx in 0 until 2 * maxRho) {
                        accumulator[rhoIdx * thetaSteps + t]++
                    }
                }
            }
        }

        // Find peaks above threshold
        val lines = mutableListOf<HoughLine>()
        for (rhoIdx in 0 until 2 * maxRho) {
            for (t in 0 until thetaSteps) {
                if (accumulator[rhoIdx * thetaSteps + t] >= threshold) {
                    val rho = (rhoIdx - maxRho).toFloat()
                    val theta = Math.toRadians(t.toDouble()).toFloat()
                    lines.add(HoughLine(rho, theta))
                }
            }
        }

        // Non-maximum suppression on Hough space: merge nearby lines
        return mergeLines(lines)
    }

    private fun mergeLines(lines: List<HoughLine>): List<HoughLine> {
        if (lines.isEmpty()) return lines
        val merged = mutableListOf<HoughLine>()
        val used = BooleanArray(lines.size)

        for (i in lines.indices) {
            if (used[i]) continue
            var sumRho = lines[i].rho.toDouble()
            var sumTheta = lines[i].theta.toDouble()
            var count = 1

            for (j in i + 1 until lines.size) {
                if (used[j]) continue
                val dRho = abs(lines[i].rho - lines[j].rho)
                val dTheta = abs(lines[i].theta - lines[j].theta)
                if (dRho < 15f && dTheta < Math.toRadians(10.0)) {
                    sumRho += lines[j].rho
                    sumTheta += lines[j].theta
                    count++
                    used[j] = true
                }
            }
            merged.add(HoughLine((sumRho / count).toFloat(), (sumTheta / count).toFloat()))
            used[i] = true
        }
        return merged
    }

    // ── Find Quadrilateral from Hough Lines ──
    private fun findQuadrilateral(
        lines: List<HoughLine>,
        w: Int,
        h: Int,
    ): List<Pair<Float, Float>>? {
        // Separate into roughly horizontal and roughly vertical lines
        val horizontals = mutableListOf<HoughLine>()
        val verticals = mutableListOf<HoughLine>()

        for (line in lines) {
            val thetaDeg = Math.toDegrees(line.theta.toDouble())
            when {
                thetaDeg < 30 || thetaDeg > 150 -> verticals.add(line)   // near-vertical
                thetaDeg in 60.0..120.0 -> horizontals.add(line)          // near-horizontal
            }
        }

        if (horizontals.size < 2 || verticals.size < 2) return null

        // Sort horizontals by rho (top to bottom for theta ~90°)
        horizontals.sortBy { it.rho }
        // Sort verticals by rho (left to right)
        verticals.sortBy { it.rho }

        // Take the outermost two of each
        val topLine = horizontals.first()
        val bottomLine = horizontals.last()
        val leftLine = verticals.first()
        val rightLine = verticals.last()

        // Check that the pairs are sufficiently separated
        val hSep = abs(bottomLine.rho - topLine.rho)
        val vSep = abs(rightLine.rho - leftLine.rho)
        if (hSep < h * 0.15f || vSep < w * 0.15f) return null

        // Find 4 intersections
        val tl = intersect(topLine, leftLine) ?: return null
        val tr = intersect(topLine, rightLine) ?: return null
        val br = intersect(bottomLine, rightLine) ?: return null
        val bl = intersect(bottomLine, leftLine) ?: return null

        // Validate all corners are within reasonable bounds
        val margin = -20f
        val maxX = w.toFloat() + 20f
        val maxY = h.toFloat() + 20f
        for ((x, y) in listOf(tl, tr, br, bl)) {
            if (x < margin || x > maxX || y < margin || y > maxY) return null
        }

        return listOf(
            tl.first.coerceIn(0f, w.toFloat()) to tl.second.coerceIn(0f, h.toFloat()),
            tr.first.coerceIn(0f, w.toFloat()) to tr.second.coerceIn(0f, h.toFloat()),
            br.first.coerceIn(0f, w.toFloat()) to br.second.coerceIn(0f, h.toFloat()),
            bl.first.coerceIn(0f, w.toFloat()) to bl.second.coerceIn(0f, h.toFloat()),
        )
    }

    private fun intersect(l1: HoughLine, l2: HoughLine): Pair<Float, Float>? {
        val ct1 = cos(l1.theta.toDouble())
        val st1 = sin(l1.theta.toDouble())
        val ct2 = cos(l2.theta.toDouble())
        val st2 = sin(l2.theta.toDouble())
        val det = ct1 * st2 - ct2 * st1
        if (abs(det) < 1e-6) return null  // parallel lines
        val x = ((l1.rho * st2 - l2.rho * st1) / det).toFloat()
        val y = ((l2.rho * ct1 - l1.rho * ct2) / det).toFloat()
        return x to y
    }

    // ── Temporal Smoothing ──
    private fun smoothResult(newCorners: List<Offset>): List<Offset> {
        noDetectCount = 0
        val prev = smoothedCorners
        return if (prev != null && prev.size == 4) {
            // Exponential moving average for smooth transitions
            val alpha = 0.4f
            val smoothed = newCorners.mapIndexed { i, new ->
                Offset(
                    prev[i].x + alpha * (new.x - prev[i].x),
                    prev[i].y + alpha * (new.y - prev[i].y),
                )
            }
            smoothedCorners = smoothed
            smoothed
        } else {
            smoothedCorners = newCorners
            newCorners
        }
    }

    private fun handleNoDetection(): List<Offset>? {
        noDetectCount++
        return if (noDetectCount > MAX_NO_DETECT) {
            smoothedCorners = null
            lastCorners = null
            null
        } else {
            smoothedCorners  // keep showing last good detection briefly
        }
    }

    private fun polygonArea(corners: List<Pair<Float, Float>>): Float {
        var area = 0f
        val n = corners.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += corners[i].first * corners[j].second
            area -= corners[j].first * corners[i].second
        }
        return abs(area) / 2f
    }
}
