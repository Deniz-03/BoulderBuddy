package com.boulderbuddy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
// hangSec/restSec/totalSets = die aktuelle Konfiguration (für die Vorbefüllung des Dialogs).
data class HangboardTimerUiState(
    val progress: Float,     // 0..1 – Füllgrad des Rings
    val time: String,        // bereits zu mm:ss formatiert (z.B. "00:07")
    val restTime: String,    // Rest-/Pausendauer als Hinweis in der Satz-Zeile (mm:ss)
    val phase: TimerPhase,
    val currentSet: Int,
    val totalSets: Int,
    val hangSec: Int,
    val restSec: Int,
    val isRunning: Boolean,
)

@Composable
fun HangboardTimerScreen(
    state: HangboardTimerUiState,
    onPlayPause: () -> Unit,
    onReset: () -> Unit,
    // Übernimmt eine neue Konfiguration (Sätze, Hang-Sek, Pausen-Sek).
    onUpdateConfig: (Int, Int, Int) -> Unit,
) {
    // Der Einstellungs-Dialog wird lokal auf diesem Screen gesteuert (kein Navigieren mehr
    // in die allgemeinen App-Einstellungen).
    var showConfigDialog by remember { mutableStateOf(false) }

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
                            contentDescription = "Smartwatch",
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
                    onSettings = { showConfigDialog = true },
                )
            }
        }
    )

    if (showConfigDialog) {
        TimerConfigDialog(
            initialSets = state.totalSets,
            initialHangSec = state.hangSec,
            initialRestSec = state.restSec,
            onConfirm = { sets, hang, rest ->
                onUpdateConfig(sets, hang, rest)
                showConfigDialog = false
            },
            onDismiss = { showConfigDialog = false },
        )
    }
}

// Einstell-Dialog für den Timer: drei Stepper (Sätze, Hang-Sekunden, Pausen-Sekunden).
// Die Werte werden beim Bestätigen übernommen und vom ViewModel persistiert.
@Composable
private fun TimerConfigDialog(
    initialSets: Int,
    initialHangSec: Int,
    initialRestSec: Int,
    onConfirm: (Int, Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var sets by remember { mutableIntStateOf(initialSets) }
    var hangSec by remember { mutableIntStateOf(initialHangSec) }
    var restSec by remember { mutableIntStateOf(initialRestSec) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Timer einstellen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
                StepperRow(
                    label = "Sätze",
                    value = sets,
                    onChange = { sets = it.coerceAtLeast(1) },
                )
                StepperRow(
                    label = "Hang (s)",
                    value = hangSec,
                    onChange = { hangSec = it.coerceAtLeast(1) },
                )
                StepperRow(
                    label = "Pause (s)",
                    value = restSec,
                    onChange = { restSec = it.coerceAtLeast(0) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(sets, hangSec, restSec) }) { Text("Übernehmen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

// Eine Label-Zeile mit -/+ Steppern. onChange bekommt den unbeschränkten neuen Wert;
// das Clamping (min. Grenzen) macht der Aufrufer je Feld.
@Composable
private fun StepperRow(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange(value - 1) }) {
                Icon(Icons.Filled.Remove, contentDescription = "$label verringern")
            }
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = Dimens.paddingS),
            )
            IconButton(onClick = { onChange(value + 1) }) {
                Icon(Icons.Filled.Add, contentDescription = "$label erhöhen")
            }
        }
    }
}

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
                hangSec = 7,
                restSec = 3,
                isRunning = true,
            ),
            onPlayPause = {},
            onReset = {},
            onUpdateConfig = { _, _, _ -> },
        )
    }
}
