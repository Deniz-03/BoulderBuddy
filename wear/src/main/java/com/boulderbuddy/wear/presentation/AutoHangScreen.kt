package com.boulderbuddy.wear.presentation

import com.boulderbuddy.wear.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

private val HangColor = Color(0xFF4CAF50)   // grün – hängt (wie TimerScreen)
private val RestColor = Color(0xFFFFA726)   // orange – Pause
private val EndColor = Color(0xFFD64541)    // rot – Session beenden

/**
 * Auto-Screen (B.6/M3): großer Live-Status (BEREIT / HÄNGT mm:ss / PAUSE mm:ss, hochzählend),
 * aktueller Satz-Index, Haptik übernimmt der [com.boulderbuddy.wear.sensing.AutoHangService].
 * Ein prominenter „Session beenden"-Button mit Bestätigung (kein versehentliches Ende);
 * danach die Kurz-Zusammenfassung (N Sätze, Gesamt-Hängezeit).
 */
@Composable
fun AutoHangScreen(viewModel: AutoHangViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmEnd by remember { mutableStateOf(false) }

    Scaffold(timeText = { TimeText() }) {
        val listState = rememberScalingLazyListState()
        ScalingLazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val result = state.result
            when {
                // --- Zusammenfassung nach dem Beenden (§0 Säule 3) ---
                result != null -> {
                    item {
                        Text(
                            text = stringResource(R.string.auto_fertig),
                            style = MaterialTheme.typography.title2,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        )
                    }
                    item {
                        Text(
                            text = stringResource(
                                R.string.auto_ergebnis,
                                pluralStringResource(
                                    R.plurals.auto_saetze,
                                    result.sets,
                                    result.sets,
                                ),
                                result.hangTimeText,
                            ),
                            style = MaterialTheme.typography.body2,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        )
                    }
                    if (result.sets > 0) {
                        // Welche Session das Workout bekommt, entscheidet das Phone (§0 Säule 3);
                        // ob es überhaupt ankam, sagt die Uhr. Vorher stand hier bedingungslos
                        // "An Phone übertragen" — auch ohne verbundenes Phone, und da die Uhr
                        // Workouts nirgends lokal ablegt, war der Durchlauf damit weg.
                        item {
                            Text(
                                text = stringResource(
                                    when (result.uebertragen) {
                                        true -> R.string.auto_uebertragen
                                        null -> R.string.auto_uebertraegt
                                        false -> R.string.auto_nicht_uebertragen
                                    },
                                ),
                                style = MaterialTheme.typography.caption2,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            )
                        }
                    }
                    item {
                        Chip(
                            onClick = viewModel::onDismissResult,
                            label = { Text(stringResource(R.string.auto_ok)) },
                            colors = ChipDefaults.primaryChipColors(),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        )
                    }
                }

                // --- Startzustand ---
                !state.active -> {
                    item {
                        Text(
                            text = stringResource(R.string.auto_titel),
                            style = MaterialTheme.typography.title3,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        )
                    }
                    item {
                        Text(
                            text = stringResource(R.string.auto_erklaerung),
                            style = MaterialTheme.typography.caption2,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        )
                    }
                    item {
                        Chip(
                            onClick = viewModel::onStart,
                            label = { Text(stringResource(R.string.auto_starten)) },
                            colors = ChipDefaults.primaryChipColors(backgroundColor = HangColor),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        )
                    }
                }

                // --- Live-Erkennung ---
                else -> {
                    item {
                        Text(
                            text = stringResource(
                                when (state.status) {
                                    AutoStatus.HAENGT -> R.string.auto_status_haengt
                                    AutoStatus.PAUSE -> R.string.auto_status_pause
                                    AutoStatus.BEREIT -> R.string.auto_status_bereit
                                },
                            ),
                            style = MaterialTheme.typography.title1,
                            // Farbe am Zustand, nicht am angezeigten Wort — vorher stand hier
                            // ein Vergleich mit dem Text, den die Übersetzung mitgenommen hätte.
                            color = when (state.status) {
                                AutoStatus.HAENGT -> HangColor
                                AutoStatus.PAUSE -> RestColor
                                AutoStatus.BEREIT -> MaterialTheme.colors.onBackground
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                        )
                    }
                    item {
                        Text(
                            text = state.timeText,
                            style = MaterialTheme.typography.display3,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        Text(
                            text = when {
                                state.status == AutoStatus.HAENGT -> stringResource(
                                    R.string.auto_laufender_satz,
                                    state.abgeschlosseneSaetze + 1,
                                )
                                state.abgeschlosseneSaetze > 0 -> pluralStringResource(
                                    R.plurals.auto_saetze,
                                    state.abgeschlosseneSaetze,
                                    state.abgeschlosseneSaetze,
                                )
                                else -> stringResource(R.string.auto_erster_griff)
                            },
                            style = MaterialTheme.typography.body2,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (!confirmEnd) {
                        item {
                            Chip(
                                onClick = { confirmEnd = true },
                                label = { Text(stringResource(R.string.auto_beenden)) },
                                colors = ChipDefaults.secondaryChipColors(),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            )
                        }
                    } else {
                        item {
                            Chip(
                                onClick = {
                                    confirmEnd = false
                                    viewModel.onFinish()
                                },
                                label = { Text(stringResource(R.string.auto_beenden_ja)) },
                                colors = ChipDefaults.primaryChipColors(backgroundColor = EndColor),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            )
                        }
                        item {
                            Chip(
                                onClick = { confirmEnd = false },
                                label = { Text(stringResource(R.string.auto_weiter)) },
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
