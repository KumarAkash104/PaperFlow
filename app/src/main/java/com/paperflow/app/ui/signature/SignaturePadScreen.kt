package com.paperflow.app.ui.signature

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.History
import androidx.compose.runtime.collectAsState
import com.paperflow.app.data.local.AppDatabase
import com.paperflow.app.data.model.SignatureEntity
import java.io.File
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.paperflow.app.util.SignatureExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StrokePath(
    val points: List<Offset>,
    val color: Color,
    val width: Float,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignaturePadScreen(
    onBack: () -> Unit,
    onSave: (Bitmap, Boolean) -> Unit,
) {
    var editingBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Draw", "Camera", "Gallery", "Saved")

    // ── Draw state ──
    var strokes by remember { mutableStateOf(listOf<StrokePath>()) }
    var currentPoints by remember { mutableStateOf(listOf<Offset>()) }
    var selectedColor by remember { mutableStateOf(Color.Black) }
    var strokeWidth by remember { mutableFloatStateOf(5f) }
    var canvasWidth by remember { mutableFloatStateOf(0f) }
    var canvasHeight by remember { mutableFloatStateOf(0f) }

    // ── Camera/Gallery state ──
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var extractedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isExtracting by remember { mutableStateOf(false) }
    var sensitivity by remember { mutableIntStateOf(30) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    val savedSignatures by remember(db) {
        db.signatureDao().getAllSignatures()
    }.collectAsState(initial = emptyList())

    val inkColors = listOf(
        Color.Black to "Black",
        Color(0xFF1565C0) to "Blue",
        Color(0xFFC62828) to "Red",
        Color(0xFF2E7D32) to "Green",
    )

    // ── Launchers ──
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            capturedBitmap = bitmap
            extractedBitmap = null
            scope.launch {
                isExtracting = true
                extractedBitmap = withContext(Dispatchers.Default) {
                    SignatureExtractor.extract(bitmap, sensitivity)
                }
                isExtracting = false
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isExtracting = true
                val bitmap = withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openInputStream(uri)
                    android.graphics.BitmapFactory.decodeStream(stream)
                }
                if (bitmap != null) {
                    capturedBitmap = bitmap
                    extractedBitmap = withContext(Dispatchers.Default) {
                        SignatureExtractor.extract(bitmap, sensitivity)
                    }
                }
                isExtracting = false
            }
        }
    }

    if (editingBitmap != null) {
        SignatureEditor(
            bitmap = editingBitmap!!,
            onBack = { editingBitmap = null },
            onSave = { finalBitmap ->
                editingBitmap = null
                onSave(finalBitmap, true)
            }
        )
    } else {
        Scaffold(
            topBar = {
            TopAppBar(
                title = { Text("Signature", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(
                            onClick = {
                                if (strokes.isNotEmpty()) strokes = strokes.dropLast(1)
                            },
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, "Undo")
                        }
                        IconButton(onClick = { strokes = emptyList() }) {
                            Icon(Icons.Default.Delete, "Clear")
                        }
                    } else if (selectedTab in 1..2 && capturedBitmap != null) {
                        IconButton(onClick = {
                            capturedBitmap = null
                            extractedBitmap = null
                        }) {
                            Icon(Icons.Default.Delete, "Clear")
                        }
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
            // ── Tab Row ──
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                        icon = {
                            Icon(
                                imageVector = when (index) {
                                    0 -> Icons.Default.Draw
                                    1 -> Icons.Default.CameraAlt
                                    2 -> Icons.Default.Image
                                    else -> Icons.Default.History
                                },
                                contentDescription = title,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }

            when (selectedTab) {
                0 -> DrawTab(
                    strokes = strokes,
                    currentPoints = currentPoints,
                    selectedColor = selectedColor,
                    strokeWidth = strokeWidth,
                    canvasWidth = canvasWidth,
                    canvasHeight = canvasHeight,
                    inkColors = inkColors,
                    onStrokesChange = { strokes = it },
                    onCurrentPointsChange = { currentPoints = it },
                    onColorChange = { selectedColor = it },
                    onStrokeWidthChange = { strokeWidth = it },
                    onCanvasSizeChange = { w, h -> canvasWidth = w; canvasHeight = h },
                    onSave = {
                        if (strokes.isNotEmpty() && canvasWidth > 0) {
                            val bitmap = renderStrokesToBitmap(
                                strokes, canvasWidth.toInt(), canvasHeight.toInt(),
                            )
                            editingBitmap = bitmap
                        }
                    },
                )
                1 -> CaptureTab(
                    extractedBitmap = extractedBitmap,
                    isExtracting = isExtracting,
                    sensitivity = sensitivity,
                    onSensitivityChange = { newSens ->
                        sensitivity = newSens
                        capturedBitmap?.let { src ->
                            scope.launch {
                                isExtracting = true
                                extractedBitmap = withContext(Dispatchers.Default) {
                                    SignatureExtractor.extract(src, newSens)
                                }
                                isExtracting = false
                            }
                        }
                    },
                    onCapture = { cameraLauncher.launch(null) },
                    onSave = { extractedBitmap?.let { editingBitmap = it } },
                    label = "Take Photo",
                    icon = Icons.Default.CameraAlt,
                )
                2 -> CaptureTab(
                    extractedBitmap = extractedBitmap,
                    isExtracting = isExtracting,
                    sensitivity = sensitivity,
                    onSensitivityChange = { newSens ->
                        sensitivity = newSens
                        capturedBitmap?.let { src ->
                            scope.launch {
                                isExtracting = true
                                extractedBitmap = withContext(Dispatchers.Default) {
                                    SignatureExtractor.extract(src, newSens)
                                }
                                isExtracting = false
                            }
                        }
                    },
                    onCapture = { galleryLauncher.launch("image/*") },
                    onSave = { extractedBitmap?.let { editingBitmap = it } },
                    label = "Pick from Gallery",
                    icon = Icons.Default.Image,
                )
                3 -> SavedTab(
                    savedSignatures = savedSignatures,
                    onDelete = { signature ->
                        scope.launch(Dispatchers.IO) {
                            try {
                                val file = File(signature.imagePath)
                                if (file.exists()) {
                                    file.delete()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            db.signatureDao().deleteSignatureById(signature.id)
                        }
                    },
                    onSelect = { signature ->
                        scope.launch(Dispatchers.IO) {
                            try {
                                val file = File(signature.imagePath)
                                if (file.exists()) {
                                    val bitmap = android.graphics.BitmapFactory.decodeFile(signature.imagePath)
                                    if (bitmap != null) {
                                        withContext(Dispatchers.Main) {
                                            onSave(bitmap, false)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                )
            }
        }
    }
}
}

// ═══════════════════════════════════════════════════
// Draw Tab
// ═══════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawTab(
    strokes: List<StrokePath>,
    currentPoints: List<Offset>,
    selectedColor: Color,
    strokeWidth: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    inkColors: List<Pair<Color, String>>,
    onStrokesChange: (List<StrokePath>) -> Unit,
    onCurrentPointsChange: (List<Offset>) -> Unit,
    onColorChange: (Color) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onCanvasSizeChange: (Float, Float) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .clipToBounds(),
        ) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(strokes, selectedColor, strokeWidth) {
                        var localPoints = listOf<Offset>()
                        detectDragGestures(
                            onDragStart = { offset ->
                                localPoints = listOf(offset)
                                onCurrentPointsChange(localPoints)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                localPoints = localPoints + change.position
                                onCurrentPointsChange(localPoints)
                            },
                            onDragEnd = {
                                if (localPoints.size >= 2) {
                                    onStrokesChange(strokes + StrokePath(localPoints, selectedColor, strokeWidth))
                                }
                                localPoints = emptyList()
                                onCurrentPointsChange(emptyList())
                            },
                            onDragCancel = {
                                localPoints = emptyList()
                                onCurrentPointsChange(emptyList())
                            },
                        )
                    },
            ) {
                onCanvasSizeChange(size.width, size.height)

                strokes.forEach { stroke ->
                    drawStrokePoints(stroke.points, stroke.color, stroke.width)
                }
                if (currentPoints.size >= 2) {
                    drawStrokePoints(currentPoints, selectedColor, strokeWidth)
                }
            }

            if (strokes.isEmpty() && currentPoints.isEmpty()) {
                Text(
                    "Draw your signature here",
                    color = Color.Gray.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Color picker
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Color", style = MaterialTheme.typography.labelMedium)
            inkColors.forEach { (color, _) ->
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (color == selectedColor) 3.dp else 1.dp,
                            color = if (color == selectedColor) MaterialTheme.colorScheme.primary
                            else Color.Gray.copy(alpha = 0.3f),
                            shape = CircleShape,
                        )
                        .clickable { onColorChange(color) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Stroke width
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Width", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(8.dp))
            Slider(
                value = strokeWidth,
                onValueChange = { onStrokeWidthChange(it) },
                valueRange = 2f..20f,
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
            Text("${strokeWidth.toInt()}", style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(16.dp))

        FilledTonalButton(
            onClick = onSave,
            enabled = strokes.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Icon(Icons.Default.Save, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Save Signature")
        }
    }
}

// ═══════════════════════════════════════════════════
// Camera / Gallery Tab
// ═══════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptureTab(
    extractedBitmap: Bitmap?,
    isExtracting: Boolean,
    sensitivity: Int,
    onSensitivityChange: (Int) -> Unit,
    onCapture: () -> Unit,
    onSave: () -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Preview area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (isExtracting) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Extracting signature...", style = MaterialTheme.typography.bodyMedium)
                }
            } else if (extractedBitmap != null) {
                // Show extracted signature on a checkered background to show transparency
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = extractedBitmap.asImageBitmap(),
                        contentDescription = "Extracted signature",
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Capture or pick a signature photo",
                        color = Color.Gray.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Background will be removed automatically",
                        color = Color.Gray.copy(alpha = 0.4f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Sensitivity slider (only visible when we have a captured image)
        if (extractedBitmap != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Sensitivity", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(8.dp))
                Slider(
                    value = sensitivity.toFloat(),
                    onValueChange = { onSensitivityChange(it.toInt()) },
                    valueRange = 5f..80f,
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
                Text("$sensitivity", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(8.dp))
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledTonalButton(
                onClick = onCapture,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            ) {
                Icon(icon, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(label)
            }

            if (extractedBitmap != null) {
                FilledTonalButton(
                    onClick = onSave,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Use Signature")
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════
// Drawing Helpers
// ═══════════════════════════════════════════════════

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStrokePoints(
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

private fun renderStrokesToBitmap(
    strokes: List<StrokePath>,
    width: Int,
    height: Int,
): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    strokes.forEach { stroke ->
        if (stroke.points.size < 2) return@forEach
        val paint = Paint().apply {
            this.color = stroke.color.toArgb()
            this.strokeWidth = stroke.width
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        val path = android.graphics.Path().apply {
            moveTo(stroke.points.first().x, stroke.points.first().y)
            for (i in 1 until stroke.points.size) {
                lineTo(stroke.points[i].x, stroke.points[i].y)
            }
        }
        canvas.drawPath(path, paint)
    }
    return bitmap
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignatureEditor(
    bitmap: Bitmap,
    onBack: () -> Unit,
    onSave: (Bitmap) -> Unit,
) {
    var currentBitmap by remember(bitmap) { mutableStateOf(bitmap) }

    var cropLeft by remember { mutableFloatStateOf(0f) }
    var cropTop by remember { mutableFloatStateOf(0f) }
    var cropRight by remember { mutableFloatStateOf(1f) }
    var cropBottom by remember { mutableFloatStateOf(1f) }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val imageBitmap = remember(currentBitmap) { currentBitmap.asImageBitmap() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crop & Rotate Signature", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val left = (cropLeft * currentBitmap.width).toInt().coerceIn(0, currentBitmap.width - 1)
                            val top = (cropTop * currentBitmap.height).toInt().coerceIn(0, currentBitmap.height - 1)
                            val right = (cropRight * currentBitmap.width).toInt().coerceIn(left + 1, currentBitmap.width)
                            val bottom = (cropBottom * currentBitmap.height).toInt().coerceIn(top + 1, currentBitmap.height)

                            val croppedW = right - left
                            val croppedH = bottom - top
                            if (croppedW > 0 && croppedH > 0) {
                                val cropped = Bitmap.createBitmap(currentBitmap, left, top, croppedW, croppedH)
                                onSave(cropped)
                            } else {
                                onSave(currentBitmap)
                            }
                        }
                    ) {
                        Icon(Icons.Default.Check, "Save")
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
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Interactive Crop Box Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF5F5F5)) // light gray background to contrast signature transparency
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .onSizeChanged { containerSize = it }
                    .clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                if (containerSize.width > 0 && containerSize.height > 0) {
                    // Show Signature Image
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "Signature to edit",
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Semi-transparent masking & crop grid overlay
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val w = size.width
                        val h = size.height

                        val leftPx = cropLeft * w
                        val topPx = cropTop * h
                        val rightPx = cropRight * w
                        val bottomPx = cropBottom * h

                        // 1. Draw dimmed mask outside crop area
                        // Top
                        drawRect(
                            color = Color.Black.copy(alpha = 0.55f),
                            topLeft = Offset(0f, 0f),
                            size = Size(w, topPx)
                        )
                        // Left
                        drawRect(
                            color = Color.Black.copy(alpha = 0.55f),
                            topLeft = Offset(0f, topPx),
                            size = Size(leftPx, bottomPx - topPx)
                        )
                        // Right
                        drawRect(
                            color = Color.Black.copy(alpha = 0.55f),
                            topLeft = Offset(rightPx, topPx),
                            size = Size(w - rightPx, bottomPx - topPx)
                        )
                        // Bottom
                        drawRect(
                            color = Color.Black.copy(alpha = 0.55f),
                            topLeft = Offset(0f, bottomPx),
                            size = Size(w, h - bottomPx)
                        )

                        // 2. Draw active crop box border
                        drawRect(
                            color = Color(0xFF2196F3), // premium blue border
                            topLeft = Offset(leftPx, topPx),
                            size = Size(rightPx - leftPx, bottomPx - topPx),
                            style = Stroke(width = 2.dp.toPx())
                        )

                        // 3. Draw 3x3 grid lines
                        val gridW = (rightPx - leftPx) / 3f
                        val gridH = (bottomPx - topPx) / 3f

                        // Vertical grid lines
                        drawLine(
                            color = Color.White.copy(alpha = 0.5f),
                            start = Offset(leftPx + gridW, topPx),
                            end = Offset(leftPx + gridW, bottomPx),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.5f),
                            start = Offset(leftPx + 2 * gridW, topPx),
                            end = Offset(leftPx + 2 * gridW, bottomPx),
                            strokeWidth = 1.dp.toPx()
                        )
                        // Horizontal grid lines
                        drawLine(
                            color = Color.White.copy(alpha = 0.5f),
                            start = Offset(leftPx, topPx + gridH),
                            end = Offset(rightPx, topPx + gridH),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.5f),
                            start = Offset(leftPx, topPx + 2 * gridH),
                            end = Offset(rightPx, topPx + 2 * gridH),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Draggable Handles
                    val density = LocalDensity.current
                    val handleSizeDp = 24.dp
                    val handleSizePx = with(density) { handleSizeDp.toPx() }

                    // Top-Left handle
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (cropLeft * containerSize.width).roundToInt() - (handleSizePx / 2).roundToInt(),
                                    (cropTop * containerSize.height).roundToInt() - (handleSizePx / 2).roundToInt()
                                )
                            }
                            .size(handleSizeDp)
                            .background(Color.White, CircleShape)
                            .border(3.dp, Color(0xFF2196F3), CircleShape)
                            .pointerInput(containerSize) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val newLeft = (cropLeft + dragAmount.x / containerSize.width).coerceIn(0f, cropRight - 0.1f)
                                    val newTop = (cropTop + dragAmount.y / containerSize.height).coerceIn(0f, cropBottom - 0.1f)
                                    cropLeft = newLeft
                                    cropTop = newTop
                                }
                            }
                    )

                    // Top-Right handle
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (cropRight * containerSize.width).roundToInt() - (handleSizePx / 2).roundToInt(),
                                    (cropTop * containerSize.height).roundToInt() - (handleSizePx / 2).roundToInt()
                                )
                            }
                            .size(handleSizeDp)
                            .background(Color.White, CircleShape)
                            .border(3.dp, Color(0xFF2196F3), CircleShape)
                            .pointerInput(containerSize) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val newRight = (cropRight + dragAmount.x / containerSize.width).coerceIn(cropLeft + 0.1f, 1f)
                                    val newTop = (cropTop + dragAmount.y / containerSize.height).coerceIn(0f, cropBottom - 0.1f)
                                    cropRight = newRight
                                    cropTop = newTop
                                }
                            }
                    )

                    // Bottom-Left handle
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (cropLeft * containerSize.width).roundToInt() - (handleSizePx / 2).roundToInt(),
                                    (cropBottom * containerSize.height).roundToInt() - (handleSizePx / 2).roundToInt()
                                )
                            }
                            .size(handleSizeDp)
                            .background(Color.White, CircleShape)
                            .border(3.dp, Color(0xFF2196F3), CircleShape)
                            .pointerInput(containerSize) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val newLeft = (cropLeft + dragAmount.x / containerSize.width).coerceIn(0f, cropRight - 0.1f)
                                    val newBottom = (cropBottom + dragAmount.y / containerSize.height).coerceIn(cropTop + 0.1f, 1f)
                                    cropLeft = newLeft
                                    cropBottom = newBottom
                                }
                            }
                    )

                    // Bottom-Right handle
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (cropRight * containerSize.width).roundToInt() - (handleSizePx / 2).roundToInt(),
                                    (cropBottom * containerSize.height).roundToInt() - (handleSizePx / 2).roundToInt()
                                )
                            }
                            .size(handleSizeDp)
                            .background(Color.White, CircleShape)
                            .border(3.dp, Color(0xFF2196F3), CircleShape)
                            .pointerInput(containerSize) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val newRight = (cropRight + dragAmount.x / containerSize.width).coerceIn(cropLeft + 0.1f, 1f)
                                    val newBottom = (cropBottom + dragAmount.y / containerSize.height).coerceIn(cropTop + 0.1f, 1f)
                                    cropRight = newRight
                                    cropBottom = newBottom
                                }
                            }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Bottom Action buttons (Rotate, Smart Auto Crop, Reset)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Rotate 90
                FilledTonalButton(
                    onClick = {
                        val rotated = rotateBitmap90(currentBitmap)
                        currentBitmap = rotated
                        // reset crop coordinates to cover rotated image fully
                        cropLeft = 0f
                        cropTop = 0f
                        cropRight = 1f
                        cropBottom = 1f
                    },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.RotateRight, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Rotate")
                }

                // Smart Auto Crop
                FilledTonalButton(
                    onClick = {
                        val bounds = findSignatureBounds(currentBitmap)
                        cropLeft = bounds.left.toFloat() / currentBitmap.width
                        cropTop = bounds.top.toFloat() / currentBitmap.height
                        cropRight = bounds.right.toFloat() / currentBitmap.width
                        cropBottom = bounds.bottom.toFloat() / currentBitmap.height
                    },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.Crop, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Auto Crop")
                }

                // Reset
                FilledTonalButton(
                    onClick = {
                        currentBitmap = bitmap
                        cropLeft = 0f
                        cropTop = 0f
                        cropRight = 1f
                        cropBottom = 1f
                    },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reset")
                }
            }
        }
    }
}

private fun findSignatureBounds(bitmap: Bitmap): android.graphics.Rect {
    var minX = bitmap.width
    var minY = bitmap.height
    var maxX = -1
    var maxY = -1

    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

    for (y in 0 until bitmap.height) {
        for (x in 0 until bitmap.width) {
            val pixel = pixels[y * bitmap.width + x]
            val alpha = android.graphics.Color.alpha(pixel)
            if (alpha > 10) {
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
            }
        }
    }

    return if (maxX < minX || maxY < minY) {
        android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
    } else {
        val pad = 15
        android.graphics.Rect(
            (minX - pad).coerceAtLeast(0),
            (minY - pad).coerceAtLeast(0),
            (maxX + pad).coerceAtMost(bitmap.width - 1),
            (maxY + pad).coerceAtMost(bitmap.height - 1)
        )
    }
}

private fun rotateBitmap90(bitmap: Bitmap): Bitmap {
    val matrix = android.graphics.Matrix().apply { postRotate(90f) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

@Composable
private fun SavedTab(
    savedSignatures: List<SignatureEntity>,
    onDelete: (SignatureEntity) -> Unit,
    onSelect: (SignatureEntity) -> Unit,
) {
    if (savedSignatures.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "No saved signatures yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Draw or import a signature, crop it, and save it. It will appear here for one-click reuse!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(savedSignatures) { signature ->
                SavedSignatureCard(
                    signature = signature,
                    onDelete = { onDelete(signature) },
                    onClick = { onSelect(signature) }
                )
            }
        }
    }
}

@Composable
private fun SavedSignatureCard(
    signature: SignatureEntity,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    var bitmap by remember(signature.imagePath) { mutableStateOf<Bitmap?>(null) }
    
    androidx.compose.runtime.LaunchedEffect(signature.imagePath) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(signature.imagePath)
                if (file.exists()) {
                    bitmap = android.graphics.BitmapFactory.decodeFile(signature.imagePath)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
    ) {
        CheckerboardBackground(modifier = Modifier.fillMaxSize())

        bitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Saved signature",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
        }

        if (bitmap == null) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.Center)
            )
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(28.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun CheckerboardBackground(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val squareSize = 8.dp.toPx()
        val numX = (size.width / squareSize).toInt() + 1
        val numY = (size.height / squareSize).toInt() + 1
        for (i in 0 until numX) {
            for (j in 0 until numY) {
                if ((i + j) % 2 == 0) {
                    drawRect(
                        color = Color(0xFFE8E8E8),
                        topLeft = Offset(i * squareSize, j * squareSize),
                        size = Size(squareSize, squareSize)
                    )
                } else {
                    drawRect(
                        color = Color(0xFFFBFBFB),
                        topLeft = Offset(i * squareSize, j * squareSize),
                        size = Size(squareSize, squareSize)
                    )
                }
            }
        }
    }
}

