package com.boulderbuddy.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.R
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.EmptyState
import com.boulderbuddy.ui.components.FilterChip
import com.boulderbuddy.ui.components.RouteCard
import com.boulderbuddy.ui.components.TextField
import com.boulderbuddy.ui.components.UebersichtTopBar
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.inhaltsBreite
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
    var selectedSystemId by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedGradeId by rememberSaveable { mutableStateOf<Int?>(null) }
    // Suche: das Feld wird über das Lupen-Icon ein-/ausgeblendet und filtert zusätzlich
    // zu den Grad-Filtern (beides greift gleichzeitig).
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

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
                current = stringResource(R.string.uebersicht_boulder),
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
                            contentDescription = stringResource(
                                if (searchOpen) R.string.boulder_suche_schliessen
                                else R.string.boulder_suchen,
                            ),
                            tint = BoulderBuddy.colors.onChrome,
                        )
                    }
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
        // Keine BottomNav: Dies ist ein Push-Ziel (kein Top-Level-Tab). Die gemeinsame
        // Die Navigationsleiste stellt das Gerüst, und nur auf den vier Tab-Zielen.
        content = { _ ->
            /*
             * `LazyVerticalGrid` mit `GridCells.Adaptive` statt `LazyColumn` + `chunked(2)`.
             *
             * Die feste Zweispaltigkeit war eine Telefon-Entscheidung, die als Konstante im
             * Code stand: am Tablet quer wurden daraus zwei Karten von je 615 dp für drei
             * Wörter Inhalt. `Adaptive` leitet die Spaltenzahl aus der tatsächlich verfügbaren
             * Breite ab — 2 am Telefon, 3–4 am Tablet — ohne dass irgendwo eine Zahl steht,
             * die zu einem Gerät gehört.
             *
             * Kopfzeilen (Suche, Filter, Leerzustand) laufen über die volle Rasterbreite;
             * `maxLineSpan` liest die aktuelle Spaltenzahl, statt sie erneut anzunehmen.
             */
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = Dimens.rasterSpalteMin),
                modifier = Modifier
                    .fillMaxSize()
                    .inhaltsBreite(Dimens.spaltenBreiteWeit),
                contentPadding = PaddingValues(
                    horizontal = Dimens.paddingL,
                    vertical = Dimens.paddingL,
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.paddingM),
                horizontalArrangement = Arrangement.spacedBy(Dimens.paddingM),
            ) {
                // --- Suchfeld (nur wenn die Lupe aktiviert wurde) ---
                if (searchOpen) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        val focusRequester = remember { FocusRequester() }
                        // Direkt nach dem Aufklappen den Fokus holen, damit die Tastatur
                        // ohne zweiten Tap aufgeht.
                        LaunchedEffect(Unit) { focusRequester.requestFocus() }
                        TextField(
                            value = searchQuery,
                            onChange = { searchQuery = it },
                            placeholder = stringResource(R.string.boulder_suche_platzhalter),
                            modifier = Modifier.focusRequester(focusRequester),
                        )
                    }
                }

                // --- Filterebene 1: Gradsystem (nur Systeme mit Bouldern) ---
                if (state.systems.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS),
                        ) {
                            FilterChip(
                                label = stringResource(R.string.boulder_filter_alle),
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
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS),
                        ) {
                            FilterChip(
                                label = stringResource(R.string.boulder_filter_alle),
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
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        if (state.boulders.isEmpty()) {
                            EmptyState(
                                icon = Icons.Outlined.Landscape,
                                title = stringResource(R.string.boulder_leer_titel),
                                description = stringResource(R.string.boulder_leer_text),
                            )
                        } else {
                            EmptyState(
                                icon = Icons.Outlined.SearchOff,
                                title = stringResource(R.string.boulder_nichts_gefunden),
                                description = stringResource(
                                    R.string.boulder_nichts_gefunden_text,
                                ),
                            )
                        }
                    }
                }

                // --- Boulder-Raster ---
                // Jede Karte ist ein eigenes Raster-Element; Umbruch und Restplatz der letzten
                // Zeile erledigt das Grid. Der frühere Spacer für die ungerade Reihe entfällt
                // damit — er war nur nötig, weil die Reihe von Hand gebaut wurde.
                items(boulders, key = { it.id }) { boulder ->
                    RouteCard(
                        grade = boulder.grade,
                        name = boulder.name,
                        meta = boulder.meta,
                        accentColor = boulder.accentColor,
                        // Navigation zur Boulder-Detailansicht (mit boulderId).
                        onClick = { onOpenBoulder(boulder.id) },
                    )
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
