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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.R
import androidx.compose.ui.unit.dp
import com.boulderbuddy.ui.components.ActivityHeatmap
import com.boulderbuddy.ui.components.BarChart
import com.boulderbuddy.ui.components.LineChart
import com.boulderbuddy.ui.components.BarChartEntry
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.FilterChip
import com.boulderbuddy.ui.components.SectionHeader
import com.boulderbuddy.ui.components.SelectableChip
import com.boulderbuddy.ui.components.EmptyState
import com.boulderbuddy.ui.components.StatCard
import com.boulderbuddy.ui.components.TopBar
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.model.Zeitraum
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
        QuickStat(
            label = stringResource(R.string.statistik_flash_rate),
            value = state.flashRate,
        ),
        QuickStat(
            label = stringResource(R.string.statistik_tops_gesamt),
            value = state.totalTops.toString(),
        ),
        QuickStat(
            label = stringResource(R.string.uebersicht_sessions),
            value = state.totalSessions.toString(),
        ),
    )
    val activity = state.activity

    // Gewähltes System für die Grade-Verteilung (Default: erstes System mit Tops).
    var selectedSystemId by rememberSaveable { mutableStateOf<Int?>(null) }
    val effectiveSystemId = selectedSystemId?.takeIf { id -> state.distributionSystems.any { it.id == id } }
        ?: state.distributionSystems.firstOrNull()?.id
    val gradeDistribution = effectiveSystemId?.let { state.distributionBySystem[it] }.orEmpty()

    // Körnung der beiden Verlaufs-Diagramme. Ein gemeinsamer Umschalter für beide: sie
    // beantworten dieselbe Frage aus zwei Richtungen ("wie viel" und "wie schwer"), und zwei
    // getrennte Regler nebeneinander würde man unweigerlich gegeneinander verstellen.
    var zeitraum by rememberSaveable { mutableStateOf(Zeitraum.Woche) }

    // Gewählter Tag und Gradsystem der Tagesstatistik. Beide fallen auf den jüngsten bzw.
    // ersten vorhandenen Wert zurück, sobald die Auswahl ins Leere zeigt — etwa wenn ein Tag
    // aus der Leiste rutscht, weil neuere dazugekommen sind.
    var tagIndex by rememberSaveable { mutableIntStateOf(0) }
    var tagesSystem by rememberSaveable { mutableStateOf<Int?>(null) }
    val gewaehlterTag = state.tage.getOrNull(tagIndex) ?: state.tage.firstOrNull()
    val tagesSysteme = gewaehlterTag
        ?.let { state.tagesstatistik[it.datum].orEmpty().keys }
        .orEmpty()
        .mapNotNull { id -> state.systemNamen[id]?.let { TagesSystemUi(id, it) } }
        .sortedBy { it.name }
    if (tagesSystem == null || tagesSysteme.none { it.id == tagesSystem }) {
        tagesSystem = tagesSysteme.firstOrNull()?.id
    }
    val routenVerlauf = state.routenVerlauf[zeitraum].orEmpty()
    val gradVerlauf = effectiveSystemId
        ?.let { state.gradVerlauf[zeitraum]?.get(it) }
        .orEmpty()

    BoulderBuddyScaffold(
        topBar = {
            TopBar(
                title = stringResource(R.string.statistik_titel),
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.aktion_einstellungen),
                            tint = BoulderBuddy.colors.onChrome,
                        )
                    }
                },
            )
        },
        // Die Navigationsleiste stellt das Gerüst (AppNavigation), nicht der Screen.
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
                            title = stringResource(R.string.statistik_leer_titel),
                            description = stringResource(R.string.statistik_leer_text),
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

                // --- Einzelner Tag ---
                // Steht vor den Verläufen über Wochen und Monate: die Frage „wie lief mein
                // letzter Klettertag" ist die näherliegende, und die Antwort darauf ist noch
                // frisch erinnerbar. Die Entwicklung über Monate schaut man seltener an.
                if (gewaehlterTag != null) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS)) {
                                state.tage.forEachIndexed { index, tag ->
                                    SelectableChip(
                                        label = tag.label,
                                        selected = tag.datum == gewaehlterTag.datum,
                                        onClick = { tagIndex = index },
                                    )
                                }
                            }
                            TagesstatistikBlock(
                                titel = stringResource(R.string.tag_verlauf_tag),
                                systeme = tagesSysteme,
                                gewaehltesSystem = tagesSystem,
                                statistik = state.tagesstatistik[gewaehlterTag.datum]
                                    ?.get(tagesSystem),
                                onSystemWaehlen = { tagesSystem = it },
                            )
                        }
                    }
                }

                // --- Hangboard-Training ---
                // Steht bewusst VOR Grade-Verteilung und Aktivität: Hangboard-Training ist
                // gezieltes Training mit Zielwerten, die anderen beiden sind Rückblicke auf
                // das, was ohnehin passiert ist. Was man steuert, gehört nach oben.
                item { HangboardSection(state, onOpen = onOpenHangboardHistorie) }

                // --- Verlauf über die Zeit ---
                item {
                    ZeitraumUmschalter(
                        aktuell = zeitraum,
                        onSelect = { zeitraum = it },
                    )
                }
                item {
                    VerlaufSection(
                        titel = stringResource(
                            R.string.statistik_routen_pro,
                            zeitraum.label.dropLast(1),
                        ),
                        // Kein kumulativer Zähler: jeder Balken ist das, was in genau diesem
                        // Abschnitt geklettert wurde. Abschnitte ohne Aktivität stehen als
                        // Lücke drin und werden nicht weggelassen.
                        hinweis = stringResource(R.string.statistik_routen_hinweis),
                        leerText = stringResource(R.string.statistik_routen_leer),
                        istLeer = routenVerlauf.all { it.value == 0f },
                    ) {
                        BarChart(entries = routenVerlauf, modifier = Modifier.fillMaxWidth())
                    }
                }
                item {
                    VerlaufSection(
                        titel = stringResource(R.string.statistik_grad_verlauf),
                        hinweis = stringResource(
                            R.string.statistik_grad_hinweis,
                            zeitraum.label.dropLast(1),
                        ),
                        leerText = stringResource(R.string.statistik_keine_tops),
                        istLeer = gradVerlauf.none { it.wert != null },
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
                            // Der Verlauf zeigt immer genau ein System — Grade verschiedener
                            // Systeme liegen auf verschiedenen Skalen und ergäben eine Kurve
                            // durch zwei Maßstäbe.
                            SystemUmschalter(
                                systeme = state.distributionSystems,
                                aktuell = effectiveSystemId,
                                onSelect = { selectedSystemId = it },
                            )
                            LineChart(points = gradVerlauf, modifier = Modifier.fillMaxWidth())
                        }
                    }
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

/**
 * Umschalter für die Körnung der Verlaufs-Diagramme.
 *
 * `FilterChip` und nicht `SelectableChip`: das ist derselbe Baustein, mit dem in der
 * Boulder-Übersicht und über der Grade-Verteilung gefiltert wird — eine Auswahl aus wenigen
 * sich ausschließenden Möglichkeiten. Ein Dropdown wäre für drei Einträge ein Klick zu viel.
 */
@Composable
private fun ZeitraumUmschalter(
    aktuell: Zeitraum,
    onSelect: (Zeitraum) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS),
    ) {
        Zeitraum.entries.forEach { z ->
            FilterChip(
                label = z.label,
                selected = z == aktuell,
                onClick = { onSelect(z) },
            )
        }
    }
}

/**
 * Rahmen für ein Verlaufs-Diagramm: Überschrift, erklärende Zeile, und entweder das Diagramm
 * oder ein Hinweis.
 *
 * Der Leerfall ist ausdrücklich behandelt, statt ein Diagramm aus lauter Nullen zu zeichnen.
 * Eine flache Linie auf 0 sieht aus wie ein Messwert; „noch keine Daten" ist einer.
 */
@Composable
private fun VerlaufSection(
    titel: String,
    hinweis: String,
    leerText: String,
    istLeer: Boolean,
    modifier: Modifier = Modifier,
    diagramm: @Composable () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
        SectionHeader(text = titel)
        if (istLeer) {
            Text(
                text = leerText,
                style = MaterialTheme.typography.bodySmall,
                color = BoulderBuddy.colors.textSecondary,
            )
        } else {
            Text(
                text = hinweis,
                style = MaterialTheme.typography.bodySmall,
                color = BoulderBuddy.colors.textSecondary,
            )
            diagramm()
        }
    }
}

/**
 * Umschalter für das Gradsystem — nur sichtbar, wenn überhaupt in mehreren Systemen getoppt
 * wurde. Bei einem System wäre er eine Auswahl ohne Alternative.
 *
 * Bewusst an **jeder** Stelle wiederholt, die vom gewählten System abhängt (Grad-Verlauf und
 * Grade-Verteilung). Sie teilen sich denselben Zustand, hängen also zusammen — aber sie
 * stehen inzwischen weit auseinander im Screen, und ein Regler, dessen Wirkung man erst nach
 * dem Scrollen sieht, ist kein Regler, sondern eine Überraschung.
 */
@Composable
private fun SystemUmschalter(
    systeme: List<GradeSystemFilterUi>,
    aktuell: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (systeme.size <= 1) return
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS),
    ) {
        systeme.forEach { system ->
            FilterChip(
                label = system.name,
                selected = aktuell == system.id,
                onClick = { onSelect(system.id) },
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
        SectionHeader(text = stringResource(R.string.statistik_grad_verteilung))
        if (state.distributionSystems.isEmpty()) {
            Text(
                text = stringResource(R.string.statistik_keine_tops),
                style = MaterialTheme.typography.bodySmall,
                color = BoulderBuddy.colors.textSecondary,
            )
        } else {
            SystemUmschalter(
                systeme = state.distributionSystems,
                aktuell = effectiveSystemId,
                onSelect = onSelectSystem,
            )
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
            SectionHeader(text = stringResource(R.string.session_hangboard_ueberschrift))
            Text(
                text = stringResource(R.string.statistik_alle_workouts),
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
                label = stringResource(R.string.statistik_durchlaeufe),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatCard(
                value = state.hangboardSets.toString(),
                label = stringResource(R.string.timer_saetze),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatCard(
                value = state.hangboardHangTime,
                label = stringResource(R.string.statistik_haengezeit),
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
        SectionHeader(text = stringResource(R.string.statistik_aktivitaet))
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
            text = stringResource(R.string.statistik_weniger),
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
            text = stringResource(R.string.statistik_mehr),
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
