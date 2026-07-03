package com.boulderbuddy.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.FilterChip
import com.boulderbuddy.ui.components.RouteCard
import com.boulderbuddy.ui.components.UebersichtTopBar
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.M3OnPrimary
import androidx.compose.ui.graphics.Color
import com.boulderbuddy.ui.viewmodel.BoulderOverviewItemUi
import com.boulderbuddy.ui.viewmodel.BoulderUebersichtUiState

// Grade-Filter über der Liste. "Alle" ist der Default (kein Filter).
// TODO: Bereiche/Optionen aus dem gewählten Grading-System ableiten (Room),
//  statt sie hier fest zu verdrahten.
private val filterOptions = listOf("Alle", "5a–6a", "6b–7a", "7b+")

// Übersetzt ein französisches Grade-Label ("6b") in einen vergleichbaren Wert (Zahl*10 + a/b/c).
// Nicht-numerische Labels (z.B. Farbnamen "Grün") liefern null → passen nur zu "Alle".
private fun gradeValue(label: String): Int? {
    val match = Regex("""(\d+)\s*([abcABC])?""").find(label.trim()) ?: return null
    val number = match.groupValues[1].toIntOrNull() ?: return null
    val letter = when (match.groupValues[2].lowercase()) {
        "a" -> 0; "b" -> 1; "c" -> 2; else -> 0
    }
    return number * 10 + letter
}

// Ordnet einen Boulder dem gewählten Filter zu (Index in filterOptions).
private fun matchesFilter(grade: String, filterIndex: Int): Boolean {
    if (filterIndex == 0) return true
    val value = gradeValue(grade) ?: return false
    return when (filterIndex) {
        1 -> value in 50..60   // 5a–6a
        2 -> value in 61..70   // 6b–7a
        3 -> value >= 71       // 7b+
        else -> true
    }
}

// Teilt sich mit der Session-Übersicht den Bottom-Nav-Tab 2; der Header-Dropdown schaltet
// zwischen beiden um. Zusätzlich über die "Alle Boulder"-Schnellaktion auf dem Home-Screen
// erreichbar (Navigation hierher).
@Composable
fun BoulderUebersichtScreen(
    // Anzeige-Zustand aus dem BoulderUebersichtViewModel (Phase 6.6).
    state: BoulderUebersichtUiState = BoulderUebersichtUiState(),
    // Navigations-Callbacks (Phase 2). Defaults = {} halten Preview & Tests lauffähig.
    // Kein onBack: die UebersichtTopBar trägt keinen Zurück-Pfeil — System-Back genügt.
    onOpenBoulder: (Int) -> Unit = {},
    onOpenSessionOverview: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    // Aktiver Grade-Filter (Index in filterOptions). Start: "Alle".
    var selectedFilter by remember { mutableIntStateOf(0) }

    val boulders = state.boulders.filter { matchesFilter(it.grade, selectedFilter) }

    BoulderBuddyScaffold(
        topBar = {
            UebersichtTopBar(
                current = "Boulder",
                // Navigation zur Session-Übersicht (SessionUebersichtScreen).
                onSelectSessions = onOpenSessionOverview,
                onSelectBoulder = { /* bereits hier — Dropdown schließt nur */ },
                actions = {
                    IconButton(onClick = { /* TODO: Boulder-Suche öffnen */ }) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Suchen",
                            tint = M3OnPrimary,
                        )
                    }
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
        // Keine BottomNav: Dies ist ein Push-Ziel (kein Top-Level-Tab). Die gemeinsame
        // BottomNav stellt das Navigations-Gerüst nur auf den 4 Tab-Zielen (Phase 1.3).
        content = { _ ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = Dimens.paddingL,
                    vertical = Dimens.paddingL,
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.paddingM),
            ) {
                // --- Filter-Chips (horizontal scrollbar, falls mehr dazukommen) ---
                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS),
                    ) {
                        filterOptions.forEachIndexed { index, label ->
                            FilterChip(
                                label = label,
                                selected = selectedFilter == index,
                                onClick = { selectedFilter = index },
                            )
                        }
                    }
                }

                // --- Boulder-Grid (2 Spalten) ---
                // chunked(2) macht aus der Liste 2er-Reihen; jede Reihe ist ein eigenes
                // Lazy-Item, damit lange Listen performant bleiben.
                items(boulders.chunked(2)) { rowBoulders ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.paddingM),
                    ) {
                        rowBoulders.forEach { boulder ->
                            RouteCard(
                                grade = boulder.grade,
                                name = boulder.name,
                                meta = boulder.meta,
                                accentColor = boulder.accentColor,
                                // Navigation zur Boulder-Detailansicht (mit boulderId).
                                onClick = { onOpenBoulder(boulder.id) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Ungerade Reihe: Spacer hält die einzelne Kachel auf halber Breite.
                        if (rowBoulders.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun BoulderUebersichtScreenPreview() {
    BoulderBuddyTheme {
        BoulderUebersichtScreen(
            state = BoulderUebersichtUiState(
                boulders = listOf(
                    BoulderOverviewItemUi(0, "5c", "Dachrinne", "Sektor A", Color(0xFFD64541)),
                    BoulderOverviewItemUi(1, "6a", "Slab Talk", "Sektor A", Color(0xFF2F6FE0)),
                    BoulderOverviewItemUi(2, "5a", "Warmup", "Sektor D", Color(0xFFF4C20D)),
                    BoulderOverviewItemUi(3, "6b", "Überhang", "Sektor C", Color(0xFF2E9E52)),
                ),
            ),
        )
    }
}
