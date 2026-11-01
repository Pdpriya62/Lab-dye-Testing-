package com.team11.labdip.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.team11.labdip.ui.theme.Fail
import com.team11.labdip.ui.theme.InkFaint
import com.team11.labdip.ui.theme.InkSoft
import com.team11.labdip.ui.theme.LabType
import kotlin.math.roundToInt

/**
 * Line chart drawn on Canvas to match the mock-up. Drag or tap to read a point.
 * [values] run oldest to newest; null breaks the line (e.g. bath sensor unplugged).
 */
@Composable
fun LineChart(
    values: List<Double?>,
    lineColor: Color,
    unit: String,
    modifier: Modifier = Modifier,
    decimals: Int = 1,
    refLines: List<Double> = emptyList(),
    band: ClosedFloatingPointRange<Double>? = null,
    failAbove: Double? = null,
    height: Dp = 150.dp,
) {
    var selected by remember(values.size) { mutableStateOf<Int?>(null) }
    val measurer = rememberTextMeasurer()
    val present = values.filterNotNull()
    val all = present + refLines + listOfNotNull(band?.start, band?.endInclusive)
    val rawMin = all.minOrNull() ?: 0.0
    val rawMax = all.maxOrNull() ?: 1.0
    val pad = ((rawMax - rawMin) * 0.12).coerceAtLeast(0.1)
    val yMin = rawMin - pad
    val yMax = rawMax + pad

    val callout = selected?.let { i -> values.getOrNull(i) }?.let { v ->
        "Reading ${selected!! + 1} of ${values.size}: ${v.fmt(decimals)} $unit"
    } ?: "Latest: ${present.lastOrNull()?.fmt(decimals) ?: "–"} $unit · drag to inspect"

    Column(modifier) {
        Text(callout, style = LabType.label, color = InkSoft)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(values.size) {
                    fun pick(x: Float) {
                        if (values.size > 1) selected = (x / size.width * (values.size - 1)).roundToInt().coerceIn(0, values.size - 1)
                    }
                    detectTapGestures { pick(it.x) }
                }
                .pointerInput(values.size) {
                    detectDragGestures(onDragEnd = {}) { change, _ ->
                        if (values.size > 1) selected = (change.position.x / size.width * (values.size - 1)).roundToInt().coerceIn(0, values.size - 1)
                    }
                },
        ) {
            val labelW = 40.dp.toPx()
            val w = size.width - labelW
            val h = size.height
            fun x(i: Int) = if (values.size <= 1) w / 2 else i / (values.size - 1f) * w
            fun y(v: Double) = (h - ((v - yMin) / (yMax - yMin) * h)).toFloat()

            band?.let {
                drawRect(InkFaint.copy(alpha = 0.3f), Offset(0f, y(it.endInclusive)), Size(w, y(it.start) - y(it.endInclusive)))
            }
            for (r in refLines) {
                drawLine(InkSoft, Offset(0f, y(r)), Offset(w, y(r)), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            }
            for (v in listOf(yMin + pad, yMax - pad)) {
                drawText(measurer, v.fmt(decimals), Offset(w + 6.dp.toPx(), y(v) - 8.dp.toPx()), LabType.small.copy(color = InkSoft))
            }

            val path = Path()
            var penDown = false
            values.forEachIndexed { i, v ->
                if (v == null) { penDown = false; return@forEachIndexed }
                if (penDown) path.lineTo(x(i), y(v)) else path.moveTo(x(i), y(v))
                penDown = true
            }
            drawPath(path, lineColor, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            if (failAbove != null) {
                values.forEachIndexed { i, v -> if (v != null && v > failAbove) drawCircle(Fail, 3.5.dp.toPx(), Offset(x(i), y(v))) }
            }
            if (values.size == 1) values[0]?.let { drawCircle(lineColor, 4.dp.toPx(), Offset(x(0), y(it))) }

            selected?.let { i ->
                drawLine(InkSoft, Offset(x(i), 0f), Offset(x(i), h), 1.dp.toPx())
                values.getOrNull(i)?.let { drawCircle(lineColor, 5.dp.toPx(), Offset(x(i), y(it))) }
            }
        }
    }
}
