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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.EmptyState
import com.boulderbuddy.ui.components.FilterChip
import com.boulderbuddy.ui.components.RouteCard
import com.boulderbuddy.ui.components.TextField
import com.boulderbuddy.ui.components.UebersichtTopBar
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.M3OnPrimary
import androidx.compose.ui.graphics.Color
import com.boulderbuddy.ui.viewmodel.BoulderOverviewItemUi
import com.boulderbuddy.ui.viewmodel.BoulderUebersichtUiState
import com.boulderbuddy.ui.viewmodel.GradeFilterUi
import com.boulderbuddy.ui.viewmodel.GradeSystemFilterUi

// Teilt sich mit der Session-Übersicht den Bottom-Nav-Tab 2; der Header-Dropdown schaltet
// zwischen beiden um. Zusätzlich über die "Alle Boulder"-Schnellaktion auf dem Home-Screen
// erreichbar (Navigation hierher).
//
// Filter: zweistufig & dynamisch (Ansatz A). Erst ein Gradsystem wählen (nur Systeme, die
// real Boulder haben), dann optional einen konkreten Grad dieses Systems. So funktioniert der
// Filter über beliebige Systeme hinweg — inkl. Custom-/Farb-Labels — ohne feste Bereiche.
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
    // Gewähltes System (null = "Alle Systeme") und Grad (null = "Alle Grade" des Systems).
    var selectedSystemId by remember { mutableStateOf<Int?>(null) }
    var selectedGradeId by remember { mutableStateOf<Int?>(null) }
    // Suche: das Feld wird über das Lupen-Icon ein-/ausgeblendet und filtert zusätzlich
    // zu den Grad-Filtern (beides greift gleichzeitig).
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Gegen veraltete Auswahl absichern, falls sich die Daten ändern (System/Grad verschwindet).
    val effectiveSystemId = selectedSystemId?.takeIf { id -> state.systems.any { it.id == id } }
    val gradeChips = effectiveSystemId?.let { state.gradesBySystem[it] }.orEmpty()
    val effectiveGradeId = selectedGradeId?.takeIf { id -> gradeChips.any { it.id == id } }

    // Suchbegriff greift über Name, Sektor und Grad-Label — die drei Dinge, die auf der
    // Kachel stehen. Leerer Begriff = kein Filter.
    val query = searchQuery.trim()
    val boulders = state.boulders.filter { boulder ->
        val systemOk = effectiveSystemId == null || boulder.systemId == effectiveSystemId
        val gradeOk = effectiveGradeId == null || boulder.gradeId == effectiveGradeId
        val searchOk = query.isEmpty() ||
            boulder.name.contains(query, ignoreCase = true) ||
            boulder.meta.contains(query, ignoreCase = true) ||
            boulder.grade.contains(query, ignoreCase = true)
        systemOk && gradeOk && searchOk
    }

    BoulderBuddyScaffold(
        topBar = {
            UebersichtTopBar(
                current = "Boulder",
                // Navigation zur Session-Übersicht (SessionUebersichtScreen).
                onSelectSessions = onOpenSessionOverview,
                onSelectBoulder = { /* bereits hier — Dropdown schließt nur */ },
                actions = {
                    // Schließen setzt den Begriff zurück, damit kein unsichtbarer Filter
                    // aktiv bleibt, wenn das Feld wieder eingeklappt ist.
                    IconButton(
                        onClick = {
                            searchOpen = !searchOpen
                            if (!searchOpen) searchQuery = ""
                        },
                    ) {
                        Icon(
                            imageVector = if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search,
                            contentDescription = if (searchOpen) "Suche schließen" else "Suchen",
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
                // --- Suchfeld (nur wenn die Lupe aktiviert wurde) ---
                if (searchOpen) {
                    item {
                        val focusRequester = remember { FocusRequester() }
                        // Direkt nach dem Aufklappen den Fokus holen, damit die Tastatur
                        // ohne zweiten Tap aufgeht.
                        LaunchedEffect(Unit) { focusRequester.requestFocus() }
                        TextField(
                            value = searchQuery,
                            onChange = { searchQuery = it },
                            placeholder = "Name, Sektor oder Grad",
                            modifier = Modifier.focusRequester(focusRequester),
                        )
                    }
                }

                // --- Filterebene 1: Gradsystem (nur Systeme mit Bouldern) ---
                if (state.systems.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS),
                        ) {
                            FilterChip(
                                label = "Alle",
                                selected = effectiveSystemId == null,
                                onClick = {
                                    selectedSystemId = null
                                    selectedGradeId = null
                                },
                            )
                            state.systems.forEach { system ->
                                FilterChip(
                                    label = system.name,
                                    selected = effectiveSystemId == system.id,
                                    onClick = {
                                        selectedSystemId = system.id
                                        // Grad-Auswahl beim Systemwechsel zurücksetzen.
                                        selectedGradeId = null
                                    },
                                )
                            }
                        }
                    }
                }

                // --- Filterebene 2: Grade des gewählten Systems (dynamisch) ---
                if (gradeChips.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS),
                        ) {
                            FilterChip(
                                label = "Alle",
                                selected = effectiveGradeId == null,
                                onClick = { selectedGradeId = null },
                            )
                            gradeChips.forEach { grade ->
                                FilterChip(
                                    label = grade.label,
                                    selected = effectiveGradeId == grade.id,
                                    onClick = { selectedGradeId = grade.id },
                                )
                            }
                        }
                    }
                }

                // --- Leerzustand: gar keine Boulder vs. nichts gefunden ---
                // Die Unterscheidung ist wichtig: im ersten Fall fehlen Daten (der Nutzer muss
                // etwas anlegen), im zweiten filtert er nur zu eng (er muss anders suchen).
                if (boulders.isEmpty()) {
                    item {
                        if (state.boulders.isEmpty()) {
                            EmptyState(
                                icon = Icons.Outlined.Landscape,
                                title = "Noch keine Boulder",
                                description = "Boulder, die du in einer Session anlegst, " +
                                    "sammeln sich hier sessionübergreifend.",
                            )
                        } else {
                            EmptyState(
                                icon = Icons.Outlined.SearchOff,
                                title = "Nichts gefunden",
                                description = "Kein Boulder passt zu Suche und Filter. " +
                                    "Probier einen anderen Begriff oder setz die Filter zurück.",
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
                    BoulderOverviewItemUi(0, "5c", "Dachrinne", "Sektor A", Color(0xFFD64541), systemId = 3, gradeId = 30),
                    BoulderOverviewItemUi(1, "6a", "Slab Talk", "Sektor A", Color(0xFF2F6FE0), systemId = 3, gradeId = 31),
                    BoulderOverviewItemUi(2, "V2", "Warmup", "Sektor D", Color(0xFFF4C20D), systemId = 2, gradeId = 20),
                    BoulderOverviewItemUi(3, "6b", "Überhang", "Sektor C", Color(0xFF2E9E52), systemId = 3, gradeId = 32),
                ),
                systems = listOf(
                    GradeSystemFilterUi(2, "V-Scale"),
                    GradeSystemFilterUi(3, "Französisch"),
                ),
                gradesBySystem = mapOf(
                    2 to listOf(GradeFilterUi(20, "V2")),
                    3 to listOf(
                        GradeFilterUi(30, "5c"),
                        GradeFilterUi(31, "6a"),
                        GradeFilterUi(32, "6b"),
                    ),
                ),
            ),
        )
    }
}
