package com.boulderbuddy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.BarChart
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
import com.boulderbuddy.ui.components.EmptyState
import com.boulderbuddy.ui.components.StatCard
import com.boulderbuddy.ui.components.TopBar
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import androidx.compose.ui.graphics.Color
import com.boulderbuddy.ui.viewmodel.GradeSystemFilterUi
import com.boulderbuddy.ui.viewmodel.StatistikUiState

// ─────────────────────────────────────────────────────────────────────────────
// Statistik-Screen (#10 der Wireframes). Bottom-Nav-Tab "Statistik".
//
// Stateless: sämtliche Werte kommen fertig aggregiert und formatiert aus dem
// StatistikViewModel (Room), dieser Composable rechnet nichts selbst. Ohne eine
// einzige Session zeigt er statt lauter Nullen einen Leerzustand.
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
    // Öffnet die Hangboard-Historie (alle Workouts inkl. eigenständiger Trainings).
    onOpenHangboardHistorie: () -> Unit = {},
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
                            tint = BoulderBuddy.colors.onChrome,
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
                // Ohne eine einzige Session sind alle Auswertungen Nullen und leere Diagramme —
                // dann lieber erklären, wodurch die Daten entstehen.
                if (state.totalSessions == 0) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.BarChart,
                            title = "Noch keine Auswertung",
                            description = "Sobald du deine erste Session geloggt hast, entstehen " +
                                "hier Flash-Rate, Grad-Verteilung und Aktivität.",
                        )
                    }
                    return@LazyColumn
                }

                // --- Quick-Stats ---
                // Bleibt ganz oben: die drei Kacheln sind keine eigene Auswertung, sondern die
                // Kurzfassung des ganzen Screens — dieselbe Rolle wie die Stat-Reihe auf Home.
                item {
                    QuickStatsRow(quickStats)
                }

                // --- Hangboard-Training ---
                // Steht bewusst VOR Grade-Verteilung und Aktivität: Hangboard-Training ist
                // gezieltes Training mit Zielwerten, die anderen beiden sind Rückblicke auf
                // das, was ohnehin passiert ist. Was man steuert, gehört nach oben.
                item { HangboardSection(state, onOpen = onOpenHangboardHistorie) }

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
                                range = state.activityRange,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                } else {
                    // Phone: alles untereinander.
                    item {
                        GradeDistributionSection(
                            state = state,
                            effectiveSystemId = effectiveSystemId,
                            gradeDistribution = gradeDistribution,
                            onSelectSystem = { selectedSystemId = it },
                        )
                    }
                    item { ActivitySection(activity = activity, range = state.activityRange) }
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

// Hangboard-Training-Kacheln. Antippen öffnet die Historie aller Workouts (§0 Säule 5) —
// nur so werden eigenständige Trainings (ohne Session) als Einträge sichtbar.
@Composable
private fun HangboardSection(
    state: StatistikUiState,
    onOpen: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clickable(onClick = onOpen),
        verticalArrangement = Arrangement.spacedBy(Dimens.paddingM),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SectionHeader(text = "Hangboard-Training")
            Text(
                text = "Alle Workouts ›",
                style = MaterialTheme.typography.labelLarge,
                color = BoulderBuddy.colors.textTertiary,
            )
        }
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
private fun ActivitySection(
    activity: List<Float>,
    range: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
        SectionHeader(text = "Aktivität")
        Text(
            // Konkrete Spanne aus dem ViewModel; fehlt sie, bleibt die allgemeine Angabe.
            text = range.takeIf { it.isNotBlank() }
                ?.let { "Deine Kletteraktivität · $it" }
                ?: "Deine Kletteraktivität der letzten 4 Wochen",
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
            style = MaterialTheme.typography.bodySmall,
            color = BoulderBuddy.colors.textSecondary,
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
            style = MaterialTheme.typography.bodySmall,
            color = BoulderBuddy.colors.textSecondary,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFCF6E4)
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
