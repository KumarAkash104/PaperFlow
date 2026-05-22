package com.paperflow.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Teal400,
    onPrimary = Dark950,
    primaryContainer = Teal600,
    onPrimaryContainer = Teal200,
    secondary = Gold400,
    onSecondary = Dark950,
    secondaryContainer = Gold500,
    background = Dark900,
    onBackground = Dark100,
    surface = Dark850,
    onSurface = Dark100,
    surfaceVariant = Dark800,
    onSurfaceVariant = Dark400,
    error = ErrorRed,
    onError = Dark950,
    outline = Dark600,
    outlineVariant = Dark800,
)

@Composable
fun PaperFlowTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = PaperFlowTypography,
        content = content,
    )
}
