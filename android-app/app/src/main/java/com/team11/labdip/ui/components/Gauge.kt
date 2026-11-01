package com.team11.labdip.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import com.team11.labdip.ui.theme.Ink
import com.team11.labdip.ui.theme.InkFaint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Half-circle of radial ticks, lit up to [fraction] in [color]. [marks] are fractions drawn as long
 * ticks (pass / marginal limits, tolerance edges). A small pointer sits above the current value.
 */
@Composable
fun TickGauge(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    marks: List<Float> = emptyList(),
    ticks: Int = 56,
) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(300), label = "gauge")
    Canvas(modifier) {
        val radius = min(size.width / 2f, size.height) * 0.92f
        val center = Offset(size.width / 2f, size.height * 0.98f)
        val tickLen = radius * 0.16f
        val stroke = radius * 0.022f
        for (i in 0 until ticks) {
            val t = i / (ticks - 1f)
            val angle = PI + t * PI
            val dir = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            val lit = t <= f
            drawLine(
                color = if (lit) color else InkFaint.copy(alpha = 0.6f),
                start = center + dir * (radius - tickLen),
                end = center + dir * radius,
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        for (m in marks) {
            val angle = PI + m.coerceIn(0f, 1f) * PI
            val dir = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            drawLine(Ink, center + dir * (radius - tickLen * 1.7f), center + dir * (radius + tickLen * 0.15f), stroke * 1.4f, StrokeCap.Round)
        }
        // Pointer: a small triangle just inside the arc
        val angle = PI + f * PI
        val dir = Offset(cos(angle).toFloat(), sin(angle).toFloat())
        val normal = Offset(-dir.y, dir.x)
        val tip = center + dir * (radius - tickLen * 1.35f)
        val base = center + dir * (radius - tickLen * 2.3f)
        val path = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(base.x + normal.x * stroke * 2.4f, base.y + normal.y * stroke * 2.4f)
            lineTo(base.x - normal.x * stroke * 2.4f, base.y - normal.y * stroke * 2.4f)
            close()
        }
        drawPath(path, Ink)
    }
}
