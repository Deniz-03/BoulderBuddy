package com.boulderbuddy.wear.presentation

import com.boulderbuddy.wear.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.boulderbuddy.wear.sensing.SensorLoggingService

private val RecColor = Color(0xFFD64541)    // rot – Aufnahme läuft
private val HangColor = Color(0xFF4CAF50)   // grün – Label „Hängen" (wie TimerScreen)
private val RestColor = Color(0xFFFFA726)   // orange – Label „Pause"

/**
 * Debug-Screen für die Sensor-Aufzeichnung (B.5.1): Aufnahme starten/stoppen, während der
 * Aufnahme das aktuelle Label setzen („Hängen"/„Pause" — genau das macht die Logs für die
 * Offline-Kalibrierung auswertbar), danach die letzte Aufnahme ans Phone exportieren.
 */
@Composable
fun SensorLogScreen(viewModel: SensorLogViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(timeText = { TimeText() }) {
        val listState = rememberScalingLazyListState()
        ScalingLazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Text(
                    text = stringResource(
                        if (state.isRecording) R.string.log_laeuft else R.string.log_titel,
                    ),
                    color = if (state.isRecording) RecColor else MaterialTheme.colors.onBackground,
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                )
            }
            item {
                Chip(
                    onClick = viewModel::onStartStop,
                    label = {
                        Text(
                            stringResource(
                                if (state.isRecording) R.string.log_stopp
                                else R.string.log_starten,
                            ),
                        )
                    },
                    colors = if (state.isRecording) {
                        ChipDefaults.primaryChipColors(backgroundColor = RecColor)
                    } else {
                        ChipDefaults.primaryChipColors()
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }
            if (state.isRecording) {
                // Label-Umschalter: markiert im Log, was der Träger GERADE tut.
                item {
                    Chip(
                        onClick = viewModel::onLabelHang,
                        label = { Text(stringResource(R.string.log_jetzt_haengen)) },
                        colors = labelChipColors(
                            active = state.label == SensorLoggingService.LABEL_HANG,
                            color = HangColor,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
                item {
                    Chip(
                        onClick = viewModel::onLabelRest,
                        label = { Text(stringResource(R.string.log_jetzt_pause)) },
                        colors = labelChipColors(
                            active = state.label == SensorLoggingService.LABEL_REST,
                            color = RestColor,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
            } else if (state.lastLogName != null) {
                item {
                    Text(
                        text = stringResource(
                            R.string.log_datei,
                            state.lastLogName.orEmpty(),
                            state.lastLogSizeKb,
                        ),
                        style = MaterialTheme.typography.caption2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    )
                }
                item {
                    Chip(
                        onClick = viewModel::onExport,
                        label = { Text(stringResource(R.string.log_exportieren)) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
            }
            val export = state.exportStatus
            if (export != null) {
                item {
                    Text(
                        text = stringResource(
                            when (export) {
                                ExportStatus.LAEUFT -> R.string.log_export_laeuft
                                ExportStatus.ERFOLGREICH -> R.string.log_export_ok
                                ExportStatus.FEHLGESCHLAGEN -> R.string.log_export_fehler
                            },
                        ),
                        style = MaterialTheme.typography.caption2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun labelChipColors(active: Boolean, color: Color) =
    if (active) {
        ChipDefaults.primaryChipColors(backgroundColor = color)
    } else {
        ChipDefaults.secondaryChipColors()
    }
