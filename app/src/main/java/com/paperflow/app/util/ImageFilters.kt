package com.paperflow.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import java.util.Random

object ImageFilters {

    enum class Filter(val label: String, val category: String) {
        ORIGINAL("Original", "Standard"),
        
        // DSLR & Bokeh
        DSLR_PORTRAIT("Portrait Blur", "DSLR"),
        TILT_SHIFT("Tilt-Shift", "DSLR"),
        
        // Film & Analog
        KODAK_GOLD("Kodak Gold", "Film"),
        FUJI_VELVIA("Fuji Velvia", "Film"),
        POLAROID("Polaroid", "Film"),
        BW_FILM("B&W Film", "Film"),
        
        // Pro Enhance
        HDR_EFFECT("HDR Effect", "Pro"),
        CLARITY_BOOST("Clarity Boost", "Pro"),
        DEHAZE("Dehaze", "Pro"),
        
        // Color Grade
        TEAL_ORANGE("Teal & Orange", "Cinematic"),
        MOODY_MATTE("Moody Matte", "Cinematic"),
        GOLDEN_HOUR("Golden Hour", "Cinematic"),
        
        // Artistic
        VIGNETTE("Vignette", "Artistic")
    }

    fun applyFilter(src: Bitmap, filter: Filter): Bitmap {
        return when (filter) {
            Filter.ORIGINAL -> src.copy(src.config ?: Bitmap.Config.ARGB_8888, true)
            Filter.DSLR_PORTRAIT -> applyDslrBlur(src, isRadial = true)
            Filter.TILT_SHIFT -> applyDslrBlur(src, isRadial = false)
            Filter.KODAK_GOLD -> applyKodakGold(src)
            Filter.FUJI_VELVIA -> applyFujiVelvia(src)
            Filter.POLAROID -> applyPolaroid(src)
            Filter.BW_FILM -> applyBwFilm(src)
            Filter.HDR_EFFECT -> applyHdr(src)
            Filter.CLARITY_BOOST -> applyClarity(src)
            Filter.DEHAZE -> applyDehaze(src)
            Filter.TEAL_ORANGE -> applyTealOrange(src)
            Filter.MOODY_MATTE -> applyMoodyMatte(src)
            Filter.GOLDEN_HOUR -> applyGoldenHour(src)
            Filter.VIGNETTE -> applyVignetteEffect(src)
        }
    }

    // Fast Box Blur implementation in Kotlin (avoids Renderscript deprecation)
    private fun fastBoxBlur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        val pix = IntArray(w * h)
        src.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int

        val vmin = IntArray(maxOf(w, h))
        val dv = IntArray(256 * div)
        for (i in 0 until 256 * div) {
            dv[i] = i / div
        }

        yi = 0

        y = 0
        while (y < h) {
            rsum = 0
            gsum = 0
            bsum = 0
            for (i in -radius..radius) {
                p = pix[yi + minOf(wm, maxOf(i, 0))]
                rsum += p and 0xff0000 checkAndShift 16
                gsum += p and 0x00ff00 checkAndShift 8
                bsum += p and 0x0000ff
            }
            x = 0
            while (x < w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]

                if (y == 0) {
                    vmin[x] = minOf(x + radius + 1, wm)
                }
                val p1 = pix[yi - x + vmin[x]]
                val p2 = pix[yi - x + maxOf(x - radius, 0)]

                rsum += (p1 and 0xff0000 checkAndShift 16) - (p2 and 0xff0000 checkAndShift 16)
                gsum += (p1 and 0x00ff00 checkAndShift 8) - (p2 and 0x00ff00 checkAndShift 8)
                bsum += (p1 and 0x0000ff) - (p2 and 0x0000ff)
                yi++
                x++
            }
            y++
        }

        x = 0
        while (x < w) {
            rsum = 0
            gsum = 0
            bsum = 0
            yp = -radius * w
            for (i in -radius..radius) {
                yi = maxOf(0, yp) + x
                rsum += r[yi]
                gsum += g[yi]
                bsum += b[yi]
                yp += w
            }
            yi = x
            y = 0
            while (y < h) {
                pix[yi] = (-0x1000000 or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum])
                if (x == 0) {
                    vmin[y] = minOf(y + radius + 1, hm) * w
                }
                val p1 = x + vmin[y]
                val p2 = x + maxOf(0, y - radius) * w

                rsum += r[p1] - r[p2]
                gsum += g[p1] - g[p2]
                bsum += b[p1] - b[p2]

                yi += w
                y++
            }
            x++
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pix, 0, w, 0, 0, w, h)
        return out
    }

    private infix fun Int.checkAndShift(shift: Int): Int {
        return (this ushr shift) and 0xFF
    }

    // DSLR Depth / Tilt shift blur
    private fun applyDslrBlur(src: Bitmap, isRadial: Boolean): Bitmap {
        val blurred = fastBoxBlur(src, 16)
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        
        canvas.drawBitmap(blurred, 0f, 0f, null)

        val mask = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(mask)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (isRadial) {
            // Radial mask for Portrait
            val cx = src.width / 2f
            val cy = src.height * 0.4f
            val radius = minOf(src.width, src.height) * 0.35f
            val gradient = RadialGradient(
                cx, cy, radius * 1.5f,
                intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT),
                floatArrayOf(0f, 0.4f, 1f),
                Shader.TileMode.CLAMP
            )
            paint.shader = gradient
        } else {
            // Linear mask for Tilt Shift
            val gradient = LinearGradient(
                0f, 0f, 0f, src.height.toFloat(),
                intArrayOf(Color.TRANSPARENT, Color.BLACK, Color.BLACK, Color.TRANSPARENT),
                floatArrayOf(0f, 0.35f, 0.65f, 1f),
                Shader.TileMode.CLAMP
            )
            paint.shader = gradient
        }

        maskCanvas.drawRect(0f, 0f, src.width.toFloat(), src.height.toFloat(), paint)

        // Draw original bitmap through the mask onto the blurred bitmap
        val blendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        
        val temp = src.copy(Bitmap.Config.ARGB_8888, true)
        val tempCanvas = Canvas(temp)
        tempCanvas.drawBitmap(mask, 0f, 0f, blendPaint)
        
        canvas.drawBitmap(temp, 0f, 0f, null)
        
        // Clean up
        mask.recycle()
        temp.recycle()
        blurred.recycle()
        
        return result
    }

    private fun applyColorFilter(src: Bitmap, matrix: ColorMatrix): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }

    private fun applyKodakGold(src: Bitmap): Bitmap {
        val matrix = ColorMatrix().apply {
            set(floatArrayOf(
                1.12f, 0.05f, -0.02f, 0f, 10f, // warm reds
                0.02f, 1.05f, -0.05f, 0f, 5f,  // soft greens
                -0.05f, -0.05f, 0.90f, 0f, -10f, // yellow highlights
                0f, 0f, 0f, 1f, 0f
            ))
        }
        val colored = applyColorFilter(src, matrix)
        return addGrain(colored, 12)
    }

    private fun applyFujiVelvia(src: Bitmap): Bitmap {
        val matrix = ColorMatrix().apply {
            set(floatArrayOf(
                1.15f, 0f, 0f, 0f, -5f,
                0f, 1.22f, 0f, 0f, -5f, // strong greens
                0f, 0f, 1.25f, 0f, 5f,  // deep blues
                0f, 0f, 0f, 1f, 0f
            ))
        }
        return applyColorFilter(src, matrix)
    }

    private fun applyPolaroid(src: Bitmap): Bitmap {
        val matrix = ColorMatrix().apply {
            set(floatArrayOf(
                0.9f, 0.05f, 0.05f, 0f, 15f,
                0.05f, 0.85f, 0.05f, 0f, 10f,
                0.02f, 0.02f, 0.75f, 0f, 25f, // low blue contrast, faded yellow shadows
                0f, 0f, 0f, 1f, 0f
            ))
        }
        val colored = applyColorFilter(src, matrix)
        val result = applyVignette(colored, 110)
        colored.recycle()
        return result
    }

    private fun applyBwFilm(src: Bitmap): Bitmap {
        val matrix = ColorMatrix().apply {
            setSaturation(0f)
        }
        // Increase contrast
        val contrastMatrix = ColorMatrix().apply {
            setScale(1.2f, 1.2f, 1.2f, 1f)
        }
        matrix.postConcat(contrastMatrix)
        
        val colored = applyColorFilter(src, matrix)
        return addGrain(colored, 18)
    }

    private fun applyHdr(src: Bitmap): Bitmap {
        // High Dynamic Range simulation:
        // Boost shadows (exposure + contrast boost in lower ranges) and dim highlights
        val matrix = ColorMatrix().apply {
            set(floatArrayOf(
                1.05f, 0f, 0f, 0f, 15f,
                0f, 1.05f, 0f, 0f, 15f,
                0f, 0f, 1.05f, 0f, 15f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        val base = applyColorFilter(src, matrix)
        
        // Simulating high-pass overlay for local contrast
        val sharpened = applySharpen(base)
        base.recycle()
        return sharpened
    }

    private fun applyClarity(src: Bitmap): Bitmap {
        // Boost midtone contrast
        val matrix = ColorMatrix().apply {
            set(floatArrayOf(
                1.15f, -0.08f, -0.08f, 0f, 5f,
                -0.08f, 1.15f, -0.08f, 0f, 5f,
                -0.08f, -0.08f, 1.15f, 0f, 5f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        return applyColorFilter(src, matrix)
    }

    private fun applyDehaze(src: Bitmap): Bitmap {
        // Dehaze: restore blacks and increase local contrast and saturation
        val matrix = ColorMatrix().apply {
            set(floatArrayOf(
                1.2f, 0f, 0f, 0f, -20f,
                0f, 1.2f, 0f, 0f, -20f,
                0f, 0f, 1.2f, 0f, -20f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        return applyColorFilter(src, matrix)
    }

    private fun applyTealOrange(src: Bitmap): Bitmap {
        // Teal shadows (cyan/blue) and orange highlights
        val matrix = ColorMatrix().apply {
            set(floatArrayOf(
                1.15f, 0.02f, -0.05f, 0f, 10f,  // orange highlights
                0.02f, 0.98f, 0.05f, 0f, -5f,
                -0.10f, 0.05f, 1.20f, 0f, 5f,   // teal shadows
                0f, 0f, 0f, 1f, 0f
            ))
        }
        return applyColorFilter(src, matrix)
    }

    private fun applyMoodyMatte(src: Bitmap): Bitmap {
        // Crushed blacks, warm muted shadows
        val matrix = ColorMatrix().apply {
            set(floatArrayOf(
                0.90f, 0.05f, 0.05f, 0f, 20f,
                0.05f, 0.90f, 0.05f, 0f, 15f,
                0.05f, 0.05f, 0.85f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        val colored = applyColorFilter(src, matrix)
        val matte = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(matte)
        canvas.drawBitmap(colored, 0f, 0f, null)
        
        // Slightly raise bottom shadows (matte look)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(12, 10, 10, 15) // dark grey blue tint
        }
        canvas.drawRect(0f, 0f, src.width.toFloat(), src.height.toFloat(), paint)
        colored.recycle()
        return matte
    }

    private fun applyGoldenHour(src: Bitmap): Bitmap {
        // Warm rich sunlight, slight golden bloom
        val matrix = ColorMatrix().apply {
            set(floatArrayOf(
                1.25f, 0.05f, -0.05f, 0f, 15f,
                0.05f, 1.10f, -0.05f, 0f, 5f,
                -0.10f, -0.10f, 0.85f, 0f, -20f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        return applyColorFilter(src, matrix)
    }

    private fun applyVignetteEffect(src: Bitmap): Bitmap {
        return applyVignette(src, 120)
    }

    private fun applyVignette(src: Bitmap, intensity: Int): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(src, 0f, 0f, null)

        val radius = kotlin.math.hypot(src.width / 2f, src.height / 2f)
        val gradient = RadialGradient(
            src.width / 2f, src.height / 2f, radius,
            intArrayOf(Color.TRANSPARENT, Color.BLACK),
            floatArrayOf(0.4f, 1f),
            Shader.TileMode.CLAMP
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = gradient
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
            alpha = intensity
        }

        canvas.drawRect(0f, 0f, src.width.toFloat(), src.height.toFloat(), paint)
        return result
    }

    private fun addGrain(src: Bitmap, amount: Int): Bitmap {
        val result = src.copy(src.config ?: Bitmap.Config.ARGB_8888, true)
        val w = result.width
        val h = result.height
        val pixels = IntArray(w * h)
        result.getPixels(pixels, 0, w, 0, 0, w, h)

        val rand = Random()
        for (i in pixels.indices) {
            val color = pixels[i]
            val r = (color shr 16) and 0xff
            val g = (color shr 8) and 0xff
            val b = color and 0xff

            // Add pseudo-random noise
            val noise = rand.nextInt(amount * 2 + 1) - amount
            val nr = minOf(255, maxOf(0, r + noise))
            val ng = minOf(255, maxOf(0, g + noise))
            val nb = minOf(255, maxOf(0, b + noise))

            pixels[i] = (color and -0x1000000) or (nr shl 16) or (ng shl 8) or nb
        }
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun applySharpen(src: Bitmap): Bitmap {
        // Custom simple high-pass/sharpening kernel
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(src, 0f, 0f, null)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }

        // Just copy over for now (actual kernel manipulation is slow in pure Kotlin for high-res images;
        // colorMatrix contrast boost behaves similarly)
        val matrix = ColorMatrix().apply {
            set(floatArrayOf(
                1.1f, -0.05f, -0.05f, 0f, 0f,
                -0.05f, 1.1f, -0.05f, 0f, 0f,
                -0.05f, -0.05f, 1.1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        canvas.drawBitmap(src, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        })

        return result
    }
}
