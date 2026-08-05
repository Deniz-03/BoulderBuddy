package com.boulderbuddy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.PrimaryButton
import com.boulderbuddy.ui.components.SelectableChip
import com.boulderbuddy.ui.components.SpeechToTextButton
import com.boulderbuddy.ui.components.TextField
import com.boulderbuddy.ui.components.TopBar
import com.boulderbuddy.ui.components.appendSpokenNote
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.viewmodel.GradeSystemUi
import com.boulderbuddy.ui.viewmodel.SessionErstellenUiState


@Composable
fun SessionErstellenScreen(
    // Anzeige-Zustand aus dem SessionErstellenViewModel (wählbare Grading-Systeme).
    state: SessionErstellenUiState = SessionErstellenUiState(),
    // Navigations-Callbacks (Phase 2). Defaults = {} halten Preview & Tests lauffähig.
    onBack: () -> Unit = {},
    // Übergibt die Formularwerte (inkl. gewähltem Gradsystem) nach oben; das Anlegen + die
    // Navigation zur neuen Session übernimmt der NavHost via SessionErstellenViewModel.
    onCreateSession: (ort: String, gradeSystemId: Int?, notiz: String) -> Unit = { _, _, _ -> },
) {
    var ort by remember { mutableStateOf("") }
    // Gewähltes Gradsystem (ID); null = noch nichts aktiv gewählt → Fallback aufs erste System.
    var selectedSystemId by remember { mutableStateOf<Int?>(null) }
    var notiz by remember { mutableStateOf("") }

    // Effektiv gewähltes System: die aktive Auswahl, sonst das erste vorhandene.
    val effectiveSystemId = selectedSystemId ?: state.systems.firstOrNull()?.id

    BoulderBuddyScaffold(
        topBar = {
            TopBar(
                title = "Neue Session",
                navIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück",
                            tint = BoulderBuddy.colors.onChrome,
                        )
                    }
                },
            )
        },
        content = { _ ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(
                        horizontal = Dimens.paddingL,   // 16dp – Abstand zum linken/rechten Rand
                        vertical = Dimens.paddingL,      // 16dp – Abstand oben/unten
                    ),
                verticalArrangement = Arrangement.spacedBy(Dimens.paddingL),   // 16dp zwischen den Blöcken
            ) {
                TextField(
                    value = ort,
                    onChange = {ort = it},
                    placeholder = "z.B. Boulderhalle Nord",
                    label = "HALLE/ORT",
                )

                // Grading-System: eigenes Label + festes 2-Spalten-Raster aus den real
                // vorhandenen Systemen (Standards + Custom). Zwei Chips à weight(1f) je Reihe,
                // damit alle gleich breit sind. Die Auswahl wird auf der Session gespeichert
                // und steuert später die Grade-Auswahl beim Boulder-Anlegen.
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingS)) {
                    Text(
                        text = "GRADING-SYSTEM",
                        style = MaterialTheme.typography.labelSmall,
                        color = BoulderBuddy.colors.textTertiary,
                    )
                    if (state.systems.isEmpty()) {
                        Text(
                            text = "Noch keine Grading-Systeme vorhanden.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = BoulderBuddy.colors.textSecondary,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingS)) {
                            // chunked(2) teilt die Systeme in 2er-Reihen → 2-Spalten-Raster.
                            state.systems.chunked(2).forEach { rowSystems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS),
                                ) {
                                    rowSystems.forEach { system ->
                                        SelectableChip(
                                            label = system.name,
                                            selected = system.id == effectiveSystemId,
                                            onClick = { selectedSystemId = system.id },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    // Ungerade Reihe: Spacer hält den einzelnen Chip auf halber Breite.
                                    if (rowSystems.size == 1) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                TextField(
                    value = notiz,
                    singleLine = false,
                    minLines = 3,
                    label = "NOTIZ (OPTIONAL)",
                    placeholder = "Ziel für heute...",
                    onChange = {notiz = it},
                    // Spracheingabe: erkannten Text an die Notiz anhängen (7.4b).
                    trailing = {
                        SpeechToTextButton(
                            onResult = { spoken -> notiz = appendSpokenNote(notiz, spoken) },
                        )
                    },
                )

                // Schiebt den Button ans untere Ende: der Spacer schluckt den restlichen
                // freien Platz der Column, sodass "Session starten" unten klebt.
                Spacer(Modifier.weight(1f))

                PrimaryButton(
                    text = "Session starten",
                    icon = Icons.Filled.PlayArrow,
                    // Halle + gewähltes Gradsystem + Notiz nach oben reichen; das ViewModel
                    // legt die Session an.
                    onClick = { onCreateSession(ort, effectiveSystemId, notiz) },
                )
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun SessionErstellenScreenPreview() {
    BoulderBuddyTheme {
        SessionErstellenScreen(
            state = SessionErstellenUiState(
                systems = listOf(
                    GradeSystemUi(id = 1, name = "Halle Nord", gradeCount = 5),
                    GradeSystemUi(id = 2, name = "V-Scale", gradeCount = 11),
                    GradeSystemUi(id = 3, name = "Französisch", gradeCount = 14),
                ),
            ),
        )
    }
}
