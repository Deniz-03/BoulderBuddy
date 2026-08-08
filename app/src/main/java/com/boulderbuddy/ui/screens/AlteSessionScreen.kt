package com.boulderbuddy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.BoulderListRow
import com.boulderbuddy.ui.components.SectionHeader
import com.boulderbuddy.ui.components.SpeechToTextButton
import com.boulderbuddy.ui.components.StatCard
import com.boulderbuddy.ui.components.TextField
import com.boulderbuddy.ui.components.TopBar
import com.boulderbuddy.ui.components.appendSpokenNote
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.inhaltsBreite
import com.boulderbuddy.ui.viewmodel.SessionBoulderUi
import kotlinx.coroutines.launch

// Status eines Boulders. getoppt = als geschafft gewertet (Top oder Flash zählen beide
// als Top; Projekt nicht). Das Symbol steckt am Status, damit es nur eine Quelle gibt.
// Screen-übergreifend genutzt (SessionDetail, BoulderDetail, Mapper) — daher nicht private.
enum class BoulderStatus(val symbol: String) {
    TOP("✓"),
    FLASH("🔥"),
    PROJEKT("⏳");

    val getoppt: Boolean get() = this == TOP || this == FLASH
}

@Composable
fun AlteSessionScreen(
    // Anzeige-Daten der abgeschlossenen Session (Phase 6.4, aus SessionViewModel).
    gym: String = "Boulderhalle Nord",
    dateSubtitle: String = "12. Juni · abgeschlossen",
    durationText: String = "1.5h",
    notes: String = "",
    boulders: List<SessionBoulderUi> = emptyList(),
    // Schreibt die geänderte Session-Notiz zurück (beim Verlassen des Feldes).
    onNotesChange: (String) -> Unit = {},
    // Navigations-Callbacks (Phase 2). Defaults = {} halten Preview & Tests lauffähig.
    // `onBack = null` = kein Weg zurück, also kein Pfeil — siehe SessionDetailScreen.
    onBack: (() -> Unit)? = {},
    onOpenBoulder: (Int) -> Unit = {},
) {
    /*
     * Session-Notiz: nachträglich editierbar (die Reflexion schreibt man meist nach der Session).
     *
     * Bewusst **ohne** `notes` als remember-Schlüssel. Da beim Tippen gespeichert wird, kommt der
     * geschriebene Wert über den Room-Flow gleich wieder herein; mit Schlüssel würde das Feld bei
     * jedem Rücklauf neu aufgesetzt und beim schnellen Tippen Zeichen verlieren, die zwischen
     * Schreiben und Rücklauf entstanden sind. Der Startwert genügt einmalig: `SessionRoute`
     * rendert diesen Screen erst, wenn die Session geladen ist.
     */
    var notiz by remember { mutableStateOf(notes) }

    // Nur noch für die Spracheingabe: die trägt in einem Zug einen ganzen Satz ein, und das
    // bestätigt die kurze Rückmeldung. Beim Tippen braucht es sie nicht — dort sieht man den
    // Text ja entstehen.
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Stat-Werte werden aus den Daten ABGELEITET: Boulder = Anzahl, Tops = davon geschaffte.
    val boulderAnzahl = boulders.size
    val topAnzahl = boulders.count { it.status.getoppt }

    BoulderBuddyScaffold(
        topBar = {
            TopBar(
                title = gym,
                subtitle = dateSubtitle,
                navIcon = onBack?.let { zurueck ->
                    {
                        IconButton(onClick = zurueck) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Zurück",
                                tint = BoulderBuddy.colors.onChrome,
                            )
                        }
                    }
                },
            )
        },
        content = { _ ->
          Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    // Nachtrag einer Session: überwiegend Notiztext, also Textspaltenbreite.
                    .inhaltsBreite(),
                contentPadding = PaddingValues(
                    horizontal = Dimens.paddingL,
                    vertical = Dimens.paddingL,
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.paddingXL),
            ) {
                // --- Stats ---
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.paddingM),
                    ) {
                        StatCard(
                            value = boulderAnzahl.toString(),
                            label = "Boulder",
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        StatCard(
                            value = topAnzahl.toString(),
                            label = "Tops",
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        StatCard(
                            value = durationText,
                            label = "Dauer",
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }

                // --- Notiz (editierbar, speichert beim Verlassen des Feldes) ---
                item {
                    TextField(
                        value = notiz,
                        // Beim Tippen speichern, nicht beim Verlassen des Feldes: der Screen
                        // wird über Zurück verlassen, und dabei gibt es keinen Fokuswechsel
                        // mehr — die Notiz war damit schlicht weg. Ein Tastendruck kostet jetzt
                        // eine einzelne UPDATE-Anweisung (SessionDao.updateNotes).
                        onChange = {
                            notiz = it
                            onNotesChange(it)
                        },
                        label = "NOTIZ",
                        placeholder = "Notiz zu dieser Session…",
                        singleLine = false,
                        minLines = 3,
                        // Spracheingabe, wie in „Neue Session" und „Route hinzufügen". Sie
                        // fehlte hier als einziges der drei Notizfelder — und ausgerechnet
                        // beim nachträglichen Festhalten einer Session, wo man am ehesten
                        // spricht statt tippt.
                        trailing = {
                            SpeechToTextButton(
                                onResult = { spoken ->
                                    // Eigener Speicher-Aufruf, weil der erkannte Text nicht
                                    // durch `onChange` läuft — er wird hier direkt gesetzt.
                                    val ergaenzt = appendSpokenNote(notiz, spoken)
                                    notiz = ergaenzt
                                    onNotesChange(ergaenzt)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Notiz gespeichert")
                                    }
                                },
                            )
                        },
                    )
                }

                // --- Gekletterte Boulder (read-only) ---
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
                        SectionHeader(text = "Gekletterte Boulder")
                        if (boulders.isEmpty()) {
                            Text(
                                text = "In dieser Session wurde kein Boulder geloggt.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = BoulderBuddy.colors.textSecondary,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingS)) {
                            boulders.forEach { boulder ->
                                BoulderListRow(
                                    grade = boulder.grade,
                                    name = boulder.name,
                                    accentColor = boulder.accentColor,
                                    statusIcon = {
                                        Text(
                                            text = boulder.status.symbol,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = statusColorFor(boulder.status),
                                        )
                                    },
                                    onClick = { onOpenBoulder(boulder.id) },
                                )
                            }
                        }
                    }
                }
            }

            // Schwebt über der Liste am unteren Rand — quittiert das gespeicherte Notizfeld.
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
            )
          }
        },
    )
}

// Status → Farbe fürs Status-Symbol. Top grün, Flash orange, Projekt dezent.
@Composable
private fun statusColorFor(status: BoulderStatus): Color = when (status) {
    BoulderStatus.TOP -> BoulderBuddy.colors.routes.green
    BoulderStatus.FLASH -> BoulderBuddy.colors.routes.orange
    BoulderStatus.PROJEKT -> BoulderBuddy.colors.textTertiary
}

@Preview(showBackground = true)
@Composable
private fun AlteSessionScreenPreview() {
    BoulderBuddyTheme {
        val routes = BoulderBuddy.colors.routes
        AlteSessionScreen(
            notes = "Überhang endlich geschafft. Linke Schulter zwickt.",
            boulders = listOf(
                SessionBoulderUi(0, "6b", "Überhang", routes.green, BoulderStatus.TOP, 2),
                SessionBoulderUi(1, "5c", "Dachrinne", routes.red, BoulderStatus.TOP, 1),
                SessionBoulderUi(2, "5a", "Warmup", routes.yellow, BoulderStatus.FLASH, 1),
                SessionBoulderUi(3, "6a", "Slab Talk", routes.blue, BoulderStatus.PROJEKT, 4),
            ),
        )
    }
}
