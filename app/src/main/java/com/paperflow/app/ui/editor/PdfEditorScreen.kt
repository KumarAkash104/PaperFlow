package com.paperflow.app.ui.editor

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Size
import com.paperflow.app.pdf.OverlayType
import com.paperflow.app.pdf.PdfEditOverlay
import com.paperflow.app.pdf.PdfTextBlock
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfEditorScreen(
    viewModel: PdfEditorViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Zoom and pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Modal page grid drawer
    var showPageGridDrawer by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Observe saved state to display confirmation and redirect to Documents screen
    LaunchedEffect(uiState.savedFilePath) {
        uiState.savedFilePath?.let {
            Toast.makeText(context, "Edited PDF saved to Documents!", Toast.LENGTH_SHORT).show()
            viewModel.clearSavedState()
            onBack()
        }
    }

    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()

    val activeMeta = uiState.pagesMeta.getOrNull(uiState.activePageIndex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            uiState.pdfTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        if (uiState.totalPageCount > 0) {
                            Text(
                                "Page ${uiState.activePageIndex + 1} of ${uiState.totalPageCount} • ${uiState.overlays.size} edits",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = canUndo
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            "Undo",
                            tint = if (canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = canRedo
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Redo,
                            "Redo",
                            tint = if (canRedo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.saveEdits() },
                        enabled = !uiState.isSaving && !uiState.isProcessing
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Save, "Save PDF", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isProcessing) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(strokeWidth = 2.5.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Loading PDF…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (uiState.error != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        uiState.error ?: "An unexpected error occurred",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else if (activeMeta != null) {
                // ── Zoomable Active Page Canvas ──
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val pointerCount = event.changes.count { it.pressed }
                                    if (pointerCount >= 2) {
                                        // Pinch-to-zoom
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        scale = (scale * zoomChange).coerceIn(0.8f, 4f)
                                        if (scale > 1f) {
                                            offsetX += panChange.x
                                            offsetY += panChange.y
                                        } else {
                                            offsetX = 0f
                                            offsetY = 0f
                                        }
                                        event.changes.forEach { it.consume() }
                                    } else if (pointerCount == 1 && scale > 1.05f && uiState.selectedOverlayId == null) {
                                        // Single finger pan when zoomed in (only when no overlay box is selected)
                                        val panChange = event.calculatePan()
                                        offsetX += panChange.x
                                        offsetY += panChange.y
                                        event.changes.forEach { it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                        .clickable { viewModel.selectOverlay(null) },
                    contentAlignment = Alignment.Center
                ) {
                    ActivePageCanvas(
                        viewModel = viewModel,
                        pageMeta = activeMeta,
                        activePageBlocks = uiState.activePageBlocks,
                        overlays = uiState.overlays.filter { it.pageIndex == activeMeta.index },
                        selectedOverlayId = uiState.selectedOverlayId,
                        zoomScale = scale,
                        onSelectOverlay = { id -> viewModel.selectOverlay(id) },
                        onTapTextBlock = { block -> viewModel.handleTextTapped(activeMeta.index, block) },
                        onUpdateOverlay = { updateBlock -> viewModel.updateSelectedOverlay(updateBlock) }
                    )
                }

                // ── Floating Overlay Controls (When an item is selected) ──
                AnimatedVisibility(
                    visible = uiState.selectedOverlayId != null,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                ) {
                    val selected = uiState.overlays.find { it.id == uiState.selectedOverlayId }
                    if (selected != null) {
                        SelectedOverlayControlBar(
                            overlay = selected,
                            onUpdate = { updateBlock -> viewModel.updateSelectedOverlay(updateBlock) },
                            onDelete = { viewModel.deleteSelectedOverlay() },
                            onDeselect = { viewModel.selectOverlay(null) }
                        )
                    }
                }

                // ── Bottom Glassmorphic Action Bar ──
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .navigationBarsPadding(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    EditorBottomActionBar(
                        onOpenPageGrid = { showPageGridDrawer = true },
                        onAddText = { viewModel.addCustomTextOverlay(uiState.activePageIndex) },
                        onAddWhiteout = { viewModel.addWhiteoutMaskOverlay(uiState.activePageIndex) },
                        onRotatePage = { viewModel.rotatePage(uiState.activePageIndex) },
                        onDeletePage = { viewModel.deletePage(uiState.activePageIndex) },
                        canDeletePage = uiState.totalPageCount > 1
                    )
                }
            }

            // ── Modal Page Grid Drawer ──
            if (showPageGridDrawer) {
                ModalBottomSheet(
                    onDismissRequest = { showPageGridDrawer = false },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    PageGridDrawerContent(
                        viewModel = viewModel,
                        pagesMeta = uiState.pagesMeta,
                        activePageIndex = uiState.activePageIndex,
                        onSelectPage = { index ->
                            viewModel.setActivePage(index)
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                showPageGridDrawer = false
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Interactive canvas displaying high-res page image, text bounding boxes, and editable overlays.
 */
@Composable
fun ActivePageCanvas(
    viewModel: PdfEditorViewModel,
    pageMeta: PdfPageMeta,
    activePageBlocks: List<PdfTextBlock>,
    overlays: List<PdfEditOverlay>,
    selectedOverlayId: String?,
    zoomScale: Float = 1f,
    onSelectOverlay: (String?) -> Unit,
    onTapTextBlock: (PdfTextBlock) -> Unit,
    onUpdateOverlay: ((PdfEditOverlay) -> PdfEditOverlay) -> Unit
) {
    var highResPath by remember(pageMeta.index) { mutableStateOf<String?>(null) }
    var isLoading by remember(pageMeta.index) { mutableStateOf(true) }

    LaunchedEffect(pageMeta.index) {
        isLoading = true
        highResPath = viewModel.getPageHighResPath(pageMeta.index)
        isLoading = false
    }

    BoxWithConstraints(
        modifier = Modifier
            .padding(16.dp)
            .graphicsLayer { rotationZ = pageMeta.rotation.toFloat() },
        contentAlignment = Alignment.Center
    ) {
        val aspect = pageMeta.width / pageMeta.height.coerceAtLeast(1f)
        val containerWidth = maxWidth
        val containerHeight = containerWidth / aspect

        Box(
            modifier = Modifier
                .size(containerWidth, containerHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
        ) {
            // Page Image
            if (highResPath != null) {
                val currentContext = LocalContext.current
                val editorImageRequest = remember<ImageRequest>(highResPath) {
                    ImageRequest.Builder(currentContext)
                        .data(File(highResPath!!))
                        .size(Size.ORIGINAL)
                        .precision(Precision.EXACT)
                        .build()
                }
                AsyncImage(
                    model = editorImageRequest,
                    contentDescription = "Page ${pageMeta.index + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
                }
            }

            // Tappable Text Bounding Boxes (for existing PDF text blocks)
            activePageBlocks.forEach { block ->
                val xPx = (block.x / pageMeta.width) * containerWidth.value
                val yPx = (block.y / pageMeta.height) * containerHeight.value
                val wPx = (block.width / pageMeta.width) * containerWidth.value
                val hPx = (block.height / pageMeta.height) * containerHeight.value

                Box(
                    modifier = Modifier
                        .offset { IntOffset(xPx.dp.roundToPx(), yPx.dp.roundToPx()) }
                        .size(wPx.dp, hPx.dp)
                        .border(1.dp, Color(0xFF2196F3).copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                        .clickable { onTapTextBlock(block) }
                )
            }

            // Render Overlays (Text edit, whiteout, signature, stamp)
            overlays.forEach { overlay ->
                val isSelected = overlay.id == selectedOverlayId
                val xPx = overlay.xFraction * containerWidth.value
                val yPx = overlay.yFraction * containerHeight.value
                val wPx = overlay.widthFraction * containerWidth.value
                val hPx = overlay.heightFraction * containerHeight.value

                Box(
                    modifier = Modifier
                        .offset { IntOffset(xPx.dp.roundToPx(), yPx.dp.roundToPx()) }
                        .size(wPx.dp, hPx.dp)
                        .pointerInput(overlay.id, containerWidth.value, containerHeight.value, zoomScale) {
                            detectDragGestures(
                                onDragStart = { onSelectOverlay(overlay.id) },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val safeScale = zoomScale.coerceAtLeast(0.1f)
                                    val dxFraction = (dragAmount.x / safeScale) / containerWidth.value
                                    val dyFraction = (dragAmount.y / safeScale) / containerHeight.value
                                    onUpdateOverlay { current ->
                                        if (current.id == overlay.id) {
                                            current.copy(
                                                xFraction = (current.xFraction + dxFraction).coerceIn(0f, (1f - current.widthFraction).coerceAtLeast(0f)),
                                                yFraction = (current.yFraction + dyFraction).coerceIn(0f, (1f - current.heightFraction).coerceAtLeast(0f))
                                            )
                                        } else current
                                    }
                                }
                            )
                        }
                        .clickable { onSelectOverlay(overlay.id) }
                        .then(
                            if (isSelected) Modifier.border(1.5.dp, Color(0xFF1E88E5), RoundedCornerShape(4.dp))
                            else Modifier
                        )
                ) {
                    // Whiteout mask layer
                    if (overlay.whiteout) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(overlay.color))
                        )
                    }

                    // Content layer
                    when (overlay.type) {
                        OverlayType.TEXT_EDIT, OverlayType.CUSTOM_TEXT -> {
                            if (isSelected) {
                                BasicTextField(
                                    value = overlay.text,
                                    onValueChange = { newText ->
                                        onUpdateOverlay { current ->
                                            if (current.id == overlay.id) current.copy(text = newText) else current
                                        }
                                    },
                                    textStyle = TextStyle(
                                        fontSize = (overlay.fontSize * (containerWidth.value / pageMeta.width)).sp,
                                        fontWeight = if (overlay.isBold) FontWeight.Bold else FontWeight.Normal,
                                        fontStyle = if (overlay.isItalic) FontStyle.Italic else FontStyle.Normal,
                                        fontFamily = when (overlay.fontName) {
                                            "Serif" -> FontFamily.Serif
                                            "Monospace" -> FontFamily.Monospace
                                            else -> FontFamily.SansSerif
                                        },
                                        color = Color(overlay.color)
                                    ),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(2.dp)
                                )
                            } else {
                                Text(
                                    text = overlay.text,
                                    fontSize = (overlay.fontSize * (containerWidth.value / pageMeta.width)).sp,
                                    fontWeight = if (overlay.isBold) FontWeight.Bold else FontWeight.Normal,
                                    fontStyle = if (overlay.isItalic) FontStyle.Italic else FontStyle.Normal,
                                    fontFamily = when (overlay.fontName) {
                                        "Serif" -> FontFamily.Serif
                                        "Monospace" -> FontFamily.Monospace
                                        else -> FontFamily.SansSerif
                                    },
                                    color = Color(overlay.color),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(2.dp)
                                )
                            }
                        }
                        OverlayType.SIGNATURE, OverlayType.IMAGE_STAMP -> {
                            if (overlay.imagePath != null && File(overlay.imagePath).exists()) {
                                AsyncImage(
                                    model = File(overlay.imagePath),
                                    contentDescription = "Overlay Image",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        OverlayType.WHITEOUT_MASK -> {
                            // Already rendered whiteout background box above
                        }
                    }

                    // Interactive Corner Resize Drag Handle (When Selected)
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 6.dp, y = 6.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E88E5))
                                .border(1.5.dp, Color.White, CircleShape)
                                .pointerInput(overlay.id, containerWidth.value, containerHeight.value, zoomScale) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val safeScale = zoomScale.coerceAtLeast(0.1f)
                                        val dwFraction = (dragAmount.x / safeScale) / containerWidth.value
                                        val dhFraction = (dragAmount.y / safeScale) / containerHeight.value
                                        onUpdateOverlay { current ->
                                            if (current.id == overlay.id) {
                                                current.copy(
                                                    widthFraction = (current.widthFraction + dwFraction).coerceIn(0.02f, 1f - current.xFraction),
                                                    heightFraction = (current.heightFraction + dhFraction).coerceIn(0.01f, 1f - current.yFraction)
                                                )
                                            } else current
                                        }
                                    }
                                }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Floating bar when an overlay is selected to edit text, size, color, or delete.
 */
@Composable
fun SelectedOverlayControlBar(
    overlay: PdfEditOverlay,
    onUpdate: ((PdfEditOverlay) -> PdfEditOverlay) -> Unit,
    onDelete: () -> Unit,
    onDeselect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (overlay.type == OverlayType.TEXT_EDIT || overlay.type == OverlayType.CUSTOM_TEXT) {
            OutlinedTextField(
                value = overlay.text,
                onValueChange = { newText -> onUpdate { it.copy(text = newText) } },
                label = { Text("Edit Text") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (overlay.type == OverlayType.TEXT_EDIT || overlay.type == OverlayType.CUSTOM_TEXT) {
                // Bold toggle
                IconButton(
                    onClick = { onUpdate { it.copy(isBold = !it.isBold) } },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.FormatBold,
                        "Bold",
                        tint = if (overlay.isBold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                // Italic toggle
                IconButton(
                    onClick = { onUpdate { it.copy(isItalic = !it.isItalic) } },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.FormatItalic,
                        "Italic",
                        tint = if (overlay.isItalic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                // Font size controls
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onUpdate { it.copy(fontSize = (it.fontSize - 1f).coerceAtLeast(8f)) } },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("-", fontWeight = FontWeight.Bold)
                    }
                    Text("${overlay.fontSize.roundToInt()}pt", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    IconButton(
                        onClick = { onUpdate { it.copy(fontSize = (it.fontSize + 1f).coerceAtMost(48f)) } },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("+", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (overlay.type == OverlayType.WHITEOUT_MASK || overlay.type == OverlayType.SIGNATURE || overlay.type == OverlayType.IMAGE_STAMP) {
                // Dimension fine-tuning controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Width:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(
                        onClick = { onUpdate { it.copy(widthFraction = (it.widthFraction - 0.02f).coerceAtLeast(0.02f)) } },
                        modifier = Modifier.size(28.dp)
                    ) { Text("-", fontWeight = FontWeight.Bold) }
                    IconButton(
                        onClick = { onUpdate { it.copy(widthFraction = (it.widthFraction + 0.02f).coerceAtMost(1f - it.xFraction)) } },
                        modifier = Modifier.size(28.dp)
                    ) { Text("+", fontWeight = FontWeight.Bold) }

                    Spacer(Modifier.width(6.dp))

                    Text("Height:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(
                        onClick = { onUpdate { it.copy(heightFraction = (it.heightFraction - 0.01f).coerceAtLeast(0.01f)) } },
                        modifier = Modifier.size(28.dp)
                    ) { Text("-", fontWeight = FontWeight.Bold) }
                    IconButton(
                        onClick = { onUpdate { it.copy(heightFraction = (it.heightFraction + 0.01f).coerceAtMost(1f - it.yFraction)) } },
                        modifier = Modifier.size(28.dp)
                    ) { Text("+", fontWeight = FontWeight.Bold) }
                }
            }

            Spacer(Modifier.weight(1f))

            // Delete Overlay
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, "Delete Overlay", tint = Color.Red.copy(alpha = 0.8f))
            }

            // Deselect / Done
            IconButton(onClick = onDeselect, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Check, "Done", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/**
 * Bottom glassmorphic dock with targeted editing action buttons.
 */
@Composable
fun EditorBottomActionBar(
    onOpenPageGrid: () -> Unit,
    onAddText: () -> Unit,
    onAddWhiteout: () -> Unit,
    onRotatePage: () -> Unit,
    onDeletePage: () -> Unit,
    canDeletePage: Boolean
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Page Grid Selector
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onOpenPageGrid() }.padding(4.dp)) {
            Icon(Icons.Default.GridView, "Pages", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            Text("Pages", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
        }

        // Add Text
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onAddText() }.padding(4.dp)) {
            Icon(Icons.Default.TextFields, "Add Text", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            Text("Add Text", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
        }

        // Whiteout Mask (Redact Box)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onAddWhiteout() }.padding(4.dp)) {
            Icon(Icons.Default.CropSquare, "Whiteout Box", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            Text("Whiteout", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
        }

        // Rotate Page
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onRotatePage() }.padding(4.dp)) {
            Icon(Icons.Default.RotateRight, "Rotate", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            Text("Rotate", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
        }

        // Delete Page
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(enabled = canDeletePage) { onDeletePage() }.padding(4.dp)
        ) {
            Icon(
                Icons.Default.Delete,
                "Delete",
                tint = if (canDeletePage) Color.Red.copy(alpha = 0.8f) else Color.Gray.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
            Text("Delete", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = if (canDeletePage) Color.Red else Color.Gray)
        }
    }
}

/**
 * Modal bottom sheet presenting a 3-column thumbnail grid of all document pages for 1-tap switching.
 */
@Composable
fun PageGridDrawerContent(
    viewModel: PdfEditorViewModel,
    pagesMeta: List<PdfPageMeta>,
    activePageIndex: Int,
    onSelectPage: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Select Page",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${pagesMeta.size} pages",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
        ) {
            items(pagesMeta) { pageMeta ->
                val isCurrent = pageMeta.index == activePageIndex
                var thumbPath by remember(pageMeta.index) { mutableStateOf<String?>(null) }

                LaunchedEffect(pageMeta.index) {
                    thumbPath = viewModel.getPageThumbnailPath(pageMeta.index)
                }

                Box(
                    modifier = Modifier
                        .aspectRatio(0.75f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(
                            if (isCurrent) 2.5.dp else 1.dp,
                            if (isCurrent) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.6f),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelectPage(pageMeta.index) },
                    contentAlignment = Alignment.Center
                ) {
                    if (thumbPath != null) {
                        AsyncImage(
                            model = File(thumbPath!!),
                            contentDescription = "Page ${pageMeta.index + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { rotationZ = pageMeta.rotation.toFloat() }
                        )
                    } else {
                        CircularProgressIndicator(strokeWidth = 1.5.dp, modifier = Modifier.size(20.dp))
                    }

                    // Page number badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "${pageMeta.index + 1}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
