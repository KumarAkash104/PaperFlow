package com.paperflow.app.ui.editor

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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.paperflow.app.pdf.PdfEditOverlay
import com.paperflow.app.pdf.PdfTextBlock
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

    // Track current visible page for "Add Text"
    val listState = rememberLazyListState()
    var currentVisiblePage by remember { mutableStateOf(0) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { page ->
            currentVisiblePage = page
        }
    }

    // Observe saved state to automatically exit
    LaunchedEffect(uiState.savedFilePath) {
        uiState.savedFilePath?.let {
            viewModel.clearSavedState()
            onBack()
        }
    }

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
                        if (uiState.pages.isNotEmpty()) {
                            Text(
                                "${uiState.overlays.size} edits",
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
                    if (uiState.overlays.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.saveEdits() },
                            enabled = !uiState.isSaving && !uiState.isProcessing
                        ) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Save, "Save PDF")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ),
            )
        },
        floatingActionButton = {
            if (!uiState.isProcessing && uiState.pages.isNotEmpty() && uiState.selectedOverlayId == null) {
                FloatingActionButton(
                    onClick = {
                        viewModel.addCustomTextOverlay(
                            pageIndex = currentVisiblePage.coerceIn(0, (uiState.pages.size - 1).coerceAtLeast(0)),
                            xFraction = 0.1f,
                            yFraction = 0.3f
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, "Add Text", modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add Text", fontWeight = FontWeight.Medium)
                    }
                }
            }
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
                    CircularProgressIndicator(strokeWidth = 3.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Analyzing PDF…",
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
            } else {
                // ── Zoomable content ──
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        }
                        // Pinch-to-zoom + single-finger pan when zoomed
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val pointerCount = event.changes.count { it.pressed }
                                    if (pointerCount >= 2) {
                                        val zoom = event.calculateZoom()
                                        val pan = event.calculatePan()
                                        scale = (scale * zoom).coerceIn(1f, 5f)
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
                                    } else if (pointerCount == 1 && scale > 1.05f) {
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
                        // Double-tap zoom toggle
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = { tapOffset ->
                                    if (scale > 1.1f) {
                                        scope.launch {
                                            val startScale = scale
                                            val startOffsetX = offsetX
                                            val startOffsetY = offsetY
                                            androidx.compose.animation.core.animate(0f, 1f, animationSpec = androidx.compose.animation.core.tween(300)) { value, _ ->
                                                scale = startScale + (1f - startScale) * value
                                                offsetX = startOffsetX + (0f - startOffsetX) * value
                                                offsetY = startOffsetY + (0f - startOffsetY) * value
                                            }
                                        }
                                    } else {
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
                                            androidx.compose.animation.core.animate(0f, 1f, animationSpec = androidx.compose.animation.core.tween(300)) { value, _ ->
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
                    // Pages list
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp, start = 8.dp, end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        userScrollEnabled = scale <= 1.05f
                    ) {
                        items(uiState.pages) { page ->
                            EditorPageContainer(
                                page = page,
                                extractedBlocks = uiState.extractedBlocks,
                                overlays = uiState.overlays,
                                selectedOverlayId = uiState.selectedOverlayId,
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }

            // ── Compact Edit Toolbar ──
            AnimatedVisibility(
                visible = uiState.selectedOverlayId != null,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val selectedOverlay = uiState.overlays.find { it.id == uiState.selectedOverlayId }
                if (selectedOverlay != null) {
                    CompactEditToolbar(
                        overlay = selectedOverlay,
                        onUpdate = { viewModel.updateSelectedOverlay(it) },
                        onDelete = { viewModel.deleteSelectedOverlay() },
                        onClose = { viewModel.selectOverlay(null) }
                    )
                }
            }
        }
    }
}

@Composable
fun EditorPageContainer(
    page: PdfPageData,
    extractedBlocks: List<PdfTextBlock>,
    overlays: List<PdfEditOverlay>,
    selectedOverlayId: String?,
    viewModel: PdfEditorViewModel
) {
    val density = LocalDensity.current

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val pageAspectRatio = page.width / page.height
            val boxW = maxWidth
            val boxH = boxW / pageAspectRatio

            Box(modifier = Modifier.size(boxW, boxH)) {
                // ── Page image ──
                AsyncImage(
                    model = File(page.imagePath),
                    contentDescription = "Page ${page.index + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                // ── Tap handler for selecting/creating overlays ──
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(page.index) {
                            detectTapGestures { offset ->
                                val clickXFr = offset.x / size.width.toFloat()
                                val clickYFr = offset.y / size.height.toFloat()

                                // 1. Check overlays first
                                val tappedOverlay = overlays
                                    .filter { it.pageIndex == page.index }
                                    .find { o ->
                                        clickXFr >= o.xFraction - 0.02f && clickXFr <= o.xFraction + o.widthFraction + 0.02f &&
                                        clickYFr >= o.yFraction - 0.02f && clickYFr <= o.yFraction + o.heightFraction + 0.02f
                                    }
                                if (tappedOverlay != null) {
                                    viewModel.selectOverlay(tappedOverlay.id)
                                    return@detectTapGestures
                                }

                                // 2. Find nearest extracted text block (expanded tap area)
                                val clickedBlock = extractedBlocks
                                    .filter { it.pageIndex == page.index }
                                    .minByOrNull { block ->
                                        val bxCenter = (block.x + block.width / 2f) / page.width
                                        val byCenter = (block.y + block.height / 2f) / page.height
                                        val dx = clickXFr - bxCenter
                                        val dy = clickYFr - byCenter
                                        dx * dx + dy * dy
                                    }?.takeIf { block ->
                                        val bxCenter = (block.x + block.width / 2f) / page.width
                                        val byCenter = (block.y + block.height / 2f) / page.height
                                        val dx = clickXFr - bxCenter
                                        val dy = clickYFr - byCenter
                                        // Within ~5% radius
                                        dx * dx + dy * dy < 0.003f
                                    }

                                if (clickedBlock != null) {
                                    viewModel.handleTextTapped(page.index, clickedBlock)
                                } else {
                                    viewModel.selectOverlay(null)
                                }
                            }
                        }
                )

                // ── Render overlays (whiteout + text) ──
                val pageOverlays = overlays.filter { it.pageIndex == page.index }
                pageOverlays.forEach { overlay ->
                    val leftDp = (overlay.xFraction * boxW.value).dp
                    val topDp = (overlay.yFraction * boxH.value).dp
                    val widthDp = (overlay.widthFraction * boxW.value).dp
                    val heightDp = (overlay.heightFraction * boxH.value).dp

                    // Whiteout
                    if (overlay.whiteout) {
                        Box(
                            modifier = Modifier
                                .offset(leftDp - 1.dp, topDp - 1.dp)
                                .size(widthDp + 2.dp, heightDp + 2.dp)
                                .background(Color.White)
                        )
                    }

                    val isSelected = overlay.id == selectedOverlayId

                    // Draggable text overlay
                    Box(
                        modifier = Modifier
                            .offset(leftDp, topDp)
                            .size(widthDp, heightDp)
                            .then(
                                if (isSelected) Modifier.border(
                                    1.5.dp,
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(3.dp)
                                ) else Modifier
                            )
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                                else Color.Transparent
                            )
                            .clickable { viewModel.selectOverlay(overlay.id) }
                            .pointerInput(overlay.id) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    // Convert drag pixels to fraction of page
                                    val dxFr = dragAmount.x / (with(density) { boxW.toPx() })
                                    val dyFr = dragAmount.y / (with(density) { boxH.toPx() })
                                    viewModel.updateOverlayById { o ->
                                        if (o.id == overlay.id) {
                                            o.copy(
                                                xFraction = (o.xFraction + dxFr).coerceIn(0f, 1f - o.widthFraction),
                                                yFraction = (o.yFraction + dyFr).coerceIn(0f, 1f - o.heightFraction)
                                            )
                                        } else o
                                    }
                                    viewModel.selectOverlay(overlay.id)
                                }
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        val fontFamily = when (overlay.fontName.lowercase()) {
                            "serif" -> FontFamily.Serif
                            "monospace" -> FontFamily.Monospace
                            else -> FontFamily.SansSerif
                        }
                        Text(
                            text = overlay.text,
                            color = Color(overlay.color),
                            fontSize = (overlay.fontSize * (boxH.value / page.height)).sp,
                            fontFamily = fontFamily,
                            fontWeight = if (overlay.isBold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (overlay.isItalic) FontStyle.Italic else FontStyle.Normal,
                            modifier = Modifier.padding(horizontal = 1.dp),
                            maxLines = 3
                        )
                    }
                }

                // ── Page number badge ──
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        "Page ${page.index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Compact edit toolbar — clean UI with clear Done/Delete semantics. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompactEditToolbar(
    overlay: PdfEditOverlay,
    onUpdate: ((PdfEditOverlay) -> PdfEditOverlay) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // ── Row 1: Text input + Done ✓ + Delete 🗑 ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = overlay.text,
                    onValueChange = { newText -> onUpdate { it.copy(text = newText) } },
                    placeholder = { Text("Edit text…") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.width(8.dp))

                // ✓ Done — confirms edit and closes toolbar
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Default.Check, "Done",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(6.dp))

                // 🗑 Delete — removes the text overlay
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(34.dp)
                        .background(
                            MaterialTheme.colorScheme.errorContainer,
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Default.Delete, "Delete",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Row 2: Bold + Italic + Font + Size ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Bold
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (overlay.isBold) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { onUpdate { it.copy(isBold = !it.isBold) } },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.FormatBold, null,
                        tint = if (overlay.isBold) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Italic
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (overlay.isItalic) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { onUpdate { it.copy(isItalic = !it.isItalic) } },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.FormatItalic, null,
                        tint = if (overlay.isItalic) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Font dropdown
                var showFontMenu by remember { mutableStateOf(false) }
                Box {
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { showFontMenu = true }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            overlay.fontName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(expanded = showFontMenu, onDismissRequest = { showFontMenu = false }) {
                        listOf("Sans" to "Sans-Serif", "Serif" to "Serif", "Monospace" to "Mono").forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { onUpdate { it.copy(fontName = key) }; showFontMenu = false },
                                trailingIcon = if (overlay.fontName == key) {
                                    { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // Font size
                Text(
                    "${overlay.fontSize.toInt()}pt",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(28.dp),
                    textAlign = TextAlign.Center
                )
                Slider(
                    value = overlay.fontSize,
                    onValueChange = { onUpdate { o -> o.copy(fontSize = it) } },
                    valueRange = 6f..72f,
                    modifier = Modifier.width(100.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            Spacer(Modifier.height(10.dp))

            // ── Row 3: Erase toggle + Color palette ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Erase old text toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (overlay.whiteout) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { onUpdate { o -> o.copy(whiteout = !o.whiteout) } }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        "Erase",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (overlay.whiteout) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (overlay.whiteout) FontWeight.SemiBold else FontWeight.Normal
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Color palette
                val colors = listOf(
                    0xFF000000L, 0xFF444444L, 0xFF1976D2L, 0xFFD32F2FL,
                    0xFF388E3CL, 0xFFF57C00L, 0xFF7B1FA2L
                )
                colors.forEach { colorVal ->
                    val color = Color(colorVal)
                    val isColorSelected = overlay.color == colorVal.toInt()
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (isColorSelected) 26.dp else 22.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (isColorSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                else Modifier.border(0.5.dp, Color.LightGray, CircleShape)
                            )
                            .clickable { onUpdate { it.copy(color = colorVal.toInt()) } }
                    )
                }
            }
        }
    }
}

