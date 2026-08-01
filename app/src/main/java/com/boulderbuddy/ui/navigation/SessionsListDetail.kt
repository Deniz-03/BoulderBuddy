package com.boulderbuddy.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.boulderbuddy.ui.screens.SessionRoute
import com.boulderbuddy.ui.screens.SessionUebersichtScreen
import com.boulderbuddy.ui.viewmodel.SessionListUiState
import kotlinx.coroutines.launch

// =============================================================================
// SessionsListDetail — adaptives Zwei-Spalten-Layout für den Sessions-Tab (Phase 7.1).
//
// Nur auf Medium/Expanded-Breiten (Tablet) verwendet: links die Sessions-Liste
// (List-Pane), rechts die Session-Detailansicht (Detail-Pane). Auf Compact rendert
// AppNavigation stattdessen den klassischen SessionUebersichtScreen mit Push-Navigation
// — das Verhalten dort bleibt unverändert.
//
// Der Pane-Zustand (welche Session ist gewählt) lebt im ThreePaneScaffoldNavigator und
// koexistiert bewusst mit dem äußeren NavController: adaptive Navigation NUR innerhalb
// des Sessions-Tabs, alle anderen Ziele bleiben klassisch (siehe PHASE7_PLAN 7.1.3).
// =============================================================================

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SessionsListDetail(
    state: SessionListUiState,
    onCreateSession: () -> Unit,
    onOpenBoulderOverview: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBoulder: (Int) -> Unit,
    onAddRoute: (Int) -> Unit,
) {
    // contentKey des Detail-Panes = die gewählte sessionId.
    val navigator = rememberListDetailPaneScaffoldNavigator<Int>()
    val scope = rememberCoroutineScope()

    // Zurück-Geste räumt zuerst den Pane-Stack ab (Detail → Liste), bevor der äußere
    // NavController den Tab verlässt.
    BackHandler(enabled = navigator.canNavigateBack()) {
        scope.launch { navigator.navigateBack() }
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                SessionUebersichtScreen(
                    state = state,
                    onOpenSession = { sessionId ->
                        scope.launch {
                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, sessionId)
                        }
                    },
                    onCreateSession = onCreateSession,
                    onOpenBoulderOverview = onOpenBoulderOverview,
                    onOpenSettings = onOpenSettings,
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val selectedId = navigator.currentDestination?.contentKey
                if (selectedId != null) {
                    SessionRoute(
                        sessionId = selectedId,
                        onBack = { scope.launch { navigator.navigateBack() } },
                        onOpenBoulder = onOpenBoulder,
                        onAddRoute = onAddRoute,
                    )
                } else {
                    SessionDetailPlaceholder()
                }
            }
        },
    )
}

// Leerer Detail-Pane, solange keine Session gewählt ist (nur auf breiten Layouts sichtbar,
// wo beide Panes gleichzeitig existieren).
@Composable
private fun SessionDetailPlaceholder() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Wähle links eine Session aus.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
