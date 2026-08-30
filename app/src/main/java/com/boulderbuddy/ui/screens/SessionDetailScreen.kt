package com.boulderbuddy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.R
import com.boulderbuddy.ui.components.AddRouteCard
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.PrimaryButton
import com.boulderbuddy.ui.components.RouteCard
import com.boulderbuddy.ui.components.SectionHeader
import com.boulderbuddy.ui.components.StatCard
import com.boulderbuddy.ui.components.TopBar
import androidx.compose.foundation.layout.BoxWithConstraints
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.inhaltsBreite
import com.boulderbuddy.ui.theme.spaltenFuer
import com.boulderbuddy.ui.viewmodel.HangboardWorkoutUi
import com.boulderbuddy.ui.viewmodel.SessionBoulderUi
import com.boulderbuddy.ui.viewmodel.SessionGhostAnalyseUi
import com.boulderbuddy.ui.viewmodel.TagesstatistikUi
import kotlinx.coroutines.delay

// Status → Farbe fürs Status-Symbol der RouteCard. Top grün, Flash orange, Projekt dezent.
@Composable
private fun statusColorFor(status: BoulderStatus): Color = when (status) {
    BoulderStatus.TOP -> BoulderBuddy.colors.routes.green
    BoulderStatus.FLASH -> BoulderBuddy.colors.routes.orange
    BoulderStatus.PROJEKT -> BoulderBuddy.colors.textTertiary
}

@Composable
fun SessionDetailScreen(
    // Anzeige-Daten der aktiven Session (Phase 6.4, aus SessionViewModel).
    gym: String = "Boulderhalle Nord",
    startMillis: Long = System.currentTimeMillis(),
    topGrade: String = "–",
    boulders: List<SessionBoulderUi> = emptyList(),
    // Getrackte Hangboard-Workouts dieser Session; leer = Block wird ausgeblendet.
    hangboardWorkouts: List<HangboardWorkoutUi> = emptyList(),
    // Ghost-Analysen dieser Session. Der Block steht auch leer da — er ist der Einstieg.
    ghostAnalysen: List<SessionGhostAnalyseUi> = emptyList(),
    // Verlauf dieser Session je Gradsystem; leer = kein Boulder mit Grad, Block entfällt.
    tagesstatistik: Map<Int, TagesstatistikUi> = emptyMap(),
    tagesSysteme: List<TagesSystemUi> = emptyList(),
    // Navigations-Callbacks (Phase 2). onAddRoute ist von SessionRoute bereits an die
    // sessionId dieser Session gebunden.
    // `null` = es gibt von hier keinen Weg zurück, also auch keinen Pfeil. Genau der Fall im
    // Zwei-Pane-Layout des Tablets: die Liste steht daneben, ein Zurück führte nur in den
    // Leerzustand des Detail-Panes.
    onBack: (() -> Unit)? = {},
    onOpenBoulder: (Int) -> Unit = {},
    onAddRoute: () -> Unit = {},
    onOpenGhostAnalyse: (Int) -> Unit = {},
    onAddGhostAnalyse: () -> Unit = {},
    onEndSession: () -> Unit = {},
) {
    // Verstrichene Session-Dauer als "HH:mm" für den Header ("● Läuft · 01:12 h"),
    // gerechnet ab dem echten Startzeitpunkt der Session.
    var elapsed by remember { mutableStateOf("00:00") }
    LaunchedEffect(startMillis) {
        while (true) {
            val totalSeconds = ((System.currentTimeMillis() - startMillis) / 1000).coerceAtLeast(0)
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            elapsed = "%02d:%02d".format(hours, minutes)
            delay(1000)
        }
    }

    // Stats aus den Bouldern ableiten: Tops = geschaffte, Versuche = Summe.
    val tops = boulders.count { it.status.getoppt }
    val versuche = boulders.sumOf { it.versuche }

    // Steuert den Bestätigungsdialog des zentralen "Session beenden"-Buttons.
    var showEndDialog by remember { mutableStateOf(false) }

    // Gewähltes Gradsystem des Verlaufs. Vorbelegt mit dem ersten vorkommenden; die
    // Umschaltleiste erscheint ohnehin erst ab zwei Systemen.
    var gewaehltesSystem by rememberSaveable { mutableStateOf<Int?>(null) }
    if (gewaehltesSystem == null || tagesSysteme.none { it.id == gewaehltesSystem }) {
        gewaehltesSystem = tagesSysteme.firstOrNull()?.id
    }

    BoulderBuddyScaffold(
        topBar = {
            TopBar(
                title = gym,
                subtitle = stringResource(R.string.session_laeuft_seit, elapsed),
                navIcon = onBack?.let { zurueck ->
                    {
                        IconButton(onClick = zurueck) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.aktion_zurueck),
                                tint = BoulderBuddy.colors.onChrome,
                            )
                        }
                    }
                },
            )
        },
        content = { _ ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    /*
                     * Auch als Vollbild-Ziel eine lesbare Spalte.
                     *
                     * Im Detail-Pane des Tablets begrenzt der Pane die Breite von selbst — als
                     * Sprungziel von Home, Widget oder Näherungs-Push füllt derselbe Screen aber
                     * das ganze Fenster. Am 1280-dp-Tablet stand „Session beenden" dann als
                     * 2500 px breiter Balken da. Die anderen Detail-Screens (AlteSession,
                     * BoulderDetail) machen das längst; hier fehlte es als einzigem.
                     */
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
                            value = tops.toString(),
                            label = stringResource(R.string.session_tops),
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        StatCard(
                            value = versuche.toString(),
                            label = stringResource(R.string.session_versuche),
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        StatCard(
                            value = topGrade,
                            label = stringResource(R.string.session_top_grade),
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }

                // --- Boulder-Raster ---
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
                        SectionHeader(text = stringResource(R.string.session_boulder_ueberschrift))

                        // null = Platzhalter für die AddRouteCard, immer als letzte Kachel.
                        val cells: List<SessionBoulderUi?> = boulders + null

                        /*
                         * Die Spaltenzahl wird gemessen, nicht angenommen.
                         *
                         * Vorher `chunked(2)` — fest. Am Tablet ergab das zwei Karten von je
                         * 615 dp für „V3 / Dachrinne / 1 Vers.".
                         *
                         * Hier steht `BoxWithConstraints` und nicht `aktuelleBreite()`, weil
                         * dieser Screen im Tablet-Layout **im Detail-Pane** läuft: das Fenster
                         * meldet dort 1280 dp, verfügbar sind aber nur ~900 dp. Und ein
                         * `LazyVerticalGrid` wie in der Boulder-Übersicht geht nicht — es säße
                         * in einem `LazyColumn`-Item, also zwei Lazy-Container derselben Achse.
                         */
                        BoxWithConstraints {
                            val spalten = spaltenFuer(maxWidth)
                            Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
                                cells.chunked(spalten).forEach { rowCells ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(Dimens.paddingM),
                                    ) {
                                        rowCells.forEach { cell ->
                                            if (cell == null) {
                                                AddRouteCard(
                                                    onClick = onAddRoute,
                                                    modifier = Modifier.weight(1f),
                                                )
                                            } else {
                                                RouteCard(
                                                    grade = cell.grade,
                                                    name = cell.name,
                                                    meta = pluralStringResource(
                                                        R.plurals.boulder_versuche_kurz,
                                                        cell.versuche,
                                                        cell.versuche,
                                                    ),
                                                    accentColor = cell.accentColor,
                                                    statusIcon = {
                                                        Text(
                                                            text = cell.status.symbol,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = statusColorFor(cell.status),
                                                        )
                                                    },
                                                    onClick = { onOpenBoulder(cell.id) },
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                        }
                                        // Angebrochene Reihe: leere Slots halten die vorhandenen
                                        // Kacheln auf ihrer Spaltenbreite, statt sie zu strecken.
                                        repeat(spalten - rowCells.size) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // --- Hangboard-Training (nur wenn damit trainiert wurde) ---
                if (hangboardWorkouts.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
                            SectionHeader(text = stringResource(R.string.session_hangboard_ueberschrift))
                            hangboardWorkouts.forEach { hb ->
                                Text(
                                    text = stringResource(R.string.session_hangboard_zeile, hb.summary),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                // --- Verlauf dieser Session ---
                if (tagesSysteme.isNotEmpty()) {
                    item {
                        TagesstatistikBlock(
                            titel = stringResource(R.string.tag_verlauf_session),
                            systeme = tagesSysteme,
                            gewaehltesSystem = gewaehltesSystem,
                            statistik = tagesstatistik[gewaehltesSystem],
                            onSystemWaehlen = { gewaehltesSystem = it },
                            // Tops, Versuche und Top-Grad stehen oben schon als Kopfzeile.
                            zeigeKennzahlen = false,
                        )
                    }
                }

                // --- Ghost Climber ---
                item {
                    SessionGhostBlock(
                        analysen = ghostAnalysen,
                        onOeffnen = onOpenGhostAnalyse,
                        onHinzufuegen = onAddGhostAnalyse,
                    )
                }

                // --- Zentraler "Session beenden"-Button ---
                // Setzt (nach Bestätigung) endedAt (SessionViewModel.endSession); danach kippt
                // der Dispatcher automatisch in die abgeschlossene Ansicht.
                item {
                    PrimaryButton(
                        text = stringResource(R.string.session_beenden),
                        icon = Icons.Filled.Flag,
                        onClick = { showEndDialog = true },
                    )
                }
            }
        },
    )

    if (showEndDialog) {
        AlertDialog(
            onDismissRequest = { showEndDialog = false },
            title = { Text(stringResource(R.string.session_beenden_titel)) },
            text = {
                Text(stringResource(R.string.session_beenden_text))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEndDialog = false
                        onEndSession()
                    },
                ) { Text(stringResource(R.string.session_beenden_ja)) }
            },
            dismissButton = {
                TextButton(onClick = { showEndDialog = false }) {
                    Text(stringResource(R.string.aktion_abbrechen))
                }
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SessionDetailScreenPreview() {
    BoulderBuddyTheme {
        val routes = BoulderBuddy.colors.routes
        SessionDetailScreen(
            topGrade = "6b",
            boulders = listOf(
                SessionBoulderUi(0, "5c", "Dachrinne", routes.red, BoulderStatus.FLASH, 1),
                SessionBoulderUi(1, "6a", "Slab Talk", routes.blue, BoulderStatus.PROJEKT, 3),
                SessionBoulderUi(2, "6b", "Überhang", routes.green, BoulderStatus.TOP, 2),
            ),
        )
    }
}
