package com.team11.labdip.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.team11.labdip.data.model.Status
import com.team11.labdip.ui.theme.Fail
import com.team11.labdip.ui.theme.Glass
import com.team11.labdip.ui.theme.GlassEdge
import com.team11.labdip.ui.theme.GlassStrong
import com.team11.labdip.ui.theme.Ink
import com.team11.labdip.ui.theme.LabType
import com.team11.labdip.ui.theme.Marginal
import com.team11.labdip.ui.theme.Pass
import java.util.Locale

val CardShape = RoundedCornerShape(28.dp)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(CardShape)
            .background(Brush.verticalGradient(listOf(Glass, Color(0x0AFFFFFF))))
            .border(1.dp, GlassEdge, CardShape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(padding),
        content = content,
    )
}

/** Round frosted icon button, as in the mock-up's top bar and metric row. */
@Composable
fun GlassIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    selected: Boolean = false,
    tint: Color = Ink,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    if (selected) listOf(Color(0x55FFFFFF), GlassStrong) else listOf(GlassStrong, Glass),
                ),
            )
            .border(1.dp, if (selected) Color(0x55FFFFFF) else GlassEdge, CircleShape)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.45f))
    }
}

@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 56.dp),
        shape = CircleShape,
        border = BorderStroke(1.dp, Color(0x40FFFFFF)),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = Glass, contentColor = Ink),
    ) { Text(text, style = LabType.body) }
}

fun Status.color(): Color = when (this) {
    Status.Pass -> Pass
    Status.Marginal -> Marginal
    Status.Fail -> Fail
}

fun Status.icon(): ImageVector = when (this) {
    Status.Pass -> Icons.Rounded.Check
    Status.Marginal -> Icons.Rounded.Remove
    Status.Fail -> Icons.Rounded.Close
}

fun Status.word(): String = when (this) {
    Status.Pass -> "Pass"
    Status.Marginal -> "Marginal"
    Status.Fail -> "Fail"
}

/** Status as word + icon so it never relies on colour alone. */
@Composable
fun StatusLabel(status: Status, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = LabType.status) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(status.icon(), contentDescription = null, tint = status.color(), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        Text(status.word(), style = style, color = status.color())
    }
}

fun Double.fmt(decimals: Int = 1): String = String.format(Locale.US, "%.${decimals}f", this)

fun Double?.fmtOr(decimals: Int = 1, dash: String = "–"): String = this?.fmt(decimals) ?: dash

fun ago(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "$s s ago"
        s < 3600 -> "${s / 60} min ago"
        else -> "${s / 3600} h ago"
    }
}

@Composable
fun SectionGap() = Spacer(Modifier.padding(top = 12.dp))
