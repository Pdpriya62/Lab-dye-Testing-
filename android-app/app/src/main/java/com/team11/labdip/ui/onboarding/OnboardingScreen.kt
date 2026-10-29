package com.team11.labdip.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.HotTub
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Texture
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team11.labdip.ui.components.GlassIcon
import com.team11.labdip.ui.components.PillButton
import com.team11.labdip.ui.theme.Amber
import com.team11.labdip.ui.theme.Aqua
import com.team11.labdip.ui.theme.Backdrop
import com.team11.labdip.ui.theme.Ember
import com.team11.labdip.ui.theme.Ink
import com.team11.labdip.ui.theme.InkFaint
import com.team11.labdip.ui.theme.InkSoft
import com.team11.labdip.ui.theme.LabDipTheme
import com.team11.labdip.ui.theme.LabType
import com.team11.labdip.ui.theme.Lime
import kotlinx.coroutines.launch

private data class Page(val title: String, val body: String)

private val pages = listOf(
    Page("Every dip, judged at a glance.", "Live colour, ΔE and pass or fail, straight from the rig."),
    Page("Bath in range, always.", "A banner and a buzz the moment the dye bath drifts off setpoint."),
    Page("Every sample, on record.", "Trends, history and CSV export for each sample ID."),
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    Backdrop(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Progress((pager.currentPage + 1f) / pages.size, Modifier.weight(1f))
                TextButton(onClick = onFinish) { Text("Skip", style = LabType.body, color = InkSoft) }
            }
            IconCluster(Modifier.fillMaxWidth().weight(1f))
            HorizontalPager(pager, Modifier.fillMaxWidth()) { i ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(pages[i].title, style = LabType.title.copy(fontSize = 34.sp, lineHeight = 40.sp), color = Ink, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(pages[i].body, style = LabType.body, color = InkSoft, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(32.dp))
            val last = pager.currentPage == pages.lastIndex
            PillButton(
                if (last) "Set up connection" else "Next",
                onClick = { if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Progress(fraction: Float, modifier: Modifier) {
    Box(
        modifier.height(4.dp).clip(CircleShape).background(InkFaint.copy(alpha = 0.5f))
            .semantics { contentDescription = "Step ${(fraction * pages.size).toInt()} of ${pages.size}" },
    ) {
        Box(Modifier.fillMaxWidth(fraction).height(4.dp).clip(CircleShape).background(Ink))
    }
}

/** Honeycomb of frosted icon bubbles, as on the mock-up's first screen. */
@Composable
private fun IconCluster(modifier: Modifier) {
    val bubbles = listOf(
        Triple(Icons.Rounded.Science, 0, -3) to Ink,
        Triple(Icons.Rounded.Palette, -1, -2) to Ember,
        Triple(Icons.Rounded.WaterDrop, 1, -2) to Aqua,
        Triple(Icons.Rounded.Checkroom, -2, -1) to Ink,
        Triple(Icons.Rounded.ColorLens, 0, -1) to Ember,
        Triple(Icons.Rounded.Sensors, 2, -1) to Lime,
        Triple(Icons.Rounded.Thermostat, -1, 0) to Amber,
        Triple(Icons.Rounded.HotTub, 1, 0) to Ember,
        Triple(Icons.Rounded.Opacity, -2, 1) to Aqua,
        Triple(Icons.Rounded.WbSunny, 0, 1) to Amber,
        Triple(Icons.Rounded.ShowChart, 2, 1) to Lime,
        Triple(Icons.Rounded.Timer, -1, 2) to Ink,
        Triple(Icons.Rounded.Texture, 1, 2) to Ink,
    )
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val cell = (minOf(maxWidth, maxHeight) / 6.5f)
        Box(Modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
            for ((spec, tint) in bubbles) {
                val (icon, col, row) = spec
                GlassIcon(
                    icon, contentDescription = null, size = cell * 0.92f, tint = tint, selected = col == 0 && row == -1,
                    modifier = Modifier.offset(x = cell * col * 0.9f, y = cell * row * 0.8f),
                )
            }
        }
    }
}

@Preview(widthDp = 380, heightDp = 800)
@Composable
private fun OnboardingPreview() = LabDipTheme { OnboardingScreen {} }
