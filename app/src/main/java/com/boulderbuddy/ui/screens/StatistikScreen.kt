package com.boulderbuddy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.boulderbuddy.ui.components.ActivityHeatmap
import com.boulderbuddy.ui.components.BarChart
import com.boulderbuddy.ui.components.BarChartEntry
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.FilterChip
import com.boulderbuddy.ui.components.SectionHeader
import com.boulderbuddy.ui.components.StatCard
import com.boulderbuddy.ui.components.TopBar
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.M3OnPrimary
import androidx.compose.ui.graphics.Color
import com.boulderbuddy.ui.viewmodel.GradeSystemFilterUi
import com.boulderbuddy.ui.viewmodel.StatistikUiState

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

// Ein Quick-Stat-Kärtchen (Label + fertig formatierter Wert).
private data class QuickStat(val label: String, val value: String)

@Composable
fun StatistikScreen(
    // Anzeige-Zustand aus dem StatistikViewModel (Phase 6.8, aus Room aggregiert).
    state: StatistikUiState = StatistikUiState(),
    // true = breites Layout (Tablet): Grade-Verteilung + Aktivität nebeneinander (Phase 7.1).
    wide: Boolean = false,
    // Navigations-Callback (Phase 2). Default = {} hält Preview & Tests lauffähig.
    onOpenSettings: () -> Unit = {},
) {
    val quickStats = listOf(
        QuickStat(label = "Flash Rate", value = state.flashRate),
        QuickStat(label = "Tops gesamt", value = state.totalTops.toString()),
        QuickStat(label = "Sessions", value = state.totalSessions.toString()),
    )
    val activity = state.activity

    // Gewähltes System für die Grade-Verteilung (Default: erstes System mit Tops).
    var selectedSystemId by remember { mutableStateOf<Int?>(null) }
    val effectiveSystemId = selectedSystemId?.takeIf { id -> state.distributionSystems.any { it.id == id } }
        ?: state.distributionSystems.firstOrNull()?.id
    val gradeDistribution = effectiveSystemId?.let { state.distributionBySystem[it] }.orEmpty()

    BoulderBuddyScaffold(
        topBar = {
            TopBar(
                title = "Statistik",
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Einstellungen",
                            tint = M3OnPrimary,
                        )
                    }
                },
            )
        },
        // BottomNav wird ab Phase 1.3 zentral vom Navigations-Gerüst gestellt.
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
                item {
                    QuickStatsRow(quickStats)
                }

                if (wide) {
                    // Tablet: Grade-Verteilung und Aktivität nebeneinander (top-aligned).
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.paddingXL),
                        ) {
                            GradeDistributionSection(
                                state = state,
                                effectiveSystemId = effectiveSystemId,
                                gradeDistribution = gradeDistribution,
                                onSelectSystem = { selectedSystemId = it },
                                modifier = Modifier.weight(1f),
                            )
                            ActivitySection(
                                activity = activity,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    item { HangboardSection(state) }
                } else {
                    // Phone: alles untereinander (unverändert).
                    item {
                        GradeDistributionSection(
                            state = state,
                            effectiveSystemId = effectiveSystemId,
                            gradeDistribution = gradeDistribution,
                            onSelectSystem = { selectedSystemId = it },
                        )
                    }
                    item { HangboardSection(state) }
                    item { ActivitySection(activity = activity) }
                }
            }
        },
    )
}

// Quick-Stats-Reihe. height(IntrinsicSize.Min) + fillMaxHeight() → alle Karten gleich hoch.
@Composable
private fun QuickStatsRow(quickStats: List<QuickStat>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
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

// Grade-Verteilung (pro System, da Grade systemübergreifend nicht vergleichbar).
@Composable
private fun GradeDistributionSection(
    state: StatistikUiState,
    effectiveSystemId: Int?,
    gradeDistribution: List<BarChartEntry>,
    onSelectSystem: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
        SectionHeader(text = "Grade-Verteilung")
        if (state.distributionSystems.isEmpty()) {
            Text(
                text = "Noch keine getoppten Boulder.",
                style = MaterialTheme.typography.bodySmall,
                color = BoulderBuddy.colors.textSecondary,
            )
        } else {
            // System-Umschalter — nur nötig, wenn in mehreren Systemen getoppt wurde.
            if (state.distributionSystems.size > 1) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS),
                ) {
                    state.distributionSystems.forEach { system ->
                        FilterChip(
                            label = system.name,
                            selected = effectiveSystemId == system.id,
                            onClick = { onSelectSystem(system.id) },
                        )
                    }
                }
            }
            BarChart(
                entries = gradeDistribution,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// Hangboard-Training-Kacheln.
@Composable
private fun HangboardSection(state: StatistikUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
        SectionHeader(text = "Hangboard-Training")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(Dimens.paddingM),
        ) {
            StatCard(
                value = state.hangboardWorkouts.toString(),
                label = "Durchläufe",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatCard(
                value = state.hangboardSets.toString(),
                label = "Sätze",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatCard(
                value = state.hangboardHangTime,
                label = "Hängezeit",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

// Aktivitäts-Heatmap der letzten Wochen.
@Composable
private fun ActivitySection(activity: List<Float>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
        SectionHeader(text = "Aktivität")
        // TODO(DB): Zeitraum dynamisch aus den echten Daten bilden
        //  (z.B. konkrete Datumsspanne statt "letzten 4 Wochen").
        Text(
            text = "Deine Kletteraktivität der letzten 4 Wochen",
            style = MaterialTheme.typography.bodySmall,
            color = BoulderBuddy.colors.textSecondary,
        )
        // fillWidth: Heatmap spannt sich über die gesamte Breite.
        ActivityHeatmap(
            intensities = activity,
            fillWidth = true,
        )
        ActivityLegend()
    }
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
        StatistikScreen(
            state = StatistikUiState(
                flashRate = "42%",
                totalTops = 23,
                totalSessions = 12,
                hangboardWorkouts = 8,
                hangboardSets = 48,
                hangboardHangTime = "56min",
                distributionSystems = listOf(
                    GradeSystemFilterUi(2, "V-Scale"),
                    GradeSystemFilterUi(3, "Französisch"),
                ),
                distributionBySystem = mapOf(
                    3 to listOf(
                        BarChartEntry("5", 3f, Color(0xFF2E9E52)),
                        BarChartEntry("6a", 7f, Color(0xFF2F6FE0)),
                        BarChartEntry("6b", 5f, Color(0xFFF39C12)),
                        BarChartEntry("6c", 4f, Color(0xFFD64541)),
                        BarChartEntry("7a", 2f, Color(0xFF8E44AD)),
                    ),
                    2 to listOf(
                        BarChartEntry("V2", 4f, Color(0xFF2E9E52)),
                        BarChartEntry("V3", 6f, Color(0xFF2F6FE0)),
                        BarChartEntry("V4", 3f, Color(0xFFF39C12)),
                    ),
                ),
                activity = listOf(
                    0f, 0.2f, 0f, 0.6f, 1f, 0.4f, 0f,
                    0.3f, 0f, 0.8f, 0.5f, 0f, 0.2f, 0.9f,
                    1f, 0.7f, 0.3f, 0f, 0.6f, 0.4f, 0.1f,
                    0f, 0.5f, 0.9f, 0.2f, 0.7f, 0f, 0.3f,
                ),
            ),
        )
    }
}
