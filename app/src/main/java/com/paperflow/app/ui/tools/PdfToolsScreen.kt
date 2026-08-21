package com.paperflow.app.ui.tools

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.SaveAlt
import android.widget.Toast
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    viewModel: PdfToolsViewModel,
    onBack: () -> Unit,
    onImported: (String) -> Unit,
    onEditPdf: (String) -> Unit,
    onReadPdf: (String) -> Unit,
    onPhotoResizeClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Compression quality dialog state
    var showCompressDialog by remember { mutableStateOf(false) }
    var compressUri by remember { mutableStateOf<Uri?>(null) }
    var compressQuality by remember { mutableFloatStateOf(60f) }

    // Watermark dialog state
    var showWatermarkDialog by remember { mutableStateOf(false) }
    var watermarkUri by remember { mutableStateOf<Uri?>(null) }
    var watermarkText by remember { mutableStateOf("CONFIDENTIAL") }
    var watermarkOpacity by remember { mutableFloatStateOf(0.35f) }

    // Protect dialog state
    var showProtectDialog by remember { mutableStateOf(false) }
    var protectUri by remember { mutableStateOf<Uri?>(null) }
    var protectPassword by remember { mutableStateOf("") }

    // Unlock dialog state
    var showUnlockDialog by remember { mutableStateOf(false) }
    var unlockUri by remember { mutableStateOf<Uri?>(null) }
    var unlockPassword by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(uiState.importedDocumentId) {
        uiState.importedDocumentId?.let { docId ->
            onImported(docId)
            viewModel.clearResult()
        }
    }

    // File pickers
    val mergePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.size >= 2) {
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (_: Exception) {}
            }
            viewModel.mergePdfs(uris)
        }
    }

    val compressPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            compressUri = uri
            compressQuality = 60f
            showCompressDialog = true
        }
    }

    // Split dialog state
    var showSplitDialog by remember { mutableStateOf(false) }
    var splitUri by remember { mutableStateOf<Uri?>(null) }
    var splitRangeInput by remember { mutableStateOf("") }

    val splitPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            splitUri = uri
            splitRangeInput = ""
            showSplitDialog = true
        }
    }

    val extractImagesPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.extractImages(uri)
        }
    }

    val watermarkPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            watermarkUri = uri
            showWatermarkDialog = true
        }
    }

    val protectPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            protectUri = uri
            protectPassword = ""
            showProtectDialog = true
        }
    }

    val unlockPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            unlockUri = uri
            unlockPassword = ""
            showUnlockDialog = true
        }
    }

    val editPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.selectPdfForEditing(uri) { path ->
                onEditPdf(path)
            }
        }
    }

    val readPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.selectPdfForEditing(uri) { path ->
                onReadPdf(path)
            }
        }
    }

    // ── Watermark dialog ──
    if (showWatermarkDialog && watermarkUri != null) {
        Dialog(onDismissRequest = { showWatermarkDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Watermark PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = watermarkText,
                        onValueChange = { watermarkText = it },
                        label = { Text("Watermark Text") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Opacity: ${(watermarkOpacity * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = watermarkOpacity,
                        onValueChange = { watermarkOpacity = it },
                        valueRange = 0.1f..0.8f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { showWatermarkDialog = false }, modifier = Modifier.weight(1f)) {
                            Text("Cancel")
                        }
                        FilledTonalButton(
                            onClick = {
                                showWatermarkDialog = false
                                watermarkUri?.let { viewModel.watermarkPdf(it, watermarkText.ifBlank { "CONFIDENTIAL" }, watermarkOpacity) }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Apply")
                        }
                    }
                }
            }
        }
    }

    // ── Protect PDF dialog ──
    if (showProtectDialog && protectUri != null) {
        Dialog(onDismissRequest = { showProtectDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Password Protect PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = protectPassword,
                        onValueChange = { protectPassword = it },
                        label = { Text("Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { showProtectDialog = false }, modifier = Modifier.weight(1f)) {
                            Text("Cancel")
                        }
                        FilledTonalButton(
                            onClick = {
                                if (protectPassword.isNotBlank()) {
                                    showProtectDialog = false
                                    protectUri?.let { viewModel.protectPdf(it, protectPassword) }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = protectPassword.isNotBlank()
                        ) {
                            Text("Protect")
                        }
                    }
                }
            }
        }
    }

    // ── Unlock PDF dialog ──
    if (showUnlockDialog && unlockUri != null) {
        Dialog(onDismissRequest = { showUnlockDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Unlock PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = unlockPassword,
                        onValueChange = { unlockPassword = it },
                        label = { Text("Current Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { showUnlockDialog = false }, modifier = Modifier.weight(1f)) {
                            Text("Cancel")
                        }
                        FilledTonalButton(
                            onClick = {
                                if (unlockPassword.isNotBlank()) {
                                    showUnlockDialog = false
                                    unlockUri?.let { viewModel.unlockPdf(it, unlockPassword) }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = unlockPassword.isNotBlank()
                        ) {
                            Text("Unlock")
                        }
                    }
                }
            }
        }
    }

    // ── Split PDF dialog ──
    if (showSplitDialog && splitUri != null) {
        Dialog(onDismissRequest = { showSplitDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Split PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Enter the page ranges you want to extract, or use presets:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = splitRangeInput,
                        onValueChange = { splitRangeInput = it },
                        label = { Text("Page Ranges (e.g. 1-3, 5, 8-10)") },
                        placeholder = { Text("Leave blank to split all pages") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { splitRangeInput = "all" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("All Pages", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { splitRangeInput = "1-3" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Pages 1-3", fontSize = 11.sp)
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { showSplitDialog = false }, modifier = Modifier.weight(1f)) {
                            Text("Cancel")
                        }
                        FilledTonalButton(
                            onClick = {
                                showSplitDialog = false
                                splitUri?.let { viewModel.splitPdf(it, splitRangeInput) }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Split PDF")
                        }
                    }
                }
            }
        }
    }

    // ── Compress quality dialog ──
    if (showCompressDialog && compressUri != null) {
        Dialog(onDismissRequest = { showCompressDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Compression Quality",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "Lower quality = smaller file size",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(20.dp))

                    // Quality value display
                    Text(
                        "${compressQuality.toInt()}%",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(Modifier.height(4.dp))

                    // Quality label
                    Text(
                        when {
                            compressQuality <= 30 -> "Maximum compression"
                            compressQuality <= 50 -> "High compression"
                            compressQuality <= 70 -> "Balanced"
                            compressQuality <= 85 -> "High quality"
                            else -> "Best quality"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(16.dp))

                    // Slider
                    Slider(
                        value = compressQuality,
                        onValueChange = { compressQuality = it },
                        valueRange = 10f..100f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Small", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Large", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(Modifier.height(20.dp))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showCompressDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        FilledTonalButton(
                            onClick = {
                                showCompressDialog = false
                                compressUri?.let { uri ->
                                    viewModel.compressPdf(uri, compressQuality.toInt())
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Compress")
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tools", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
        ) {
            // Tool grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                item {
                    ToolCard(
                        icon = Icons.Default.Edit,
                        title = "Edit PDF",
                        onClick = { editPicker.launch(arrayOf("application/pdf")) },
                    )
                }
                item {
                    ToolCard(
                        icon = Icons.Default.AutoStories,
                        title = "Read PDF",
                        onClick = { readPicker.launch(arrayOf("application/pdf")) },
                    )
                }
                item {
                    ToolCard(
                        icon = Icons.Default.CallMerge,
                        title = "Merge PDFs",
                        onClick = { mergePicker.launch(arrayOf("application/pdf")) },
                    )
                }
                item {
                    ToolCard(
                        icon = Icons.Default.CallSplit,
                        title = "Split PDF",
                        onClick = { splitPicker.launch(arrayOf("application/pdf")) },
                    )
                }
                item {
                    ToolCard(
                        icon = Icons.Default.Compress,
                        title = "Compress",
                        onClick = { compressPicker.launch(arrayOf("application/pdf")) },
                    )
                }
                item {
                    ToolCard(
                        icon = Icons.Default.Collections,
                        title = "PDF to Images",
                        onClick = { extractImagesPicker.launch(arrayOf("application/pdf")) },
                    )
                }
                item {
                    ToolCard(
                        icon = Icons.Default.BrandingWatermark,
                        title = "Watermark",
                        onClick = { watermarkPicker.launch(arrayOf("application/pdf")) },
                    )
                }
                item {
                    ToolCard(
                        icon = Icons.Default.Lock,
                        title = "Lock PDF",
                        onClick = { protectPicker.launch(arrayOf("application/pdf")) },
                    )
                }
                item {
                    ToolCard(
                        icon = Icons.Default.LockOpen,
                        title = "Unlock PDF",
                        onClick = { unlockPicker.launch(arrayOf("application/pdf")) },
                    )
                }
                item {
                    ToolCard(
                        icon = Icons.Default.AspectRatio,
                        title = "Photo Resize",
                        onClick = onPhotoResizeClick,
                    )
                }
            }

            // Result / status area
            if (uiState.isProcessing) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(uiState.resultMessage ?: "Processing...")
                    }
                }
            }

            if (uiState.resultFile != null && !uiState.isProcessing) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(uiState.resultMessage ?: "Done!")
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val file = uiState.resultFile!!
                                val success = com.paperflow.app.util.FileUtils.saveFileToDownloads(context, file)
                                if (success) {
                                    Toast.makeText(context, "Saved to Downloads/PaperFlow folder!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Failed to save file", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save to Phone")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val file = uiState.resultFile!!
                                val uri = FileProvider.getUriForFile(
                                    context, "${context.packageName}.fileprovider", file,
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share Result")
                        }
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = { viewModel.importToApp() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.SaveAlt, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import to Edit")
                        }
                    }
                }
            }

            if (uiState.error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    ),
                ) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolCard(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Icon(
                icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
        }
    }
}
