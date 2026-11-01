package com.team11.labdip.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// No dynamic colour: a wallpaper tint would fight the fixed palette
private val Scheme = darkColorScheme(
    primary = Ink,
    onPrimary = Night,
    secondary = Violet,
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

/**
 * Violet backdrop: a stage spotlight top right (as in the mock-up), slow-drifting dye blobs
 * and a faint woven-thread texture that ripples, so screens never look flat.
 */
@Composable
fun Backdrop(modifier: Modifier = Modifier, accent: Color = Violet, content: @Composable () -> Unit) {
    val t = rememberInfiniteTransition(label = "backdrop")
    // Read only inside drawBehind, so the animation redraws without recomposing the screen
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(30_000, easing = LinearEasing)), label = "phase")
    Box(
        modifier
            .background(Brush.verticalGradient(listOf(Dusk, Night)))
            .drawBehind { drawBackdrop(phase, accent) },
    ) { content() }
}

private fun DrawScope.drawBackdrop(phase: Float, accent: Color) {
    val w = size.width
    val h = size.height
    val a = phase * 2f * PI.toFloat()

    fun glow(color: Color, alpha: Float, center: Offset, radius: Float) =
        drawRect(Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = center, radius = radius))

    // Dye blobs diffusing through the bath, each on its own slow loop
    glow(Indigo, 0.55f, Offset(w * (0.2f + 0.15f * sin(a)), h * (0.75f + 0.08f * cos(a))), w * 0.9f)
    glow(accent, 0.30f, Offset(w * (0.5f + 0.3f * cos(a)), h * (0.35f + 0.1f * sin(2 * a))), w * 0.75f)
    glow(Orchid, 0.38f, Offset(w * (0.9f + 0.06f * sin(a)), -h * 0.02f), w * 0.95f)

    // Woven fabric: weft rows alternate phase by half a wave so threads read as over/under
    val gap = 26f * density
    val step = 10f * density
    val amp = 3f * density
    val weave = Path()
    var y = gap / 2
    var row = 0
    while (y < h) {
        var x = 0f
        weave.moveTo(x, y)
        while (x <= w + step) {
            weave.lineTo(x, y + amp * sin(x / gap * PI.toFloat() + a + row * PI.toFloat()))
            x += step
        }
        y += gap
        row++
    }
    var x = gap / 2
    while (x < w) {
        weave.moveTo(x, 0f)
        weave.lineTo(x, h)
        x += gap
    }
    // Threads fade in towards the bottom so headings stay clean
    drawPath(
        weave,
        Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = 0.035f), Ink.copy(alpha = 0.07f))),
        style = Stroke(width = density),
    )
}
