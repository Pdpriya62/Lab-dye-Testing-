package com.team11.labdip.ui.samples

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.team11.labdip.data.db.SampleSummary
import com.team11.labdip.data.model.Status
import com.team11.labdip.ui.components.CardShape
import com.team11.labdip.ui.components.GlassCard
import com.team11.labdip.ui.components.GlassIcon
import com.team11.labdip.ui.components.PillButton
import com.team11.labdip.ui.components.StatusLabel
import com.team11.labdip.ui.components.ago
import com.team11.labdip.ui.components.fmt
import com.team11.labdip.ui.theme.Backdrop
import com.team11.labdip.ui.theme.Fail
import com.team11.labdip.ui.theme.Ink
import com.team11.labdip.ui.theme.InkSoft
import com.team11.labdip.ui.theme.LabDipTheme
import com.team11.labdip.ui.theme.LabType
import com.team11.labdip.ui.trend.TrendCharts
import kotlinx.coroutines.launch

@Composable
fun SamplesRoute(snackbar: SnackbarHostState, onOpen: (String) -> Unit, vm: SamplesViewModel = viewModel(factory = SamplesViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    SamplesScreen(state, onOpen) { id ->
        vm.delete(id)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar("Sample $id deleted", actionLabel = "Undo", withDismissAction = true) == SnackbarResult.ActionPerformed) {
                vm.undoDelete()
            }
        }
    }
}

@Composable
fun SamplesScreen(state: SamplesUiState, onOpen: (String) -> Unit, onDelete: (String) -> Unit) {
    val now = System.currentTimeMillis()
    Backdrop(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Samples", style = LabType.title, color = Ink, modifier = Modifier.semantics { heading() })
                Text("Swipe left to delete. Tap to open.", style = LabType.body, color = InkSoft)
            }
            if (state.rows.isEmpty() && !state.loading) {
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text("No samples yet. Readings appear here once the rig publishes.", style = LabType.body, color = InkSoft)
                    }
                }
            }
            items(state.rows, key = { it.summary.sampleId }) { row ->
                SwipeRow(row, now, onOpen, onDelete)
            }
        }
    }
}

@Composable
private fun SwipeRow(row: SampleRow, now: Long, onOpen: (String) -> Unit, onDelete: (String) -> Unit) {
    val id = row.summary.sampleId
    val swipe = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it == SwipeToDismissBoxValue.EndToStart) { onDelete(id); true } else false
    })
    SwipeToDismissBox(
        state = swipe,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(Modifier.fillMaxSize().clip(CardShape).background(Color(0xFF3A1F1F)).padding(end = 24.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Fail)
            }
        },
    ) {
        GlassCard(
            Modifier.fillMaxWidth().semantics {
                customActions = listOf(CustomAccessibilityAction("Delete sample $id") { onDelete(id); true })
            },
            onClick = { onOpen(id) },
        ) {
            SampleRowContent(row.summary, row.status, now)
        }
    }
}

@Composable
private fun SampleRowContent(s: SampleSummary, status: Status, now: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(s.sampleId, style = LabType.cardTitle, color = Ink)
            val meta = listOfNotNull(s.fabric, s.dye?.replace('_', ' ')).joinToString(", ").ifEmpty { "No fabric or dye set" }
            Text(meta, style = LabType.label, color = InkSoft)
            Text("${s.n} readings · ${ago(now - s.lastAt)}", style = LabType.small, color = InkSoft, modifier = Modifier.padding(top = 2.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("ΔE ${s.lastDeltaE.fmt(2)}", style = LabType.value, color = Ink)
            StatusLabel(status, style = LabType.label)
        }
    }
}

@Composable
fun SampleDetailRoute(onBack: () -> Unit, vm: SampleDetailViewModel = viewModel(factory = SampleDetailViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    SampleDetailScreen(state, onBack) {
        scope.launch {
            val uri = vm.exportCsv()
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Lab dip ${state.sampleId}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Export CSV"))
        }
    }
}

@Composable
fun SampleDetailScreen(state: SampleDetailUiState, onBack: () -> Unit, onExport: () -> Unit) {
    Backdrop(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.sampleId, style = LabType.title, color = Ink, modifier = Modifier.semantics { heading() })
                    val first = state.rows.firstOrNull()
                    Text(
                        listOfNotNull(first?.fabric, first?.dye?.replace('_', ' ')).joinToString(", ").ifEmpty { "${state.rows.size} readings" },
                        style = LabType.body, color = InkSoft,
                    )
                }
                GlassIcon(Icons.Rounded.IosShare, "Export CSV", onClick = onExport)
            }
            Spacer(Modifier.height(16.dp))
            state.rows.lastOrNull()?.let { last ->
                com.team11.labdip.ui.components.SplitSwatch(
                    measured = Color(last.r, last.g, last.b),
                    target = if (last.targetSet && last.targetL != null && last.targetA != null && last.targetB != null)
                        com.team11.labdip.data.model.labToRgb(last.targetL, last.targetA, last.targetB).let { Color(it.first, it.second, it.third) }
                    else null,
                    deltaE = last.deltaE,
                    status = com.team11.labdip.data.model.statusFor(last.deltaE, state.settings.passMax, state.settings.marginalMax),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Final reading", style = LabType.small, color = InkSoft, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
            }
            TrendCharts(state.rows, state.settings, "All ${state.rows.size}")
            Spacer(Modifier.height(16.dp))
            PillButton("Export CSV", onExport, Modifier.fillMaxWidth(), enabled = state.rows.isNotEmpty())
        }
    }
}

@Preview(widthDp = 380, heightDp = 700)
@Composable
private fun SamplesPreview() = LabDipTheme {
    val now = System.currentTimeMillis()
    SamplesScreen(
        SamplesUiState(
            listOf(
                SampleRow(SampleSummary("S002", now - 4_000, 120, "cotton", "reactive_red", 0.82), Status.Pass),
                SampleRow(SampleSummary("S001", now - 3_600_000, 640, "viscose", "reactive_green", 2.31), Status.Fail),
            ),
            loading = false,
        ),
        {}, {},
    )
}
