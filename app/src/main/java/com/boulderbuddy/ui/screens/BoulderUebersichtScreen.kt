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
import com.boulderbuddy.ui.theme.routeColorForKey

// Grade-Filter über der Liste. "Alle" ist der Default (kein Filter).
// TODO: Bereiche/Optionen aus dem gewählten Grading-System ableiten (Room),
//  statt sie hier fest zu verdrahten.
private val filterOptions = listOf("Alle", "5a–6a", "6b–7a", "7b+")

// Platzhalter-Daten eines Boulders. accentColorKey = gespeicherter Farbwert,
// wird via routeColorForKey() in die Routenfarbe übersetzt.
// id: Platzhalter-Boulderschlüssel für die Navigation (später die echte Room-ID).
private data class BoulderData(
    val id: Int,
    val grade: String,
    val name: String,
    val sektor: String,
    val accentColorKey: String,
)

// TODO: Diese Liste kommt später aus dem ViewModel — ALLE Boulder, unabhängig von
//  Sessions (Room: SELECT * FROM Route bzw. eigene Boulder-Tabelle).
private val placeholderBoulders = listOf(
    BoulderData(0, "5c", "Dachrinne", "Sektor A", "red"),
    BoulderData(1, "6a", "Slab Talk", "Sektor A", "blue"),
    BoulderData(2, "5a", "Warmup", "Sektor D", "yellow"),
    BoulderData(3, "6b", "Überhang", "Sektor C", "green"),
    BoulderData(4, "6c", "Dynamo", "Sektor B", "purple"),
    BoulderData(5, "7a", "Crux", "Sektor B", "orange"),
)

// Teilt sich mit der Session-Übersicht den Bottom-Nav-Tab 2; der Header-Dropdown schaltet
// zwischen beiden um. Zusätzlich über die "Alle Boulder"-Schnellaktion auf dem Home-Screen
// erreichbar (Navigation hierher).
@Composable
fun BoulderUebersichtScreen(
    // Navigations-Callbacks (Phase 2). Defaults = {} halten Preview & Tests lauffähig.
    // Kein onBack: die UebersichtTopBar trägt keinen Zurück-Pfeil — System-Back genügt.
    onOpenBoulder: (Int) -> Unit = {},
    onOpenSessionOverview: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    // Aktiver Grade-Filter (Index in filterOptions). Start: "Alle".
    var selectedFilter by remember { mutableIntStateOf(0) }

    // TODO: Liste nach selectedFilter filtern, sobald echte Grade-Daten vorliegen.
    //  Vorerst werden immer alle Platzhalter-Boulder gezeigt.
    val boulders = placeholderBoulders

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
                                meta = boulder.sektor,
                                accentColor = routeColorForKey(boulder.accentColorKey),
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
        BoulderUebersichtScreen()
    }
}
