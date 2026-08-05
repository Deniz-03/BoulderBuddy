package com.boulderbuddy.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.EmptyState
import com.boulderbuddy.ui.components.TopBar
import com.boulderbuddy.ui.screens.BoulderDetailRoute
import com.boulderbuddy.ui.screens.BoulderUebersichtScreen
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.viewmodel.BoulderUebersichtUiState
import kotlinx.coroutines.launch

// =============================================================================
// BoulderListDetail — Zwei-Spalten-Layout für die Boulder-Übersicht.
//
// Dasselbe Muster wie [SessionsListDetail], und aus demselben Grund: die Übersicht ist eine
// Auswahlliste, deren Einträge einzeln betrachtet werden. Am Telefon ist das ein Push, auf
// einem 1280 dp breiten Fenster ein Wechsel des ganzen Bildes für eine Karte mit drei Zeilen
// — der Zusammenhang zur Liste geht dabei verloren, obwohl daneben Platz frei ist.
//
// Der Unterschied zum Sessions-Tab: das Raster links bleibt mehrspaltig. Es schrumpft nur von
// drei Spalten auf die, die im List-Pane noch Platz haben — `GridCells.Adaptive` regelt das
// von selbst, ohne dass hier eine Zahl steht.
// =============================================================================

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun BoulderListDetail(
    state: BoulderUebersichtUiState,
    onOpenSessionOverview: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditBoulder: (Int) -> Unit,
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<Int>()
    val scope = rememberCoroutineScope()

    BackHandler(enabled = navigator.canNavigateBack()) {
        scope.launch { navigator.navigateBack() }
    }

    val randfarbe = BoulderBuddy.colors.borderSubtle
    val beidePanesSichtbar =
        navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded

    // Kein Spalt zwischen den Panes, sondern eine Kante — Begründung in [SessionsListDetail].
    val direktive = navigator.scaffoldDirective.copy(horizontalPartitionSpacerSize = 0.dp)

    ListDetailPaneScaffold(
        directive = direktive,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                BoulderUebersichtScreen(
                    state = state,
                    onOpenBoulder = { boulderId ->
                        scope.launch {
                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, boulderId)
                        }
                    },
                    onOpenSessionOverview = onOpenSessionOverview,
                    onOpenSettings = onOpenSettings,
                )
            }
        },
        detailPane = {
            AnimatedPane {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()
                            val staerke = 1.dp.toPx()
                            drawRect(color = randfarbe, size = Size(staerke, size.height))
                        },
                ) {
                    val selectedId = navigator.currentDestination?.contentKey
                    if (selectedId != null) {
                        BoulderDetailRoute(
                            boulderId = selectedId,
                            onBack = if (beidePanesSichtbar) {
                                null
                            } else {
                                { scope.launch { navigator.navigateBack() } }
                            },
                            onEdit = onEditBoulder,
                        )
                    } else {
                        BoulderDetailPlaceholder()
                    }
                }
            }
        },
    )
}

/** Leerer Detail-Pane. Aufbau und Begründung wie beim Sessions-Platzhalter. */
@Composable
private fun BoulderDetailPlaceholder() {
    BoulderBuddyScaffold(
        topBar = { TopBar(title = "") },
        content = { _ ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Outlined.TouchApp,
                    title = "Kein Boulder gewählt",
                    description = "Tippe links auf einen Boulder, um Grad, Versuche, " +
                        "Notiz und Foto hier zu sehen.",
                )
            }
        },
    )
}
