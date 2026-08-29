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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.boulderbuddy.R
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.EmptyState
import com.boulderbuddy.ui.components.TopBar
import com.boulderbuddy.ui.screens.SessionRoute
import com.boulderbuddy.ui.screens.SessionUebersichtScreen
import com.boulderbuddy.ui.viewmodel.SessionListUiState
import com.boulderbuddy.ui.viewmodel.SessionSortMode
import kotlinx.coroutines.launch

// =============================================================================
// SessionsListDetail — adaptives Zwei-Spalten-Layout für den Sessions-Tab (Phase 7.1).
//
// Auf breiten Fenstern (Tablet) stehen links die Sessions-Liste (List-Pane) und rechts die
// Session-Detailansicht (Detail-Pane) nebeneinander. Am Telefon klappt dasselbe Scaffold auf
// EINEN Pane um und wirkt wie die frühere Push-Navigation — verwendet wird dieser Screen aber
// auf allen Breiten. (Bis zur Vereinheitlichung stand hier "nur auf Medium/Expanded"; das ist
// seit dem Wegfall der `isWideLayout`-Verzweigung in AppNavigation falsch und hat beim Suchen
// eines Navigationsfehlers erst einmal in die Irre geführt.)
//
// Der Pane-Zustand (welche Session ist gewählt) lebt im ThreePaneScaffoldNavigator und
// koexistiert bewusst mit dem äußeren NavController: adaptive Navigation NUR innerhalb
// des Sessions-Tabs, alle anderen Ziele bleiben klassisch (siehe PHASE7_PLAN 7.1.3).
// =============================================================================

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SessionsListDetail(
    state: SessionListUiState,
    onSetSortMode: (SessionSortMode) -> Unit,
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

    // Vor dem Zeichenblock lesen: der DrawScope ist kein Composable und kommt ans Theme nicht heran.
    val randfarbe = BoulderBuddy.colors.borderSubtle

    // Stehen Liste und Detail nebeneinander? Auf schmalen Fenstern zeigt das Scaffold nur
    // einen der beiden Panes — dann ist der Detail-Bereich über die Liste gelegt und braucht
    // seinen Zurück-Weg.
    val beidePanesSichtbar =
        navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded

    /*
     * DIE LÜCKE ZWISCHEN DEN PANES WIRD AUF EINE LINIE REDUZIERT.
     *
     * `ListDetailPaneScaffold` setzt die beiden Spalten voreingestellt mit 24 dp Abstand
     * nebeneinander. Durch diesen Spalt sieht man den Fensterhintergrund — und weil er auch
     * durch das Chrome-Band oben läuft, wirkt die obere Leiste wie eingekerbt statt wie eine
     * durchgehende Leiste. Genau das liest sich als „unfertig": eine Fläche, in der ein Stück
     * fehlt, ohne dass die Lücke etwas bedeutet.
     *
     * Der Abstand fällt deshalb auf 0, und die Trennung übernimmt eine 1-dp-Linie in
     * `borderSubtle` — dieselbe Sprache, die die App überall sonst benutzt: unter der TopBar,
     * über der BottomNav, rechts neben der SideNav. Ein Spalt trennt durch Abwesenheit, eine
     * Linie durch eine Kante; das ganze Designsystem hier ist auf Kanten aufgebaut.
     */
    val direktive = navigator.scaffoldDirective.copy(horizontalPartitionSpacerSize = 0.dp)

    ListDetailPaneScaffold(
        directive = direktive,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                SessionUebersichtScreen(
                    state = state,
                    onSetSortMode = onSetSortMode,
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
                // Die Trennlinie sitzt am linken Rand des Detail-Panes und wird ÜBER den
                // Inhalt gezeichnet — `drawWithContent` nach `drawContent()`, aus demselben
                // Grund wie bei TopBar und SideNav: die Flächen der Screens darin würden eine
                // dahinter gezeichnete Linie überdecken.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()
                            val staerke = 1.dp.toPx()
                            drawRect(
                                color = randfarbe,
                                size = Size(staerke, size.height),
                            )
                        },
                ) {
                    val selectedId = navigator.currentDestination?.contentKey
                    if (selectedId != null) {
                        SessionRoute(
                            sessionId = selectedId,
                            /*
                             * Kein Zurück-Pfeil, solange die Liste daneben steht.
                             *
                             * Er führte dort in den Leerzustand des Detail-Panes — also einen
                             * Schritt in eine Ansicht, die weniger zeigt als die aktuelle,
                             * ohne dass der Nutzer irgendwo „hergekommen" wäre. Ein
                             * Zurück-Pfeil verspricht eine vorige Ansicht; die gibt es hier
                             * nicht.
                             *
                             * Wird das Fenster schmal (Telefon, geteilter Bildschirm), klappt
                             * das Scaffold auf **einen** Pane um — dann ist der Detail-Bereich
                             * tatsächlich über die Liste gelegt, und der Pfeil kommt zurück.
                             */
                            onBack = if (beidePanesSichtbar) {
                                null
                            } else {
                                { scope.launch { navigator.navigateBack() } }
                            },
                            onOpenBoulder = onOpenBoulder,
                            onAddRoute = onAddRoute,
                        )
                    } else {
                        SessionDetailPlaceholder()
                    }
                }
            }
        },
    )
}

/**
 * Der Detail-Pane, solange keine Session gewählt ist.
 *
 * Hier stand ein zentrierter Satz auf einer nackten `Surface` — und das riss das Bild
 * auseinander: die TopBar des Sessions-Screens sitzt **im** List-Pane und endete deshalb
 * mitten im Fenster bei 360 dp. Rechts davon fehlten Chrome, Trennlinie und Punktmuster;
 * die Leiste sah aus wie abgeschnitten, nicht wie eine Spalte neben einer anderen.
 *
 * Der Platzhalter trägt jetzt dasselbe Gerüst wie jeder andere Screen. Die TopBar bleibt
 * **ohne Titel**: sie hat hier nichts zu benennen, ihre Aufgabe ist allein, das Chrome-Band
 * über die volle Breite durchlaufen zu lassen. Ein erfundener Titel („Details") würde eine
 * Überschrift für etwas behaupten, das noch nicht da ist.
 */
@Composable
private fun SessionDetailPlaceholder() {
    BoulderBuddyScaffold(
        topBar = { TopBar(title = "") },
        content = { _ ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Outlined.TouchApp,
                    title = stringResource(R.string.listdetail_keine_session),
                    description = stringResource(R.string.listdetail_keine_session_text),
                )
            }
        },
    )
}
