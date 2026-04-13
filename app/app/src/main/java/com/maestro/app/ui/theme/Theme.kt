package com.maestro.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = MaestroPrimary,
    onPrimary = MaestroOnPrimary,
    primaryContainer = MaestroPrimaryContainer,
    onPrimaryContainer = MaestroOnPrimaryContainer,
    secondary = MaestroSecondary,
    secondaryContainer = MaestroSecondaryContainer,
    background = MaestroBackground,
    surface = MaestroSurface,
    surfaceVariant = MaestroSurfaceContainerHigh,
    onSurface = MaestroOnSurface,
    onSurfaceVariant = MaestroOnSurfaceVariant,
    outline = MaestroOutline,
    outlineVariant = MaestroOutlineVariant,
    error = MaestroError
)

@Composable
fun MaestroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
