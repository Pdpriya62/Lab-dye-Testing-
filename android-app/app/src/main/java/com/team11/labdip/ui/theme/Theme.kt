package com.team11.labdip.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush

// No dynamic colour: a wallpaper tint would fight the fixed palette
private val Scheme = darkColorScheme(
    primary = Ink,
    onPrimary = Night,
    secondary = Ember,
    background = Night,
    onBackground = Ink,
    surface = Dusk,
    onSurface = Ink,
    surfaceVariant = Glass,
    onSurfaceVariant = InkSoft,
    surfaceContainer = Dusk,
    surfaceContainerHigh = Dusk,
    outline = GlassEdge,
    error = Fail,
)

@Composable
fun LabDipTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, content = content)
}

/** Dark backdrop with the soft moss glow at the top, as in the mock-up. */
@Composable
fun Backdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .background(Brush.verticalGradient(listOf(Dusk, Night)))
            .background(Brush.radialGradient(listOf(Moss.copy(alpha = 0.55f), Moss.copy(alpha = 0f)), center = Offset(540f, 380f), radius = 900f)),
    ) { content() }
}
