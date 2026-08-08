package com.boulderbuddy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import com.boulderbuddy.ui.theme.inhaltsBreite
import com.boulderbuddy.ui.components.appendSpokenNote
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.viewmodel.GradeSystemUi
import com.boulderbuddy.ui.viewmodel.HalleUi
import com.boulderbuddy.ui.viewmodel.Hallenwahl
import com.boulderbuddy.ui.viewmodel.SessionErstellenUiState


@Composable
fun SessionErstellenScreen(
    // Anzeige-Zustand aus dem SessionErstellenViewModel (wählbare Grading-Systeme).
    state: SessionErstellenUiState = SessionErstellenUiState(),
    // Navigations-Callbacks (Phase 2). Defaults = {} halten Preview & Tests lauffähig.
    onBack: () -> Unit = {},
    // Übergibt die Formularwerte (inkl. gewähltem Gradsystem) nach oben; das Anlegen + die
    // Navigation zur neuen Session übernimmt der NavHost via SessionErstellenViewModel.
    onCreateSession: (halle: Hallenwahl, gradeSystemId: Int?, notiz: String) -> Unit =
        { _, _, _ -> },
) {
    // Gewählte Halle (ID); null = noch nichts aktiv angetippt → Fallback auf die Vorauswahl.
    var selectedGymId by remember { mutableStateOf<Int?>(null) }
    // Der Nutzer hat ausdrücklich "Neue Halle" gewählt.
    var neueHalleGewaehlt by remember { mutableStateOf(false) }
    var neueHalle by remember { mutableStateOf("") }
    // Gewähltes Gradsystem (ID); null = noch nichts aktiv gewählt → Fallback aufs erste System.
    var selectedSystemId by remember { mutableStateOf<Int?>(null) }
    var notiz by remember { mutableStateOf("") }

    /*
     * Beides abgeleitet statt gespeichert — dieselbe Form wie beim Gradsystem darunter.
     *
     * Die Vorauswahl kommt asynchron aus dem ViewModel (Deep-Link-Halle bzw. zuletzt benutzte).
     * Sie in einen `LaunchedEffect` zu schreiben hieße, sie könnte eine spätere eigene Auswahl
     * des Nutzers wieder überschreiben. So gewinnt immer, was zuletzt angetippt wurde.
     *
     * Ohne jede Halle gibt es nichts zu wählen: dann ist der Neu-Fall der einzige Fall.
     */
    val neueHalleAktiv = neueHalleGewaehlt || state.gyms.isEmpty()
    val effectiveGymId = if (neueHalleAktiv) null else selectedGymId ?: state.vorauswahlGymId

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
                    // Formulare bleiben eine Spalte, egal wie breit das Fenster ist. Ein
                    // Eingabefeld von 1248 dp ist keine bessere Version eines Feldes von
                    // 400 dp — der Cursor steht dann irgendwo in einer leeren Fläche.
                    .inhaltsBreite()
                    .navigationBarsPadding()
                    .padding(
                        horizontal = Dimens.paddingL,   // 16dp – Abstand zum linken/rechten Rand
                        vertical = Dimens.paddingL,      // 16dp – Abstand oben/unten
                    ),
                verticalArrangement = Arrangement.spacedBy(Dimens.paddingL),   // 16dp zwischen den Blöcken
            ) {
                /*
                 * Halle: auswählen statt tippen.
                 *
                 * Vorher stand hier ein freies Textfeld, aus dem das ViewModel per
                 * "find-or-create" eine Halle machte. Wer den vorbefüllten Namen abwandelte,
                 * legte damit unbemerkt eine zweite Halle an — ohne Koordinaten, ohne Geofence,
                 * und die Näherungs-Politik sah für die eigentliche Halle nie eine Session.
                 * Eine Auswahl gibt der Halle eine sichtbare Identität; getippt wird nur noch,
                 * was es wirklich noch nicht gibt.
                 */
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingS)) {
                    Text(
                        text = "HALLE/ORT",
                        style = MaterialTheme.typography.labelSmall,
                        color = BoulderBuddy.colors.textTertiary,
                    )
                    if (state.gyms.isNotEmpty()) {
                        // Zuletzt benutzte zuerst (Reihenfolge kommt so aus dem ViewModel), der
                        // Neu-Chip immer am Ende — er ist der seltene Fall.
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS),
                            verticalArrangement = Arrangement.spacedBy(Dimens.paddingS),
                        ) {
                            state.gyms.forEach { gym ->
                                SelectableChip(
                                    label = gym.name,
                                    selected = gym.id == effectiveGymId,
                                    onClick = {
                                        selectedGymId = gym.id
                                        neueHalleGewaehlt = false
                                    },
                                )
                            }
                            SelectableChip(
                                label = "+ Neue Halle",
                                selected = neueHalleAktiv,
                                onClick = { neueHalleGewaehlt = true },
                            )
                        }
                    }
                    if (neueHalleAktiv) {
                        TextField(
                            value = neueHalle,
                            onChange = { neueHalle = it },
                            placeholder = "z.B. Boulderhalle Nord",
                        )
                    }
                }

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
                        /*
                         * Chips fließen, sie werden nicht in ein Raster gezwungen.
                         *
                         * Vorher: `chunked(2)` + `weight(1f)` — zwei gleich breite Spalten.
                         * Damit war die Breite eines Chips eine Funktion der Fensterbreite und
                         * nicht seiner Beschriftung: am Tablet wurde „V-Scale" 615 dp breit,
                         * und drei Systeme brachen in zwei Zeilen um, obwohl sie nebeneinander
                         * gepasst hätten.
                         *
                         * `FlowRow` legt so viele nebeneinander, wie hineinpassen, und jeder
                         * ist so breit wie sein Text. Das ist auch die richtige Form für eine
                         * Auswahl: Chips sind Etiketten, keine Tabellenzellen.
                         */
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS),
                            verticalArrangement = Arrangement.spacedBy(Dimens.paddingS),
                        ) {
                            state.systems.forEach { system ->
                                SelectableChip(
                                    label = system.name,
                                    selected = system.id == effectiveSystemId,
                                    onClick = { selectedSystemId = system.id },
                                )
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
                    // legt die Session an. Ein leerer Name im Neu-Fall fällt dort weiterhin auf
                    // "Meine Halle" zurück — der Button bleibt bedienbar.
                    onClick = {
                        val halle = effectiveGymId
                            ?.let { Hallenwahl.Bestehende(it) }
                            ?: Hallenwahl.Neue(neueHalle)
                        onCreateSession(halle, effectiveSystemId, notiz)
                    },
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
                gyms = listOf(
                    HalleUi(id =1, name = "Boulder World München"),
                    HalleUi(id =2, name = "Halle Nord"),
                ),
                vorauswahlGymId = 1,
            ),
        )
    }
}

// Erster Start: noch keine Halle angelegt — dann gibt es nichts auszuwählen und der Screen
// zeigt direkt das Namensfeld, ohne einen einsamen "+ Neue Halle"-Chip darüber.
@Preview(showBackground = true, name = "Ohne bestehende Hallen")
@Composable
private fun SessionErstellenScreenOhneHallenPreview() {
    BoulderBuddyTheme {
        SessionErstellenScreen(
            state = SessionErstellenUiState(
                systems = listOf(GradeSystemUi(id = 2, name = "V-Scale", gradeCount = 11)),
            ),
        )
    }
}
