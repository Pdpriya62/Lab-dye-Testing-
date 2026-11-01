package com.team11.labdip.ui.trend

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HotTub
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.team11.labdip.data.db.ReadingEntity
import com.team11.labdip.data.model.statusFor
import com.team11.labdip.data.settings.AppSettings
import com.team11.labdip.ui.components.GlassCard
import com.team11.labdip.ui.components.GlassIcon
import com.team11.labdip.ui.components.LineChart
import com.team11.labdip.ui.components.StatusLabel
import com.team11.labdip.ui.components.fmt
import com.team11.labdip.ui.components.fmtOr
import com.team11.labdip.ui.theme.Amber
import com.team11.labdip.ui.theme.Aqua
import com.team11.labdip.ui.theme.Backdrop
import com.team11.labdip.ui.theme.Ember
import com.team11.labdip.ui.theme.Ink
import com.team11.labdip.ui.theme.InkSoft
import com.team11.labdip.ui.theme.LabDipTheme
import com.team11.labdip.ui.theme.LabType
import com.team11.labdip.ui.theme.Lime

@Composable
fun TrendRoute(vm: TrendViewModel = viewModel(factory = TrendViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    TrendScreen(state, vm::setRange)
}

@Composable
fun TrendScreen(state: TrendUiState, onRange: (Range) -> Unit) {
    Backdrop(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp),
        ) {
            Text("Trend", style = LabType.title, color = Ink, modifier = Modifier.padding(top = 12.dp).semantics { heading() })
            Text(
                state.sampleId?.let { "Sample $it, ${state.rows.size} readings" } ?: "No readings yet",
                style = LabType.body, color = InkSoft,
            )
            Spacer(Modifier.height(12.dp))
            RangeTabs(state.range, onRange)
            Spacer(Modifier.height(12.dp))
            if (state.rows.isEmpty() && !state.loading) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("No samples yet. Readings appear here once the rig publishes.", style = LabType.body, color = InkSoft)
                }
            } else {
                TrendCharts(state.rows, state.settings, state.range.label)
            }
        }
    }
}

/** Text tabs like the mock-up's "All time / Today / Last week" row. */
@Composable
fun RangeTabs(current: Range, onRange: (Range) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        for (r in Range.entries) {
            val sel = r == current
            Text(
                r.label,
                style = LabType.body.copy(fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal),
                color = if (sel) Ink else InkSoft,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .semantics { selected = sel }
                    .clickable(role = Role.Tab) { onRange(r) }
                    .padding(vertical = 12.dp),
            )
        }
    }
}

/** Shared by Trend and Sample detail. [rows] run oldest to newest. */
@Composable
fun TrendCharts(rows: List<ReadingEntity>, s: AppSettings, rangeLabel: String) {
    val last = rows.lastOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChartCard(Icons.Rounded.Palette, "Colour difference", rangeLabel, last?.deltaE?.fmt(2) ?: "–", "ΔE", Ink, footer = {
            last?.let { StatusLabel(statusFor(it.deltaE, s.passMax, s.marginalMax), style = LabType.label.copy(fontWeight = FontWeight.Bold)) }
        }) {
            LineChart(rows.map { it.deltaE }, Ink, "ΔE", decimals = 2, refLines = listOf(s.passMax, s.marginalMax), failAbove = s.marginalMax)
        }
        val sp = last?.setpointC
        ChartCard(Icons.Rounded.HotTub, "Bath temperature", rangeLabel, last?.bathTemp.fmtOr(1), "°C", Ember, footer = {
            Text(if (sp != null) "Setpoint ${sp.fmt(0)} ± ${s.bathTolerance.fmt(0)} °C" else "No setpoint", style = LabType.small, color = InkSoft)
        }) {
            LineChart(
                rows.map { it.bathTemp }, Ember, "°C",
                band = sp?.let { (it - s.bathTolerance)..(it + s.bathTolerance) },
                refLines = listOfNotNull(sp),
            )
        }
        ChartCard(Icons.Rounded.Thermostat, "Ambient temperature", rangeLabel, last?.ambientTemp.fmtOr(1), "°C", Amber) {
            LineChart(rows.map { it.ambientTemp }, Amber, "°C")
        }
        ChartCard(Icons.Rounded.WaterDrop, "Humidity", rangeLabel, last?.humidity.fmtOr(0), "%", Aqua) {
            LineChart(rows.map { it.humidity }, Aqua, "%", decimals = 0)
        }
        ChartCard(Icons.Rounded.WbSunny, "Light", rangeLabel, last?.lightLux?.toString() ?: "–", "lx", Lime, footer = {
            Text("Normal ${s.luxMin.fmt(0)}–${s.luxMax.fmt(0)} lx", style = LabType.small, color = InkSoft)
        }) {
            LineChart(rows.map { it.lightLux?.toDouble() }, Lime, "lx", decimals = 0, band = s.luxMin..s.luxMax)
        }
    }
}

@Composable
private fun ChartCard(
    icon: ImageVector,
    title: String,
    rangeLabel: String,
    value: String,
    unit: String,
    accent: Color,
    footer: @Composable () -> Unit = {},
    chart: @Composable () -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIcon(icon, contentDescription = null, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Text(title, style = LabType.cardTitle, color = Ink, modifier = Modifier.weight(1f))
            Text(rangeLabel, style = LabType.small, color = InkSoft)
        }
        Spacer(Modifier.height(10.dp))
        chart()
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = LabType.big, color = accent)
            Text(" $unit", style = LabType.value, color = accent, modifier = Modifier.padding(bottom = 3.dp))
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) { footer() }
        }
    }
}

@Preview(widthDp = 380, heightDp = 1600)
@Composable
private fun TrendPreview() = LabDipTheme {
    val rows = (0 until 40).map { i ->
        ReadingEntity(
            sampleId = "S001", deviceTime = "", receivedAt = i * 3000L, deltaE = 0.6 + (i % 9) * 0.22, r = 180, g = 40, b = 50,
            labL = 41.0, labA = 58.0, labB = 30.0, ambientTemp = 27.0 + (i % 5) * 0.2, humidity = 55.0 + i % 4, lightLux = 1100 + i * 5,
            bathTemp = if (i in 18..21) null else 58.0 + (i % 7) * 0.6, bathAlert = false, setpointC = 60.0,
            targetL = null, targetA = null, targetB = null, targetSet = false, fabric = null, dye = null, dyeConc = null,
        )
    }
    TrendScreen(TrendUiState("S001", rows, Range.Last100, AppSettings(), loading = false)) {}
}
