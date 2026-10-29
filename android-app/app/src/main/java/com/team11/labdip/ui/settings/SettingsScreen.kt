package com.team11.labdip.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.team11.labdip.ui.components.GlassCard
import com.team11.labdip.ui.components.PillButton
import com.team11.labdip.ui.theme.Backdrop
import com.team11.labdip.ui.theme.Fail
import com.team11.labdip.ui.theme.GlassEdge
import com.team11.labdip.ui.theme.Ink
import com.team11.labdip.ui.theme.InkSoft
import com.team11.labdip.ui.theme.LabDipTheme
import com.team11.labdip.ui.theme.LabType
import com.team11.labdip.ui.theme.Night
import com.team11.labdip.ui.theme.Pass

@Composable
fun SettingsRoute(firstRun: Boolean, onDone: () -> Unit, vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved && firstRun) onDone() }
    SettingsScreen(state, firstRun, vm::edit, vm::save, vm::test, vm::resetDefaults)
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    firstRun: Boolean,
    onEdit: ((SettingsForm) -> SettingsForm) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onReset: () -> Unit,
) {
    val f = state.form
    val e = state.errors
    Backdrop(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (firstRun) "Connect" else "Settings", style = LabType.title, color = Ink, modifier = Modifier.padding(top = 12.dp).semantics { heading() })
            Text(
                if (firstRun) "Defaults match the rig firmware. Change them only if you use your own broker." else "Broker, limits and target shade",
                style = LabType.body, color = InkSoft,
            )
            if (!state.loaded) return@Column

            Section("Broker") {
                Field("Host", f.host, e[Field.Host]) { v -> onEdit { it.copy(host = v) } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Field("Port", f.port, e[Field.Port], KeyboardType.Number, Modifier.weight(1f)) { v -> onEdit { it.copy(port = v) } }
                    Spacer(Modifier.padding(6.dp))
                    Text("TLS", style = LabType.body, color = Ink)
                    Spacer(Modifier.padding(4.dp))
                    Switch(
                        checked = f.tls,
                        onCheckedChange = { on -> onEdit { it.copy(tls = on, port = if (on && it.port == "1883") "8883" else if (!on && it.port == "8883") "1883" else it.port) } },
                        colors = SwitchDefaults.colors(checkedThumbColor = Night, checkedTrackColor = Ink),
                    )
                }
                if (!f.tls) Text("Without TLS anyone on the broker can read readings. Fine for demos only.", style = LabType.small, color = InkSoft)
                Field("Username (optional)", f.username, null) { v -> onEdit { it.copy(username = v) } }
                PasswordField(f.password) { v -> onEdit { it.copy(password = v) } }
                Field("Topic", f.topic, e[Field.Topic]) { v -> onEdit { it.copy(topic = v) } }
            }

            Section("Pass / fail limits") {
                Row {
                    Field("Pass at ΔE ≤", f.passMax, e[Field.PassMax], KeyboardType.Decimal, Modifier.weight(1f)) { v -> onEdit { it.copy(passMax = v) } }
                    Spacer(Modifier.padding(6.dp))
                    Field("Marginal up to", f.marginalMax, e[Field.MarginalMax], KeyboardType.Decimal, Modifier.weight(1f)) { v -> onEdit { it.copy(marginalMax = v) } }
                }
                Text("Above the marginal limit is a fail.", style = LabType.small, color = InkSoft)
                Field("Bath tolerance ± °C", f.bathTolerance, e[Field.BathTol], KeyboardType.Decimal) { v -> onEdit { it.copy(bathTolerance = v) } }
                Row {
                    Field("Light min lx", f.luxMin, e[Field.LuxMin], KeyboardType.Number, Modifier.weight(1f)) { v -> onEdit { it.copy(luxMin = v) } }
                    Spacer(Modifier.padding(6.dp))
                    Field("Light max lx", f.luxMax, e[Field.LuxMax], KeyboardType.Number, Modifier.weight(1f)) { v -> onEdit { it.copy(luxMax = v) } }
                }
            }

            Section("Target shade (optional)") {
                Text("Used only when the rig has no target set. Same values as the TARGET serial command.", style = LabType.small, color = InkSoft)
                Row {
                    Field("L", f.targetL, null, KeyboardType.Decimal, Modifier.weight(1f), signed = true) { v -> onEdit { it.copy(targetL = v) } }
                    Spacer(Modifier.padding(4.dp))
                    Field("a", f.targetA, null, KeyboardType.Decimal, Modifier.weight(1f), signed = true) { v -> onEdit { it.copy(targetA = v) } }
                    Spacer(Modifier.padding(4.dp))
                    Field("b", f.targetB, null, KeyboardType.Decimal, Modifier.weight(1f), signed = true) { v -> onEdit { it.copy(targetB = v) } }
                }
                e[Field.Target]?.let { Text(it, style = LabType.small, color = Fail) }
            }

            state.message?.let {
                Text(it, style = LabType.body, color = if (state.messageIsError) Fail else Pass)
            }
            PillButton(if (firstRun) "Connect" else "Save and reconnect", onSave, Modifier.fillMaxWidth(), enabled = !state.busy)
            PillButton("Test connection", onTest, Modifier.fillMaxWidth(), enabled = !state.busy)
            TextButton(onClick = onReset, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Restore defaults", style = LabType.body, color = InkSoft)
            }
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

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Ink,
    unfocusedBorderColor = GlassEdge.copy(alpha = 0.3f),
    focusedLabelColor = Ink,
    unfocusedLabelColor = InkSoft,
    cursorColor = Ink,
    focusedTextColor = Ink,
    unfocusedTextColor = Ink,
    errorBorderColor = Fail,
    errorLabelColor = Fail,
    errorSupportingTextColor = Fail,
)

@Composable
private fun Field(
    label: String,
    value: String,
    error: String?,
    keyboard: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth(),
    signed: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        // Decimal keypads on some phones have no minus key, so signed fields use the full keyboard
        keyboardOptions = KeyboardOptions(keyboardType = if (signed) KeyboardType.Text else keyboard),
        shape = RoundedCornerShape(16.dp),
        textStyle = LabType.body,
        colors = fieldColors(),
        modifier = modifier,
    )
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit) {
    var show by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("Password (optional)") },
        singleLine = true,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { show = !show }) {
                Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (show) "Hide password" else "Show password", tint = InkSoft)
            }
        },
        shape = RoundedCornerShape(16.dp),
        textStyle = LabType.body,
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Preview(widthDp = 380, heightDp = 1500)
@Composable
private fun SettingsPreview() = LabDipTheme {
    val form = remember { com.team11.labdip.data.settings.AppSettings().toForm() }
    SettingsScreen(SettingsUiState(form = form, loaded = true), firstRun = false, {}, {}, {}, {})
}
