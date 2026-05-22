package com.paperflow.app.ui.review


import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.paperflow.app.ui.components.DocumentSelectionDialog
import com.paperflow.app.ui.home.DocumentTransferMode
import java.io.File

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel,
    onBack: () -> Unit,
    onAddMore: (documentId: String) -> Unit,
    onEditPage: (pageId: String) -> Unit = {},
    onExport: () -> Unit = {},
    onSignPage: (pageId: String) -> Unit = {},
    onAnnotatePage: (pageId: String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    var deleteConfirmPageId by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    androidx.compose.runtime.LaunchedEffect(uiState.transferMessage, uiState.transferError) {
        val message = uiState.transferMessage ?: uiState.transferError
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearTransferStatus()
        }
    }

    var showTransferDialog by remember { mutableStateOf(false) }
    val isSelectionMode = uiState.activeSelectionType != null
    val selectedCount = uiState.selectedPageIds.size

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text(
                            text = "Select to ${uiState.activeSelectionType?.label?.lowercase()}",
                            style = MaterialTheme.typography.titleMedium
                        )
                    } else {
                        Column {
                            Text(
                                text = uiState.documentTitle.ifEmpty { "Untitled" },
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = "${uiState.pages.size} page${if (uiState.pages.size != 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { viewModel.cancelSelectionType() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(Icons.Default.DoneAll, "Select all")
                        }
                        if (selectedCount > 0) {
                            if (uiState.activeSelectionType == ReviewSelectionType.CONVERT) {
                                TextButton(onClick = {
                                    viewModel.convertSelectedPagesToPhotos {
                                        onBack()
                                    }
                                }) {
                                    Text("Convert ($selectedCount)", color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Default.Collections, contentDescription = "Convert")
                                }
                            } else {
                                TextButton(onClick = { showTransferDialog = true }) {
                                    Text("Next ($selectedCount)", color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Default.ArrowForward, contentDescription = "Next")
                                }
                            }
                        }
                    } else if (uiState.pages.isNotEmpty()) {
                        val canTransfer = uiState.allDocuments.size > 1
                        IconButton(
                            onClick = { viewModel.startSelectionType(ReviewSelectionType.COPY) },
                            enabled = canTransfer
                        ) {
                            Icon(Icons.Default.ContentCopy, "Copy pages")
                        }
                        IconButton(
                            onClick = { viewModel.startSelectionType(ReviewSelectionType.MOVE) },
                            enabled = canTransfer
                        ) {
                            Icon(Icons.Default.DriveFileMove, "Move pages")
                        }
                        IconButton(
                            onClick = { viewModel.startSelectionType(ReviewSelectionType.CONVERT) }
                        ) {
                            Icon(Icons.Default.Collections, "Convert to Photos")
                        }
                        IconButton(onClick = onExport) {
                            Icon(Icons.Default.Share, "Export")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isSelectionMode) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAddMore(uiState.documentId) },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add pages")
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (uiState.pages.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No pages yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tap + to scan pages",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(
                    items = uiState.pages,
                    key = { _, page -> page.id },
                ) { index, page ->
                    PageCard(
                        page = page,
                        pageNumber = index + 1,
                        canMoveUp = index > 0,
                        canMoveDown = index < uiState.pages.size - 1,
                        isSelected = page.id in uiState.selectedPageIds,
                        isSelectionMode = isSelectionMode,
                        onMoveUp = { viewModel.movePage(index, index - 1) },
                        onMoveDown = { viewModel.movePage(index, index + 1) },
                        onDelete = { deleteConfirmPageId = page.id },
                        onEdit = { onEditPage(page.id) },
                        onSign = { onSignPage(page.id) },
                        onAnnotate = { onAnnotatePage(page.id) },
                        onClick = {
                            if (isSelectionMode) {
                                viewModel.toggleSelection(page.id)
                            } else {
                                onEditPage(page.id)
                            }
                        }
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    if (deleteConfirmPageId != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmPageId = null },
            title = { Text("Delete page?") },
            text = { Text("This page will be permanently removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteConfirmPageId?.let { viewModel.deletePage(it) }
                        deleteConfirmPageId = null
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmPageId = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showTransferDialog && uiState.activeSelectionType != null && uiState.activeSelectionType != ReviewSelectionType.CONVERT) {
        val targets = uiState.allDocuments.filter { it.id != uiState.documentId }
        val mode = if (uiState.activeSelectionType == ReviewSelectionType.COPY) {
            com.paperflow.app.ui.home.DocumentTransferMode.COPY
        } else {
            com.paperflow.app.ui.home.DocumentTransferMode.MOVE
        }
        DocumentSelectionDialog(
            documents = targets,
            mode = mode,
            onDismiss = { showTransferDialog = false },
            onConfirm = { targetId ->
                viewModel.transferSelectedPages(targetId) {
                    showTransferDialog = false
                    if (uiState.pages.isEmpty() && mode == com.paperflow.app.ui.home.DocumentTransferMode.MOVE) {
                        onBack()
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageCard(
    page: com.paperflow.app.data.model.PageEntity,
    pageNumber: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit = {},
    onSign: () -> Unit = {},
    onAnnotate: () -> Unit = {},
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() },
    ) {
        Column {
            Box {
                val file = remember(page.imagePath) { File(page.imagePath) }
                var lastMod by remember(page.imagePath) { mutableStateOf(page.createdAt.toString()) }
                
                androidx.compose.runtime.LaunchedEffect(page.imagePath) {
                    lastMod = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        file.lastModified().toString()
                    }
                }
                
                AsyncImage(
                    model = coil3.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(file)
                        .memoryCacheKey("${page.imagePath}_$lastMod")
                        .diskCacheKey("${page.imagePath}_$lastMod")
                        .build(),
                    contentDescription = "Page $pageNumber",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.75f)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                )

                // Page number badge
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .align(Alignment.TopStart),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$pageNumber",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }

                // Delete button
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { onDelete() }
                        .align(Alignment.TopEnd),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Delete",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }


            // Reorder + edit controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.size(26.dp),
                ) {
                    Icon(
                        androidx.compose.material.icons.Icons.Default.ArrowUpward,
                        contentDescription = "Move up",
                        modifier = Modifier.size(14.dp),
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.size(26.dp),
                ) {
                    Icon(
                        androidx.compose.material.icons.Icons.Default.ArrowDownward,
                        contentDescription = "Move down",
                        modifier = Modifier.size(14.dp),
                    )
                }

                IconButton(
                    onClick = onSign,
                    modifier = Modifier.size(26.dp),
                ) {
                    Icon(
                        Icons.Default.Gesture,
                        contentDescription = "Sign",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(14.dp),
                    )
                }
                IconButton(
                    onClick = onAnnotate,
                    modifier = Modifier.size(26.dp),
                ) {
                    Icon(
                        Icons.Default.Draw,
                        contentDescription = "Annotate",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}
