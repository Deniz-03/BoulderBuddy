package com.boulderbuddy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material.icons.outlined.WatchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.R
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.EingabeDialog
import com.boulderbuddy.ui.components.TimerControls
import com.boulderbuddy.ui.components.TimerRing
import com.boulderbuddy.ui.components.TopBar
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens

// Phase eines Hangboard-Satzes. Steuert Label-Text und Ring-Farbe.
// HANG = aktiv hängen (grün), REST = Pause (orange), DONE = alle Sätze fertig.
// Zustandsautomat: Timer kann sich lediglich in einer der drei phasen befinden.
enum class TimerPhase { HANG, REST, DONE }

// Eine benannte Timer-Voreinstellung (Preset) für die Auswahl im Einstell-Dialog.
// UI-Modell — entkoppelt vom HangboardTemplateEntity der Datenschicht.
data class TimerPreset(
    val id: Int,
    val name: String,
    val sets: Int,
    val hangSec: Int,
    val restSec: Int,
)

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
    // Kurz-Zusammenfassung nach DONE (z.B. "6 Sätze · 00:42 Hängezeit"); null solange läuft.
    val doneSummary: String? = null,
    // Wohin das Workout gespeichert wurde (Session vs. eigenständig); null bis gespeichert.
    val savedTo: String? = null,
    /** Ob eine Uhr verbunden ist — steuert den Smartwatch-Indikator in der Top-Bar. */
    val watchConnected: Boolean = false,
    val presets: List<TimerPreset> = emptyList(),
)

@Composable
fun HangboardTimerScreen(
    state: HangboardTimerUiState,
    onPlayPause: () -> Unit,
    onReset: () -> Unit,
    // Übernimmt eine neue Konfiguration (Sätze, Hang-Sek, Pausen-Sek).
    onUpdateConfig: (Int, Int, Int) -> Unit,
    // Speichert die übergebene Konfiguration als benanntes Preset.
    onSavePreset: (String, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    // Löscht ein Preset anhand seiner ID.
    onDeletePreset: (Int) -> Unit = {},
) {
    // Der Einstellungs-Dialog wird lokal auf diesem Screen gesteuert (kein Navigieren mehr
    // in die allgemeinen App-Einstellungen).
    var showConfigDialog by rememberSaveable { mutableStateOf(false) }

    // Phasenabhängige Anzeige aus dem State ableiten. Die Color kommt aus dem Theme
    // und kann daher nicht im UiState liegen (das ist theme-unabhängig).
    val ringColor: Color = when (state.phase) {
        TimerPhase.HANG -> BoulderBuddy.colors.routes.green
        TimerPhase.REST -> BoulderBuddy.colors.routes.orange
        TimerPhase.DONE -> BoulderBuddy.colors.routes.green
    }
    val phaseLabel: String = stringResource(
        when (state.phase) {
            TimerPhase.HANG -> R.string.timer_phase_hang
            TimerPhase.REST -> R.string.timer_phase_rest
            TimerPhase.DONE -> R.string.timer_phase_fertig
        },
    )

    BoulderBuddyScaffold(
        topBar = {
            TopBar(
                title = stringResource(R.string.timer_titel),
                actions = {
                    // Smartwatch-Indikator: reine Statusanzeige, kein Button — die Kopplung
                    // passiert in der Wear-App, hier gäbe es nichts zu tippen. Getrennt =
                    // durchgestrichenes Icon + abgeschwächte Farbe.
                    Icon(
                        imageVector = if (state.watchConnected) {
                            Icons.Outlined.Watch
                        } else {
                            Icons.Outlined.WatchOff
                        },
                        contentDescription = stringResource(
                            if (state.watchConnected) R.string.timer_uhr_verbunden
                            else R.string.timer_uhr_getrennt,
                        ),
                        // Beide Farben sitzen auf dem Chrome und drehen deshalb mit dem
                        // Theme. Der getrennte Zustand nimmt textTertiary statt eines
                        // Alpha-Werts: 40 % Deckkraft ergab auf hellem Chrome 2,4:1.
                        tint = if (state.watchConnected) {
                            BoulderBuddy.colors.onChrome
                        } else {
                            BoulderBuddy.colors.textTertiary
                        },
                        modifier = Modifier.padding(horizontal = Dimens.paddingM),
                    )
                }
            )
        },
        // Die Navigationsleiste stellt das Gerüst (AppNavigation), nicht der Screen.
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
                    text = stringResource(
                        R.string.timer_satz_stand,
                        state.currentSet,
                        state.totalSets,
                        state.restTime,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BoulderBuddy.colors.textTertiary,
                )

                // Nach dem Ende: Kurz-Zusammenfassung + wohin gespeichert wurde (§0 Säule 3).
                if (state.doneSummary != null) {
                    Spacer(Modifier.height(Dimens.paddingM))
                    Text(
                        text = state.doneSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (state.savedTo != null) {
                        Text(
                            text = state.savedTo,
                            style = MaterialTheme.typography.bodyMedium,
                            color = BoulderBuddy.colors.textTertiary,
                        )
                    }
                }

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
            presets = state.presets,
            onConfirm = { sets, hang, rest ->
                onUpdateConfig(sets, hang, rest)
                showConfigDialog = false
            },
            onSavePreset = onSavePreset,
            onDeletePreset = onDeletePreset,
            onDismiss = { showConfigDialog = false },
        )
    }
}

// Einstell-Dialog für den Timer: benannte Presets (Chips) zum Laden/Löschen sowie drei
// Stepper (Sätze, Hang-Sekunden, Pausen-Sekunden). Ein Preset-Tap befüllt die Stepper;
// beim Bestätigen wird die Konfiguration übernommen und vom ViewModel persistiert.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimerConfigDialog(
    initialSets: Int,
    initialHangSec: Int,
    initialRestSec: Int,
    presets: List<TimerPreset>,
    onConfirm: (Int, Int, Int) -> Unit,
    onSavePreset: (String, Int, Int, Int) -> Unit,
    onDeletePreset: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var sets by rememberSaveable { mutableIntStateOf(initialSets) }
    var hangSec by rememberSaveable { mutableIntStateOf(initialHangSec) }
    var restSec by rememberSaveable { mutableIntStateOf(initialRestSec) }
    // Schaltet die Preset-Chips in einen Lösch-Modus (Chip zeigt dann ein X).
    var deleteMode by remember { mutableStateOf(false) }
    // Zeigt den Name-Eingabe-Dialog zum Speichern des aktuellen Presets.
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.timer_einstellen)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
                if (presets.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.timer_voreinstellungen),
                        style = MaterialTheme.typography.labelLarge,
                        color = BoulderBuddy.colors.textTertiary,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS)) {
                        presets.forEach { preset ->
                            InputChip(
                                selected = false,
                                onClick = {
                                    if (deleteMode) {
                                        onDeletePreset(preset.id)
                                    } else {
                                        // Preset in die Stepper laden (noch nicht übernehmen).
                                        sets = preset.sets
                                        hangSec = preset.hangSec
                                        restSec = preset.restSec
                                    }
                                },
                                label = { Text(preset.name) },
                                trailingIcon = if (deleteMode) {
                                    {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = stringResource(
                                                R.string.timer_preset_loeschen,
                                                preset.name,
                                            ),
                                            modifier = Modifier.size(Dimens.iconS),
                                        )
                                    }
                                } else null,
                            )
                        }
                    }
                    TextButton(onClick = { deleteMode = !deleteMode }) {
                        Text(
                            stringResource(
                                if (deleteMode) R.string.aktion_fertig
                                else R.string.timer_presets_bearbeiten,
                            ),
                        )
                    }
                }

                StepperRow(
                    label = stringResource(R.string.timer_saetze),
                    value = sets,
                    onChange = { sets = it.coerceAtLeast(1) },
                )
                StepperRow(
                    label = stringResource(R.string.timer_hang_sekunden),
                    value = hangSec,
                    onChange = { hangSec = it.coerceAtLeast(1) },
                )
                StepperRow(
                    label = stringResource(R.string.timer_pause_sekunden),
                    value = restSec,
                    onChange = { restSec = it.coerceAtLeast(0) },
                )

                TextButton(onClick = { showSaveDialog = true }) {
                    Icon(
                        Icons.Filled.Add,
                        // null: "Als Preset speichern" steht direkt daneben.
                        contentDescription = null,
                        modifier = Modifier.size(Dimens.iconS),
                    )
                    Spacer(Modifier.width(Dimens.paddingS))
                    Text(stringResource(R.string.timer_als_preset_speichern))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(sets, hangSec, restSec) }) {
                Text(stringResource(R.string.timer_uebernehmen))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.aktion_abbrechen)) }
        },
    )

    if (showSaveDialog) {
        SavePresetDialog(
            onConfirm = { name ->
                onSavePreset(name, sets, hangSec, restSec)
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false },
        )
    }
}

// Kleiner Eingabe-Dialog für den Namen eines neuen Presets. Leerer Name bleibt gesperrt.
@Composable
private fun SavePresetDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    EingabeDialog(
        titel = stringResource(R.string.timer_preset_speichern_titel),
        bestaetigenText = stringResource(R.string.aktion_speichern),
        bestaetigenAktiv = name.isNotBlank(),
        onBestaetigen = { onConfirm(name) },
        onAbbrechen = onDismiss,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.timer_preset_name)) },
            singleLine = true,
        )
    }
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
                Icon(
                    Icons.Filled.Remove,
                    contentDescription = stringResource(R.string.timer_verringern, label),
                )
            }
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = Dimens.paddingS),
            )
            IconButton(onClick = { onChange(value + 1) }) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.timer_erhoehen, label),
                )
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
