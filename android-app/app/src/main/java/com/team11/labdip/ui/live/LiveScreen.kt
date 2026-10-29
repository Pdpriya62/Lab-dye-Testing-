package com.team11.labdip.ui.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HotTub
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.team11.labdip.data.db.ReadingEntity
import com.team11.labdip.data.mqtt.ConnectionState
import com.team11.labdip.ui.components.GlassCard
import com.team11.labdip.ui.components.GlassIcon
import com.team11.labdip.ui.components.PillButton
import com.team11.labdip.ui.components.SplitSwatch
import com.team11.labdip.ui.components.StatusLabel
import com.team11.labdip.ui.components.TickGauge
import com.team11.labdip.ui.components.ago
import com.team11.labdip.ui.components.color
import com.team11.labdip.ui.components.fmt
import com.team11.labdip.ui.components.fmtOr
import com.team11.labdip.ui.components.icon
import com.team11.labdip.ui.theme.Amber
import com.team11.labdip.ui.theme.Aqua
import com.team11.labdip.ui.theme.Backdrop
import com.team11.labdip.ui.theme.Ember
import com.team11.labdip.ui.theme.Fail
import com.team11.labdip.ui.theme.Ink
import com.team11.labdip.ui.theme.InkSoft
import com.team11.labdip.ui.theme.LabDipTheme
import com.team11.labdip.ui.theme.LabType
import com.team11.labdip.ui.theme.Lime
import com.team11.labdip.ui.theme.Marginal
import com.team11.labdip.ui.theme.Pass

@Composable
fun LiveRoute(onOpenSettings: () -> Unit, vm: LiveViewModel = viewModel(factory = LiveViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    LiveScreen(state, onSelect = vm::select, onOpenSettings = onOpenSettings)
}

@Composable
fun LiveScreen(state: LiveUiState, onSelect: (Metric) -> Unit, onOpenSettings: () -> Unit) {
    Backdrop(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            Header(state)
            Spacer(Modifier.height(16.dp))
            val r = state.reading
            if (r == null) {
                EmptyHero(state.connection, onOpenSettings)
            } else {
                Column(Modifier.alpha(if (state.stale) 0.55f else 1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HeroGauge(state, r, onSelect)
                    SplitSwatch(
                        measured = Color(r.r, r.g, r.b),
                        target = state.target?.let { Color(it.r, it.g, it.b) },
                        deltaE = r.deltaE,
                        status = state.status,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BathCard(state, r, Modifier.weight(1f)) { onSelect(Metric.Bath) }
                        ReadingCard(Icons.Rounded.Thermostat, "Ambient", r.ambientTemp.fmtOr(1), "°C", Amber, "DHT11 air temperature", Modifier.weight(1f)) { onSelect(Metric.Ambient) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ReadingCard(Icons.Rounded.WaterDrop, "Humidity", r.humidity.fmtOr(0), "%", Aqua, "Relative humidity", Modifier.weight(1f)) { onSelect(Metric.Humidity) }
                        LightCard(state, r, Modifier.weight(1f)) { onSelect(Metric.Light) }
                    }
                    LabCard(r)
                }
                if (state.dropped > 0) {
                    Text(
                        "${state.dropped} malformed messages ignored",
                        style = LabType.small,
                        color = InkSoft,
                        modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = state.bathAlert,
            enter = slideInVertically(tween(200)) { -it },
            exit = slideOutVertically(tween(200)) { -it },
            modifier = Modifier.statusBarsPadding().padding(16.dp),
        ) { state.reading?.let { BathAlertBanner(it, state.settings.bathTolerance) } }
    }
}

@Composable
private fun Header(state: LiveUiState) {
    val r = state.reading
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            val meta = listOfNotNull(r?.fabric, r?.dye?.replace('_', ' ')).joinToString(", ")
            Text(
                if (r == null) "No sample yet" else "Sample ${r.sampleId}" + if (meta.isNotEmpty()) " · $meta" else "",
                style = LabType.body,
                color = InkSoft,
            )
            Text("Live dip", style = LabType.title, color = Ink, modifier = Modifier.semantics { heading() })
        }
        ConnectionPill(state)
    }
}

@Composable
fun ConnectionPill(state: LiveUiState) {
    val (dot, text) = when (val c = state.connection) {
        is ConnectionState.Connected -> Pass to (if (state.reading != null) ago(state.ageMs) else "Connected")
        is ConnectionState.Connecting -> Marginal to "Connecting"
        is ConnectionState.Reconnecting -> Marginal to "Reconnecting"
        is ConnectionState.Offline -> Fail to "Offline"
        ConnectionState.Idle -> InkSoft to "Not connected"
    }
    Row(
        Modifier.clip(CircleShape).background(Color(0x1AFFFFFF)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(6.dp))
        Text(text, style = LabType.label, color = Ink)
    }
}

private data class GaugeSpec(
    val fraction: Float,
    val color: Color,
    val marks: List<Float>,
    val value: String,
    val unit: String,
    val caption: String,
    val title: String,
)

private fun gaugeSpec(state: LiveUiState, r: ReadingEntity): GaugeSpec {
    val s = state.settings
    return when (state.metric) {
        Metric.DeltaE -> {
            val max = s.marginalMax * 2
            GaugeSpec(
                (r.deltaE / max).toFloat(), state.status?.color() ?: Ink,
                listOf((s.passMax / max).toFloat(), (s.marginalMax / max).toFloat()),
                r.deltaE.fmt(2), "ΔE",
                if (state.target == null) "No target set. Send TARGET or CAPTURE to the rig." else "Pass at ${s.passMax.fmt(2)} or less, fail above ${s.marginalMax.fmt(2)}",
                "Colour difference (CIE76)",
            )
        }
        Metric.Bath -> {
            val sp = r.setpointC ?: 60.0
            val span = s.bathTolerance * 4
            val lo = sp - span
            GaugeSpec(
                r.bathTemp?.let { ((it - lo) / (2 * span)).toFloat() } ?: 0f,
                if (r.bathAlert) Fail else Ember,
                listOf(((sp - s.bathTolerance - lo) / (2 * span)).toFloat(), ((sp + s.bathTolerance - lo) / (2 * span)).toFloat()),
                r.bathTemp.fmtOr(1), "°C",
                if (r.bathTemp == null) "Bath sensor disconnected" else "Setpoint ${sp.fmt(0)} ± ${s.bathTolerance.fmt(0)} °C",
                "Dye-bath temperature",
            )
        }
        Metric.Ambient -> GaugeSpec(((r.ambientTemp ?: 0.0) / 50).toFloat(), Amber, emptyList(), r.ambientTemp.fmtOr(1), "°C", "Scale 0–50 °C", "Ambient temperature")
        Metric.Humidity -> GaugeSpec(((r.humidity ?: 0.0) / 100).toFloat(), Aqua, emptyList(), r.humidity.fmtOr(0), "%", "Relative humidity, 0–100 %", "Humidity")
        Metric.Light -> {
            val max = s.luxMax * 1.5
            GaugeSpec(
                ((r.lightLux ?: 0) / max).toFloat(), Lime,
                listOf((s.luxMin / max).toFloat(), (s.luxMax / max).toFloat()),
                r.lightLux?.toString() ?: "–", "lx",
                "Normal ${s.luxMin.fmt(0)}–${s.luxMax.fmt(0)} lx", "Light level",
            )
        }
    }
}

@Composable
private fun HeroGauge(state: LiveUiState, r: ReadingEntity, onSelect: (Metric) -> Unit) {
    val spec = gaugeSpec(state, r)
    GlassCard(Modifier.fillMaxWidth(), padding = 16.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            for (m in Metric.entries) {
                val sel = m == state.metric
                GlassIcon(
                    icon = m.icon(),
                    contentDescription = "Show ${m.label()}",
                    size = if (sel) 56.dp else 48.dp,
                    selected = sel,
                    onClick = { onSelect(m) },
                )
            }
        }
        Text(state.metric.label(), style = LabType.label, color = Ink, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = TextAlign.Center)
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.BottomCenter) {
            TickGauge(spec.fraction, spec.color, Modifier.fillMaxWidth().aspectRatio(2f), marks = spec.marks)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 4.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(spec.value, style = LabType.hero, color = Ink)
                    Text(" ${spec.unit}", style = LabType.value, color = InkSoft, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when (state.metric) {
                Metric.DeltaE -> state.status?.let { StatusLabel(it) }
                Metric.Bath -> RangeWord(if (r.bathTemp == null) null else !r.bathAlert, "In range", "Out of range")
                Metric.Light -> RangeWord(r.lightLux?.let { it >= state.settings.luxMin && it <= state.settings.luxMax }, "Normal", "Outside normal range")
                else -> Text(spec.title, style = LabType.status, color = Ink)
            }
            Text(spec.caption, style = LabType.label, color = InkSoft, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun RangeWord(ok: Boolean?, okText: String, badText: String) {
    val status = when (ok) {
        true -> com.team11.labdip.data.model.Status.Pass
        false -> com.team11.labdip.data.model.Status.Fail
        null -> null
    }
    if (status == null) {
        Text("No reading", style = LabType.status, color = InkSoft)
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(status.icon(), contentDescription = null, tint = status.color(), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (ok == true) okText else badText, style = LabType.status, color = status.color())
        }
    }
}

fun Metric.label() = when (this) {
    Metric.DeltaE -> "ΔE"
    Metric.Bath -> "Bath"
    Metric.Ambient -> "Ambient"
    Metric.Humidity -> "Humidity"
    Metric.Light -> "Light"
}

fun Metric.icon(): ImageVector = when (this) {
    Metric.DeltaE -> Icons.Rounded.Palette
    Metric.Bath -> Icons.Rounded.HotTub
    Metric.Ambient -> Icons.Rounded.Thermostat
    Metric.Humidity -> Icons.Rounded.WaterDrop
    Metric.Light -> Icons.Rounded.WbSunny
}

@Composable
private fun ReadingCard(
    icon: ImageVector,
    title: String,
    value: String,
    unit: String,
    accent: Color,
    caption: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    GlassCard(modifier, onClick = onClick) {
        GlassIcon(icon, contentDescription = null, size = 44.dp)
        Text(title, style = LabType.cardTitle, color = Ink, modifier = Modifier.padding(top = 12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = LabType.big, color = accent)
            Text(" $unit", style = LabType.value, color = accent, modifier = Modifier.padding(bottom = 3.dp))
        }
        Text(caption, style = LabType.small, color = InkSoft)
    }
}

@Composable
private fun BathCard(state: LiveUiState, r: ReadingEntity, modifier: Modifier, onClick: () -> Unit) {
    val caption = when {
        r.bathTemp == null -> "Bath sensor disconnected"
        else -> "Set ${r.setpointC.fmtOr(0)} ± ${state.settings.bathTolerance.fmt(0)} °C"
    }
    ReadingCard(Icons.Rounded.HotTub, "Bath", r.bathTemp.fmtOr(1), "°C", if (r.bathAlert) Fail else Ember, caption, modifier, onClick)
}

@Composable
private fun LightCard(state: LiveUiState, r: ReadingEntity, modifier: Modifier, onClick: () -> Unit) {
    val lux = r.lightLux
    val s = state.settings
    val caption = when {
        lux == null -> "No light reading"
        lux < s.luxMin -> "Too dim for judging shade"
        lux > s.luxMax -> "Brighter than normal range"
        else -> "Normal range"
    }
    ReadingCard(Icons.Rounded.WbSunny, "Light", lux?.toString() ?: "–", "lx", Lime, caption, modifier, onClick)
}

@Composable
private fun LabCard(r: ReadingEntity) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text("CIE Lab", style = LabType.cardTitle, color = Ink)
        Spacer(Modifier.height(8.dp))
        LabRow("Measured", r.labL, r.labA, r.labB)
        if (r.targetSet && r.targetL != null && r.targetA != null && r.targetB != null) {
            LabRow("Target", r.targetL, r.targetA, r.targetB)
        }
        Text("Device time ${r.deviceTime.replace('T', ' ')}", style = LabType.small, color = InkSoft, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun LabRow(label: String, l: Double, a: Double, b: Double) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = LabType.label, color = InkSoft, modifier = Modifier.weight(1f))
        for ((k, v) in listOf("L" to l, "a" to a, "b" to b)) {
            Text("$k ", style = LabType.label, color = InkSoft)
            Text(v.fmt(1), style = LabType.value, color = Ink, modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun EmptyHero(connection: ConnectionState, onOpenSettings: () -> Unit) {
    val (title, body, showSettings) = when (connection) {
        is ConnectionState.Connected -> Triple("Connected", "Waiting for the rig to publish on ${connection.topic}.", false)
        is ConnectionState.Offline -> Triple("Offline", "Can't reach ${connection.host}. Check Wi-Fi or broker settings.", true)
        is ConnectionState.Connecting -> Triple("Connecting", "Connecting to ${connection.host}…", false)
        is ConnectionState.Reconnecting -> Triple("Reconnecting", "Connection to ${connection.host} lost. Retrying…", false)
        ConnectionState.Idle -> Triple("Not connected", "Set up a broker to start receiving readings.", true)
    }
    GlassCard(Modifier.fillMaxWidth()) {
        TickGauge(0f, InkSoft, Modifier.fillMaxWidth().aspectRatio(2f))
        Text(title, style = LabType.status, color = Ink, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), textAlign = TextAlign.Center)
        Text(body, style = LabType.body, color = InkSoft, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        if (showSettings) {
            PillButton("Open settings", onOpenSettings, Modifier.fillMaxWidth().padding(top = 16.dp))
        }
    }
}

@Composable
private fun BathAlertBanner(r: ReadingEntity, tol: Double) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF3A1F1F), shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = Fail)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Bath temperature alert", style = LabType.cardTitle, color = Fail)
                Text(
                    "Bath ${r.bathTemp.fmtOr(1)} °C is outside ${r.setpointC.fmtOr(0)} ± ${tol.fmt(0)} °C.",
                    style = LabType.label,
                    color = Ink,
                )
            }
        }
    }
}

private val previewReading = ReadingEntity(
    sampleId = "S001", deviceTime = "2026-10-29T10:15:02", receivedAt = System.currentTimeMillis() - 2_000, deltaE = 1.42,
    r = 182, g = 40, b = 52, labL = 41.2, labA = 58.3, labB = 30.1, ambientTemp = 27.4, humidity = 58.0, lightLux = 1180,
    bathTemp = 61.8, bathAlert = false, setpointC = 60.0, targetL = 42.0, targetA = 57.0, targetB = 31.0, targetSet = true,
    fabric = "cotton", dye = "reactive_red", dyeConc = 2.0,
)

@Preview(widthDp = 380, heightDp = 1400)
@Composable
private fun LivePreview() = LabDipTheme {
    LiveScreen(LiveUiState(ConnectionState.Connected("broker.hivemq.com", "rvu/team11/labdip/data"), previewReading), {}, {})
}

@Preview(widthDp = 380, heightDp = 800)
@Composable
private fun LiveOfflinePreview() = LabDipTheme {
    LiveScreen(LiveUiState(ConnectionState.Offline("broker.hivemq.com", "timeout")), {}, {})
}
