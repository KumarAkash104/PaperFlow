package com.paperflow.app.ui.signature

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border

enum class AnnotationTool(val label: String) {
    PEN("Pen"),
    HIGHLIGHTER("Highlighter"),
    ERASER("Eraser"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationScreen(
    pageBitmap: Bitmap,
    onSave: (Bitmap) -> Unit,
    onBack: () -> Unit,
) {
    var strokes by remember { mutableStateOf(listOf<StrokePath>()) }
    var currentPoints by remember { mutableStateOf(listOf<Offset>()) }
    var selectedTool by remember { mutableStateOf(AnnotationTool.PEN) }
    var selectedColor by remember { mutableStateOf(Color(0xFFC62828)) }
    var canvasWidth by remember { mutableFloatStateOf(0f) }
    var canvasHeight by remember { mutableFloatStateOf(0f) }

    var highlighterWidth by remember { mutableFloatStateOf(20f) }
    var highlighterAlpha by remember { mutableFloatStateOf(0.4f) }

    var showColorGridDialog by remember { mutableStateOf(false) }
    var eraserPosition by remember { mutableStateOf<Offset?>(null) }

    val toolWidth = when (selectedTool) {
        AnnotationTool.PEN -> 4f
        AnnotationTool.HIGHLIGHTER -> highlighterWidth
        AnnotationTool.ERASER -> 0f
    }
    val toolAlpha = when (selectedTool) {
        AnnotationTool.PEN -> 1f
        AnnotationTool.HIGHLIGHTER -> highlighterAlpha
        AnnotationTool.ERASER -> 0f
    }

    val currentSelectedTool = rememberUpdatedState(selectedTool)
    val currentSelectedColor = rememberUpdatedState(selectedColor)
    val currentToolWidth = rememberUpdatedState(toolWidth)
    val currentToolAlpha = rememberUpdatedState(toolAlpha)
    val currentStrokes = rememberUpdatedState(strokes)

    val colors = listOf(
        Color(0xFFC62828) to "Red",
        Color(0xFF1565C0) to "Blue",
        Color.Black to "Black",
        Color(0xFF2E7D32) to "Green",
        Color(0xFFF9A825) to "Yellow",
    )

    val pageImage = remember(pageBitmap) { pageBitmap.asImageBitmap() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Annotate", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (strokes.isNotEmpty()) strokes = strokes.dropLast(1)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Undo, "Undo")
                    }
                    IconButton(onClick = { strokes = emptyList() }) {
                        Icon(Icons.Default.Delete, "Clear")
                    }
                    FilledTonalButton(
                        onClick = {
                            val result = renderAnnotations(pageBitmap, strokes, canvasWidth, canvasHeight)
                            onSave(result)
                        },
                        enabled = strokes.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clipToBounds()
                    .onSizeChanged {
                        canvasWidth = it.width.toFloat()
                        canvasHeight = it.height.toFloat()
                    },
            ) {
                Image(
                    bitmap = pageImage,
                    contentDescription = "Page",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )

                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            var localPoints = listOf<Offset>()
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (currentSelectedTool.value == AnnotationTool.ERASER) {
                                        eraserPosition = offset
                                        strokes = eraseStrokesAt(currentStrokes.value, offset)
                                    } else {
                                        localPoints = listOf(offset)
                                        currentPoints = localPoints
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    if (currentSelectedTool.value == AnnotationTool.ERASER) {
                                        eraserPosition = change.position
                                        strokes = eraseStrokesAt(currentStrokes.value, change.position)
                                    } else {
                                        localPoints = localPoints + change.position
                                        currentPoints = localPoints
                                    }
                                },
                                onDragEnd = {
                                    if (currentSelectedTool.value == AnnotationTool.ERASER) {
                                        eraserPosition = null
                                    } else {
                                        if (localPoints.size >= 2) {
                                            strokes = strokes + StrokePath(
                                                localPoints,
                                                currentSelectedColor.value.copy(alpha = currentToolAlpha.value),
                                                currentToolWidth.value,
                                            )
                                        }
                                        localPoints = emptyList()
                                        currentPoints = emptyList()
                                    }
                                },
                                onDragCancel = {
                                    eraserPosition = null
                                    localPoints = emptyList()
                                    currentPoints = emptyList()
                                },
                            )
                        },
                ) {
                    // Draw completed strokes
                    strokes.forEach { stroke ->
                        drawAnnotationStroke(stroke.points, stroke.color, stroke.width)
                    }
                    // Draw current stroke
                    if (currentPoints.size >= 2 && selectedTool != AnnotationTool.ERASER) {
                        drawAnnotationStroke(
                            currentPoints,
                            selectedColor.copy(alpha = toolAlpha),
                            toolWidth,
                        )
                    }
                    // Draw eraser footprint preview
                    if (selectedTool == AnnotationTool.ERASER && eraserPosition != null) {
                        drawCircle(
                            color = Color.Gray.copy(alpha = 0.15f),
                            radius = 35f,
                            center = eraserPosition!!
                        )
                        drawCircle(
                            color = Color.Gray.copy(alpha = 0.5f),
                            radius = 35f,
                            center = eraserPosition!!,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                        )
                    }
                }
            }

            // Tool selection
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedTool == AnnotationTool.PEN,
                    onClick = { selectedTool = AnnotationTool.PEN },
                    label = { Text("Pen") },
                    leadingIcon = { Icon(Icons.Default.Draw, null, Modifier.size(16.dp)) },
                )
                FilterChip(
                    selected = selectedTool == AnnotationTool.HIGHLIGHTER,
                    onClick = { selectedTool = AnnotationTool.HIGHLIGHTER },
                    label = { Text("Highlighter") },
                    leadingIcon = { Icon(Icons.Default.BorderColor, null, Modifier.size(16.dp)) },
                )
                FilterChip(
                    selected = selectedTool == AnnotationTool.ERASER,
                    onClick = { selectedTool = AnnotationTool.ERASER },
                    label = { Text("Eraser") },
                    leadingIcon = { Icon(Icons.Default.Clear, null, Modifier.size(16.dp)) },
                )
            }

            // Color picker
            AnimatedVisibility(visible = selectedTool != AnnotationTool.ERASER) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    colors.forEach { (color, _) ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { selectedColor = color },
                        ) {
                            if (color == selectedColor) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(3.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.4f)),
                                )
                            }
                        }
                    }

                    // Palette icon button for more colors
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { showColorGridDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "More Colors",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = selectedTool == AnnotationTool.HIGHLIGHTER) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Thickness", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(80.dp))
                        Slider(
                            value = highlighterWidth,
                            onValueChange = { highlighterWidth = it },
                            valueRange = 10f..50f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            track = { sliderState ->
                                SliderDefaults.Track(
                                    sliderState = sliderState,
                                    modifier = Modifier.height(4.dp),
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                )
                            }
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Opacity", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(80.dp))
                        Slider(
                            value = highlighterAlpha,
                            onValueChange = { highlighterAlpha = it },
                            valueRange = 0.1f..1.0f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            track = { sliderState ->
                                SliderDefaults.Track(
                                    sliderState = sliderState,
                                    modifier = Modifier.height(4.dp),
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                )
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showColorGridDialog) {
        AlertDialog(
            onDismissRequest = { showColorGridDialog = false },
            title = {
                Text(
                    text = "Select Color",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    val expandedColors = listOf(
                        Color(0xFF000000), // Black
                        Color(0xFF333333), // Dark Gray
                        Color(0xFF666666), // Medium Gray
                        Color(0xFF999999), // Light Gray
                        Color(0xFFCCCCCC), // Very Light Gray
                        
                        Color(0xFFC62828), // Red
                        Color(0xFFAD1457), // Pink
                        Color(0xFF6A1B9A), // Purple
                        Color(0xFF4527A0), // Deep Purple
                        Color(0xFF283593), // Indigo
                        
                        Color(0xFF1565C0), // Blue
                        Color(0xFF0277BD), // Light Blue
                        Color(0xFF00838F), // Cyan
                        Color(0xFF00695C), // Teal
                        Color(0xFF2E7D32), // Green
                        
                        Color(0xFF558B2F), // Light Green
                        Color(0xFF9E9D24), // Lime
                        Color(0xFFF9A825), // Yellow
                        Color(0xFFFF8F00), // Amber
                        Color(0xFFEF6C00), // Orange
                        
                        Color(0xFFD84315), // Deep Orange
                        Color(0xFF4E342E), // Brown
                        Color(0xFF37474F), // Blue Gray
                        Color(0xFF00E676), // Bright Green
                        Color(0xFF00B0FF), // Bright Blue
                    )
                    
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        expandedColors.chunked(5).forEach { rowColors ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                            ) {
                                rowColors.forEach { color ->
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                width = if (selectedColor == color) 3.dp else 1.dp,
                                                color = if (selectedColor == color) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.2f),
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                selectedColor = color
                                                showColorGridDialog = false
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (selectedColor == color) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = if (color == Color.White || color.toArgb() == Color(0xFFCCCCCC).toArgb() || color.toArgb() == Color(0xFF999999).toArgb()) Color.Black else Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showColorGridDialog = false }
                ) {
                    Text("Close")
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        )
    }
}

private fun eraseStrokesAt(strokes: List<StrokePath>, offset: Offset, radius: Float = 35f): List<StrokePath> {
    val result = mutableListOf<StrokePath>()
    val radiusSq = radius * radius

    strokes.forEach { stroke ->
        val currentSegment = mutableListOf<Offset>()
        
        stroke.points.forEach { pt ->
            val dx = pt.x - offset.x
            val dy = pt.y - offset.y
            val isErased = (dx * dx + dy * dy) <= radiusSq
            
            if (isErased) {
                if (currentSegment.isNotEmpty()) {
                    if (currentSegment.size == 1) {
                        val singlePt = currentSegment.first()
                        result.add(stroke.copy(points = listOf(singlePt, singlePt + Offset(0.1f, 0.1f))))
                    } else {
                        result.add(stroke.copy(points = currentSegment.toList()))
                    }
                    currentSegment.clear()
                }
            } else {
                currentSegment.add(pt)
            }
        }
        
        if (currentSegment.isNotEmpty()) {
            if (currentSegment.size == 1) {
                val singlePt = currentSegment.first()
                result.add(stroke.copy(points = listOf(singlePt, singlePt + Offset(0.1f, 0.1f))))
            } else {
                result.add(stroke.copy(points = currentSegment.toList()))
            }
        }
    }
    
    return result
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAnnotationStroke(
    points: List<Offset>,
    color: Color,
    width: Float,
) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        for (i in 1 until points.size) {
            lineTo(points[i].x, points[i].y)
        }
    }
    drawPath(
        path, color,
        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private fun renderAnnotations(
    page: Bitmap,
    strokes: List<StrokePath>,
    canvasWidth: Float,
    canvasHeight: Float,
): Bitmap {
    val result = page.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(result)

    val pageAspect = page.width.toFloat() / page.height
    val viewAspect = canvasWidth / canvasHeight
    val (drawW, drawH) = if (pageAspect > viewAspect) {
        canvasWidth to (canvasWidth / pageAspect)
    } else {
        (canvasHeight * pageAspect) to canvasHeight
    }
    val scaleX = page.width / drawW
    val scaleY = page.height / drawH
    val padX = (canvasWidth - drawW) / 2f
    val padY = (canvasHeight - drawH) / 2f

    strokes.forEach { stroke ->
        if (stroke.points.size < 2) return@forEach
        val paint = Paint().apply {
            color = stroke.color.toArgb()
            strokeWidth = stroke.width * scaleX
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        val path = android.graphics.Path().apply {
            val first = stroke.points.first()
            moveTo((first.x - padX) * scaleX, (first.y - padY) * scaleY)
            for (i in 1 until stroke.points.size) {
                val pt = stroke.points[i]
                lineTo((pt.x - padX) * scaleX, (pt.y - padY) * scaleY)
            }
        }
        canvas.drawPath(path, paint)
    }
    return result
}
