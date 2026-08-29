package com.boulderbuddy.wear.presentation

import com.boulderbuddy.wear.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText

private val HangColor = Color(0xFF4CAF50)   // grün – aktiv hängen
private val RestColor = Color(0xFFFFA726)   // orange – Pause
private val DoneColor = Color(0xFF64B5F6)   // blau – fertig

/**
 * Der Hangboard-Timer auf der Uhr — dieselbe Zustandsmaschine wie am Telefon, aber für ein
 * Handgelenk gebaut.
 *
 * Daraus folgen die Unterschiede zur Phone-Fassung: `ScalingLazyColumn` statt `LazyColumn`
 * (die Wear-Liste staucht ihre Ränder am runden Display), Farben als lokale Konstanten statt
 * aus einem Designsystem, und keine Einstellmöglichkeit — die Konfiguration kommt vom
 * Telefon über den Preset-Kanal.
 */
@Composable
fun TimerScreen(viewModel: TimerViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(timeText = { TimeText() }) {
        val listState = rememberScalingLazyListState()
        ScalingLazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { TimerRing(state) }
            item { SetCounter(state) }
            item { Controls(state, viewModel) }
            if (state.isConfigurable) {
                item {
                    StepperRow(
                        stringResource(R.string.timer_saetze),
                        state.totalSets.toString(),
                        onMinus = { viewModel.changeSets(-1) },
                        onPlus = { viewModel.changeSets(1) })
                }
                item {
                    StepperRow(
                        stringResource(R.string.timer_hang),
                        stringResource(R.string.timer_sekunden, state.hangSec),
                        onMinus = { viewModel.changeHang(-1) },
                        onPlus = { viewModel.changeHang(1) })
                }
                item {
                    StepperRow(
                        stringResource(R.string.timer_pause),
                        stringResource(R.string.timer_sekunden, state.restSec),
                        onMinus = { viewModel.changeRest(-1) },
                        onPlus = { viewModel.changeRest(1) })
                }
                // Vom Phone synchronisierte Presets (§0 Säule 4): ein Tap übernimmt die Werte.
                if (state.presets.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.timer_presets),
                            style = MaterialTheme.typography.caption2,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                    }
                    state.presets.forEach { preset ->
                        item {
                            Chip(
                                onClick = { viewModel.applyPreset(preset) },
                                label = { Text(preset.name) },
                                secondaryLabel = {
                                    Text(
                                        stringResource(
                                            R.string.timer_preset_kurz,
                                            preset.sets,
                                            preset.hangSec,
                                            preset.restSec,
                                        ),
                                    )
                                },
                                colors = ChipDefaults.secondaryChipColors(),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerRing(state: WearTimerUiState) {
    val color = when (state.phase) {
        TimerPhase.HANG -> HangColor
        TimerPhase.REST -> RestColor
        TimerPhase.DONE -> DoneColor
    }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
        CircularProgressIndicator(
            progress = state.progress,
            modifier = Modifier.size(96.dp),
            indicatorColor = color,
            trackColor = MaterialTheme.colors.onSurface.copy(alpha = 0.2f),
            strokeWidth = 6.dp,
        )
        Text(
            text = when (state.phase) {
                TimerPhase.HANG ->
                    stringResource(R.string.timer_phase_hang, state.timeText)
                TimerPhase.REST ->
                    stringResource(R.string.timer_phase_rest, state.timeText)
                TimerPhase.DONE -> stringResource(R.string.timer_phase_fertig)
            },
            textAlign = TextAlign.Center,
            color = color,
            style = MaterialTheme.typography.title3,
        )
    }
}

@Composable
private fun SetCounter(state: WearTimerUiState) {
    Text(
        text = stringResource(
            R.string.timer_satz_stand,
            state.currentSet,
            state.totalSets,
        ),
        color = MaterialTheme.colors.onSurface,
        style = MaterialTheme.typography.caption1,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun Controls(state: WearTimerUiState, viewModel: TimerViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Chip(
            onClick = { viewModel.onPlayPause() },
            label = {
                Text(
                    stringResource(
                        when {
                            state.phase == TimerPhase.DONE -> R.string.timer_neu
                            state.isRunning -> R.string.timer_pause_knopf
                            else -> R.string.timer_start
                        },
                    )
                )
            },
            colors = ChipDefaults.primaryChipColors(),
        )
        Spacer(Modifier.width(6.dp))
        Chip(
            onClick = { viewModel.onReset() },
            label = { Text(stringResource(R.string.timer_reset)) },
            colors = ChipDefaults.secondaryChipColors(),
        )
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Button(
            onClick = onMinus,
            modifier = Modifier.size(32.dp),
            colors = ButtonDefaults.secondaryButtonColors(),
        ) { Text("−") }
        Text(
            text = stringResource(R.string.timer_stepper_zelle, label, value),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.onSurface,
            style = MaterialTheme.typography.caption2,
            modifier = Modifier.padding(horizontal = 8.dp).width(56.dp),
        )
        Button(
            onClick = onPlus,
            modifier = Modifier.size(32.dp),
            colors = ButtonDefaults.secondaryButtonColors(),
        ) { Text("+") }
    }
}
