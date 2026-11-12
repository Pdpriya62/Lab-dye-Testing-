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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
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
            // Weave paths are built once per size; each frame only translates them (rebuilding ~1500 points
            // per frame cost ~10 ms of main thread and caused an ANR on a low-end emulator)
            .drawWithCache {
                val weave = weave(size.width, size.height, density)
                val threads = Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = 0.035f), Ink.copy(alpha = 0.07f)))
                onDrawBehind { drawBackdrop(phase, accent, weave, threads) }
            },
    ) { content() }
}

private class Weave(val weft: Path, val warp: Path, val gap: Float)

private fun weave(w: Float, h: Float, density: Float): Weave {
    // Woven fabric: weft rows alternate phase by half a wave so threads read as over/under
    val gap = 26f * density
    val step = 10f * density
    val amp = 3f * density
    val weft = Path()
    var y = gap / 2
    var row = 0
    while (y < h) {
        // Runs 2 gaps past the right edge: the animation shifts it left by up to one full wave (2 gaps)
        var x = 0f
        weft.moveTo(x, y + amp * sin(row * PI.toFloat()))
        while (x <= w + 2 * gap + step) {
            x += step
            weft.lineTo(x, y + amp * sin(x / gap * PI.toFloat() + row * PI.toFloat()))
        }
        y += gap
        row++
    }
    val warp = Path()
    var x = gap / 2
    while (x < w) {
        warp.moveTo(x, 0f)
        warp.lineTo(x, h)
        x += gap
    }
    return Weave(weft, warp, gap)
}

private fun DrawScope.drawBackdrop(phase: Float, accent: Color, weave: Weave, threads: Brush) {
    val w = size.width
    val h = size.height
    val a = phase * 2f * PI.toFloat()

    fun glow(color: Color, alpha: Float, center: Offset, radius: Float) =
        drawRect(Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = center, radius = radius))

    // Dye blobs diffusing through the bath, each on its own slow loop
    glow(Indigo, 0.55f, Offset(w * (0.2f + 0.15f * sin(a)), h * (0.75f + 0.08f * cos(a))), w * 0.9f)
    glow(accent, 0.30f, Offset(w * (0.5f + 0.3f * cos(a)), h * (0.35f + 0.1f * sin(2 * a))), w * 0.75f)
    glow(Orchid, 0.38f, Offset(w * (0.9f + 0.06f * sin(a)), -h * 0.02f), w * 0.95f)

    // sin(x/gap*pi + a) is the static wave moved left by 2*gap*phase; keep it moving the same way as before
    // Threads fade in towards the bottom so headings stay clean
    translate(left = -2 * weave.gap * phase) { drawPath(weave.weft, threads, style = Stroke(width = density)) }
    drawPath(weave.warp, threads, style = Stroke(width = density))
}
