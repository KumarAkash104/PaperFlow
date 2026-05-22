package com.paperflow.app.ui.editor

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.hypot

@Composable
fun CropView(
    bitmap: Bitmap?,
    corners: List<PointF>,
    onCornersChanged: (List<PointF>) -> Unit,
) {
    if (bitmap == null || corners.size < 4) return

    var viewSize by remember { mutableStateOf(Size.Zero) }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    val handleRadius = 40f
    val imageBitmap = remember(bitmap) { bitmap!!.asImageBitmap() }

    // Use rememberUpdatedState so gesture handler always sees latest values
    // WITHOUT restarting the gesture detector (which causes sticking)
    val currentBitmap by rememberUpdatedState(bitmap)
    val currentCorners by rememberUpdatedState(corners)
    val currentOnCornersChanged by rememberUpdatedState(onCornersChanged)

    val imageRect = remember(bitmap?.width, bitmap?.height, viewSize) {
        if (viewSize == Size.Zero || bitmap == null) return@remember Rect.Zero
        val imageAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        val viewAspect = viewSize.width / viewSize.height
        val (drawW, drawH) = if (imageAspect > viewAspect) {
            viewSize.width to viewSize.width / imageAspect
        } else {
            viewSize.height * imageAspect to viewSize.height
        }
        val left = (viewSize.width - drawW) / 2f
        val top = (viewSize.height - drawH) / 2f
        Rect(left, top, left + drawW, top + drawH)
    }

    val currentImageRect by rememberUpdatedState(imageRect)

    fun bitmapToScreen(p: PointF): Offset {
        val ir = currentImageRect
        val bmp = currentBitmap
        if (ir == Rect.Zero || bmp == null) return Offset.Zero
        val scaleX = ir.width / bmp.width
        val scaleY = ir.height / bmp.height
        return Offset(ir.left + p.x * scaleX, ir.top + p.y * scaleY)
    }

    fun screenToBitmap(o: Offset): PointF {
        val ir = currentImageRect
        val bmp = currentBitmap
        if (ir == Rect.Zero || bmp == null) return PointF(0f, 0f)
        val scaleX = bmp.width / ir.width
        val scaleY = bmp.height / ir.height
        return PointF(
            ((o.x - ir.left) * scaleX).coerceIn(0f, bmp.width.toFloat()),
            ((o.y - ir.top) * scaleY).coerceIn(0f, bmp.height.toFloat()),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(bitmap, viewSize) { // Restart whenever bitmap or viewSize changes to prevent stale closures
                detectDragGestures(
                    onDragStart = { offset ->
                        val screenCorners = currentCorners.map { bitmapToScreen(it) }
                        draggingIndex = screenCorners
                            .mapIndexed { i, pt -> i to pt }
                            .filter {
                                hypot(
                                    (it.second.x - offset.x).toDouble(),
                                    (it.second.y - offset.y).toDouble(),
                                ) < handleRadius * 5.0 // Increased to 5.0 for extremely easy grabbing
                            }
                            .minByOrNull {
                                hypot(
                                    (it.second.x - offset.x).toDouble(),
                                    (it.second.y - offset.y).toDouble(),
                                )
                            }
                            ?.first ?: -1
                    },
                    onDrag = { change, _ ->
                        if (draggingIndex >= 0) {
                            change.consume()
                            val newPt = screenToBitmap(change.position)
                            val updated = currentCorners.toMutableList()
                            updated[draggingIndex] = newPt
                            currentOnCornersChanged(updated)
                        }
                    },
                    onDragEnd = { draggingIndex = -1 },
                    onDragCancel = { draggingIndex = -1 },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (imageRect == Rect.Zero) return@Canvas

            val dstOffset = IntOffset(imageRect.left.toInt(), imageRect.top.toInt())
            val dstSize = IntSize(imageRect.width.toInt(), imageRect.height.toInt())
            val screenCorners = corners.map { bitmapToScreen(it) }

            val cropPath = Path().apply {
                moveTo(screenCorners[0].x, screenCorners[0].y)
                lineTo(screenCorners[1].x, screenCorners[1].y)
                lineTo(screenCorners[2].x, screenCorners[2].y)
                lineTo(screenCorners[3].x, screenCorners[3].y)
                close()
            }

            // Dimmed full image
            drawImage(image = imageBitmap, dstOffset = dstOffset, dstSize = dstSize, alpha = 0.3f)

            // Bright image clipped to crop area
            clipPath(cropPath) {
                drawImage(image = imageBitmap, dstOffset = dstOffset, dstSize = dstSize)
            }

            // Crop border
            val lineColor = Color(0xFF22C7B8)
            val lineWidth = 2.5f
            drawLine(lineColor, screenCorners[0], screenCorners[1], lineWidth)
            drawLine(lineColor, screenCorners[1], screenCorners[2], lineWidth)
            drawLine(lineColor, screenCorners[2], screenCorners[3], lineWidth)
            drawLine(lineColor, screenCorners[3], screenCorners[0], lineWidth)

            // Rule-of-thirds grid
            val gridColor = Color.White.copy(alpha = 0.2f)
            for (i in 1..2) {
                val t = i / 3f
                drawLine(gridColor, lerp(screenCorners[0], screenCorners[3], t), lerp(screenCorners[1], screenCorners[2], t), 1f)
                drawLine(gridColor, lerp(screenCorners[0], screenCorners[1], t), lerp(screenCorners[3], screenCorners[2], t), 1f)
            }

            // Corner handles — larger touch targets
            screenCorners.forEachIndexed { index, pt ->
                val isActive = index == draggingIndex
                val r = if (isActive) handleRadius * 1.4f else handleRadius
                // Outer ring
                drawCircle(color = lineColor, radius = r, center = pt)
                drawCircle(color = Color.White, radius = r - 4f, center = pt)
                drawCircle(color = lineColor, radius = r - 8f, center = pt)
            }
        }
    }
}

private fun lerp(a: Offset, b: Offset, t: Float): Offset =
    Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
