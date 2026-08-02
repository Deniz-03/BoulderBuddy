package com.boulderbuddy.wear.presentation

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
                    StepperRow("Sätze", state.totalSets.toString(),
                        onMinus = { viewModel.changeSets(-1) },
                        onPlus = { viewModel.changeSets(1) })
                }
                item {
                    StepperRow("Hang", "${state.hangSec}s",
                        onMinus = { viewModel.changeHang(-1) },
                        onPlus = { viewModel.changeHang(1) })
                }
                item {
                    StepperRow("Pause", "${state.restSec}s",
                        onMinus = { viewModel.changeRest(-1) },
                        onPlus = { viewModel.changeRest(1) })
                }
                // Vom Phone synchronisierte Presets (§0 Säule 4): ein Tap übernimmt die Werte.
                if (state.presets.isNotEmpty()) {
                    item {
                        Text(
                            text = "Presets",
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
                                    Text("${preset.sets}×${preset.hangSec}s/${preset.restSec}s")
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
                TimerPhase.HANG -> "HÄNGEN\n${state.timeText}"
                TimerPhase.REST -> "PAUSE\n${state.timeText}"
                TimerPhase.DONE -> "FERTIG"
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
        text = "Satz ${state.currentSet}/${state.totalSets}",
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
                    when {
                        state.phase == TimerPhase.DONE -> "Neu"
                        state.isRunning -> "Pause"
                        else -> "Start"
                    }
                )
            },
            colors = ChipDefaults.primaryChipColors(),
        )
        Spacer(Modifier.width(6.dp))
        Chip(
            onClick = { viewModel.onReset() },
            label = { Text("Reset") },
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
            text = "$label\n$value",
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
