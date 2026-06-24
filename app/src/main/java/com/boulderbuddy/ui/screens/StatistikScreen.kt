package com.boulderbuddy.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.boulderbuddy.ui.components.ActivityHeatmap
import com.boulderbuddy.ui.components.BarChart
import com.boulderbuddy.ui.components.BarChartEntry
import com.boulderbuddy.ui.components.BottomNav
import com.boulderbuddy.ui.components.BottomNavTab
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.SectionHeader
import com.boulderbuddy.ui.components.StatCard
import com.boulderbuddy.ui.components.TopBar
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.M3OnPrimary

// ─────────────────────────────────────────────────────────────────────────────
// Statistik-Screen (#10 der Wireframes). Bottom-Nav-Tab "Statistik".
//
// Dieser Screen ist fast vollständig datengetrieben — die UI steht, aber JEDER
// hier gezeigte Wert ist aktuell ein Platzhalter. Sobald Room + ViewModel
// existieren, müssen die unten markierten Stellen an echte Aggregat-Abfragen
// angebunden werden. Die TODOs beschreiben pro Block, welche Query/Berechnung
// dahinter gehört.
//
// Empfohlene Architektur (analog zu den anderen Screens):
//   StatistikViewModel exponiert einen StatistikUiState (StateFlow), den dieser
//   Composable per collectAsStateWithLifecycle() liest. Die Aggregationen laufen
//   als @Query-Methoden im DAO (COUNT/AVG/GROUP BY) bzw. als Mapping im Repository
//   — NICHT im Composable.
// ─────────────────────────────────────────────────────────────────────────────

// TODO(DB): Diese Quick-Stat-Werte kommen aus dem StatistikViewModel/Room.
//  - flashRate:  Anteil Boulder mit Status = FLASH an allen getopp-ten Boulder.
//                Query-Idee: COUNT(status=FLASH) * 100 / COUNT(status IN (TOP,FLASH)).
//                Format: "42%". Wenn keine Tops existieren → "–" statt Division durch 0.
//  - totalTops:  Anzahl aller Boulder mit status IN (TOP, FLASH) über alle Sessions.
//  - totalSessions: COUNT(*) aus der Session-Tabelle (alle abgeschlossenen Sessions).
//  - topGrade:   höchste je gekletterte Schwierigkeit. ACHTUNG: Max über Grade ist
//                NICHT lexikografisch ("6c" > "10a" wäre falsch) — braucht eine
//                numerische Sortierordnung je Grading-System (Code-Entscheidung offen,
//                siehe Datenbankschema / "Eigenes…"-Grading-Entscheidung).
private data class QuickStat(val label: String, val value: String)

private val placeholderQuickStats = listOf(
    QuickStat(label = "Flash Rate", value = "42%"),
    QuickStat(label = "Tops gesamt", value = "23"),
    QuickStat(label = "Sessions", value = "12"),
)

// TODO(DB): Grade-Verteilung — GROUP BY grade über alle getopp-ten Boulder.
//  value = Anzahl Boulder pro Grade-Bucket. Die Farbe ist hier die Routenfarbe;
//  bei der Grade-Verteilung ist die Zuordnung Grade→Farbe aber NICHT fix (eine
//  6a kann rot oder blau sein). Vor der Anbindung mit Deniz klären, ob die Balken
//  - eine neutrale Akzentfarbe bekommen, oder
//  - nach der häufigsten Routenfarbe je Grade eingefärbt werden.
//  Buckets/Reihenfolge müssen aus dem gewählten Grading-System abgeleitet werden
//  (analog zu filterOptions in BoulderUebersichtScreen), nicht fest verdrahtet.
private val placeholderGradeDistribution: List<BarChartEntry>
    @Composable get() {
        val routes = BoulderBuddy.colors.routes
        return listOf(
            BarChartEntry("5", 3f, routes.green),
            BarChartEntry("6a", 7f, routes.blue),
            BarChartEntry("6b", 5f, routes.orange),
            BarChartEntry("6c", 4f, routes.red),
            BarChartEntry("7a", 2f, routes.purple),
        )
    }

// TODO(DB): Aktivitäts-Heatmap — ein intensity-Wert (0f..1f) pro Tag, in
//  chronologischer Reihenfolge (älteste zuerst), z.B. die letzten 28 Tage (4×7).
//  Roh-Query: GROUP BY date(session.startedAt) → Anzahl Boulder bzw. Sessiondauer
//  pro Tag. Anschließend auf 0f..1f normalisieren (Tageswert / Maximum im Zeitraum).
//  Tage ohne Aktivität = 0f (bleiben als blasse Zelle sichtbar).
//  Anzahl der Tage = columns * Wochen; columns ist im Heatmap-Default 7.
private val placeholderActivity = listOf(
    0f, 0.2f, 0f, 0.6f, 1f, 0.4f, 0f,
    0.3f, 0f, 0.8f, 0.5f, 0f, 0.2f, 0.9f,
    1f, 0.7f, 0.3f, 0f, 0.6f, 0.4f, 0.1f,
    0f, 0.5f, 0.9f, 0.2f, 0.7f, 0f, 0.3f,
)

@Composable
fun StatistikScreen() {
    // TODO(DB): Statt der drei placeholder-* Konstanten hier den StatistikUiState
    //  aus dem ViewModel lesen:
    //    val state by viewModel.uiState.collectAsStateWithLifecycle()
    //  und Lade-/Leer-Zustand behandeln (noch keine Sessions → freundlicher
    //  Empty-State statt leerer Diagramme).
    val quickStats = placeholderQuickStats
    val gradeDistribution = placeholderGradeDistribution
    val activity = placeholderActivity

    BoulderBuddyScaffold(
        topBar = {
            TopBar(
                title = "Statistik",
                actions = {
                    IconButton(onClick = { /* TODO: Navigation zu Einstellungen */ }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Einstellungen",
                            tint = M3OnPrimary,
                        )
                    }
                },
            )
        },
        bottomBar = {
            BottomNav(
                selectedTab = BottomNavTab.Stats,
                onTabSelect = { /* TODO: Navigation zwischen Tabs */ },
            )
        },
        content = { _ ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = Dimens.paddingL,
                    vertical = Dimens.paddingL,
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.paddingXL),
            ) {
                // --- Quick-Stats ---
                // height(IntrinsicSize.Min) + fillMaxHeight() → alle Karten gleich hoch
                // (gleiches Muster wie auf dem Home-Screen).
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.paddingM),
                    ) {
                        quickStats.forEach { stat ->
                            StatCard(
                                value = stat.value,
                                label = stat.label,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }
                    }
                }

                // --- Grade-Verteilung ---
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
                        SectionHeader(text = "Grade-Verteilung")
                        BarChart(
                            entries = gradeDistribution,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // --- Aktivität ---
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
                        SectionHeader(text = "Aktivität")
                        // Kurze Beschreibung, was die Skala zeigt.
                        // TODO(DB): Zeitraum dynamisch aus den echten Daten bilden
                        //  (z.B. konkrete Datumsspanne statt "letzten 4 Wochen").
                        Text(
                            text = "Deine Kletteraktivität der letzten 4 Wochen",
                            style = MaterialTheme.typography.bodySmall,
                            color = BoulderBuddy.colors.textSecondary,
                        )
                        // fillWidth: Heatmap spannt sich über die gesamte Breite,
                        // egal wie viele Tage Daten vorliegen.
                        ActivityHeatmap(
                            intensities = activity,
                            fillWidth = true,
                        )
                        ActivityLegend()
                    }
                }
            }
        },
    )
}

// "weniger → mehr"-Legende für die Heatmap. Die Zellen spiegeln dieselbe
// Alpha-Staffelung wie ActivityHeatmap (Sockel 0.15 + 0.85 * intensity).
@Composable
private fun ActivityLegend(modifier: Modifier = Modifier) {
    val cellColor = BoulderBuddy.colors.routes.green
    val steps = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.paddingXS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Weniger",
            style = MaterialTheme.typography.labelSmall,
            color = BoulderBuddy.colors.textTertiary,
        )
        steps.forEach { intensity ->
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(
                        cellColor.copy(alpha = (0.15f + 0.85f * intensity).coerceIn(0f, 1f)),
                    ),
            )
        }
        Text(
            text = "Mehr",
            style = MaterialTheme.typography.labelSmall,
            color = BoulderBuddy.colors.textTertiary,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF9F4E3)
@Composable
private fun StatistikScreenPreview() {
    BoulderBuddyTheme {
        StatistikScreen()
    }
}
