package com.paperflow.app.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import android.widget.Toast
import java.io.File
import java.util.Locale
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var activeSubScreen by remember { mutableStateOf<String?>(null) }

    val versionName = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
    } catch (_: Exception) { "1.0.0" }

    var cacheSizeBytes by remember {
        mutableStateOf(getFolderSize(context.cacheDir))
    }

    val formattedCacheSize = remember(cacheSizeBytes) {
        formatBytes(cacheSizeBytes)
    }

    // Handle back button press when viewing local sub-screen
    if (activeSubScreen != null) {
        BackHandler {
            activeSubScreen = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Settings", style = MaterialTheme.typography.titleMedium) },
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
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp),
            ) {
                // ── Preferences & Storage ──
                SectionHeader("Preferences & Storage")

                SettingsItem(
                    icon = Icons.Default.DeleteSweep,
                    title = "Clear Temporary Cache",
                    subtitle = "$formattedCacheSize temporary preview & export cache",
                    onClick = {
                        val freed = clearDir(context.cacheDir)
                        cacheSizeBytes = getFolderSize(context.cacheDir)
                        Toast.makeText(context, "Cache cleared! Freed ${formatBytes(freed)}", Toast.LENGTH_SHORT).show()
                    }
                )

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))

                // ── Rate & Share ──
                SectionHeader("Support Us")

                SettingsItem(
                    icon = Icons.Default.Star,
                    title = "Rate Us",
                    subtitle = "Leave a review on the Play Store",
                    onClick = {
                        val packageName = context.packageName
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
                        } catch (e: ActivityNotFoundException) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
                        }
                    }
                )

                SettingsItem(
                    icon = Icons.Default.IosShare,
                    title = "Share App",
                    subtitle = "Share PaperFlow with your friends",
                    onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            putExtra(Intent.EXTRA_TEXT, "Check out PaperFlow, the best offline document scanner: https://play.google.com/store/apps/details?id=${context.packageName}")
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "Share App")
                        context.startActivity(shareIntent)
                    }
                )

                SettingsItem(
                    icon = Icons.Default.Apps,
                    title = "More Apps",
                    subtitle = "Check out our other apps by AKInnovations",
                    onClick = {
                        val developerId = "AKInnovations"
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=pub:$developerId")))
                        } catch (e: ActivityNotFoundException) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=pub:$developerId")))
                        }
                    }
                )

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))

                // ── Help & Legal ──
                SectionHeader("Help & Legal")

                SettingsItem(
                    icon = Icons.Default.Help,
                    title = "Help & How to Use",
                    subtitle = "Learn how to use PaperFlow at its full potential",
                    onClick = {
                        activeSubScreen = "help"
                    }
                )

                SettingsItem(
                    icon = Icons.Default.Policy,
                    title = "Privacy Policy",
                    subtitle = "Read our offline privacy policy",
                    onClick = {
                        activeSubScreen = "privacy"
                    }
                )

                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "App Version",
                    subtitle = "v$versionName • Made with ❤ by AKInnovations",
                    onClick = { }
                )
            }
        }

        // ── Local Sub-Screens Overlay ──
        if (activeSubScreen != null) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = if (activeSubScreen == "privacy") "Privacy Policy" else "How to Use PaperFlow",
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { activeSubScreen = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                },
                containerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxSize()
            ) { subPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(subPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    if (activeSubScreen == "privacy") {
                        LocalPrivacyPolicy()
                    } else {
                        LocalHelpGuide()
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LocalPrivacyPolicy() {
    Column {
        Text(
            text = "PaperFlow",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Effective Date: June 13, 2026\nLast Updated: June 13, 2026",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        PrivacySection("1. Overview", "PaperFlow is a fully offline document scanner, PDF reader, and photo editor. We do not collect, store, transmit, sell, or share any personal information.")
        PrivacySection("2. Information We Do Not Collect", "• Name\n• Email Address\n• Phone Number\n• Location Information\n• Device Identifiers\n• Usage Analytics\n• Advertising Data")
        PrivacySection("3. Information Stored on Your Device", "• Scanned Documents\n• Generated PDFs\n• Edited Photos\n• Digital Signatures\n• App Preferences")
        PrivacySection("4. Permissions", "PaperFlow may request Camera permission to scan documents and capture images. All captured content remains on your device.")
        PrivacySection("5. Third-Party Services", "PaperFlow does not use analytics, advertising, cloud storage, or tracking services.")
        PrivacySection("6. Data Security", "All information remains on your device and is protected by your device's security features.")
        PrivacySection("7. Children's Privacy", "PaperFlow does not knowingly collect personal information from children or adults.")
        PrivacySection("8. Contact Us", "If you have any questions, contact us at:\nEmail: curious.akashkumar@gmail.com")
    }
}

@Composable
private fun PrivacySection(title: String, content: String) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp
        )
    }
}

@Composable
private fun LocalHelpGuide() {
    Column {
        Text(
            text = "Master PaperFlow",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Get the most out of your offline scanner and editor.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        HelpCard(
            title = "1. Quality Control on Scanning",
            description = "Adjust the quality (Low, Med, High) directly on the scanner screen. Choose 'High' for crisp, professional documents, or 'Low' to save storage space for simple drafts."
        )

        HelpCard(
            title = "2. Flawless Auto Crop for Signatures",
            description = "When drawing, capturing, or importing a signature, tap the 'Auto Crop' button. The app will analyze the canvas and perfectly adjust the boundary boxes to fit your signature automatically."
        )

        HelpCard(
            title = "3. Powerful PDF Editing & Overlays",
            description = "Add customized text, stamps, dates, or saved signatures directly over any page in your PDFs. You can tap anywhere on a text block to create edit overlays."
        )

        HelpCard(
            title = "4. Reorder, Move, or Copy Pages",
            description = "Organize pages easily in the Review screen. Long-press a page to reorder by dragging, or use multi-select to copy or move pages to different documents, or convert them back into photos."
        )

        HelpCard(
            title = "5. 100% Offline & Private",
            description = "No internet required! All documents, filters, and digital signatures are processed locally. Export your files to PDF or JPG, or share them securely through your preferred offline channels."
        )
    }
}

@Composable
private fun HelpCard(title: String, description: String) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }
    }
}

private fun getFolderSize(dir: File): Long {
    var size = 0L
    dir.listFiles()?.forEach { file ->
        size += if (file.isDirectory) getFolderSize(file) else file.length()
    }
    return size
}

private fun clearDir(dir: File): Long {
    var deletedSize = 0L
    dir.listFiles()?.forEach { file ->
        deletedSize += if (file.isDirectory) {
            clearDir(file).also { file.delete() }
        } else {
            val len = file.length()
            file.delete()
            len
        }
    }
    return deletedSize
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f))
        bytes >= 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024f)
        else -> "$bytes B"
    }
}

