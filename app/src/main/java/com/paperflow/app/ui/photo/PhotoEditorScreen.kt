package com.paperflow.app.ui.photo

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paperflow.app.util.AdjustmentValues
import com.paperflow.app.util.ImageFilters

// ── Premium Color Palette ──
private val EditorBg = Color(0xFF0A0A0F)
private val EditorSurface = Color(0xFF12121A)
private val EditorPanel = Color(0xFF16161F)
private val EditorCardBg = Color(0xFF1C1C28)
private val AccentTeal = Color(0xFF22C7B8)
private val AccentTealDim = Color(0xFF158F85)
private val AccentGlow = Color(0xFF22C7B8).copy(alpha = 0.25f)
private val TextPrimary = Color(0xFFE8ECEF)
private val TextSecondary = Color(0xFF8B9DA7)
private val TextMuted = Color(0xFF5A6A72)
private val DividerColor = Color.White.copy(alpha = 0.06f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditorScreen(
    viewModel: PhotoEditorViewModel,
    onBack: () -> Unit,
    onConvertedToPdf: (String) -> Unit = {},
    onResizeClick: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val bitmap by viewModel.displayBitmap.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val filterPreviews by viewModel.filterPreviews.collectAsState()
    val adjustmentValues by viewModel.adjustmentValues.collectAsState()

    var activeCategory by remember { mutableStateOf("All") }
    var selectedCropRatio by remember { mutableStateOf<Float?>(null) } // null means free/custom

    var containerWidth by remember { mutableStateOf(0f) }
    var containerHeight by remember { mutableStateOf(0f) }
    
    var showResizeConfirmDialog by remember { mutableStateOf(false) }
    
    // Crop overlay state (normalized coordinates 0.0 - 1.0)
    var cropLeft by remember { mutableStateOf(0.1f) }
    var cropTop by remember { mutableStateOf(0.1f) }
    var cropRight by remember { mutableStateOf(0.9f) }
    var cropBottom by remember { mutableStateOf(0.9f) }

    // Reset crop rect when entering crop mode or changing ratio
    LaunchedEffect(state.editorMode, selectedCropRatio) {
        if (state.editorMode == PhotoEditorMode.CROP && bitmap != null) {
            val bmp = bitmap!!
            val bmpRatio = bmp.width.toFloat() / bmp.height.toFloat()
            val targetRatio = selectedCropRatio ?: bmpRatio
            
            if (targetRatio > bmpRatio) {
                // Wider than image
                cropLeft = 0.05f
                cropRight = 0.95f
                val heightPercent = (0.9f / targetRatio) * bmpRatio
                cropTop = 0.5f - heightPercent / 2f
                cropBottom = 0.5f + heightPercent / 2f
            } else {
                // Taller than image
                cropTop = 0.05f
                cropBottom = 0.95f
                val widthPercent = (0.9f * targetRatio) / bmpRatio
                cropLeft = 0.5f - widthPercent / 2f
                cropRight = 0.5f + widthPercent / 2f
            }
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(state.error) {
        state.error?.let { err ->
            android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) {
            viewModel.resetSuccess()
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(EditorBg, EditorSurface, EditorBg)
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ═══════════════════════════════════════════════
            // 1. PREMIUM TOP BAR
            // ═══════════════════════════════════════════════
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.06f), CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary, modifier = Modifier.size(20.dp))
                }

                Spacer(Modifier.width(12.dp))

                // Title
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Editor",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        state.photo?.title ?: "",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }

                // Revert to original
                IconButton(
                    onClick = { viewModel.revertToOriginal() },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.06f), CircleShape)
                ) {
                    Icon(Icons.Default.Restore, "Revert to original", tint = TextPrimary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))

                // Save button (accent)
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            brush = if (state.isSaving) Brush.horizontalGradient(listOf(AccentTealDim, AccentTealDim))
                            else Brush.horizontalGradient(listOf(AccentTeal, AccentTealDim))
                        )
                        .clickable(enabled = !state.isSaving) { viewModel.saveEdits() }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Save, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }
                }
            }

            // Thin accent line
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, AccentTeal.copy(alpha = 0.3f), Color.Transparent)
                        )
                    )
            )

            // ═══════════════════════════════════════════════
            // 2. IMAGE PREVIEW AREA
            // ═══════════════════════════════════════════════
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .onGloballyPositioned { coordinates ->
                        containerWidth = coordinates.size.width.toFloat()
                        containerHeight = coordinates.size.height.toFloat()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (state.isLoading && bitmap == null) {
                    CircularProgressIndicator(color = AccentTeal, modifier = Modifier.size(40.dp))
                }

                bitmap?.let { bmp ->
                    val bmpWidth = bmp.width.toFloat()
                    val bmpHeight = bmp.height.toFloat()
                    val bmpRatio = bmpWidth / bmpHeight
                    val containerRatio = if (containerHeight > 0) containerWidth / containerHeight else 1f

                    val fitWidth: Float
                    val fitHeight: Float
                    if (bmpRatio > containerRatio) {
                        fitWidth = containerWidth
                        fitHeight = if (bmpRatio > 0) containerWidth / bmpRatio else 0f
                    } else {
                        fitHeight = containerHeight
                        fitWidth = containerHeight * bmpRatio
                    }

                    val density = LocalDensity.current
                    val fitWidthDp = with(density) { fitWidth.toDp() }
                    val fitHeightDp = with(density) { fitHeight.toDp() }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        // Subtle checkerboard-like dark background
                        Box(
                            modifier = Modifier
                                .size(width = fitWidthDp, height = fitHeightDp)
                                .background(Color(0xFF0E0E14), RoundedCornerShape(8.dp))
                        )

                        androidx.compose.foundation.Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Editor Image",
                            modifier = Modifier
                                .size(width = fitWidthDp, height = fitHeightDp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )

                        // ── DRAGGABLE CROP OVERLAY ──
                        if (state.editorMode == PhotoEditorMode.CROP && containerWidth > 0 && containerHeight > 0) {
                            // Ensure bounds are safe
                            cropLeft = cropLeft.coerceIn(0f, 0.9f)
                            cropTop = cropTop.coerceIn(0f, 0.9f)
                            cropRight = cropRight.coerceIn(cropLeft + 0.1f, 1f)
                            cropBottom = cropBottom.coerceIn(cropTop + 0.1f, 1f)

                            val cropWidthPx = (cropRight - cropLeft) * fitWidth
                            val cropHeightPx = (cropBottom - cropTop) * fitHeight
                            
                            val cropLeftDp = with(density) { (cropLeft * fitWidth).toDp() }
                            val cropTopDp = with(density) { (cropTop * fitHeight).toDp() }
                            val cropWidthDp = with(density) { cropWidthPx.toDp() }
                            val cropHeightDp = with(density) { cropHeightPx.toDp() }

                            // Darken outside crop
                            Box(modifier = Modifier.size(fitWidthDp, fitHeightDp)) {
                                // Top
                                Box(Modifier.fillMaxWidth().height(cropTopDp).background(Color.Black.copy(alpha = 0.6f)))
                                // Bottom
                                Box(Modifier.fillMaxWidth().height(fitHeightDp - cropTopDp - cropHeightDp).align(Alignment.BottomCenter).background(Color.Black.copy(alpha = 0.6f)))
                                // Left
                                Box(Modifier.width(cropLeftDp).height(cropHeightDp).offset(y = cropTopDp).background(Color.Black.copy(alpha = 0.6f)))
                                // Right
                                Box(Modifier.width(fitWidthDp - cropLeftDp - cropWidthDp).height(cropHeightDp).align(Alignment.TopEnd).offset(y = cropTopDp).background(Color.Black.copy(alpha = 0.6f)))
                            }

                            // Crop Box
                            Box(
                                modifier = Modifier
                                    .size(width = cropWidthDp, height = cropHeightDp)
                                    .offset(
                                        x = cropLeftDp - (fitWidthDp / 2) + (cropWidthDp / 2),
                                        y = cropTopDp - (fitHeightDp / 2) + (cropHeightDp / 2)
                                    )
                                    .border(1.5.dp, AccentTeal, RoundedCornerShape(4.dp))
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val dxPercent = dragAmount.x / fitWidth
                                            val dyPercent = dragAmount.y / fitHeight
                                            
                                            // Moving the entire box
                                            val currentW = cropRight - cropLeft
                                            val currentH = cropBottom - cropTop
                                            var newL = cropLeft + dxPercent
                                            var newR = cropRight + dxPercent
                                            var newT = cropTop + dyPercent
                                            var newB = cropBottom + dyPercent
                                            
                                            if (newL < 0f) { newL = 0f; newR = currentW }
                                            if (newR > 1f) { newR = 1f; newL = 1f - currentW }
                                            if (newT < 0f) { newT = 0f; newB = currentH }
                                            if (newB > 1f) { newB = 1f; newT = 1f - currentH }
                                            
                                            cropLeft = newL
                                            cropRight = newR
                                            cropTop = newT
                                            cropBottom = newB
                                        }
                                    }
                            ) {
                                // Animated corner marks
                                val cornerSize = 20.dp
                                val cornerWeight = 3.dp
                                val hitSize = 40.dp
                                
                                // Top-left
                                Box(Modifier.size(cornerSize).border(cornerWeight, AccentTeal, RoundedCornerShape(topStart = 4.dp)).align(Alignment.TopStart))
                                Box(Modifier.size(hitSize).align(Alignment.TopStart).offset(x = (-20).dp, y = (-20).dp)
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            cropLeft = (cropLeft + dragAmount.x / fitWidth).coerceIn(0f, cropRight - 0.1f)
                                            cropTop = (cropTop + dragAmount.y / fitHeight).coerceIn(0f, cropBottom - 0.1f)
                                        }
                                    })
                                
                                // Top-right
                                Box(Modifier.size(cornerSize).border(cornerWeight, AccentTeal, RoundedCornerShape(topEnd = 4.dp)).align(Alignment.TopEnd))
                                Box(Modifier.size(hitSize).align(Alignment.TopEnd).offset(x = 20.dp, y = (-20).dp)
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            cropRight = (cropRight + dragAmount.x / fitWidth).coerceIn(cropLeft + 0.1f, 1f)
                                            cropTop = (cropTop + dragAmount.y / fitHeight).coerceIn(0f, cropBottom - 0.1f)
                                        }
                                    })
                                
                                // Bottom-left
                                Box(Modifier.size(cornerSize).border(cornerWeight, AccentTeal, RoundedCornerShape(bottomStart = 4.dp)).align(Alignment.BottomStart))
                                Box(Modifier.size(hitSize).align(Alignment.BottomStart).offset(x = (-20).dp, y = 20.dp)
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            cropLeft = (cropLeft + dragAmount.x / fitWidth).coerceIn(0f, cropRight - 0.1f)
                                            cropBottom = (cropBottom + dragAmount.y / fitHeight).coerceIn(cropTop + 0.1f, 1f)
                                        }
                                    })
                                
                                // Bottom-right
                                Box(Modifier.size(cornerSize).border(cornerWeight, AccentTeal, RoundedCornerShape(bottomEnd = 4.dp)).align(Alignment.BottomEnd))
                                Box(Modifier.size(hitSize).align(Alignment.BottomEnd).offset(x = 20.dp, y = 20.dp)
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            cropRight = (cropRight + dragAmount.x / fitWidth).coerceIn(cropLeft + 0.1f, 1f)
                                            cropBottom = (cropBottom + dragAmount.y / fitHeight).coerceIn(cropTop + 0.1f, 1f)
                                        }
                                    })

                                // Thirds grid
                                Box(Modifier.fillMaxWidth().height(0.5.dp).align(Alignment.CenterStart).offset(y = (-cropHeightDp / 6)).background(Color.White.copy(alpha = 0.15f)))
                                Box(Modifier.fillMaxWidth().height(0.5.dp).align(Alignment.CenterStart).offset(y = (cropHeightDp / 6)).background(Color.White.copy(alpha = 0.15f)))
                                Box(Modifier.fillMaxHeight().width(0.5.dp).align(Alignment.TopCenter).offset(x = (-cropWidthDp / 6)).background(Color.White.copy(alpha = 0.15f)))
                                Box(Modifier.fillMaxHeight().width(0.5.dp).align(Alignment.TopCenter).offset(x = (cropWidthDp / 6)).background(Color.White.copy(alpha = 0.15f)))
                            }
                        }

                        // Loading overlay
                        if (state.isLoading) {
                            Box(
                                modifier = Modifier
                                    .size(width = fitWidthDp, height = fitHeightDp)
                                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = AccentTeal, modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                            }
                        }
                    }

                    // ── FLOATING UNDO/REDO CAPSULE ──
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.undo() },
                            enabled = canUndo && !state.isSaving && !state.isLoading,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = if (canUndo) Color.White else Color.White.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
                        }
                        Box(Modifier.width(1.dp).height(16.dp).background(Color.White.copy(alpha = 0.2f)))
                        IconButton(
                            onClick = { viewModel.redo() },
                            enabled = canRedo && !state.isSaving && !state.isLoading,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Redo, "Redo", tint = if (canRedo) Color.White else Color.White.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════
            // 3. FLOATING PILL TAB SELECTOR
            // ═══════════════════════════════════════════════
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(EditorCardBg)
                        .border(1.dp, DividerColor, RoundedCornerShape(24.dp))
                        .padding(4.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        data class TabItem(val mode: PhotoEditorMode, val icon: @Composable () -> Unit, val label: String)
                        val tabs = listOf(
                            TabItem(PhotoEditorMode.FILTERS, { Icon(Icons.Default.FilterBAndW, null, modifier = Modifier.size(16.dp)) }, "Filters"),
                            TabItem(PhotoEditorMode.ADJUST, { Icon(Icons.Default.Tune, null, modifier = Modifier.size(16.dp)) }, "Adjust"),
                            TabItem(PhotoEditorMode.CROP, { Icon(Icons.Default.Crop, null, modifier = Modifier.size(16.dp)) }, "Crop"),
                            TabItem(PhotoEditorMode.ROTATE, { Icon(Icons.Default.RotateRight, null, modifier = Modifier.size(16.dp)) }, "Transform"),
                        )
                        tabs.forEach { tab ->
                            val isSelected = state.editorMode == tab.mode
                            val bgColor by animateColorAsState(
                                if (isSelected) AccentTeal else Color.Transparent,
                                animationSpec = tween(200), label = "tabBg"
                            )
                            val textColor by animateColorAsState(
                                if (isSelected) Color.White else TextSecondary,
                                animationSpec = tween(200), label = "tabText"
                            )

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(bgColor)
                                    .clickable { viewModel.setEditorMode(tab.mode) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CompositionLocalProvider(LocalContentColor provides textColor) {
                                        tab.icon()
                                    }
                                    if (isSelected) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(tab.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                                    }
                                }
                            }
                        }

                        // Resize shortcut
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    if (viewModel.hasUnsavedChanges()) {
                                        showResizeConfirmDialog = true
                                    } else {
                                        state.photo?.id?.let { photoId -> onResizeClick(photoId) }
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.AspectRatio, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════
            // 4. INTERACTIVE PANEL (based on mode)
            // ═══════════════════════════════════════════════
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(EditorPanel, EditorBg)),
                        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    )
                    .padding(bottom = 8.dp)
            ) {
                when (state.editorMode) {
                    // ── FILTERS ──
                    PhotoEditorMode.FILTERS -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Filter intensity slider
                            AnimatedVisibility(
                                visible = state.currentFilter != ImageFilters.Filter.ORIGINAL,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 8.dp)
                                ) {
                                    Text("Intensity", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.width(64.dp))
                                    Slider(
                                        value = state.filterIntensity,
                                        onValueChange = { viewModel.updateFilterIntensity(it) },
                                        onValueChangeFinished = { viewModel.commitFilterIntensity() },
                                        valueRange = 0f..1f,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(
                                            thumbColor = AccentTeal,
                                            activeTrackColor = AccentTeal,
                                            inactiveTrackColor = DividerColor
                                        ),
                                        track = { sliderState ->
                                            SliderDefaults.Track(
                                                sliderState = sliderState,
                                                colors = SliderDefaults.colors(
                                                    activeTrackColor = AccentTeal,
                                                    inactiveTrackColor = DividerColor
                                                ),
                                                modifier = Modifier.height(4.dp)
                                            )
                                        }
                                    )
                                    Text(
                                        "${(state.filterIntensity * 100).toInt()}%",
                                        fontSize = 12.sp, color = AccentTeal, fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.width(40.dp), textAlign = TextAlign.End
                                    )
                                }
                            }

                            // Categories
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val categories = listOf("All", "DSLR", "Film", "Pro", "Cinematic", "Artistic")
                                categories.forEach { cat ->
                                    val isSelected = activeCategory == cat
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) AccentTeal.copy(alpha = 0.15f) else Color.Transparent)
                                            .border(
                                                1.dp,
                                                if (isSelected) AccentTeal.copy(alpha = 0.5f) else DividerColor,
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable { activeCategory = cat }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            cat,
                                            color = if (isSelected) AccentTeal else TextSecondary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            // Filter thumbnails
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val allFilters = ImageFilters.Filter.values()
                                val filteredList = if (activeCategory == "All") {
                                    allFilters.toList()
                                } else {
                                    allFilters.filter { it.category == activeCategory }
                                }

                                filteredList.forEach { filter ->
                                    val isSelected = state.currentFilter == filter
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.clickable { viewModel.selectFilter(filter) }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(62.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .then(
                                                    if (isSelected) Modifier
                                                        .border(2.dp, AccentTeal, RoundedCornerShape(10.dp))
                                                        .shadow(4.dp, RoundedCornerShape(10.dp), ambientColor = AccentGlow, spotColor = AccentGlow)
                                                    else Modifier
                                                        .border(1.dp, DividerColor, RoundedCornerShape(10.dp))
                                                )
                                                .background(EditorCardBg),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val previewBmp = filterPreviews[filter]
                                            if (previewBmp != null) {
                                                androidx.compose.foundation.Image(
                                                    bitmap = previewBmp.asImageBitmap(),
                                                    contentDescription = filter.label,
                                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.PhotoFilter,
                                                    null,
                                                    tint = if (isSelected) AccentTeal else TextMuted,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            filter.label,
                                            fontSize = 10.sp,
                                            color = if (isSelected) AccentTeal else TextSecondary,
                                            textAlign = TextAlign.Center,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── ADJUST ──
                    PhotoEditorMode.ADJUST -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            AdjustSlider(
                                label = "Brightness", icon = Icons.Default.LightMode,
                                value = adjustmentValues.brightness, range = -100f..100f,
                                onValueChange = { viewModel.updateAdjustment(adjustmentValues.copy(brightness = it)) },
                                onValueChangeFinished = { viewModel.commitAdjustment() }
                            )
                            AdjustSlider(
                                label = "Contrast", icon = Icons.Default.Contrast,
                                value = adjustmentValues.contrast, range = -100f..100f,
                                onValueChange = { viewModel.updateAdjustment(adjustmentValues.copy(contrast = it)) },
                                onValueChangeFinished = { viewModel.commitAdjustment() }
                            )
                            AdjustSlider(
                                label = "Saturation", icon = Icons.Default.ColorLens,
                                value = adjustmentValues.saturation, range = -100f..100f,
                                onValueChange = { viewModel.updateAdjustment(adjustmentValues.copy(saturation = it)) },
                                onValueChangeFinished = { viewModel.commitAdjustment() }
                            )
                            AdjustSlider(
                                label = "Sharpness", icon = Icons.Default.Deblur,
                                value = adjustmentValues.sharpness, range = 0f..100f,
                                onValueChange = { viewModel.updateAdjustment(adjustmentValues.copy(sharpness = it)) },
                                onValueChangeFinished = { viewModel.commitAdjustment() }
                            )
                            AdjustSlider(
                                label = "Warmth", icon = Icons.Default.Thermostat,
                                value = adjustmentValues.warmth, range = -100f..100f,
                                onValueChange = { viewModel.updateAdjustment(adjustmentValues.copy(warmth = it)) },
                                onValueChangeFinished = { viewModel.commitAdjustment() }
                            )
                            AdjustSlider(
                                label = "Exposure", icon = Icons.Default.Exposure,
                                value = adjustmentValues.exposure, range = -100f..100f,
                                onValueChange = { viewModel.updateAdjustment(adjustmentValues.copy(exposure = it)) },
                                onValueChangeFinished = { viewModel.commitAdjustment() }
                            )
                        }
                    }

                    // ── CROP ──
                    PhotoEditorMode.CROP -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Aspect Ratio",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val ratios = listOf(
                                    "Free" to null,
                                    "1:1" to 1f,
                                    "4:3" to 4f / 3f,
                                    "3:2" to 3f / 2f,
                                    "16:9" to 16f / 9f
                                )
                                ratios.forEach { (label, ratio) ->
                                    val isSelected = selectedCropRatio == ratio
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) AccentTeal.copy(alpha = 0.15f) else EditorCardBg)
                                            .border(
                                                1.dp,
                                                if (isSelected) AccentTeal else DividerColor,
                                                RoundedCornerShape(10.dp)
                                            )
                                            .clickable { selectedCropRatio = ratio }
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            label,
                                            color = if (isSelected) AccentTeal else TextSecondary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Crop button
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.horizontalGradient(listOf(AccentTeal, AccentTealDim))
                                    )
                                    .clickable {
                                        viewModel.applyCrop(cropLeft, cropTop, cropRight, cropBottom)
                                        viewModel.setEditorMode(PhotoEditorMode.ADJUST)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Crop, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Apply Crop", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    // ── TRANSFORM ──
                    PhotoEditorMode.ROTATE -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            // Transform buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                TransformButton(
                                    icon = Icons.AutoMirrored.Filled.RotateLeft,
                                    label = "Left",
                                    onClick = { viewModel.rotate90(false) }
                                )
                                TransformButton(
                                    icon = Icons.AutoMirrored.Filled.RotateRight,
                                    label = "Right",
                                    onClick = { viewModel.rotate90(true) }
                                )
                                TransformButton(
                                    icon = Icons.Default.Flip,
                                    label = "Flip H",
                                    isActive = state.isFlippedHorizontally,
                                    onClick = { viewModel.flipHorizontal() }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Fine rotation slider
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Straighten",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(72.dp)
                                )
                                Slider(
                                    value = state.fineRotationAngle,
                                    onValueChange = { viewModel.updateFineRotation(it) },
                                    onValueChangeFinished = { viewModel.commitFineRotation() },
                                    valueRange = -45f..45f,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = AccentTeal,
                                        activeTrackColor = AccentTeal,
                                        inactiveTrackColor = DividerColor
                                    ),
                                    track = { sliderState ->
                                        SliderDefaults.Track(
                                            sliderState = sliderState,
                                            colors = SliderDefaults.colors(
                                                activeTrackColor = AccentTeal,
                                                inactiveTrackColor = DividerColor
                                            ),
                                            modifier = Modifier.height(4.dp)
                                        )
                                    }
                                )
                                Text(
                                    "${state.fineRotationAngle.toInt()}°",
                                    fontSize = 12.sp,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.width(34.dp),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }
            }

            // Bottom safe area
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    if (showResizeConfirmDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showResizeConfirmDialog = false },
            title = { Text("Unsaved Edits") },
            text = { Text("You have unsaved changes. Do you want to apply them before resizing?") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showResizeConfirmDialog = false
                    viewModel.saveEditsAndNavigate {
                        state.photo?.id?.let { photoId -> onResizeClick(photoId) }
                    }
                }) {
                    Text("Save & Resize", color = AccentTeal)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showResizeConfirmDialog = false
                    state.photo?.id?.let { photoId -> onResizeClick(photoId) }
                }) {
                    Text("Discard & Resize", color = MaterialTheme.colorScheme.error)
                }
            },
            containerColor = EditorPanel,
            titleContentColor = Color.White,
            textContentColor = TextSecondary
        )
    }
}

// ── Reusable Components ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdjustSlider(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Icon(icon, null, tint = AccentTeal, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            fontSize = 12.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(70.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = AccentTeal,
                activeTrackColor = AccentTeal,
                inactiveTrackColor = DividerColor
            ),
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    colors = SliderDefaults.colors(
                        activeTrackColor = AccentTeal,
                        inactiveTrackColor = DividerColor
                    ),
                    modifier = Modifier.height(4.dp)
                )
            }
        )
        Text(
            "${value.toInt()}",
            fontSize = 12.sp,
            color = if (value != 0f) AccentTeal else TextMuted,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun TransformButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (isActive) AccentTeal.copy(alpha = 0.15f) else EditorCardBg)
                .border(1.dp, if (isActive) AccentTeal else DividerColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = if (isActive) AccentTeal else TextPrimary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 10.sp, color = if (isActive) AccentTeal else TextSecondary)
    }
}
