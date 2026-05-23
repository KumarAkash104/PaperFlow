package com.paperflow.app.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FilterBAndW
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import com.paperflow.app.util.EditorMode
import coil3.compose.AsyncImage
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import android.view.HapticFeedbackConstants
import java.io.File
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Draw
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditorScreen(
    viewModel: PageEditorViewModel,
    onBack: () -> Unit,
    onSignPage: (String) -> Unit,
    onAnnotatePage: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { uiState.documentPages.size.coerceAtLeast(1) }
    )

    // Sync pager index with current page
    LaunchedEffect(uiState.documentPages, uiState.pageId) {
        val idx = uiState.documentPages.indexOfFirst { it.id == uiState.pageId }
        if (idx >= 0 && pagerState.currentPage != idx) {
            pagerState.scrollToPage(idx)
        }
    }

    // Switch page when swiped
    LaunchedEffect(pagerState.currentPage) {
        if (uiState.documentPages.isNotEmpty()) {
            val swipedPageId = uiState.documentPages[pagerState.currentPage].id
            if (swipedPageId != uiState.pageId) {
                viewModel.switchPage(swipedPageId)
            }
        }
    }

    var menuExpanded by remember { mutableStateOf(false) }
    var imageScale by remember { mutableStateOf(1f) }
    var isAdjustEditorVisible by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val compareInteractionSource = remember { MutableInteractionSource() }
    val isComparing by compareInteractionSource.collectIsPressedAsState()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            snackbarHostState.showSnackbar("Page saved!")
            onBack()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text("Edit Page", style = MaterialTheme.typography.titleMedium)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {

                    TextButton(onClick = { viewModel.resetAll() }) {
                        Text("Reset")
                    }
                    FilledTonalButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            viewModel.save()
                        },
                        enabled = !uiState.isSaving,
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, "More options")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Revert to original scan") },
                                leadingIcon = { Icon(Icons.Default.Restore, null) },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.revertToOriginal()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            androidx.compose.animation.AnimatedVisibility(
                visible = isAdjustEditorVisible,
                enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }),
                exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it })
            ) {
                Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    Crossfade(targetState = uiState.mode, label = "editor_mode") { mode ->
                        when (mode) {
                            EditorMode.FILTER -> FilterPanel(
                                baseBitmap = uiState.baseBitmap,
                                selectedFilter = uiState.selectedFilter,
                                filterIntensity = uiState.filterIntensity,
                                onFilterSelected = { viewModel.setFilter(it) },
                                onIntensityChanged = { viewModel.setFilterIntensity(it) },
                            )
                            EditorMode.ADJUST -> Box(
                                modifier = Modifier.heightIn(max = 280.dp) // Limits to approx half screen
                            ) {
                                AdjustPanel(
                                    adjustments = uiState.adjustments,
                                    onAdjustmentsChanged = { viewModel.setAdjustments(it) },
                                )
                            }
                            EditorMode.CROP -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                ) {
                                    FilledTonalButton(onClick = { viewModel.autoDetectEdges() }) {
                                        Icon(Icons.Default.CropFree, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Auto Detect")
                                    }
                                    FilledTonalButton(onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        viewModel.applyCrop()
                                    }) {
                                        Icon(Icons.Default.ContentCut, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Apply Crop")
                                    }
                                }
                            }
                            EditorMode.ROTATE -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                ) {
                                    RotateButton(Icons.Default.Refresh, "Left") {
                                        viewModel.rotateLeft()
                                    }
                                    RotateButton(Icons.AutoMirrored.Filled.RotateRight, "Right") {
                                        viewModel.rotateRight()
                                    }
                                    RotateButton(Icons.Default.Flip, "Flip H") {
                                        viewModel.flipHorizontal()
                                    }
                                    RotateButton(Icons.Default.SwapVert, "Flip V") {
                                        viewModel.flipVertical()
                                    }
                                }
                            }
                        }
                    }
                }

                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CapsuleEditorTab(
                            icon = Icons.Default.FilterBAndW,
                            label = "Filter",
                            selected = uiState.mode == EditorMode.FILTER,
                            onClick = { viewModel.setMode(EditorMode.FILTER) }
                        )
                        CapsuleEditorTab(
                            icon = Icons.Default.Tune,
                            label = "Adjust",
                            selected = uiState.mode == EditorMode.ADJUST,
                            onClick = { viewModel.setMode(EditorMode.ADJUST) }
                        )
                        CapsuleEditorTab(
                            icon = Icons.Default.Crop,
                            label = "Crop",
                            selected = uiState.mode == EditorMode.CROP,
                            onClick = { viewModel.setMode(EditorMode.CROP) }
                        )
                        CapsuleEditorTab(
                            icon = Icons.AutoMirrored.Filled.RotateRight,
                            label = "Rotate",
                            selected = uiState.mode == EditorMode.ROTATE,
                            onClick = { viewModel.setMode(EditorMode.ROTATE) }
                        )
                        CapsuleEditorTab(
                            icon = Icons.Default.Gesture,
                            label = "Signature",
                            selected = false,
                            onClick = {
                                viewModel.saveBeforeNavigate {
                                    onSignPage(uiState.pageId)
                                }
                            }
                        )
                        CapsuleEditorTab(
                            icon = Icons.Default.Draw,
                            label = "Annotate",
                            selected = false,
                            onClick = {
                                viewModel.saveBeforeNavigate {
                                    onAnnotatePage(uiState.pageId)
                                }
                            }
                        )
                    }
                }
            }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = imageScale <= 1.01f,
                modifier = Modifier.fillMaxSize()
            ) { pageIndex ->
                if (uiState.documentPages.isEmpty()) return@HorizontalPager
                val page = uiState.documentPages[pageIndex]
                
                if (page.id == uiState.pageId) {
                    // Active editor for current page
                    if (uiState.mode == EditorMode.CROP) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CropView(
                                bitmap = uiState.baseBitmap,
                                corners = uiState.cropCorners ?: emptyList(),
                                onCornersChanged = { viewModel.setCropCorners(it) },
                            )
                        }
                    } else {
                        var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                        var isImagePressed by remember { mutableStateOf(false) }

                        val bmpToDisplay = if (isComparing || isImagePressed) uiState.originalBitmap else uiState.previewBitmap
                        bmpToDisplay?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .graphicsLayer(
                                        scaleX = imageScale,
                                        scaleY = imageScale,
                                        translationX = offset.x,
                                        translationY = offset.y
                                    )
                                    .pointerInput(Unit) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            var up = false
                                            var moved = false
                                            
                                            val job = scope.launch {
                                                delay(300)
                                                if (!moved) isImagePressed = true
                                            }
                                            
                                            while (!up) {
                                                val event = awaitPointerEvent(PointerEventPass.Main)
                                                
                                                val zoomChange = event.calculateZoom()
                                                val panChange = event.calculatePan()
                                                
                                                if (zoomChange != 1f || panChange != androidx.compose.ui.geometry.Offset.Zero) {
                                                    val newScale = (imageScale * zoomChange).coerceIn(1f, 5f)
                                                    
                                                    // Only process zoom/pan if we are using multiple fingers (pinching)
                                                    // OR if we are already zoomed in. This is the crucial part that allows
                                                    // single-finger swiping to pass through to the HorizontalPager when scale is 1f!
                                                    val isPinching = event.changes.size > 1
                                                    
                                                    if (isPinching || imageScale > 1f) {
                                                        imageScale = newScale
                                                        if (imageScale > 1f) {
                                                            offset += panChange
                                                        } else {
                                                            offset = androidx.compose.ui.geometry.Offset.Zero
                                                        }
                                                        
                                                        // Consume the position changes so the Pager doesn't get them
                                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                                    }
                                                }

                                                if (event.changes.any { it.positionChange().getDistance() > 10f }) {
                                                    moved = true
                                                    if (isImagePressed) isImagePressed = false
                                                }
                                                if (!event.changes.any { it.pressed }) {
                                                    up = true
                                                }
                                            }
                                            
                                            job.cancel()
                                            isImagePressed = false
                                        }
                                    }
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = { tapOffset ->
                                                if (imageScale > 1.1f) {
                                                    scope.launch {
                                                        val startScale = imageScale
                                                        val startOffsetX = offset.x
                                                        val startOffsetY = offset.y
                                                        androidx.compose.animation.core.animate(0f, 1f, animationSpec = androidx.compose.animation.core.tween(300)) { value, _ ->
                                                            imageScale = startScale + (1f - startScale) * value
                                                            offset = androidx.compose.ui.geometry.Offset(
                                                                startOffsetX + (0f - startOffsetX) * value,
                                                                startOffsetY + (0f - startOffsetY) * value
                                                            )
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
                                                        val startScale = imageScale
                                                        val startOffsetX = offset.x
                                                        val startOffsetY = offset.y
                                                        androidx.compose.animation.core.animate(0f, 1f, animationSpec = androidx.compose.animation.core.tween(300)) { value, _ ->
                                                            imageScale = startScale + (targetScale - startScale) * value
                                                            offset = androidx.compose.ui.geometry.Offset(
                                                                startOffsetX + (targetOffsetX - startOffsetX) * value,
                                                                startOffsetY + (targetOffsetY - startOffsetY) * value
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                            onTap = {
                                                isAdjustEditorVisible = !isAdjustEditorVisible
                                            }
                                        )
                                    },
                            )
                        }
                    }
                } else {
                    // Display image for adjacent pages
                    val file = remember(page.imagePath) { File(page.imagePath) }
                    var lastMod by remember(page.imagePath) { mutableStateOf(page.createdAt.toString()) }
                    
                    androidx.compose.runtime.LaunchedEffect(page.imagePath) {
                        lastMod = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            file.lastModified().toString()
                        }
                    }
                    
                    AsyncImage(
                        model = coil3.request.ImageRequest.Builder(LocalContext.current)
                            .data(file)
                            .memoryCacheKey("${page.imagePath}_$lastMod")
                            .build(),
                        contentDescription = "Page ${pageIndex + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                }
            }

            // Page indicator pill
            var showJumpDialog by remember { mutableStateOf(false) }
            
            if (uiState.documentPages.size > 1) {
                val currentIdx = uiState.documentPages.indexOfFirst { it.id == uiState.pageId } + 1
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { showJumpDialog = true }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "$currentIdx / ${uiState.documentPages.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                }

                if (showJumpDialog) {
                    var jumpPageText by remember { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = { showJumpDialog = false },
                        title = { Text("Jump to Page") },
                        text = {
                            OutlinedTextField(
                                value = jumpPageText,
                                onValueChange = { jumpPageText = it.filter { char -> char.isDigit() } },
                                label = { Text("Page Number (1 - ${uiState.documentPages.size})") },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number
                                ),
                                singleLine = true
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val pageNum = jumpPageText.toIntOrNull()
                                if (pageNum != null && pageNum in 1..uiState.documentPages.size) {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pageNum - 1)
                                    }
                                }
                                showJumpDialog = false
                            }) {
                                Text("Go")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showJumpDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }

            if (uiState.isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun CapsuleEditorTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(backgroundColor)
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun RotateButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Icon(icon, label, modifier = Modifier.size(28.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
