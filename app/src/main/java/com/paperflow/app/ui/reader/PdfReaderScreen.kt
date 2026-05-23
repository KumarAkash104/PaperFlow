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
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File
import kotlin.random.Random

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
                                } else if (pointerCount == 1 && scale > 1.05f) {
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
                    // Double-tap: zoom to tap point / reset
                    .pointerInput(Unit) {
                        detectTapGestures(
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
                                    // Calculate offset so tap point stays at same screen position
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
                        userScrollEnabled = scale <= 1.05f
                    ) {
                        items(uiState.pageCount) { index ->
                            LazyPdfPage(
                                viewModel = viewModel,
                                pageIndex = index,
                                bgColor = bgColor,
                                warmth = theme.warmth,
                                contrast = uiState.contrast,
                                isDark = theme == ReadingTheme.DARK || theme == ReadingTheme.WARM_DARK,
                                onSurfaceColor = onSurfaceColor
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
                        userScrollEnabled = scale <= 1.05f
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
                                onSurfaceColor = onSurfaceColor
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
                        IconButton(onClick = onBack) {
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
                            Text(
                                text = "Page ${uiState.currentPage + 1} of ${uiState.pageCount}",
                                color = onSurfaceColor.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
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
    onSurfaceColor: Color
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
            onSurfaceColor = onSurfaceColor
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
 * A single PDF page rendered with paper-like feel.
 */
@Composable
fun PaperPage(
    imagePath: String,
    bgColor: Color,
    warmth: Float,
    contrast: Float,
    isDark: Boolean,
    pageNumber: Int,
    onSurfaceColor: Color
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

    Column {
        AsyncImage(
            model = File(imagePath),
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

/** Subtle paper grain overlay. */
@Composable
fun PaperTextureOverlay(isDark: Boolean) {
    val grainAlpha = if (isDark) 0.04f else 0.025f
    val grainColor = if (isDark) Color.White else Color.Black
    Canvas(modifier = Modifier.fillMaxSize().alpha(grainAlpha)) {
        val random = Random(42)
        val dotCount = (size.width * size.height / 800f).toInt().coerceAtMost(8000)
        for (i in 0 until dotCount) {
            val x = random.nextFloat() * size.width
            val y = random.nextFloat() * size.height
            val r = random.nextFloat() * 1.2f + 0.3f
            drawCircle(grainColor, r, Offset(x, y), alpha = random.nextFloat() * 0.6f + 0.2f)
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
                    .width(40.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(onSurfaceColor.copy(alpha = 0.25f))
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
