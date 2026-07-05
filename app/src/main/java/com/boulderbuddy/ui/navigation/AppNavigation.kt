package com.boulderbuddy.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.window.core.layout.WindowSizeClass
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.boulderbuddy.ui.components.BottomNav
import com.boulderbuddy.ui.components.BottomNavTab
import com.boulderbuddy.ui.screens.BoulderDetailScreen
import com.boulderbuddy.ui.screens.BoulderUebersichtScreen
import com.boulderbuddy.ui.screens.EinstellungenScreen
import com.boulderbuddy.ui.screens.GhostClimberScreen
import com.boulderbuddy.ui.screens.HangboardTimerScreen
import com.boulderbuddy.ui.screens.HomeScreen
import com.boulderbuddy.ui.screens.RouteHinzufuegenScreen
import com.boulderbuddy.ui.screens.SessionErstellenScreen
import com.boulderbuddy.ui.screens.SessionRoute
import com.boulderbuddy.ui.screens.SessionUebersichtScreen
import com.boulderbuddy.ui.screens.StatistikScreen
import com.boulderbuddy.ui.viewmodel.BoulderDetailViewModel
import com.boulderbuddy.ui.viewmodel.BoulderUebersichtViewModel
import com.boulderbuddy.ui.viewmodel.EinstellungenViewModel
import com.boulderbuddy.ui.viewmodel.GhostClimberViewModel
import com.boulderbuddy.ui.viewmodel.HangboardTimerViewModel
import com.boulderbuddy.ui.viewmodel.HomeViewModel
import com.boulderbuddy.ui.viewmodel.RouteHinzufuegenViewModel
import com.boulderbuddy.ui.viewmodel.SessionErstellenViewModel
import com.boulderbuddy.ui.viewmodel.SessionListViewModel
import com.boulderbuddy.ui.viewmodel.StatistikViewModel
import com.boulderbuddy.widget.WidgetIntent

// =============================================================================
// AppNavigation — der NavHost: verbindet jede Route aus Destinations.kt mit
//                  dem passenden Screen und stellt die gemeinsame BottomNav.
// =============================================================================
//
// Phase 1: Navigations-Gerüst mit Platzhalter-Daten (noch keine ViewModels/Room).
//   - Type-safe Routen (composable<Route> / toRoute()).
//   - BottomNav ist HOCHGEZOGEN: nicht mehr in den Tab-Screens, sondern hier —
//     sichtbar nur bei den 4 Tab-Zielen (Home/Sessions/Stats/Timer).
//   - Tab-Wechsel mit launchSingleTop + saveState/restoreState -> kein Stacking.
//
// Push-Navigation (Screen-Callbacks) folgt in Phase 2.

@Composable
fun AppNavigation(
    // Aus MainActivity (currentWindowAdaptiveInfo): steuert Compact vs. Medium/Expanded.
    windowSizeClass: WindowSizeClass,
    // Optionales Sprungziel vom Homescreen-Widget (7.4c); null = normaler Start (Home).
    initialNavTarget: String? = null,
) {
    val navController = rememberNavController()

    // Einmaliger Sprung ins Widget-Ziel (7.4c). key = Zielwert → feuert nur beim Start-Intent,
    // nicht bei jeder Recomposition.
    LaunchedEffect(initialNavTarget) {
        when (initialNavTarget) {
            WidgetIntent.TARGET_TIMER -> navController.navigateToTab(BottomNavTab.Timer)
            WidgetIntent.TARGET_NEW_SESSION -> navController.navigate(SessionErstellen)
        }
    }

    // Ab Medium-Breite (≥ 600 dp) nebeneinander-Layouts (Tablet). Darunter (Compact)
    // bleibt alles beim bestehenden Phone-Push-Verhalten.
    val isWideLayout = windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
    )

    // Aktuelles Ziel beobachten, um den aktiven Tab abzuleiten bzw. die BottomNav
    // nur auf den 4 Tab-Zielen einzublenden.
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = backStackEntry?.destination.toBottomNavTabOrNull()

    Column(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Home,
            modifier = Modifier.weight(1f),
            // Kurzer Crossfade (~120 ms): das Default-~700-ms-Fade des NavHost wirkt träge,
            // ein harter Instant-Wechsel lässt kurz alte Komponenten aufblitzen. Der kurze
            // Fade blendet die alte Ansicht sauber aus. Gilt für Push- UND Tab-Wechsel.
            enterTransition = { fadeIn(tween(120)) },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(120)) },
            popExitTransition = { fadeOut(tween(120)) },
        ) {
            // --- Bottom-Nav-Tabs -------------------------------------------------
            composable<Home> {
                val viewModel: HomeViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    onOpenSettings = { navController.navigate(Einstellungen) },
                    onStartSession = { navController.navigate(SessionErstellen) },
                    // Boulder zur AKTIVEN Session hinzufügen (echte sessionId aus dem ViewModel).
                    // Ohne aktive Session tut der Klick nichts (die Kachel erscheint dann ohnehin nicht).
                    onAddBoulderToActiveSession = {
                        state.activeSessionId?.let { navController.navigate(RouteHinzufuegen(sessionId = it)) }
                    },
                    // BoulderUebersicht ist laut Design die "zweite Ansicht des Sessions-Tabs"
                    // (Variante A, BottomNav bleibt sichtbar). Deshalb wie ein Tab-Wechsel
                    // navigieren, NICHT als einfacher Push über Home. Ein einfacher navigate()
                    // würde BoulderUebersicht direkt über Home legen; tippt man dann den Home-Tab,
                    // sichert popUpTo(Home){saveState} die Ansicht unter Home und restoreState
                    // stellt sie sofort wieder her → man käme nicht auf Home zurück (Nav-Quirk,
                    // siehe topLevelNavOptions).
                    onOpenAllBoulders = {
                        navController.navigate(BoulderUebersicht) { topLevelNavOptions() }
                    },
                    // Letzte Session öffnen (echte sessionId aus dem ViewModel).
                    onOpenLastSession = {
                        state.lastSession?.let { navController.navigate(Session(sessionId = it.sessionId)) }
                    },
                )
            }
            composable<Sessions> {
                val viewModel: SessionListViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                // Umschalten auf die Boulder-Ansicht desselben Tabs (Variante A):
                // ersetzt Sessions, statt es zu stapeln → Zurück-Umschalten bleibt möglich.
                val onOpenBoulderOverview = {
                    navController.navigate(BoulderUebersicht) {
                        popUpTo(Sessions) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                if (isWideLayout) {
                    // Tablet (≥ 600 dp): Liste + Detail nebeneinander. Auswahl, Detail-Navigation
                    // und Zurück laufen über den Pane-Navigator im SessionsListDetail.
                    SessionsListDetail(
                        state = state,
                        onCreateSession = { navController.navigate(SessionErstellen) },
                        onOpenBoulderOverview = onOpenBoulderOverview,
                        onOpenSettings = { navController.navigate(Einstellungen) },
                        onOpenBoulder = { boulderId -> navController.navigate(BoulderDetail(boulderId)) },
                        onAddRoute = { sessionId -> navController.navigate(RouteHinzufuegen(sessionId)) },
                    )
                } else {
                    // Phone (Compact): unverändertes Push-Verhalten.
                    SessionUebersichtScreen(
                        state = state,
                        onOpenSession = { sessionId -> navController.navigate(Session(sessionId)) },
                        onCreateSession = { navController.navigate(SessionErstellen) },
                        onOpenBoulderOverview = onOpenBoulderOverview,
                        onOpenSettings = { navController.navigate(Einstellungen) },
                    )
                }
            }
            composable<Stats> {
                val viewModel: StatistikViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                StatistikScreen(
                    state = state,
                    // Auf breiten Layouts (Tablet) mehrspaltiges Dashboard.
                    wide = isWideLayout,
                    onOpenSettings = { navController.navigate(Einstellungen) },
                )
            }
            composable<Timer> {
                val viewModel: HangboardTimerViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                HangboardTimerScreen(
                    state = state,
                    onPlayPause = viewModel::onPlayPause,
                    onReset = viewModel::onReset,
                    onUpdateConfig = viewModel::updateConfig,
                    onSavePreset = viewModel::savePreset,
                    onDeletePreset = viewModel::deletePreset,
                )
            }

            // --- Push-Ziele ohne Argument ---------------------------------------
            composable<Einstellungen> {
                val viewModel: EinstellungenViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val exportMessage by viewModel.exportMessage.collectAsStateWithLifecycle()
                EinstellungenScreen(
                    state = state,
                    // Label-basiertes Custom-Grading-System anlegen (Farbe hängt an der Route).
                    onCreateGradeSystem = viewModel::createGradeSystem,
                    onDeleteGradeSystem = viewModel::deleteGradeSystem,
                    onSelectGradeSystem = viewModel::selectGradeSystem,
                    onExportSessions = viewModel::exportSessions,
                    onSetDarkMode = viewModel::setDarkMode,
                    exportMessage = exportMessage,
                    onExportMessageShown = viewModel::consumeExportMessage,
                    onOpenGhostClimber = { navController.navigate(GhostClimber) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<GhostClimber> {
                val viewModel: GhostClimberViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                GhostClimberScreen(
                    state = state,
                    onSelectVideo = viewModel::onVideoSelected,
                    onAnalyze = viewModel::analyze,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<SessionErstellen> {
                val viewModel: SessionErstellenViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SessionErstellenScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    // Session anlegen (Room) und danach zur neuen aktiven Session navigieren;
                    // das Erstellen-Formular wird dabei vom Back-Stack genommen, damit Back von
                    // der Session direkt nach Home führt.
                    onCreateSession = { ort, gradeSystemId, notiz ->
                        viewModel.createSession(ort, gradeSystemId, notiz) { newSessionId ->
                            navController.navigate(Session(newSessionId)) {
                                popUpTo(SessionErstellen) { inclusive = true }
                            }
                        }
                    },
                )
            }
            composable<BoulderUebersicht> {
                val viewModel: BoulderUebersichtViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                BoulderUebersichtScreen(
                    state = state,
                    onOpenBoulder = { boulderId -> navController.navigate(BoulderDetail(boulderId)) },
                    // Dropdown "Sessions": zurück auf die Sessions-Ansicht desselben Tabs
                    // (Variante A); ersetzt Boulder, statt es zu stapeln.
                    onOpenSessionOverview = {
                        navController.navigate(Sessions) {
                            popUpTo(BoulderUebersicht) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onOpenSettings = { navController.navigate(Einstellungen) },
                )
            }
            composable<RouteHinzufuegen> {
                val viewModel: RouteHinzufuegenViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                RouteHinzufuegenScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    // sessionId/boulderId liest das ViewModel selbst aus den Nav-Argumenten.
                    onSave = { input -> viewModel.save(input) { navController.popBackStack() } },
                )
            }

            // --- Push-Ziele mit Argument (typsicher aus toRoute()) --------------
            composable<BoulderDetail> { entry ->
                val boulderId = entry.toRoute<BoulderDetail>().boulderId
                val viewModel: BoulderDetailViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                BoulderDetailScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    // Bearbeiten öffnet das Formular im Edit-Modus (vorbefüllt, aktualisiert die Route).
                    onEdit = { navController.navigate(RouteHinzufuegen(boulderId = boulderId)) },
                    onIncrementAttempts = viewModel::incrementAttempts,
                    onDecrementAttempts = viewModel::decrementAttempts,
                )
            }
            composable<Session> { entry ->
                val args = entry.toRoute<Session>()
                // Dispatcher: entscheidet selbst aktiv (SessionDetail) vs. beendet (AlteSession).
                SessionRoute(
                    sessionId = args.sessionId,
                    onBack = { navController.popBackStack() },
                    onOpenBoulder = { boulderId -> navController.navigate(BoulderDetail(boulderId)) },
                    onAddRoute = { sessionId -> navController.navigate(RouteHinzufuegen(sessionId)) },
                )
            }
        }

        // BottomNav: nur auf den 4 Tab-Zielen sichtbar.
        if (currentTab != null) {
            Box(modifier = Modifier.navigationBarsPadding()) {
                BottomNav(
                    selectedTab = currentTab,
                    onTabSelect = { tab -> navController.navigateToTab(tab) },
                )
            }
        }
    }
}

// Ermittelt den aktiven BottomNavTab für das aktuelle Ziel — oder null, wenn das
// Ziel kein Top-Level-Tab ist (Push-Screens zeigen keine BottomNav).
private fun NavDestination?.toBottomNavTabOrNull(): BottomNavTab? {
    val dest = this ?: return null
    // Boulder-Übersicht ist die zweite Ansicht des Sessions-Tabs (Variante A): BottomNav
    // bleibt sichtbar, der Sessions-Tab bleibt markiert.
    if (dest.hasRoute(BoulderUebersicht::class)) return BottomNavTab.Sessions
    return topLevelDestinations.firstOrNull { dest.hasRoute(it.route::class) }?.tab
}

// Wechselt zu einem Top-Level-Tab, ohne den Back-Stack zu stapeln.
private fun NavController.navigateToTab(tab: BottomNavTab) {
    val destination = topLevelDestinations.first { it.tab == tab }
    navigate(destination.route) { topLevelNavOptions() }
}

// Gemeinsame Navigations-Optionen für die Top-Level-Ebene (Tabs + BoulderUebersicht als
// zweite Ansicht des Sessions-Tabs): popUpTo(Home) mit saveState + launchSingleTop +
// restoreState → kein Stacking, Tab-Zustände überleben den Wechsel.
//
// Wichtig gegen einen Nav-Quirk: popUpTo(Home){saveState} trägt Home in die interne
// backStackMap ein ("pinnt" es). Ohne diesen Pin würde ein späterer Home-Tab-Tap die gerade
// gesicherte Ansicht sofort per restoreState wiederherstellen (executePopOperations mappt den
// gesicherten State auf das popUpTo-Ziel Home, und navigate() restauriert ihn direkt wieder)
// — man käme dann nicht auf Home zurück. Alle Wege auf die Top-Level-Ebene müssen daher diese
// Optionen nutzen, sonst bleibt Home ungepinnt und die Falle schnappt zu.
private fun NavOptionsBuilder.topLevelNavOptions() {
    popUpTo(Home) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
