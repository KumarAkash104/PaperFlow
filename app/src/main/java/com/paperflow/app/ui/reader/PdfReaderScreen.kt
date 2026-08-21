package com.paperflow.app.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewCarousel
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Size
import kotlinx.coroutines.launch
import java.io.File
import kotlin.random.Random

enum class ReaderAnnotationTool(val label: String) {
    NONE("Read Only"),
    PEN("Pen"),
    HIGHLIGHTER("Highlighter"),
    ERASER("Eraser")
}

data class ReaderStrokePath(
    val points: List<Offset>,
    val color: Color,
    val width: Float,
    val isHighlighter: Boolean = false
)

data class StrokeRecord(val pageIndex: Int, val stroke: ReaderStrokePath)

fun distanceToSegmentSq(p: Offset, a: Offset, b: Offset): Float {
    val l2 = (b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y)
    if (l2 == 0f) {
        val dx = p.x - a.x
        val dy = p.y - a.y
        return dx * dx + dy * dy
    }
    var t = ((p.x - a.x) * (b.x - a.x) + (p.y - a.y) * (b.y - a.y)) / l2
    t = t.coerceIn(0f, 1f)
    val projX = a.x + t * (b.x - a.x)
    val projY = a.y + t * (b.y - a.y)
    val dx = p.x - projX
    val dy = p.y - projY
    return dx * dx + dy * dy
}

fun strokeIntersectsTouch(stroke: ReaderStrokePath, touchPosNorm: Offset, radiusNorm: Float): Boolean {
    val radiusSq = radiusNorm * radiusNorm
    if (stroke.points.size < 2) {
        val pt = stroke.points.firstOrNull() ?: return false
        val dx = pt.x - touchPosNorm.x
        val dy = pt.y - touchPosNorm.y
        return (dx * dx + dy * dy) <= radiusSq
    }
    for (i in 0 until stroke.points.size - 1) {
        val a = stroke.points[i]
        val b = stroke.points[i + 1]
        if (distanceToSegmentSq(touchPosNorm, a, b) <= radiusSq) {
            return true
        }
    }
    return false
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawReaderStroke(stroke: ReaderStrokePath) {
    if (stroke.points.size < 2) return
    val w = size.width
    val h = size.height
    val path = Path().apply {
        val first = stroke.points.first()
        moveTo(first.x * w, first.y * h)
        for (i in 1 until stroke.points.size) {
            val pt = stroke.points[i]
            lineTo(pt.x * w, pt.y * h)
        }
    }
    val cap = if (stroke.isHighlighter) StrokeCap.Square else StrokeCap.Round
    val join = if (stroke.isHighlighter) StrokeJoin.Miter else StrokeJoin.Round
    drawPath(
        path = path,
        color = stroke.color,
        style = Stroke(width = stroke.width, cap = cap, join = join)
    )
}

@Composable
fun RubberIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height

        // Eraser main body (slanted rubber block)
        val bodyPath = Path().apply {
            moveTo(w * 0.20f, h * 0.75f)
            lineTo(w * 0.65f, h * 0.75f)
            lineTo(w * 0.85f, h * 0.35f)
            lineTo(w * 0.40f, h * 0.35f)
            close()
        }
        drawPath(bodyPath, color = tint)

        // Eraser top highlight edge
        val topHighlight = Path().apply {
            moveTo(w * 0.40f, h * 0.35f)
            lineTo(w * 0.85f, h * 0.35f)
            lineTo(w * 0.70f, h * 0.15f)
            lineTo(w * 0.25f, h * 0.15f)
            close()
        }
        drawPath(topHighlight, color = tint.copy(alpha = 0.5f))

        // Metallic / paper band wrapper around base
        val bandPath = Path().apply {
            moveTo(w * 0.10f, h * 0.90f)
            lineTo(w * 0.45f, h * 0.90f)
            lineTo(w * 0.55f, h * 0.70f)
            lineTo(w * 0.20f, h * 0.70f)
            close()
        }
        drawPath(bandPath, color = tint.copy(alpha = 0.30f))
        drawPath(bandPath, color = tint, style = Stroke(width = 1.2.dp.toPx()))
    }
}

/** Subtle paper grain overlay — cached as a bitmap for performance. */
@Composable
fun PaperTextureOverlay(isDark: Boolean) {
    val grainAlpha = if (isDark) 0.04f else 0.025f
    val grainColor = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
    // Generate grain bitmap once per theme, reuse across frames
    val grainBitmap = remember(isDark) {
        val bw = 200
        val bh = 200
        val bmp = android.graphics.Bitmap.createBitmap(bw, bh, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val random = Random(42)
        val dotCount = 400
        for (i in 0 until dotCount) {
            val x = random.nextFloat() * bw
            val y = random.nextFloat() * bh
            val r = random.nextFloat() * 1.2f + 0.3f
            val a = ((random.nextFloat() * 0.6f + 0.2f) * 255).toInt()
            paint.color = grainColor
            paint.alpha = a
            canvas.drawCircle(x, y, r, paint)
        }
        bmp
    }
    Canvas(modifier = Modifier.fillMaxSize().alpha(grainAlpha)) {
        val imageBitmap = grainBitmap.asImageBitmap()
        // Tile the small grain texture across the whole screen
        var y = 0f
        while (y < size.height) {
            var x = 0f
            while (x < size.width) {
                drawImage(imageBitmap, topLeft = Offset(x, y))
                x += grainBitmap.width
            }
            y += grainBitmap.height
        }
    }
}

/** Bottom control panel with scroll mode toggle. */
@Composable
fun ReaderControlPanel(
    currentPage: Int,
    totalPages: Int,
    theme: ReadingTheme,
    brightness: Float,
    contrast: Float,
    scrollMode: ScrollMode,
    surfaceColor: Color,
    onSurfaceColor: Color,
    onThemeChange: (ReadingTheme) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onScrollModeChange: () -> Unit,
    onPageSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount > 15f) {
                        onDismiss()
                    }
                }
            }
            .background(
                surfaceColor.copy(alpha = 0.97f),
                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
            .border(1.dp, onSurfaceColor.copy(alpha = 0.08f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp, top = 12.dp)
    ) {
        // Drag handle
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Box(
                modifier = Modifier
                    .width(44.dp).height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(onSurfaceColor.copy(alpha = 0.3f))
                    .clickable { onDismiss() }
            )
        }

        Spacer(Modifier.height(16.dp))

        // Scroll mode toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isVertical = scrollMode == ScrollMode.VERTICAL
            // Vertical option
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
                    .background(
                        if (isVertical) onSurfaceColor.copy(alpha = 0.15f)
                        else Color.Transparent
                    )
                    .border(
                        1.dp,
                        onSurfaceColor.copy(alpha = 0.2f),
                        RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp)
                    )
                    .clickable { if (!isVertical) onScrollModeChange() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.SwapVert,
                        "Scroll",
                        tint = onSurfaceColor.copy(alpha = if (isVertical) 0.9f else 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Scroll",
                        color = onSurfaceColor.copy(alpha = if (isVertical) 0.9f else 0.4f),
                        fontSize = 13.sp,
                        fontWeight = if (isVertical) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
            // Horizontal option
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
                    .background(
                        if (!isVertical) onSurfaceColor.copy(alpha = 0.15f)
                        else Color.Transparent
                    )
                    .border(
                        1.dp,
                        onSurfaceColor.copy(alpha = 0.2f),
                        RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp)
                    )
                    .clickable { if (isVertical) onScrollModeChange() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ViewCarousel,
                        "Page",
                        tint = onSurfaceColor.copy(alpha = if (!isVertical) 0.9f else 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Page",
                        color = onSurfaceColor.copy(alpha = if (!isVertical) 0.9f else 0.4f),
                        fontSize = 13.sp,
                        fontWeight = if (!isVertical) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Page slider
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("$currentPage", color = onSurfaceColor.copy(alpha = 0.6f), fontSize = 12.sp,
                modifier = Modifier.width(30.dp), textAlign = TextAlign.Center)
            Slider(
                value = currentPage.toFloat(),
                onValueChange = { onPageSelected(it.toInt()) },
                valueRange = 1f..totalPages.toFloat().coerceAtLeast(1f),
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = onSurfaceColor,
                    activeTrackColor = onSurfaceColor.copy(alpha = 0.5f),
                    inactiveTrackColor = onSurfaceColor.copy(alpha = 0.15f)
                )
            )
            Text("$totalPages", color = onSurfaceColor.copy(alpha = 0.6f), fontSize = 12.sp,
                modifier = Modifier.width(30.dp), textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(12.dp))

        // Theme selector
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            ReadingTheme.entries.forEach { t ->
                val isSelected = t == theme
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onThemeChange(t) }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(t.bgColor))
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) onSurfaceColor else onSurfaceColor.copy(alpha = 0.25f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Aa", color = Color(t.textTint), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        t.displayName,
                        color = if (isSelected) onSurfaceColor else onSurfaceColor.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Brightness
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Brightness6, "Brightness", tint = onSurfaceColor.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Slider(value = brightness, onValueChange = onBrightnessChange, valueRange = 0.3f..1.0f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = onSurfaceColor, activeTrackColor = onSurfaceColor.copy(alpha = 0.4f), inactiveTrackColor = onSurfaceColor.copy(alpha = 0.12f)))
            Spacer(Modifier.width(8.dp))
            Text("${(brightness * 100).toInt()}%", color = onSurfaceColor.copy(alpha = 0.4f), fontSize = 11.sp, modifier = Modifier.width(32.dp))
        }

        // Contrast
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Contrast, "Contrast", tint = onSurfaceColor.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Slider(value = contrast, onValueChange = onContrastChange, valueRange = 0.5f..2.0f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = onSurfaceColor, activeTrackColor = onSurfaceColor.copy(alpha = 0.4f), inactiveTrackColor = onSurfaceColor.copy(alpha = 0.12f)))
            Spacer(Modifier.width(8.dp))
            Text("${(contrast * 100).toInt()}%", color = onSurfaceColor.copy(alpha = 0.4f), fontSize = 11.sp, modifier = Modifier.width(32.dp))
        }

        Spacer(Modifier.height(4.dp))

        // Done button
        Text(
            "Done", color = onSurfaceColor, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(onSurfaceColor.copy(alpha = 0.1f))
                .clickable { onDismiss() }
                .padding(vertical = 10.dp)
        )
    }
}

/**
 * Lazily loads and displays a single PDF page.
 * Centered by default. Image rendered on-demand.
 */
@Composable
fun LazyPdfPage(
    viewModel: PdfReaderViewModel,
    pageIndex: Int,
    bgColor: Color,
    warmth: Float,
    contrast: Float,
    isDark: Boolean,
    onSurfaceColor: Color,
    strokes: List<ReaderStrokePath> = emptyList(),
    activeTool: ReaderAnnotationTool = ReaderAnnotationTool.NONE,
    selectedColor: Color = Color(0xFFE53935),
    penWidth: Float = 4f,
    highlighterWidth: Float = 24f,
    onAddStroke: ((ReaderStrokePath) -> Unit)? = null,
    onEraseAt: ((Offset) -> Unit)? = null
) {
    var imagePath by remember(pageIndex) { mutableStateOf<String?>(null) }
    var isLoading by remember(pageIndex) { mutableStateOf(true) }

    LaunchedEffect(pageIndex) {
        isLoading = true
        imagePath = viewModel.getPageImagePath(pageIndex)
        isLoading = false
    }

    if (imagePath != null) {
        PaperPage(
            imagePath = imagePath!!,
            bgColor = bgColor,
            warmth = warmth,
            contrast = contrast,
            isDark = isDark,
            pageNumber = pageIndex + 1,
            onSurfaceColor = onSurfaceColor,
            strokes = strokes,
            activeTool = activeTool,
            selectedColor = selectedColor,
            penWidth = penWidth,
            highlighterWidth = highlighterWidth,
            onAddStroke = onAddStroke,
            onEraseAt = onEraseAt
        )
    } else {
        // Loading placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        strokeWidth = 1.5.dp,
                        color = onSurfaceColor.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Page ${pageIndex + 1}",
                        color = onSurfaceColor.copy(alpha = 0.3f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * A single PDF page rendered with paper-like feel and annotation overlay.
 */
@Composable
fun PaperPage(
    imagePath: String,
    bgColor: Color,
    warmth: Float,
    contrast: Float,
    isDark: Boolean,
    pageNumber: Int,
    onSurfaceColor: Color,
    strokes: List<ReaderStrokePath> = emptyList(),
    activeTool: ReaderAnnotationTool = ReaderAnnotationTool.NONE,
    selectedColor: Color = Color(0xFFE53935),
    penWidth: Float = 4f,
    highlighterWidth: Float = 24f,
    onAddStroke: ((ReaderStrokePath) -> Unit)? = null,
    onEraseAt: ((Offset) -> Unit)? = null
) {
    val colorMatrix = remember(warmth, contrast, isDark) {
        val c = contrast
        val t = (1f - c) / 2f * 255f
        var cm = ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )
        )
        if (warmth > 0f) {
            val warmMatrix = ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, warmth * 18f,
                    0f, 1f, 0f, 0f, warmth * 8f,
                    0f, 0f, 1f, 0f, -warmth * 12f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            cm.timesAssign(warmMatrix)
        }
        if (isDark) {
            val invertMatrix = ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            cm.timesAssign(invertMatrix)
        }
        cm
    }

    var liveDrawingPoints by remember { mutableStateOf(listOf<Offset>()) }
    var currentEraserPos by remember { mutableStateOf<Offset?>(null) }

    Column {
        val context = LocalContext.current
        val imageRequest = remember<ImageRequest>(imagePath) {
            ImageRequest.Builder(context)
                .data(File(imagePath))
                .size(Size.ORIGINAL)
                .precision(Precision.EXACT)
                .build()
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(activeTool, selectedColor) {
                    if (activeTool == ReaderAnnotationTool.NONE) return@pointerInput
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var points = listOf(down.position)
                        val isErasing = activeTool == ReaderAnnotationTool.ERASER

                        if (isErasing) {
                            currentEraserPos = down.position
                            val normPos = Offset(down.position.x / w, down.position.y / h)
                            onEraseAt?.invoke(normPos)
                        } else {
                            liveDrawingPoints = points.map { Offset(it.x / w, it.y / h) }
                        }

                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.pressed } ?: break
                            val pos = change.position
                            if (isErasing) {
                                currentEraserPos = pos
                                val normPos = Offset(pos.x / w, pos.y / h)
                                onEraseAt?.invoke(normPos)
                            } else {
                                points = points + pos
                                liveDrawingPoints = points.map { Offset(it.x / w, it.y / h) }
                            }
                            change.consume()
                        } while (event.changes.any { it.pressed })

                        if (!isErasing && points.size >= 2) {
                            val isHighlighter = activeTool == ReaderAnnotationTool.HIGHLIGHTER
                            val c = if (isHighlighter) selectedColor.copy(alpha = 0.38f) else selectedColor
                            val penW = if (isHighlighter) highlighterWidth else penWidth
                            val normPoints = points.map { Offset((it.x / w).coerceIn(0f, 1f), (it.y / h).coerceIn(0f, 1f)) }
                            onAddStroke?.invoke(ReaderStrokePath(normPoints, c, penW, isHighlighter))
                        }
                        liveDrawingPoints = emptyList()
                        currentEraserPos = null
                    }
                }
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = "Page $pageNumber",
                contentScale = ContentScale.FillWidth,
                colorFilter = ColorFilter.colorMatrix(colorMatrix),
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithContent {
                        drawContent()
                        val vignetteStrength = if (isDark) 0.12f else 0.06f
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = vignetteStrength)
                                ),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = size.maxDimension * 0.75f
                            )
                        )
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.05f),
                                    Color.Transparent
                                ),
                                startX = 0f,
                                endX = size.width * 0.03f
                            )
                        )
                    }
            )

            // Annotation Canvas Overlay
            Canvas(modifier = Modifier.matchParentSize()) {
                strokes.forEach { stroke ->
                    drawReaderStroke(stroke)
                }
                if (liveDrawingPoints.size >= 2 && activeTool != ReaderAnnotationTool.ERASER) {
                    val isHighlighter = activeTool == ReaderAnnotationTool.HIGHLIGHTER
                    val c = if (isHighlighter) selectedColor.copy(alpha = 0.38f) else selectedColor
                    val w = if (isHighlighter) highlighterWidth else penWidth
                    drawReaderStroke(ReaderStrokePath(liveDrawingPoints, c, w, isHighlighter))
                }
                // Draw active rubber / eraser touch circle indicator
                if (activeTool == ReaderAnnotationTool.ERASER && currentEraserPos != null) {
                    val pos = currentEraserPos!!
                    drawCircle(
                        color = Color(0xFFFF5252).copy(alpha = 0.45f),
                        radius = 28.dp.toPx(),
                        center = pos,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = Color(0xFFFF5252).copy(alpha = 0.15f),
                        radius = 28.dp.toPx(),
                        center = pos
                    )
                }
            }
        }

        Text(
            text = "$pageNumber",
            color = onSurfaceColor.copy(alpha = 0.25f),
            fontSize = 10.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PdfReaderScreen(
    viewModel: PdfReaderViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    val theme = uiState.theme
    val bgColor = Color(theme.bgColor)
    val surfaceColor = Color(theme.uiSurface)
    val onSurfaceColor = Color(theme.uiOnSurface)

    // Zoom and pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Left Annotation Panel state
    val context = LocalContext.current
    var showLeftAnnotationPanel by remember { mutableStateOf<Boolean>(false) }
    var activeTool by remember { mutableStateOf<ReaderAnnotationTool>(ReaderAnnotationTool.NONE) }
    var selectedPenColor by remember { mutableStateOf<Color>(Color(0xFFE53935)) } // Red
    var selectedHighlighterColor by remember { mutableStateOf<Color>(Color(0xFFFFD54F)) } // Yellow
    var penWidth by remember { mutableFloatStateOf(4f) }
    var highlighterWidth by remember { mutableFloatStateOf(24f) }
    var pageStrokesMap by remember { mutableStateOf<Map<Int, List<ReaderStrokePath>>>(mapOf()) }
    var strokeHistory by remember { mutableStateOf<List<StrokeRecord>>(emptyList()) }
    var showColorPalette by remember { mutableStateOf<Boolean>(false) }

    var showUnsavedDialog by remember { mutableStateOf(false) }
    var pendingExitAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val hasUnsavedAnnotations = remember(pageStrokesMap) { pageStrokesMap.values.any { it.isNotEmpty() } }

    BackHandler(enabled = hasUnsavedAnnotations) {
        pendingExitAction = { onBack() }
        showUnsavedDialog = true
    }

    // Jump to page dialog state
    var showJumpToPageDialog by remember { mutableStateOf(false) }
    var jumpPageInput by remember { mutableStateOf("") }

    // Animated brightness overlay
    val brightnessAlpha by animateFloatAsState(
        targetValue = 1f - uiState.brightness,
        animationSpec = tween(300),
        label = "brightness"
    )

    // Vertical scroll state
    val listState = rememberLazyListState()

    // Track current page from vertical scroll
    LaunchedEffect(listState, uiState.pageCount, uiState.scrollMode) {
        if (uiState.pageCount > 0 && uiState.scrollMode == ScrollMode.VERTICAL) {
            snapshotFlow { listState.firstVisibleItemIndex }.collect { page ->
                viewModel.setCurrentPage(page)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        if (uiState.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp, color = onSurfaceColor)
                Spacer(Modifier.height(16.dp))
                Text(
                    "Opening document…",
                    color = onSurfaceColor.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }
        } else if (uiState.error != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    uiState.error ?: "Error",
                    color = Color(0xFFCC4444),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else if (uiState.pageCount > 0) {
            // ── Content area with zoom/pan ──
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
                    // Pinch-to-zoom AND single-finger pan when zoomed
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val pointerCount = event.changes.count { it.pressed }

                                if (pointerCount >= 2) {
                                    // Multi-finger: pinch zoom + pan
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                                    scale = newScale
                                    if (scale > 1f) {
                                        offsetX += pan.x
                                        offsetY += pan.y
                                        val maxX = (size.width * (scale - 1f)) / 2f
                                        val maxY = (size.height * (scale - 1f)) / 2f
                                        offsetX = offsetX.coerceIn(-maxX, maxX)
                                        offsetY = offsetY.coerceIn(-maxY, maxY)
                                    } else {
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                    event.changes.forEach { it.consume() }
                                } else if (pointerCount == 1 && scale > 1.05f && activeTool == ReaderAnnotationTool.NONE) {
                                    // Single-finger drag when zoomed: PAN
                                    val pan = event.calculatePan()
                                    if (pan.x != 0f || pan.y != 0f) {
                                        offsetX += pan.x
                                        offsetY += pan.y
                                        val maxX = (size.width * (scale - 1f)) / 2f
                                        val maxY = (size.height * (scale - 1f)) / 2f
                                        offsetX = offsetX.coerceIn(-maxX, maxX)
                                        offsetY = offsetY.coerceIn(-maxY, maxY)
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    // Double-tap: zoom to tap point / reset; Single-tap: hide controls if open
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                if (uiState.showControls) {
                                    viewModel.hideControls()
                                }
                            },
                            onDoubleTap = { tapOffset ->
                                if (scale > 1.1f) {
                                    // Currently zoomed → reset smoothly
                                    scope.launch {
                                        val startScale = scale
                                        val startOffsetX = offsetX
                                        val startOffsetY = offsetY
                                        
                                        androidx.compose.animation.core.animate(0f, 1f, animationSpec = tween(300)) { value, _ ->
                                            scale = startScale + (1f - startScale) * value
                                            offsetX = startOffsetX + (0f - startOffsetX) * value
                                            offsetY = startOffsetY + (0f - startOffsetY) * value
                                        }
                                    }
                                } else {
                                    // Zoom to 2.5x centered on tap point smoothly
                                    val targetScale = 2.5f
                                    val centerX = size.width / 2f
                                    val centerY = size.height / 2f
                                    var targetOffsetX = (centerX - tapOffset.x) * (targetScale - 1f)
                                    var targetOffsetY = (centerY - tapOffset.y) * (targetScale - 1f)
                                    
                                    val maxX = (size.width * (targetScale - 1f)) / 2f
                                    val maxY = (size.height * (targetScale - 1f)) / 2f
                                    targetOffsetX = targetOffsetX.coerceIn(-maxX, maxX)
                                    targetOffsetY = targetOffsetY.coerceIn(-maxY, maxY)
                                    
                                    scope.launch {
                                        val startScale = scale
                                        val startOffsetX = offsetX
                                        val startOffsetY = offsetY
                                        
                                        androidx.compose.animation.core.animate(0f, 1f, animationSpec = tween(300)) { value, _ ->
                                            scale = startScale + (targetScale - startScale) * value
                                            offsetX = startOffsetX + (targetOffsetX - startOffsetX) * value
                                            offsetY = startOffsetY + (targetOffsetY - startOffsetY) * value
                                        }
                                    }
                                }
                            }
                        )
                    }
            ) {
                if (uiState.scrollMode == ScrollMode.VERTICAL) {
                    // ── VERTICAL MODE: LazyColumn ──
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 0.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        userScrollEnabled = scale <= 1.05f && activeTool == ReaderAnnotationTool.NONE
                    ) {
                        items(uiState.pageCount) { index ->
                            LazyPdfPage(
                                viewModel = viewModel,
                                pageIndex = index,
                                bgColor = bgColor,
                                warmth = theme.warmth,
                                contrast = uiState.contrast,
                                isDark = theme == ReadingTheme.DARK || theme == ReadingTheme.WARM_DARK,
                                onSurfaceColor = onSurfaceColor,
                                strokes = pageStrokesMap[index] ?: emptyList(),
                                activeTool = if (showLeftAnnotationPanel) activeTool else ReaderAnnotationTool.NONE,
                                selectedColor = if (activeTool == ReaderAnnotationTool.HIGHLIGHTER) selectedHighlighterColor else selectedPenColor,
                                penWidth = penWidth,
                                highlighterWidth = highlighterWidth,
                                onAddStroke = { stroke ->
                                    val list = pageStrokesMap[index] ?: emptyList()
                                    pageStrokesMap = pageStrokesMap + (index to (list + stroke))
                                    strokeHistory = strokeHistory + StrokeRecord(index, stroke)
                                },
                                onEraseAt = { pos ->
                                    val list = pageStrokesMap[index] ?: emptyList()
                                    if (list.isNotEmpty()) {
                                        val updated = list.filterNot { stroke ->
                                            strokeIntersectsTouch(stroke, pos, 0.05f)
                                        }
                                        pageStrokesMap = pageStrokesMap + (index to updated)
                                    }
                                }
                            )
                        }
                    }
                } else {
                    // ── HORIZONTAL MODE: HorizontalPager ──
                    val pagerState = rememberPagerState(
                        initialPage = uiState.currentPage,
                        pageCount = { uiState.pageCount }
                    )

                    LaunchedEffect(pagerState) {
                        snapshotFlow { pagerState.currentPage }.collect { page ->
                            viewModel.setCurrentPage(page)
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = scale <= 1.05f && activeTool == ReaderAnnotationTool.NONE
                    ) { pageIndex ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            LazyPdfPage(
                                viewModel = viewModel,
                                pageIndex = pageIndex,
                                bgColor = bgColor,
                                warmth = theme.warmth,
                                contrast = uiState.contrast,
                                isDark = theme == ReadingTheme.DARK || theme == ReadingTheme.WARM_DARK,
                                onSurfaceColor = onSurfaceColor,
                                strokes = pageStrokesMap[pageIndex] ?: emptyList(),
                                activeTool = if (showLeftAnnotationPanel) activeTool else ReaderAnnotationTool.NONE,
                                selectedColor = if (activeTool == ReaderAnnotationTool.HIGHLIGHTER) selectedHighlighterColor else selectedPenColor,
                                penWidth = penWidth,
                                highlighterWidth = highlighterWidth,
                                onAddStroke = { stroke ->
                                    val list = pageStrokesMap[pageIndex] ?: emptyList()
                                    pageStrokesMap = pageStrokesMap + (pageIndex to (list + stroke))
                                    strokeHistory = strokeHistory + StrokeRecord(pageIndex, stroke)
                                },
                                onEraseAt = { pos ->
                                    val list = pageStrokesMap[pageIndex] ?: emptyList()
                                    if (list.isNotEmpty()) {
                                        val updated = list.filterNot { stroke ->
                                            strokeIntersectsTouch(stroke, pos, 0.05f)
                                        }
                                        pageStrokesMap = pageStrokesMap + (pageIndex to updated)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // ── Brightness dimming overlay ──
            if (brightnessAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = brightnessAlpha }
                        .background(Color.Black)
                )
            }

            // ── Paper texture grain overlay ──
            PaperTextureOverlay(
                isDark = theme == ReadingTheme.DARK || theme == ReadingTheme.WARM_DARK
            )

            // ── Left Side Premium Collapsible Annotation Panel ──
            if (!showLeftAnnotationPanel) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 0.dp)
                        .statusBarsPadding(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                            .background(surfaceColor.copy(alpha = 0.88f))
                            .border(1.dp, onSurfaceColor.copy(alpha = 0.15f), RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                            .clickable {
                                showLeftAnnotationPanel = true
                                activeTool = ReaderAnnotationTool.PEN
                            }
                            .padding(horizontal = 10.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Draw,
                            contentDescription = "Annotate & Highlight",
                            tint = onSurfaceColor.copy(alpha = 0.9f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                AnimatedVisibility(
                    visible = showLeftAnnotationPanel,
                    enter = slideInHorizontally { -it } + fadeIn(tween(250)),
                    exit = slideOutHorizontally { -it } + fadeOut(tween(200)),
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(surfaceColor.copy(alpha = 0.94f))
                            .border(1.dp, onSurfaceColor.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Pen
                        IconButton(
                            onClick = { activeTool = ReaderAnnotationTool.PEN },
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (activeTool == ReaderAnnotationTool.PEN) onSurfaceColor.copy(alpha = 0.15f) else Color.Transparent,
                                    CircleShape
                                )
                        ) {
                            Icon(Icons.Default.Draw, "Pen", tint = if (activeTool == ReaderAnnotationTool.PEN) selectedPenColor else onSurfaceColor.copy(alpha = 0.6f))
                        }

                        // Highlighter
                        IconButton(
                            onClick = { activeTool = ReaderAnnotationTool.HIGHLIGHTER },
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (activeTool == ReaderAnnotationTool.HIGHLIGHTER) onSurfaceColor.copy(alpha = 0.15f) else Color.Transparent,
                                    CircleShape
                                )
                        ) {
                            Icon(Icons.Default.BorderColor, "Highlighter", tint = if (activeTool == ReaderAnnotationTool.HIGHLIGHTER) selectedHighlighterColor else onSurfaceColor.copy(alpha = 0.6f))
                        }

                        // Eraser / Rubber
                        IconButton(
                            onClick = { activeTool = ReaderAnnotationTool.ERASER },
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (activeTool == ReaderAnnotationTool.ERASER) Color(0xFFFF5252).copy(alpha = 0.18f) else Color.Transparent,
                                    CircleShape
                                )
                        ) {
                            RubberIcon(
                                tint = if (activeTool == ReaderAnnotationTool.ERASER) Color(0xFFFF5252) else onSurfaceColor.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Color picker swatch
                        val curColor = if (activeTool == ReaderAnnotationTool.HIGHLIGHTER) selectedHighlighterColor else selectedPenColor
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(curColor)
                                .border(1.5.dp, onSurfaceColor.copy(alpha = 0.3f), CircleShape)
                                .clickable { showColorPalette = !showColorPalette }
                        )

                        if (showColorPalette) {
                            val colors = if (activeTool == ReaderAnnotationTool.HIGHLIGHTER) listOf(
                                Color(0xFFFFD54F), // Yellow
                                Color(0xFF81C784), // Green
                                Color(0xFFF48FB1), // Pink
                                Color(0xFF64B5F6), // Blue
                                Color(0xFFFFB74D)  // Orange
                            ) else listOf(
                                Color(0xFFE53935), // Red
                                Color(0xFF1E88E5), // Blue
                                Color(0xFF43A047), // Green
                                Color(0xFF000000), // Black
                                Color(0xFFFB8C00)  // Orange
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                colors.forEach { c ->
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(c)
                                            .clickable {
                                                if (activeTool == ReaderAnnotationTool.HIGHLIGHTER) selectedHighlighterColor = c else selectedPenColor = c
                                                showColorPalette = false
                                            }
                                    )
                                }
                            }
                        }

                        // Undo
                        IconButton(
                            onClick = {
                                if (strokeHistory.isNotEmpty()) {
                                    val lastRecord = strokeHistory.last()
                                    strokeHistory = strokeHistory.dropLast(1)
                                    val current = pageStrokesMap[lastRecord.pageIndex] ?: emptyList()
                                    if (current.isNotEmpty()) {
                                        val updated = current.filterNot { it == lastRecord.stroke }
                                        pageStrokesMap = pageStrokesMap + (lastRecord.pageIndex to updated)
                                    }
                                } else {
                                    val activePage = uiState.currentPage
                                    val current = pageStrokesMap[activePage] ?: emptyList()
                                    if (current.isNotEmpty()) {
                                        pageStrokesMap = pageStrokesMap + (activePage to current.dropLast(1))
                                    }
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = onSurfaceColor.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                        }

                        // Save to Documents section
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val success = viewModel.saveAnnotatedDocument("", pageStrokesMap)
                                    if (success) {
                                        Toast.makeText(context, "Saved annotated document to Documents library!", Toast.LENGTH_SHORT).show()
                                        showLeftAnnotationPanel = false
                                        activeTool = ReaderAnnotationTool.NONE
                                        pageStrokesMap = emptyMap()
                                    } else {
                                        Toast.makeText(context, "Failed to save document", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Save, "Save to Documents", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }

                        // Close panel
                        IconButton(
                            onClick = {
                                if (hasUnsavedAnnotations) {
                                    pendingExitAction = {
                                        showLeftAnnotationPanel = false
                                        activeTool = ReaderAnnotationTool.NONE
                                        showColorPalette = false
                                    }
                                    showUnsavedDialog = true
                                } else {
                                    showLeftAnnotationPanel = false
                                    activeTool = ReaderAnnotationTool.NONE
                                    showColorPalette = false
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Close, "Close Annotation", tint = onSurfaceColor.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // ── Floating settings button ──
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .navigationBarsPadding(),
                contentAlignment = Alignment.BottomEnd
            ) {
                IconButton(
                    onClick = { viewModel.toggleControls() },
                    modifier = Modifier
                        .size(48.dp)
                        .background(surfaceColor.copy(alpha = 0.88f), CircleShape)
                        .border(1.dp, onSurfaceColor.copy(alpha = 0.12f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.Tune,
                        "Reading Settings",
                        tint = onSurfaceColor.copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // ── Top bar ──
            AnimatedVisibility(
                visible = !uiState.isFullscreen,
                enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { -it },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    surfaceColor.copy(alpha = 0.95f),
                                    surfaceColor.copy(alpha = 0.85f),
                                    surfaceColor.copy(alpha = 0.0f)
                                )
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = {
                            if (hasUnsavedAnnotations) {
                                pendingExitAction = { onBack() }
                                showUnsavedDialog = true
                            } else {
                                onBack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = onSurfaceColor)
                        }
                        Column(
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = uiState.pdfTitle,
                                color = onSurfaceColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        jumpPageInput = (uiState.currentPage + 1).toString()
                                        showJumpToPageDialog = true
                                    }
                                    .padding(vertical = 1.dp)
                            ) {
                                Text(
                                    text = "Page ${uiState.currentPage + 1} of ${uiState.pageCount} ▾",
                                    color = onSurfaceColor.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        // Save to documents
                        IconButton(
                            onClick = { viewModel.saveToDocuments() },
                        ) {
                            Icon(
                                Icons.Default.SaveAlt,
                                contentDescription = "Save to Documents",
                                tint = if (uiState.savedToDocuments)
                                    onSurfaceColor.copy(alpha = 0.3f)
                                else onSurfaceColor
                            )
                        }
                        // Scroll mode toggle
                        IconButton(onClick = { viewModel.toggleScrollMode() }) {
                            Icon(
                                if (uiState.scrollMode == ScrollMode.VERTICAL)
                                    Icons.Default.ViewCarousel
                                else Icons.Default.SwapVert,
                                contentDescription = if (uiState.scrollMode == ScrollMode.VERTICAL)
                                    "Switch to page mode" else "Switch to scroll mode",
                                tint = onSurfaceColor
                            )
                        }
                        IconButton(onClick = { viewModel.toggleFullscreen() }) {
                            Icon(
                                if (uiState.isFullscreen) Icons.Default.FullscreenExit
                                else Icons.Default.Fullscreen,
                                "Fullscreen",
                                tint = onSurfaceColor
                            )
                        }
                    }
                }
            }

            // ── Full-screen dismiss scrim when controls panel is open ──
            if (uiState.showControls) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            viewModel.hideControls()
                        }
                )
            }

            // ── Bottom controls panel ──
            AnimatedVisibility(
                visible = uiState.showControls,
                enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                ReaderControlPanel(
                    currentPage = uiState.currentPage + 1,
                    totalPages = uiState.pageCount,
                    theme = uiState.theme,
                    brightness = uiState.brightness,
                    contrast = uiState.contrast,
                    scrollMode = uiState.scrollMode,
                    surfaceColor = surfaceColor,
                    onSurfaceColor = onSurfaceColor,
                    onThemeChange = { viewModel.setTheme(it) },
                    onBrightnessChange = { viewModel.setBrightness(it) },
                    onContrastChange = { viewModel.setContrast(it) },
                    onScrollModeChange = { viewModel.toggleScrollMode() },
                    onPageSelected = { page ->
                        scope.launch {
                            if (uiState.scrollMode == ScrollMode.VERTICAL) {
                                listState.animateScrollToItem(page - 1)
                            }
                        }
                    },
                    onDismiss = { viewModel.hideControls() }
                )
            }

            // ── Exit fullscreen button ──
            if (uiState.isFullscreen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .statusBarsPadding(),
                    contentAlignment = Alignment.TopEnd
                ) {
                    IconButton(
                        onClick = { viewModel.toggleFullscreen() },
                        modifier = Modifier
                            .size(40.dp)
                            .background(surfaceColor.copy(alpha = 0.7f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.FullscreenExit,
                            "Exit Fullscreen",
                            tint = onSurfaceColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
            }
        }
    }

    if (showJumpToPageDialog) {
        AlertDialog(
            onDismissRequest = { showJumpToPageDialog = false },
            title = { Text("Go to Page") },
            text = {
                Column {
                    Text("Enter page number (1 - ${uiState.pageCount}):", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = jumpPageInput,
                        onValueChange = { jumpPageInput = it.filter { ch -> ch.isDigit() } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val pageNum = jumpPageInput.toIntOrNull()
                    if (pageNum != null && pageNum in 1..uiState.pageCount) {
                        val targetIndex = pageNum - 1
                        viewModel.setCurrentPage(targetIndex)
                        scope.launch {
                            if (uiState.scrollMode == ScrollMode.VERTICAL) {
                                listState.scrollToItem(targetIndex)
                            }
                        }
                    }
                    showJumpToPageDialog = false
                }) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpToPageDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("Save Annotations?") },
            text = { Text("You have unsaved drawings or highlights. Would you like to save them to your Documents section before closing?") },
            confirmButton = {
                Button(
                    onClick = {
                        showUnsavedDialog = false
                        scope.launch {
                            val success = viewModel.saveAnnotatedDocument("", pageStrokesMap)
                            if (success) {
                                Toast.makeText(context, "Saved to Documents section!", Toast.LENGTH_SHORT).show()
                            }
                            pageStrokesMap = emptyMap()
                            showLeftAnnotationPanel = false
                            activeTool = ReaderAnnotationTool.NONE
                            pendingExitAction?.invoke()
                            pendingExitAction = null
                        }
                    }
                ) {
                    Text("Save to Documents")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showUnsavedDialog = false
                            pageStrokesMap = emptyMap()
                            showLeftAnnotationPanel = false
                            activeTool = ReaderAnnotationTool.NONE
                            pendingExitAction?.invoke()
                            pendingExitAction = null
                        }
                    ) {
                        Text("Discard", color = Color.Red)
                    }
                    TextButton(onClick = { showUnsavedDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}
}



