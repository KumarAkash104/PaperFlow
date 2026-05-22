package com.paperflow.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ArrowForward
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.paperflow.app.data.model.DocumentEntity
import com.paperflow.app.ui.components.DocumentSelectionDialog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onScanClick: (String) -> Unit,
    onDocumentClick: (String) -> Unit,
    onDocumentExport: (String) -> Unit,
    onToolsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDocumentImported: (String) -> Unit,
    onReadPdf: (String) -> Unit = {},
    onPhotoClick: (String) -> Unit = {},
) {
    val documents by viewModel.documents.collectAsState()
    val photos by viewModel.photos.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val selectedDocumentIds by viewModel.selectedDocumentIds.collectAsState()
    val isTransferring by viewModel.isTransferring.collectAsState()
    val transferMessage by viewModel.transferMessage.collectAsState()
    val transferError by viewModel.transferError.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()
    val selectedPhotoIds by viewModel.selectedPhotoIds.collectAsState()
    val isPhotoSelectionMode by viewModel.isPhotoSelectionMode.collectAsState()
    var isSearchVisible by remember { mutableStateOf(false) }
    var deleteConfirmId by remember { mutableStateOf<String?>(null) }
    var deletePhotoConfirmId by remember { mutableStateOf<String?>(null) }
    var renameDialogDoc by remember { mutableStateOf<DocumentEntity?>(null) }
    var renameDialogPhoto by remember { mutableStateOf<com.paperflow.app.data.model.PhotoEntity?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var showImportMenu by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    val isGridView by viewModel.isGridView.collectAsState()
    val activeTransferAction by viewModel.activeTransferAction.collectAsState()
    val isSelectionMode = activeTransferAction != null
    val selectedCount = selectedDocumentIds.size
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(transferMessage, transferError) {
        val message = transferMessage ?: transferError
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearTransferStatus()
        }
    }

    val importPdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importPdf(context, uri) { docId ->
                onDocumentImported(docId)
            }
        }
    }

    val importImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importImages(context, uris) {
                viewModel.setActiveTab("Photos")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text(
                            text = "Select to ${activeTransferAction?.label?.lowercase()}",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    } else if (isPhotoSelectionMode) {
                        Text(
                            text = "${selectedPhotoIds.size} selected",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    } else if (isSearchVisible) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            placeholder = { Text(if (activeTab == "Documents") "Search docs..." else "Search photos...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                cursorColor = MaterialTheme.colorScheme.primary,
                            ),
                            trailingIcon = {
                                IconButton(onClick = {
                                    isSearchVisible = false
                                    viewModel.onSearchQueryChanged("")
                                }) {
                                    Icon(Icons.Default.Close, "Close search")
                                }
                            },
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (activeTab == "Documents") MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { viewModel.setActiveTab("Documents") }
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Docs",
                                    color = if (activeTab == "Documents") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (activeTab == "Photos") MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { viewModel.setActiveTab("Photos") }
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Photos",
                                    color = if (activeTab == "Photos") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { viewModel.cancelTransferAction() }) {
                            Icon(Icons.Default.Close, "Cancel")
                        }
                    } else if (isPhotoSelectionMode) {
                        IconButton(onClick = { viewModel.cancelPhotoSelection() }) {
                            Icon(Icons.Default.Close, "Cancel")
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        if (selectedCount > 0) {
                            TextButton(onClick = { showTransferDialog = true }) {
                                Text("Next ($selectedCount)", color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.ArrowForward, contentDescription = "Next")
                            }
                        }
                    } else if (isPhotoSelectionMode) {
                        IconButton(onClick = { viewModel.selectAllPhotos(photos.map { it.id }) }) {
                            Icon(Icons.Default.DoneAll, "Select All")
                        }
                        IconButton(onClick = { viewModel.convertSelectedPhotosToPdf(context, onDocumentImported) }) {
                            Icon(Icons.Default.PictureAsPdf, "Convert to PDF")
                        }
                        IconButton(onClick = { viewModel.deleteSelectedPhotos() }) {
                            Icon(Icons.Default.Delete, "Delete selected")
                        }
                    } else if (!isSearchVisible) {
                        IconButton(onClick = { isSearchVisible = true }) {
                            Icon(Icons.Default.Search, "Search")
                        }
                        Box {
                            IconButton(onClick = { sortMenuExpanded = true }) {
                                Icon(Icons.Default.Sort, "Sort")
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Date (Newest first)") },
                                    onClick = { viewModel.setSortOption(SortOption.DATE_DESC); sortMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Date (Oldest first)") },
                                    onClick = { viewModel.setSortOption(SortOption.DATE_ASC); sortMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Name (A-Z)") },
                                    onClick = { viewModel.setSortOption(SortOption.NAME_ASC); sortMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Page Count") },
                                    onClick = { viewModel.setSortOption(SortOption.PAGE_COUNT); sortMenuExpanded = false }
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.toggleViewStyle() }) {
                            Icon(
                                if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                contentDescription = "Toggle View"
                            )
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        bottomBar = {
            if (!isSelectionMode && !isPhotoSelectionMode) {
                NavigationBar {
                    NavigationBarItem(
                        icon = {
                            Box {
                                Icon(Icons.Default.UploadFile, contentDescription = "Import")
                                DropdownMenu(
                                    expanded = showImportMenu,
                                    onDismissRequest = { showImportMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Import PDF") },
                                        leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                                        onClick = { 
                                            importPdfPicker.launch(arrayOf("application/pdf"))
                                            showImportMenu = false 
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Import Photo") },
                                        leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                                        onClick = { 
                                            importImagePicker.launch("image/*")
                                            showImportMenu = false 
                                        }
                                    )
                                }
                            }
                        },
                        label = { Text("Import") },
                        selected = false,
                        onClick = { showImportMenu = true }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.CameraAlt, contentDescription = if (activeTab == "Photos") "Camera" else "Scan") },
                        label = { Text(if (activeTab == "Photos") "Camera" else "Scan") },
                        selected = false,
                        onClick = {
                            val mode = if (activeTab == "Photos") "photo" else "scan"
                            onScanClick(mode)
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.GridView, contentDescription = "Tools") },
                        label = { Text("Tools") },
                        selected = false,
                        onClick = onToolsClick
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (activeTab == "Documents") {
                if (documents.isEmpty() && searchQuery.isBlank()) {
                    // ── Empty state ──
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp),
                        ) {
                            Icon(
                                Icons.Default.DocumentScanner,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            )
                            Spacer(Modifier.height(24.dp))
                            Text(
                                "No documents yet",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Tap the scan button to get started",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (isGridView) 2 else 1),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(documents, key = { it.id }) { doc ->
                            if (isGridView) {
                                DocumentCard(
                                    document = doc,
                                    isSelected = doc.id in selectedDocumentIds,
                                    isSelectionMode = isSelectionMode,
                                    onClick = {
                                        if (isSelectionMode) viewModel.toggleSelection(doc.id)
                                        else onDocumentClick(doc.id)
                                    },
                                    onRename = { renameDialogDoc = doc },
                                    onDelete = { deleteConfirmId = doc.id },
                                    onExport = { onDocumentExport(doc.id) },
                                    onReadPdf = { onReadPdf(doc.id) },
                                    onConvertToPhoto = { viewModel.convertDocumentToPhotos(doc.id) }
                                )
                            } else {
                                DocumentListCard(
                                    document = doc,
                                    isSelected = doc.id in selectedDocumentIds,
                                    isSelectionMode = isSelectionMode,
                                    onClick = {
                                        if (isSelectionMode) viewModel.toggleSelection(doc.id)
                                        else onDocumentClick(doc.id)
                                    },
                                    onRename = { renameDialogDoc = doc },
                                    onDelete = { deleteConfirmId = doc.id },
                                    onExport = { onDocumentExport(doc.id) },
                                    onReadPdf = { onReadPdf(doc.id) },
                                    onConvertToPhoto = { viewModel.convertDocumentToPhotos(doc.id) }
                                )
                            }
                        }
                    }
                }
            } else {
                // Photos Tab
                if (photos.isEmpty() && searchQuery.isBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp),
                        ) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            )
                            Spacer(Modifier.height(24.dp))
                            Text(
                                "No photos yet",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Import photos from the 'Import' button to edit",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (isGridView) 2 else 1),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(photos, key = { it.id }) { photo ->
                            if (isGridView) {
                                PhotoCard(
                                    photo = photo,
                                    isSelected = photo.id in selectedPhotoIds,
                                    isSelectionMode = isPhotoSelectionMode,
                                    onClick = {
                                        if (isPhotoSelectionMode) viewModel.togglePhotoSelection(photo.id)
                                        else onPhotoClick(photo.id)
                                    },
                                    onLongClick = {
                                        if (!isPhotoSelectionMode) viewModel.startPhotoSelection(photo.id)
                                    },
                                    onRename = { renameDialogPhoto = photo },
                                    onDelete = { deletePhotoConfirmId = photo.id },
                                    onConvertToPdf = { viewModel.convertPhotoToPdf(context, photo) },
                                    onSaveToGallery = {
                                        viewModel.savePhotoToGallery(context, photo) { success ->
                                            val msg = if (success) "Saved to Gallery" else "Failed to save to Gallery"
                                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            } else {
                                PhotoListCard(
                                    photo = photo,
                                    isSelected = photo.id in selectedPhotoIds,
                                    isSelectionMode = isPhotoSelectionMode,
                                    onClick = {
                                        if (isPhotoSelectionMode) viewModel.togglePhotoSelection(photo.id)
                                        else onPhotoClick(photo.id)
                                    },
                                    onLongClick = {
                                        if (!isPhotoSelectionMode) viewModel.startPhotoSelection(photo.id)
                                    },
                                    onRename = { renameDialogPhoto = photo },
                                    onDelete = { deletePhotoConfirmId = photo.id },
                                    onConvertToPdf = { viewModel.convertPhotoToPdf(context, photo) },
                                    onSaveToGallery = {
                                        viewModel.savePhotoToGallery(context, photo) { success ->
                                            val msg = if (success) "Saved to Gallery" else "Failed to save to Gallery"
                                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        
        if (isImporting || isTransferring) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (isTransferring) "Updating documents..." else "Importing Document...",
                        color = Color.White,
                    )
                }
            }
            }
        }

    // ── Delete dialog ──
    if (deleteConfirmId != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmId = null },
            title = { Text("Delete document?") },
            text = { Text("This will permanently delete the document and all its pages.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteConfirmId?.let { viewModel.deleteDocument(it) }
                    deleteConfirmId = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmId = null }) { Text("Cancel") }
            },
        )
    }

    if (deletePhotoConfirmId != null) {
        AlertDialog(
            onDismissRequest = { deletePhotoConfirmId = null },
            title = { Text("Delete photo?") },
            text = { Text("This will permanently delete the photo.") },
            confirmButton = {
                TextButton(onClick = {
                    deletePhotoConfirmId?.let { viewModel.deletePhoto(it) }
                    deletePhotoConfirmId = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletePhotoConfirmId = null }) { Text("Cancel") }
            },
        )
    }

    // ── Rename dialog ──
    if (renameDialogDoc != null) {
        var newName by remember { mutableStateOf(renameDialogDoc!!.title) }
        AlertDialog(
            onDismissRequest = { renameDialogDoc = null },
            title = { Text("Rename document") },
            text = {
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    placeholder = { Text("Document name") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        viewModel.renameDocument(renameDialogDoc!!.id, newName)
                    }
                    renameDialogDoc = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameDialogDoc = null }) { Text("Cancel") }
            },
        )
    }

    if (renameDialogPhoto != null) {
        var newName by remember { mutableStateOf(renameDialogPhoto!!.title) }
        AlertDialog(
            onDismissRequest = { renameDialogPhoto = null },
            title = { Text("Rename photo") },
            text = {
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    placeholder = { Text("Photo name") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        viewModel.renamePhoto(renameDialogPhoto!!.id, newName)
                    }
                    renameDialogPhoto = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameDialogPhoto = null }) { Text("Cancel") }
            },
        )
    }


}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DocumentListCard(
    document: DocumentEntity,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onReadPdf: () -> Unit = {},
    onConvertToPhoto: () -> Unit = {},
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dateText = remember(document.updatedAt) {
        val format = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
        format.format(java.util.Date(document.updatedAt))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .padding(12.dp)
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                if (document.thumbnailPath != null) {
                    val file = remember(document.thumbnailPath) { File(document.thumbnailPath) }
                    val lastMod = remember(file) { file.lastModified().toString() }
                    AsyncImage(
                        model = coil3.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(file)
                            .memoryCacheKey("${document.thumbnailPath}_$lastMod")
                            .diskCacheKey("${document.thumbnailPath}_$lastMod")
                            .size(120, 120)
                            .build(),
                        contentDescription = document.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.DocumentScanner,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                    }
                }
                
                // Page count badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f), RoundedCornerShape(topStart = 6.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = "${document.pageCount}p",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 9.sp
                    )
                }

                // Read PDF book icon (top-left)
                IconButton(
                    onClick = onReadPdf,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(28.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            RoundedCornerShape(bottomEnd = 8.dp)
                        )
                ) {
                    Icon(
                        Icons.Default.AutoStories,
                        contentDescription = "Read PDF",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp, horizontal = 4.dp)
            ) {
                Text(
                    text = document.title.ifEmpty { "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Menu
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 8.dp),
                )
            } else Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Export to PDF") },
                        leadingIcon = { Icon(Icons.Default.UploadFile, null) },
                        onClick = {
                            menuExpanded = false
                            onExport()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Convert to Photo") },
                        leadingIcon = { Icon(Icons.Default.Image, null) },
                        onClick = {
                            menuExpanded = false
                            onConvertToPhoto()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DocumentCard(
    document: DocumentEntity,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onReadPdf: () -> Unit = {},
    onConvertToPhoto: () -> Unit = {},
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dateText = remember(document.updatedAt) {
        SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(document.updatedAt))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            // Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.85f),
            ) {
                if (document.thumbnailPath != null) {
                    val file = remember(document.thumbnailPath) { File(document.thumbnailPath) }
                    val lastMod = remember(file) { file.lastModified().toString() }
                    AsyncImage(
                        model = coil3.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(file)
                            .memoryCacheKey("${document.thumbnailPath}_$lastMod")
                            .diskCacheKey("${document.thumbnailPath}_$lastMod")
                            .size(360, 480)
                            .build(),
                        contentDescription = document.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.DocumentScanner,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        )
                    }
                }

                // Gradient overlay at bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)),
                            ),
                        ),
                )

                // Page count badge
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .align(Alignment.TopEnd),
                ) {
                    Text(
                        text = "${document.pageCount} pg",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                CircleShape,
                            ),
                    )
                } else {
                    // Read PDF book icon (top-left)
                    IconButton(
                        onClick = onReadPdf,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .size(32.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                CircleShape,
                            )
                    ) {
                        Icon(
                            Icons.Default.AutoStories,
                            contentDescription = "Read PDF",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Info row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document.title.ifEmpty { "Untitled" },
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (isSelectionMode) {
                    Spacer(Modifier.width(32.dp))
                } else Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Export to PDF") },
                            leadingIcon = { Icon(Icons.Default.UploadFile, null) },
                            onClick = {
                                menuExpanded = false
                                onExport()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Convert to Photo") },
                            leadingIcon = { Icon(Icons.Default.Image, null) },
                            onClick = {
                                menuExpanded = false
                                onConvertToPhoto()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoCard(
    photo: com.paperflow.app.data.model.PhotoEntity,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onConvertToPdf: () -> Unit,
    onSaveToGallery: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dateText = remember(photo.updatedAt) {
        SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(photo.updatedAt))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.85f),
            ) {
                val file = remember(photo.imagePath) { File(photo.imagePath) }
                val lastMod = remember(file) { file.lastModified().toString() }
                AsyncImage(
                    model = coil3.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(file)
                        .memoryCacheKey("${photo.imagePath}_$lastMod")
                        .diskCacheKey("${photo.imagePath}_$lastMod")
                        .size(360, 480)
                        .build(),
                    contentDescription = photo.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                )

                // Image Resolution Badge (Bottom End)
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .align(Alignment.BottomEnd),
                ) {
                    Text(
                        text = "${photo.width}x${photo.height}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontSize = 9.sp
                    )
                }

                // File Size Badge (Bottom Start)
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .align(Alignment.BottomStart),
                ) {
                    Text(
                        text = formatFileSize(photo.fileSize),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontSize = 9.sp
                    )
                }

                // Selection Checkbox overlay
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                CircleShape,
                            ),
                    )
                }
            }

            // Info row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = photo.title.ifEmpty { "Untitled" },
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (isSelectionMode) {
                    Spacer(Modifier.width(32.dp))
                } else Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Convert to PDF") },
                            leadingIcon = { Icon(Icons.Default.UploadFile, null) },
                            onClick = {
                                menuExpanded = false
                                onConvertToPdf()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Save to Gallery") },
                            leadingIcon = { Icon(Icons.Default.Share, null) },
                            onClick = {
                                menuExpanded = false
                                onSaveToGallery()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoListCard(
    photo: com.paperflow.app.data.model.PhotoEntity,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onConvertToPdf: () -> Unit,
    onSaveToGallery: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dateText = remember(photo.updatedAt) {
        val format = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
        format.format(java.util.Date(photo.updatedAt))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .padding(12.dp)
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                val file = remember(photo.imagePath) { File(photo.imagePath) }
                val lastMod = remember(file) { file.lastModified().toString() }
                AsyncImage(
                    model = coil3.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(file)
                        .memoryCacheKey("${photo.imagePath}_$lastMod")
                        .diskCacheKey("${photo.imagePath}_$lastMod")
                        .size(120, 120)
                        .build(),
                    contentDescription = photo.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp, horizontal = 4.dp)
            ) {
                Text(
                    text = photo.title.ifEmpty { "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "•  ${photo.width}x${photo.height}  •  ${formatFileSize(photo.fileSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 8.dp),
                )
            } else Box(modifier = Modifier.padding(end = 8.dp)) {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Convert to PDF") },
                        leadingIcon = { Icon(Icons.Default.UploadFile, null) },
                        onClick = {
                            menuExpanded = false
                            onConvertToPdf()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Save to Gallery") },
                        leadingIcon = { Icon(Icons.Default.Share, null) },
                        onClick = {
                            menuExpanded = false
                            onSaveToGallery()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
