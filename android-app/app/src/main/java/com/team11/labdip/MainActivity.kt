package com.team11.labdip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.team11.labdip.ui.controls.ControlsRoute
import com.team11.labdip.ui.live.LiveRoute
import com.team11.labdip.ui.onboarding.OnboardingScreen
import com.team11.labdip.ui.samples.SampleDetailRoute
import com.team11.labdip.ui.samples.SamplesRoute
import com.team11.labdip.ui.settings.SettingsRoute
import com.team11.labdip.ui.theme.Ink
import com.team11.labdip.ui.theme.InkSoft
import com.team11.labdip.ui.theme.LabDipTheme
import com.team11.labdip.ui.theme.LabType
import com.team11.labdip.ui.theme.NavSurface
import com.team11.labdip.ui.theme.Night
import com.team11.labdip.ui.trend.TrendRoute
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as LabDipApp).container
        setContent {
            LabDipTheme {
                val onboarded by remember { container.settings.settings.map { it.onboarded } }.collectAsState(initial = null)
                when (val o = onboarded) {
                    null -> Box(Modifier.fillMaxSize().background(Night))
                    else -> LabDipNav(onboarded = o)
                }
            }
        }
    }
}

private object Routes {
    const val ONBOARDING = "onboarding"
    const val CONNECT = "connect"
    const val LIVE = "live"
    const val TREND = "trend"
    const val SAMPLES = "samples"
    const val SAMPLE = "sample/{id}"
    const val SETTINGS = "settings"
    const val CONTROLS = "controls"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.LIVE, "Live", Icons.Rounded.Sensors),
    Tab(Routes.TREND, "Trend", Icons.Rounded.ShowChart),
    Tab(Routes.SAMPLES, "Samples", Icons.Rounded.Inventory2),
    Tab(Routes.SETTINGS, "Settings", Icons.Rounded.Settings),
)

@Composable
private fun LabDipNav(onboarded: Boolean) {
    val nav = rememberNavController()
    // Feature intro plays on every launch; first run then goes to Connect, later runs straight to Live.
    // Fixed at first composition: finishing setup flips the flag but must not rebuild the graph
    val returning = remember { onboarded }
    val snackbar = remember { SnackbarHostState() }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Scaffold(
        containerColor = Night,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (tabs.any { it.route == route }) BottomBar(nav, route) },
    ) { pad ->
        NavHost(
            nav,
            startDestination = Routes.ONBOARDING,
            modifier = Modifier.padding(bottom = pad.calculateBottomPadding()),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(onFinish = {
                    if (returning) nav.navigate(Routes.LIVE) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                    else nav.navigate(Routes.CONNECT)
                })
            }
            composable(Routes.CONNECT) {
                SettingsRoute(firstRun = true, onDone = {
                    nav.navigate(Routes.LIVE) { popUpTo(0) { inclusive = true } }
                })
            }
            composable(Routes.LIVE) {
                LiveRoute(onOpenSettings = { nav.switchTab(Routes.SETTINGS) }, onOpenControls = { nav.navigate(Routes.CONTROLS) })
            }
            composable(Routes.CONTROLS) { ControlsRoute(onBack = { nav.popBackStack() }) }
            composable(Routes.TREND) { TrendRoute() }
            composable(Routes.SAMPLES) { SamplesRoute(snackbar, onOpen = { nav.navigate("sample/$it") }) }
            composable(Routes.SAMPLE, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                SampleDetailRoute(onBack = { nav.popBackStack() })
            }
            composable(Routes.SETTINGS) { SettingsRoute(firstRun = false, onDone = {}) }
        }
    }
}

private fun NavHostController.switchTab(route: String) = navigate(route) {
    // Live is always the back-stack root once the intro is gone
    popUpTo(Routes.LIVE) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun BottomBar(nav: NavHostController, current: String?) {
    NavigationBar(containerColor = NavSurface, tonalElevation = 0.dp) {
        for (t in tabs) {
            val sel = t.route == current
            NavigationBarItem(
                selected = sel,
                onClick = { nav.switchTab(t.route) },
                icon = { Icon(t.icon, contentDescription = null) },
                label = { Text(t.label, style = LabType.small.copy(fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Ink, selectedTextColor = Ink, indicatorColor = Color(0x1FFFFFFF),
                    unselectedIconColor = InkSoft, unselectedTextColor = InkSoft,
                ),
            )
        }
    }
}

