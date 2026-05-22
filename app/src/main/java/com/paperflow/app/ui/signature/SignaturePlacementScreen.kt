package com.paperflow.app.ui.signature

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignaturePlacementScreen(
    pageBitmap: Bitmap,
    signatureBitmap: Bitmap,
    onApply: (Bitmap) -> Unit,
    onBack: () -> Unit,
) {
    var sigOffsetX by remember { mutableFloatStateOf(100f) }
    var sigOffsetY by remember { mutableFloatStateOf(400f) }
    var sigScale by remember { mutableFloatStateOf(0.4f) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    val pageImage = remember(pageBitmap) { pageBitmap.asImageBitmap() }
    val sigImage = remember(signatureBitmap) { signatureBitmap.asImageBitmap() }

    val sigDisplayW = (signatureBitmap.width * sigScale).roundToInt()
    val sigDisplayH = (signatureBitmap.height * sigScale).roundToInt()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Place Signature", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = {
                            val result = flattenSignature(
                                pageBitmap, signatureBitmap,
                                sigOffsetX, sigOffsetY, sigScale,
                                viewSize.width, viewSize.height,
                            )
                            onApply(result)
                        },
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Apply")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                "Drag to move • Pinch to resize",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clipToBounds()
                    .onSizeChanged { viewSize = it },
            ) {
                // Page image
                Image(
                    bitmap = pageImage,
                    contentDescription = "Page",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )

                // Draggable + resizable signature overlay
                val density = LocalDensity.current
                Box(
                    modifier = Modifier
                        .offset { IntOffset(sigOffsetX.roundToInt(), sigOffsetY.roundToInt()) }
                        .size(
                            width = with(density) { sigDisplayW.toDp() },
                            height = with(density) { sigDisplayH.toDp() },
                        )
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                sigOffsetX += pan.x
                                sigOffsetY += pan.y
                                sigScale = (sigScale * zoom).coerceIn(0.05f, 3f)
                            }
                        }
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            RoundedCornerShape(4.dp),
                        ),
                ) {
                    Image(
                        bitmap = sigImage,
                        contentDescription = "Signature",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )

                    // ── Corner resize handles ──
                    val handleSize = 14.dp
                    val handleColor = MaterialTheme.colorScheme.primary

                    // Top-left
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = (-handleSize / 2), y = (-handleSize / 2))
                            .size(handleSize)
                            .clip(CircleShape)
                            .background(handleColor)
                            .border(1.5.dp, Color.White, CircleShape)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val currentW = signatureBitmap.width * sigScale
                                    val currentH = signatureBitmap.height * sigScale
                                    val currentDist = kotlin.math.hypot(currentW, currentH)
                                    val newDist = kotlin.math.hypot(-currentW + dragAmount.x, -currentH + dragAmount.y)
                                    val newScale = (sigScale * (newDist / currentDist)).coerceIn(0.05f, 3f)
                                    
                                    val anchorX = sigOffsetX + currentW
                                    val anchorY = sigOffsetY + currentH
                                    sigScale = newScale
                                    val newW = signatureBitmap.width * newScale
                                    val newH = signatureBitmap.height * newScale
                                    sigOffsetX = anchorX - newW
                                    sigOffsetY = anchorY - newH
                                }
                            },
                    )
                    // Top-right
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = handleSize / 2, y = (-handleSize / 2))
                            .size(handleSize)
                            .clip(CircleShape)
                            .background(handleColor)
                            .border(1.5.dp, Color.White, CircleShape)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val currentW = signatureBitmap.width * sigScale
                                    val currentH = signatureBitmap.height * sigScale
                                    val currentDist = kotlin.math.hypot(currentW, currentH)
                                    val newDist = kotlin.math.hypot(currentW + dragAmount.x, -currentH + dragAmount.y)
                                    val newScale = (sigScale * (newDist / currentDist)).coerceIn(0.05f, 3f)
                                    
                                    val anchorY = sigOffsetY + currentH
                                    sigScale = newScale
                                    val newH = signatureBitmap.height * newScale
                                    sigOffsetY = anchorY - newH
                                }
                            },
                    )
                    // Bottom-left
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = (-handleSize / 2), y = handleSize / 2)
                            .size(handleSize)
                            .clip(CircleShape)
                            .background(handleColor)
                            .border(1.5.dp, Color.White, CircleShape)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val currentW = signatureBitmap.width * sigScale
                                    val currentH = signatureBitmap.height * sigScale
                                    val currentDist = kotlin.math.hypot(currentW, currentH)
                                    val newDist = kotlin.math.hypot(-currentW + dragAmount.x, currentH + dragAmount.y)
                                    val newScale = (sigScale * (newDist / currentDist)).coerceIn(0.05f, 3f)
                                    
                                    val anchorX = sigOffsetX + currentW
                                    sigScale = newScale
                                    val newW = signatureBitmap.width * newScale
                                    sigOffsetX = anchorX - newW
                                }
                            },
                    )
                    // Bottom-right
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = handleSize / 2, y = handleSize / 2)
                            .size(handleSize)
                            .clip(CircleShape)
                            .background(handleColor)
                            .border(1.5.dp, Color.White, CircleShape)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val currentW = signatureBitmap.width * sigScale
                                    val currentH = signatureBitmap.height * sigScale
                                    val currentDist = kotlin.math.hypot(currentW, currentH)
                                    val newDist = kotlin.math.hypot(currentW + dragAmount.x, currentH + dragAmount.y)
                                    sigScale = (sigScale * (newDist / currentDist)).coerceIn(0.05f, 3f)
                                }
                            },
                    )

                    // ── Size indicator ──
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 24.dp)
                            .background(
                                MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.7f),
                                RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "${sigDisplayW}×${sigDisplayH}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                        )
                    }
                }
            }
        }
    }
}

private fun flattenSignature(
    page: Bitmap,
    signature: Bitmap,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    viewWidth: Int,
    viewHeight: Int,
): Bitmap {
    val result = page.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(result)

    // Map view coordinates to bitmap coordinates
    val pageAspect = page.width.toFloat() / page.height
    val viewAspect = viewWidth.toFloat() / viewHeight
    val (drawW, drawH) = if (pageAspect > viewAspect) {
        viewWidth.toFloat() to (viewWidth / pageAspect)
    } else {
        (viewHeight * pageAspect) to viewHeight.toFloat()
    }
    val padX = (viewWidth - drawW) / 2f
    val padY = (viewHeight - drawH) / 2f

    val scaleX = page.width / drawW
    val scaleY = page.height / drawH

    val bmpX = (offsetX - padX) * scaleX
    val bmpY = (offsetY - padY) * scaleY
    val sigW = (signature.width * scale * scaleX).toInt()
    val sigH = (signature.height * scale * scaleY).toInt()

    val scaledSig = Bitmap.createScaledBitmap(
        signature,
        sigW.coerceAtLeast(1),
        sigH.coerceAtLeast(1),
        true,
    )
    canvas.drawBitmap(scaledSig, bmpX, bmpY, Paint(Paint.ANTI_ALIAS_FLAG))

    return result
}
