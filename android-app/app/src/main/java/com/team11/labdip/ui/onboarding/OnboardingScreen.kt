package com.team11.labdip.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.team11.labdip.ui.theme.GlassEdge
import com.team11.labdip.ui.theme.GlassStrong
import com.team11.labdip.ui.theme.Ink
import com.team11.labdip.ui.theme.InkFaint
import com.team11.labdip.ui.theme.InkSoft
import com.team11.labdip.ui.theme.LabDipTheme
import com.team11.labdip.ui.theme.LabType
import com.team11.labdip.ui.theme.Lime
import com.team11.labdip.ui.theme.Orchid
import com.team11.labdip.ui.theme.Violet
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private data class Page(val icon: ImageVector, val accent: Color, val title: String, val body: String, val chips: List<String>)

private val pages = listOf(
    Page(
        Icons.Rounded.Palette, Orchid, "Every dip, judged at a glance.",
        "Measured colour beside the target, with ΔE and a pass, marginal or fail verdict, live from the rig.",
        listOf("Colour swatch", "ΔE", "Pass or fail"),
    ),
    Page(
        Icons.Rounded.Thermostat, Ember, "Bath in range, always.",
        "Bath temperature against setpoint. A banner and one buzz the moment it drifts.",
        listOf("Setpoint ± tolerance", "Alerts"),
    ),
    Page(
        Icons.Rounded.WbSunny, Amber, "Know the room you dye in.",
        "Ambient temperature, humidity and light level, flagged when light leaves its normal range.",
        listOf("°C", "%RH", "lx"),
    ),
    Page(
        Icons.Rounded.ShowChart, Aqua, "Watch the shade settle.",
        "ΔE and bath temperature charted over the latest readings for the current sample.",
        listOf("Trends", "Last 100 readings"),
    ),
    Page(
        Icons.Rounded.Inventory2, Lime, "Every sample, on record.",
        "History for each sample ID, kept on this phone and exported as CSV.",
        listOf("History", "CSV export"),
    ),
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    // Backdrop glow follows the feature being shown
    val accent by animateColorAsState(pages[pager.currentPage].accent, tween(600), label = "accent")
    Backdrop(Modifier.fillMaxSize(), accent = accent) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("LabDip Monitor", style = LabType.cardTitle, color = Ink, modifier = Modifier.weight(1f))
                TextButton(onClick = onFinish, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                    Text("Skip", style = LabType.body, color = InkSoft)
                }
            }
            HorizontalPager(pager, Modifier.fillMaxWidth().weight(1f)) { i ->
                FeaturePage(pages[i], offset = (pager.currentPage - i) + pager.currentPageOffsetFraction)
            }
            Dots(pager.currentPage, Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(24.dp))
            val last = pager.currentPage == pages.lastIndex
            PillButton(
                if (last) "Get started" else "Next",
                onClick = { if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FeaturePage(page: Page, offset: Float) {
    val away = min(abs(offset), 1f)
    Column(
        Modifier.fillMaxSize().graphicsLayer { alpha = 1f - 0.6f * away },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Parallax: the orb lags behind the text while swiping
        HeroOrb(page, Modifier.graphicsLayer { translationX = offset * size.width * 0.5f; scaleX = 1f - 0.25f * away; scaleY = 1f - 0.25f * away })
        Spacer(Modifier.height(32.dp))
        Text(page.title, style = LabType.title.copy(fontSize = 34.sp, lineHeight = 40.sp), color = Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(page.body, style = LabType.body, color = InkSoft, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp))
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (c in page.chips) {
                Text(
                    c, style = LabType.label, color = Ink,
                    modifier = Modifier.clip(CircleShape).background(GlassStrong).border(1.dp, GlassEdge, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** Glowing orb with a spinning dye ring and orbiting drops around the feature icon. */
@Composable
private fun HeroOrb(page: Page, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "orb")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(9_000, easing = LinearEasing)), label = "spin")
    val pulse by t.animateFloat(0.9f, 1.05f, infiniteRepeatable(tween(1_800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    Box(
        modifier.size(260.dp).drawBehind {
            val r = size.minDimension / 2
            drawCircle(Brush.radialGradient(listOf(page.accent.copy(alpha = 0.45f), Color.Transparent), radius = r * pulse), radius = r * pulse)
            rotate(spin) {
                drawCircle(
                    Brush.sweepGradient(listOf(Color.Transparent, page.accent, Violet, Color.Transparent)),
                    radius = r * 0.72f, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            repeat(3) { k ->
                val ang = Math.toRadians((spin * (1 + k * 0.35f) + k * 120f).toDouble())
                val rr = r * (0.6f + 0.13f * k)
                drawCircle(
                    page.accent.copy(alpha = 0.9f - k * 0.2f), radius = (5 - k).dp.toPx(),
                    center = center + Offset((cos(ang) * rr).toFloat(), (sin(ang) * rr).toFloat()),
                )
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        GlassIcon(page.icon, contentDescription = null, size = 104.dp, tint = page.accent, selected = true)
    }
}

@Composable
private fun Dots(current: Int, modifier: Modifier) {
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = "Page ${current + 1} of ${pages.size}" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        pages.forEachIndexed { i, p ->
            val w by animateDpAsState(if (i == current) 28.dp else 8.dp, label = "dot")
            Box(Modifier.height(8.dp).width(w).clip(CircleShape).background(if (i == current) p.accent else InkFaint))
        }
    }
}

@Preview(widthDp = 380, heightDp = 800)
@Composable
private fun OnboardingPreview() = LabDipTheme { OnboardingScreen {} }
