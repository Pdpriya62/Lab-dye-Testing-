package com.team11.labdip.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.team11.labdip.data.model.Status
import com.team11.labdip.ui.theme.LabType
import com.team11.labdip.ui.theme.SwatchSurround

/**
 * Lab-dip card: target and measured side by side on a neutral grey surround, ΔE on the seam.
 * With no target, the measured colour fills the card.
 */
@Composable
fun SplitSwatch(
    measured: Color,
    target: Color?,
    deltaE: Double,
    status: Status?,
    modifier: Modifier = Modifier,
) {
    val m by animateColorAsState(measured, tween(300), label = "swatch")
    val desc = buildString {
        append("Measured colour red ${(measured.red * 255).toInt()}, green ${(measured.green * 255).toInt()}, blue ${(measured.blue * 255).toInt()}. ")
        append("Delta E ${deltaE.fmt(2)}")
        if (status != null) append(", ${status.word().lowercase()}")
        if (target == null) append(". No target set")
    }
    Box(
        modifier
            .clip(CardShape)
            .background(SwatchSurround)
            .padding(14.dp)
            .semantics { contentDescription = desc },
    ) {
        Row(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(4.dp))) {
            if (target != null) {
                SwatchHalf(target, "Target", Modifier.weight(1f))
            }
            SwatchHalf(m, "Measured", Modifier.weight(1f))
        }
        val seamInk = if (((target ?: m).luminance() + m.luminance()) / 2 > 0.4f) Color.Black else Color.White
        Column(
            Modifier.align(if (target != null) Alignment.Center else Alignment.BottomCenter).padding(bottom = if (target != null) 0.dp else 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("ΔE ${deltaE.fmt(2)}", style = LabType.big, color = seamInk)
        }
    }
}

@Composable
private fun SwatchHalf(color: Color, label: String, modifier: Modifier) {
    val ink = if (color.luminance() > 0.4f) Color.Black else Color.White
    Box(modifier.fillMaxHeight().background(color)) {
        Text(label, style = LabType.label, color = ink.copy(alpha = 0.8f), modifier = Modifier.padding(10.dp))
    }
}

@Composable
fun SwatchDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(4.dp)).background(SwatchSurround).padding(3.dp)) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp)).background(color))
    }
}
