package com.team11.labdip.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.team11.labdip.data.model.CamResult
import com.team11.labdip.data.model.CamShot
import com.team11.labdip.data.model.Status
import com.team11.labdip.ui.components.GlassCard
import com.team11.labdip.ui.components.LevelnessGrid
import com.team11.labdip.ui.components.SplitSwatch
import com.team11.labdip.ui.components.ago
import com.team11.labdip.ui.components.color
import com.team11.labdip.ui.components.fmt
import com.team11.labdip.ui.components.icon
import com.team11.labdip.ui.theme.Backdrop
import com.team11.labdip.ui.theme.Ink
import com.team11.labdip.ui.theme.InkSoft
import com.team11.labdip.ui.theme.LabDipTheme
import com.team11.labdip.ui.theme.LabType
import com.team11.labdip.ui.theme.Marginal

/** Camera-station result: swatch vs target (ΔE2000), levelness grid, agreement with the sensor. */
@Composable
fun CameraCard(state: LiveUiState, modifier: Modifier = Modifier) {
    val shot = state.cam ?: return
    val c = shot.result
    GlassCard(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PhotoCamera, contentDescription = null, tint = Ink, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("Camera · sample ${c.sampleId}", style = LabType.cardTitle, color = Ink, modifier = Modifier.weight(1f))
            Text(ago(state.camAgeMs), style = LabType.label, color = InkSoft)
        }

        val warnings = buildList {
            if (state.camOtherSample) add("Camera result is for sample ${c.sampleId}, live reading is ${state.reading?.sampleId}.")
            if (state.camOld) add("Camera result is ${ago(state.camAgeMs)} old.")
            if (c.roi == "centre") add("Patch not found, measured the image centre.")
            if (c.white == "none") add("No white reference: colour not corrected.")
        }
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (w in warnings) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = Marginal, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(w, style = LabType.label, color = Marginal)
                }
            }
        }

        Spacer(Modifier.padding(top = 12.dp))
        SplitSwatch(
            measured = Color(c.red, c.green, c.blue),
            target = state.camTarget?.let { Color(it.r, it.g, it.b) },
            deltaE = c.deltaE2000,
            status = state.camStatus,
            label = "ΔE2000",
            spokenLabel = "Delta E 2000",
            modifier = Modifier.fillMaxWidth(),
        )
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val st = state.camStatus
            if (st != null) com.team11.labdip.ui.components.StatusLabel(st)
            else Text("No target set. Send TARGET or CAPTURE to the rig.", style = LabType.label, color = InkSoft, textAlign = TextAlign.Center)
        }

        NumberRow("Camera Lab", "${c.labL.fmt(1)}  ${c.labA.fmt(1)}  ${c.labB.fmt(1)}")
        if (c.sensorL != null && c.sensorA != null && c.sensorB != null) {
            NumberRow("Sensor Lab", "${c.sensorL.fmt(1)}  ${c.sensorA.fmt(1)}  ${c.sensorB.fmt(1)}")
        }
        c.camVsSensor?.let { NumberRow("Camera vs sensor", "ΔE2000 ${it.fmt(2)}") }

        Text("Levelness", style = LabType.cardTitle, color = Ink, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        val tol = c.levelTol ?: 1.0
        LevelnessGrid(c, tol, Modifier.align(Alignment.CenterHorizontally))
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when (c.uniform) {
                null -> Text("No levelness data", style = LabType.status, color = InkSoft)
                else -> {
                    val st = if (c.uniform) Status.Pass else Status.Fail
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(st.icon(), contentDescription = null, tint = st.color(), modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (c.uniform) "Uniform" else "Uneven", style = LabType.status, color = st.color())
                    }
                }
            }
            if (c.levelnessMax != null && c.levelnessMean != null) {
                Text(
                    "Max ${c.levelnessMax.fmt(2)}, mean ${c.levelnessMean.fmt(2)} ΔE2000 (limit ${tol.fmt(1)})",
                    style = LabType.label, color = InkSoft, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun NumberRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = LabType.label, color = InkSoft, modifier = Modifier.weight(1f))
        Text(value, style = LabType.value, color = Ink)
    }
}

internal val previewCam = CamResult(
    sampleId = "S001", timestamp = "2026-11-12T22:55:15", labL = 82.13, labA = -2.95, labB = 14.58, red = 210, green = 205, blue = 177,
    grid = 3,
    cells = listOf(
        listOf(82.4, -3.1, 15.2, 0.71), null, listOf(81.9, -2.8, 14.1, 0.6),
        listOf(82.0, -3.0, 14.5, 0.2), listOf(82.1, -2.9, 14.6, 0.1), listOf(78.2, -2.0, 19.9, 2.57),
        listOf(82.3, -3.0, 14.4, 0.3), listOf(82.2, -3.1, 14.7, 1.4), listOf(82.0, -2.9, 14.5, 0.2),
    ),
    roi = "auto", white = "flat", levelnessMax = 2.57, levelnessMean = 0.76, levelTol = 1.0, uniform = false,
    targetL = 82.0, targetA = -3.0, targetB = 15.0, deltaE2000 = 0.27, sensorL = 80.5, sensorA = -3.1, sensorB = 18.2, camVsSensor = 2.38,
)

@Preview(widthDp = 380, heightDp = 1000)
@Composable
private fun CameraCardPreview() = LabDipTheme {
    Backdrop(Modifier.padding(0.dp)) {
        CameraCard(LiveUiState(cam = CamShot(previewCam, System.currentTimeMillis() - 30_000)), Modifier.padding(16.dp))
    }
}

@Preview(widthDp = 380, heightDp = 900)
@Composable
private fun CameraCardNoTargetPreview() = LabDipTheme {
    val c = previewCam.copy(
        grid = 2, cells = listOf(listOf(82.0, -3.0, 14.5, 0.2), listOf(82.1, -2.9, 14.6, 0.4), null, listOf(82.3, -3.0, 14.4, 0.3)),
        levelnessMax = 0.4, levelnessMean = 0.3, uniform = true, targetL = null, targetA = null, targetB = null, deltaE2000 = null,
        sensorL = null, sensorA = null, sensorB = null, camVsSensor = null, white = "none",
    )
    Backdrop(Modifier.padding(0.dp)) {
        CameraCard(LiveUiState(cam = CamShot(c, System.currentTimeMillis() - 5_000)), Modifier.padding(16.dp))
    }
}
