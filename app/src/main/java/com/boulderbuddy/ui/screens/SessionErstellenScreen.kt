package com.boulderbuddy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.boulderbuddy.ui.theme.inhaltsAbstandMitTastatur
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.viewmodel.GradeSystemUi
import com.boulderbuddy.ui.viewmodel.HalleUi
import com.boulderbuddy.ui.viewmodel.SessionErstellenUiState


@Composable
fun SessionErstellenScreen(
    // Anzeige-Zustand aus dem SessionErstellenViewModel (wählbare Grading-Systeme).
    state: SessionErstellenUiState = SessionErstellenUiState(),
    // Navigations-Callbacks (Phase 2). Defaults = {} halten Preview & Tests lauffähig.
    onBack: () -> Unit = {},
    // Übergibt die Formularwerte (inkl. gewähltem Gradsystem) nach oben; das Anlegen + die
    // Navigation zur neuen Session übernimmt der NavHost via SessionErstellenViewModel.
    onCreateSession: (gymId: Int, gradeSystemId: Int?, notiz: String) -> Unit = { _, _, _ -> },
    // Führt in den Gym-Editor (dieselbe Maske wie aus den Einstellungen). Die neu angelegte
    // Halle kommt über [neueHalleId] zurück.
    onNeueHalle: () -> Unit = {},
    // ID einer gerade im Editor angelegten Halle; null = keine offene Rückmeldung.
    neueHalleId: Int? = null,
    onNeueHalleVerbraucht: () -> Unit = {},
) {
    /*
     * `rememberSaveable`, nicht `remember`: gedreht war sonst alles weg — die Notiz leer, die
     * Hallen- und Grading-Auswahl zurück auf der Vorauswahl. Am Gerät nachgestellt und der
     * Grund, warum es diese Zeilen so gibt: `remember` überlebt Recompositions, aber keinen
     * Neuaufbau der Activity, und genau der passiert beim Drehen.
     */
    // Gewählte Halle (ID); null = noch nichts aktiv angetippt → Fallback auf die Vorauswahl.
    var selectedGymId by rememberSaveable { mutableStateOf<Int?>(null) }
    // Gewähltes Gradsystem (ID); null = noch nichts aktiv gewählt → Standard der Halle.
    var selectedSystemId by rememberSaveable { mutableStateOf<Int?>(null) }
    var notiz by rememberSaveable { mutableStateOf("") }

    // Rückkehr aus dem Editor: die frisch angelegte Halle ist die gemeinte, also auswählen.
    // Danach quittieren, sonst spränge die Auswahl bei jeder Recomposition wieder dorthin.
    LaunchedEffect(neueHalleId) {
        neueHalleId?.let {
            selectedGymId = it
            // Die neue Halle bringt ihr eigenes Standard-Grading mit — eine Auswahl, die noch
            // zur vorherigen Halle gehörte, wäre jetzt falsch.
            selectedSystemId = null
            onNeueHalleVerbraucht()
        }
    }

    /*
     * Abgeleitet statt gespeichert: die Vorauswahl kommt asynchron aus dem ViewModel
     * (Deep-Link-Halle bzw. zuletzt benutzte). Sie in einen `LaunchedEffect` zu schreiben hieße,
     * sie könnte eine spätere eigene Auswahl des Nutzers wieder überschreiben. So gewinnt immer,
     * was zuletzt angetippt wurde.
     */
    val effectiveGymId = selectedGymId ?: state.vorauswahlGymId
    val gewaehlteHalle = state.gyms.firstOrNull { it.id == effectiveGymId }

    /*
     * Gradsystem: eigene Wahl schlägt Hallen-Standard schlägt erstes vorhandenes System.
     *
     * Der Hallen-Standard ist ein Vorschlag, keine Bindung — wer fürs Moonboard ein anderes
     * System braucht, tippt es an und behält es. Nur ein Hallenwechsel setzt die eigene Wahl
     * zurück (siehe `onClick` am Hallen-Chip): dann ist der Vorschlag der neuen Halle gemeint,
     * nicht der stehengebliebene der alten.
     */
    val effectiveSystemId = selectedSystemId
        ?: gewaehlteHalle?.defaultGradeSystemId
        ?: state.systems.firstOrNull()?.id

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
                    .inhaltsAbstandMitTastatur()
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
                 *
                 * "+ Neue Halle" führt deshalb in den Gym-Editor statt in ein Namensfeld: eine
                 * Halle soll beim Anlegen dieselben Felder bekommen wie beim Bearbeiten —
                 * Standort, Radius, Standard-Grading. Sonst entstehen wieder Hallen, denen
                 * alles fehlt außer dem Namen.
                 */
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingS)) {
                    Text(
                        text = "HALLE/ORT",
                        style = MaterialTheme.typography.labelSmall,
                        color = BoulderBuddy.colors.textTertiary,
                    )
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
                                    // Hallenwechsel: der Grading-Vorschlag der neuen Halle
                                    // ist jetzt gemeint, nicht die Wahl zur alten.
                                    selectedSystemId = null
                                },
                            )
                        }
                        SelectableChip(
                            label = "+ Neue Halle",
                            selected = false,
                            onClick = onNeueHalle,
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

                // Ohne jede Halle gibt es nichts zu starten — dann führt der Button dorthin,
                // wo es weitergeht, statt eine namenlose Halle im Hintergrund zu erfinden.
                if (effectiveGymId == null) {
                    PrimaryButton(
                        text = "Erste Halle anlegen",
                        onClick = onNeueHalle,
                    )
                } else {
                    PrimaryButton(
                        text = "Session starten",
                        icon = Icons.Filled.PlayArrow,
                        // Halle + gewähltes Gradsystem + Notiz nach oben reichen; das ViewModel
                        // legt die Session an.
                        onClick = { onCreateSession(effectiveGymId, effectiveSystemId, notiz) },
                    )
                }
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
                    // Standard-Grading der Halle: "Halle Nord" ist dadurch vorgewählt, ohne
                    // dass jemand es antippen musste.
                    HalleUi(id = 1, name = "Boulder World München", defaultGradeSystemId = 1),
                    HalleUi(id = 2, name = "Kletterhalle Nord"),
                ),
                vorauswahlGymId = 1,
            ),
        )
    }
}

// Erster Start: noch keine Halle angelegt. Dann gibt es nichts auszuwählen, und der Button
// führt zum Anlegen statt eine namenlose Halle zu erfinden.
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
