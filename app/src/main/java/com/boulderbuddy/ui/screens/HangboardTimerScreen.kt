package com.boulderbuddy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.TimerControls
import com.boulderbuddy.ui.components.TimerRing
import com.boulderbuddy.ui.components.TopBar
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.M3OnPrimary

// Phase eines Hangboard-Satzes. Steuert Label-Text und Ring-Farbe.
// HANG = aktiv hängen (grün), REST = Pause (orange), DONE = alle Sätze fertig.
// Zustandsautomat: Timer kann sich lediglich in einer der drei phasen befinden.
enum class TimerPhase { HANG, REST, DONE }

// Reiner UI-Zustand des Timer-Screens. Bewusst ohne Logik und ohne Theme-Farben:
// Der Screen ist stateless und rendert nur, was hier drinsteht.
// TODO: Später vom HangboardTimerViewModel als StateFlow geliefert
//  (Countdown, Phasenwechsel HANG↔REST, Satz-Hochzählen passieren dort, nicht hier).
data class HangboardTimerUiState(
    val progress: Float,     // 0..1 – Füllgrad des Rings
    val time: String,        // bereits zu mm:ss formatiert (z.B. "00:07")
    val restTime: String,    // Rest-/Pausendauer als Hinweis in der Satz-Zeile (mm:ss)
    val phase: TimerPhase,
    val currentSet: Int,
    val totalSets: Int,
    val isRunning: Boolean,
)

@Composable
fun HangboardTimerScreen(
    state: HangboardTimerUiState,
    onPlayPause: () -> Unit,
    onReset: () -> Unit,
    onSettings: () -> Unit,
) {
    // Phasenabhängige Anzeige aus dem State ableiten. Die Color kommt aus dem Theme
    // und kann daher nicht im UiState liegen (das ist theme-unabhängig).
    val ringColor: Color = when (state.phase) {
        TimerPhase.HANG -> BoulderBuddy.colors.routes.green
        TimerPhase.REST -> BoulderBuddy.colors.routes.orange
        TimerPhase.DONE -> BoulderBuddy.colors.routes.green
    }
    val phaseLabel: String = when (state.phase) {
        TimerPhase.HANG -> "HANG"
        TimerPhase.REST -> "REST"
        TimerPhase.DONE -> "FERTIG"
    }

    BoulderBuddyScaffold(
        topBar = {
            TopBar(
                title = "Hangboard-Timer",
                actions = {
                    // Smartwatch-Indikator (laut Screen-Konzept im Header dieses Screens).
                    // TODO: Echten Verbindungsstatus anzeigen + Wear-OS-Sync anstoßen.
                    IconButton(onClick = { /* TODO: Wear-OS-Verbindung/Status */ }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Einstellungen",
                            tint = M3OnPrimary,
                        )
                    }
                }
            )
        },
        // BottomNav wird ab Phase 1.3 zentral vom Navigations-Gerüst gestellt.
        content = { _ ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(
                        horizontal = Dimens.paddingL,   // 16dp – Abstand zum linken/rechten Rand
                        vertical = Dimens.paddingL,      // 16dp – Abstand oben/unten
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,  // Inhalt horizontal mittig
                verticalArrangement = Arrangement.Center,            // Inhalt vertikal mittig
            ) {
                TimerRing(
                    progress = state.progress,
                    time = state.time,
                    phaseLabel = phaseLabel,
                    ringColor = ringColor,
                )

                Spacer(Modifier.height(Dimens.paddingXL))

                Text(
                    text = "Satz ${state.currentSet} / ${state.totalSets} · Rest ${state.restTime}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BoulderBuddy.colors.textTertiary,
                )

                Spacer(Modifier.height(Dimens.paddingXL))

                TimerControls(
                    isRunning = state.isRunning,
                    onPlayPause = onPlayPause,
                    onReset = onReset,
                    onSettings = onSettings,
                )
            }
        }
    )
}

// TODO: Stateful-Wrapper HangboardTimerRoute(viewModel) ergänzen, sobald das
//  HangboardTimerViewModel existiert: collectAsStateWithLifecycle() lesen und
//  an HangboardTimerScreen(state, ...) durchreichen. Erst dann wird der Timer "live".

@Preview(showBackground = true)
@Composable
private fun HangboardTimerScreenPreview() {
    BoulderBuddyTheme {
        HangboardTimerScreen(
            state = HangboardTimerUiState(
                progress = 0.7f,
                time = "00:07",
                restTime = "00:03",
                phase = TimerPhase.HANG,
                currentSet = 3,
                totalSets = 6,
                isRunning = true,
            ),
            onPlayPause = {},
            onReset = {},
            onSettings = {},
        )
    }
}
