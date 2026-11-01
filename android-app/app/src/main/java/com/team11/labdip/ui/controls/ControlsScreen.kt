package com.team11.labdip.ui.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.team11.labdip.data.mqtt.ConnectionState
import com.team11.labdip.ui.components.GlassCard
import com.team11.labdip.ui.components.GlassIcon
import com.team11.labdip.ui.components.LabField
import com.team11.labdip.ui.components.PillButton
import com.team11.labdip.ui.theme.Backdrop
import com.team11.labdip.ui.theme.Fail
import com.team11.labdip.ui.theme.Ink
import com.team11.labdip.ui.theme.InkSoft
import com.team11.labdip.ui.theme.LabDipTheme
import com.team11.labdip.ui.theme.LabType
import com.team11.labdip.ui.theme.Marginal
import com.team11.labdip.ui.theme.Orchid
import com.team11.labdip.ui.theme.Pass
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ControlsRoute(onBack: () -> Unit, vm: ControlsViewModel = viewModel(factory = ControlsViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    ControlsScreen(
        state, onBack, vm::edit,
        onCapture = vm::capture, onSetId = vm::setSampleId, onSetTarget = vm::setTarget,
        onSetBath = vm::setBath, onSetDetails = vm::setDetails,
    )
}

@Composable
fun ControlsScreen(
    state: ControlsUiState,
    onBack: () -> Unit,
    onEdit: ((ControlsForm) -> ControlsForm) -> Unit,
    onCapture: () -> Unit,
    onSetId: () -> Unit,
    onSetTarget: () -> Unit,
    onSetBath: () -> Unit,
    onSetDetails: () -> Unit,
) {
    val f = state.form
    val on = state.canSend
    Backdrop(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIcon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", onClick = onBack)
                Spacer(Modifier.width(12.dp))
                Text("Rig controls", style = LabType.title, color = Ink, modifier = Modifier.semantics { heading() })
            }
            Text(
                if (state.connection is ConnectionState.Connected) "Same commands as the Serial Monitor. Sent to ${state.commandTopic}."
                else "Not connected to the broker. Commands can't be sent.",
                style = LabType.body, color = if (state.connection is ConnectionState.Connected) InkSoft else Fail,
            )

            if (state.rigOffline) {
                Text("Rig offline: commands will get no reply until the ESP32 is back.", style = LabType.body, color = Fail)
            }

            Section("Target shade") {
                PillButton("Capture current colour as target", onCapture, Modifier.fillMaxWidth(), enabled = on)
                Text("Or enter the target Lab values:", style = LabType.small, color = InkSoft)
                Row {
                    LabField("L", f.targetL, null, modifier = Modifier.weight(1f), signed = true) { v -> onEdit { it.copy(targetL = v) } }
                    Spacer(Modifier.width(8.dp))
                    LabField("a", f.targetA, null, modifier = Modifier.weight(1f), signed = true) { v -> onEdit { it.copy(targetA = v) } }
                    Spacer(Modifier.width(8.dp))
                    LabField("b", f.targetB, null, modifier = Modifier.weight(1f), signed = true) { v -> onEdit { it.copy(targetB = v) } }
                }
                PillButton("Set target", onSetTarget, Modifier.fillMaxWidth(), enabled = on)
            }

            Section("Sample") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabField("Sample ID", f.sampleId, null, modifier = Modifier.weight(1f)) { v -> onEdit { it.copy(sampleId = v) } }
                    Spacer(Modifier.width(8.dp))
                    PillButton("Set", onSetId, enabled = on)
                }
                LabField("Fabric", f.fabric, null) { v -> onEdit { it.copy(fabric = v) } }
                Row {
                    LabField("Dye", f.dye, null, modifier = Modifier.weight(2f)) { v -> onEdit { it.copy(dye = v) } }
                    Spacer(Modifier.width(8.dp))
                    LabField("Conc. % owf", f.conc, null, KeyboardType.Decimal, Modifier.weight(1f)) { v -> onEdit { it.copy(conc = v) } }
                }
                PillButton("Set fabric and dye", onSetDetails, Modifier.fillMaxWidth(), enabled = on)
            }

            Section("Dye bath") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabField("Setpoint °C", f.bath, null, KeyboardType.Decimal, Modifier.weight(1f)) { v -> onEdit { it.copy(bath = v) } }
                    Spacer(Modifier.width(8.dp))
                    PillButton("Set", onSetBath, enabled = on)
                }
            }

            state.error?.let { Text(it, style = LabType.body, color = Fail) }

            Section("Rig replies") {
                if (state.log.isEmpty()) Text("Nothing sent yet.", style = LabType.body, color = InkSoft)
                for (line in state.log) LogRow(line)
            }
            Text(
                "Demo setup: the broker is public, so anyone who knows the topic can send commands.",
                style = LabType.small, color = InkSoft,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(title, style = LabType.cardTitle, color = Ink)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

/** Every line carries a word as well as an icon, so success and failure never depend on colour. */
@Composable
private fun LogRow(line: LogLine) {
    val ok = line.text.startsWith("OK")
    val (icon, tint, prefix) = when (line.kind) {
        LogKind.Sent -> Triple(Icons.Rounded.ArrowUpward, Orchid, "Sent")
        LogKind.Reply -> if (ok) Triple(Icons.Rounded.Check, Pass, "Rig") else Triple(Icons.Rounded.Close, Fail, "Rig")
        LogKind.Problem -> Triple(Icons.Rounded.WarningAmber, Marginal, "Problem")
    }
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp).padding(top = 2.dp))
        Spacer(Modifier.width(8.dp))
        Text(timeFormat.format(Date(line.at)), style = LabType.label, color = InkSoft)
        Spacer(Modifier.width(8.dp))
        Text("$prefix: ${line.text}", style = LabType.label, color = Ink)
    }
}

@Preview(widthDp = 380, heightDp = 1400)
@Composable
private fun ControlsPreview() = LabDipTheme {
    ControlsScreen(
        ControlsUiState(
            connection = ConnectionState.Connected("broker.hivemq.com", "rvu/team11/labdip/data"),
            commandTopic = "rvu/team11/labdip/cmd",
            form = ControlsForm(sampleId = "S002", fabric = "cotton", dye = "reactive red", conc = "2.0", bath = "60"),
            log = listOf(
                LogLine(0, LogKind.Reply, "OK sample=S002"),
                LogLine(0, LogKind.Sent, "ID S002"),
                LogLine(0, LogKind.Problem, "No reply from the rig. Is it powered and on Wi-Fi?"),
            ),
        ),
        {}, {}, {}, {}, {}, {}, {},
    )
}
