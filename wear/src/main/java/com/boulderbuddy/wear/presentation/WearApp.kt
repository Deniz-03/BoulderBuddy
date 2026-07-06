package com.boulderbuddy.wear.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController

// Routen der Wear-Navigation. M3 ersetzt den Sensor-Log-Eintrag durch die
// Modus-Wahl Manuell/Auto; der Debug-Screen bleibt für Kalibrier-Sessions erreichbar.
private object WearRoutes {
    const val MENU = "menu"
    const val TIMER = "timer"
    const val SENSOR_LOG = "sensorlog"
}

/**
 * Compose-Root der Wear-App: Wear-Material-Theme + kleine Navigation.
 * Start = Menü (Timer / Sensor-Log); zurück per Swipe-to-Dismiss (Wear-Standard).
 */
@Composable
fun WearApp() {
    MaterialTheme {
        val navController = rememberSwipeDismissableNavController()
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = WearRoutes.MENU,
        ) {
            composable(WearRoutes.MENU) {
                MenuScreen(
                    onOpenTimer = { navController.navigate(WearRoutes.TIMER) },
                    onOpenSensorLog = { navController.navigate(WearRoutes.SENSOR_LOG) },
                )
            }
            composable(WearRoutes.TIMER) { TimerScreen() }
            composable(WearRoutes.SENSOR_LOG) { SensorLogScreen() }
        }
    }
}

@Composable
private fun MenuScreen(
    onOpenTimer: () -> Unit,
    onOpenSensorLog: () -> Unit,
) {
    Scaffold(timeText = { TimeText() }) {
        val listState = rememberScalingLazyListState()
        ScalingLazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Text(
                    text = "BoulderBuddy",
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                )
            }
            item {
                Chip(
                    onClick = onOpenTimer,
                    label = { Text("Hangboard-Timer") },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }
            item {
                Chip(
                    onClick = onOpenSensorLog,
                    label = { Text("Sensor-Log (Debug)") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }
        }
    }
}
