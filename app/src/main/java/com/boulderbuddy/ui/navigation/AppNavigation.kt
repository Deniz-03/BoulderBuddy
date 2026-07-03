package com.boulderbuddy.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
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
import com.boulderbuddy.ui.screens.HangboardTimerScreen
import com.boulderbuddy.ui.screens.HangboardTimerUiState
import com.boulderbuddy.ui.screens.HomeScreen
import com.boulderbuddy.ui.screens.RouteHinzufuegenScreen
import com.boulderbuddy.ui.screens.SessionErstellenScreen
import com.boulderbuddy.ui.screens.SessionRoute
import com.boulderbuddy.ui.screens.SessionUebersichtScreen
import com.boulderbuddy.ui.screens.StatistikScreen
import com.boulderbuddy.ui.screens.TimerPhase

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
fun AppNavigation() {
    val navController = rememberNavController()

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
                HomeScreen(
                    onOpenSettings = { navController.navigate(Einstellungen) },
                    onStartSession = { navController.navigate(SessionErstellen) },
                    // Platzhalter: aktive Session hat (per SessionRoute-Konvention) id 0,
                    // bis das ViewModel die echte aktive sessionId liefert (Phase 6.1).
                    onAddBoulderToActiveSession = {
                        navController.navigate(RouteHinzufuegen(sessionId = 0))
                    },
                    onOpenAllBoulders = { navController.navigate(BoulderUebersicht) },
                    // Platzhalter: letzte Session ist eine abgeschlossene Beispiel-Session (id 1).
                    onOpenLastSession = { navController.navigate(Session(sessionId = 1)) },
                )
            }
            composable<Sessions> {
                SessionUebersichtScreen(
                    onOpenSession = { sessionId -> navController.navigate(Session(sessionId)) },
                    onCreateSession = { navController.navigate(SessionErstellen) },
                    // Umschalten auf die Boulder-Ansicht desselben Tabs (Variante A):
                    // ersetzt Sessions, statt es zu stapeln → Zurück-Umschalten bleibt möglich.
                    onOpenBoulderOverview = {
                        navController.navigate(BoulderUebersicht) {
                            popUpTo(Sessions) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onOpenSettings = { navController.navigate(Einstellungen) },
                )
            }
            composable<Stats> {
                StatistikScreen(
                    onOpenSettings = { navController.navigate(Einstellungen) },
                )
            }
            composable<Timer> {
                HangboardTimerScreen(
                    // Platzhalter-State, bis das HangboardTimerViewModel steht (Phase 6.9).
                    state = placeholderTimerState,
                    onPlayPause = { /* TODO: Phase 6.9 */ },
                    onReset = { /* TODO: Phase 6.9 */ },
                    onSettings = { navController.navigate(Einstellungen) },
                )
            }

            // --- Push-Ziele ohne Argument ---------------------------------------
            composable<Einstellungen> {
                EinstellungenScreen(onBack = { navController.popBackStack() })
            }
            composable<SessionErstellen> {
                SessionErstellenScreen(
                    onBack = { navController.popBackStack() },
                    // Nach dem Anlegen zur (aktiven) Session; das Erstellen-Formular wird dabei
                    // vom Back-Stack genommen, damit Back von der Session direkt nach Home führt.
                    onSessionCreated = { newSessionId ->
                        navController.navigate(Session(newSessionId)) {
                            popUpTo(SessionErstellen) { inclusive = true }
                        }
                    },
                )
            }
            composable<BoulderUebersicht> {
                BoulderUebersichtScreen(
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
            composable<RouteHinzufuegen> { entry ->
                val args = entry.toRoute<RouteHinzufuegen>()
                RouteHinzufuegenScreen(
                    sessionId = args.sessionId,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }

            // --- Push-Ziele mit Argument (typsicher aus toRoute()) --------------
            composable<BoulderDetail> { entry ->
                val args = entry.toRoute<BoulderDetail>()
                BoulderDetailScreen(
                    boulderId = args.boulderId,
                    onBack = { navController.popBackStack() },
                    // Platzhalter: Bearbeiten öffnet vorerst "Boulder hinzufügen" (ohne Vorbefüllung),
                    // bis der Edit-Modus mit boulderId steht (Phase 6.5/6.7).
                    onEdit = { navController.navigate(RouteHinzufuegen()) },
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

        // Gemeinsame BottomNav: nur auf den 4 Tab-Zielen sichtbar.
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

// Wechselt zu einem Top-Level-Tab, ohne den Back-Stack zu stapeln:
// popUpTo(Home) mit saveState + launchSingleTop + restoreState.
private fun NavController.navigateToTab(tab: BottomNavTab) {
    val destination = topLevelDestinations.first { it.tab == tab }
    navigate(destination.route) {
        // Bis zum Start-Ziel zurückräumen und dessen Zustand sichern.
        popUpTo(Home) { saveState = true }
        // Nicht mehrfach dasselbe Ziel auf den Stack legen.
        launchSingleTop = true
        // Zustand eines zuvor besuchten Tabs wiederherstellen.
        restoreState = true
    }
}

// Platzhalter-Timer-State für Phase 1 (entspricht dem Preview-State, aber pausiert).
// TODO: In Phase 6.9 durch echten StateFlow des HangboardTimerViewModel ersetzen.
private val placeholderTimerState = HangboardTimerUiState(
    progress = 0f,
    time = "00:07",
    restTime = "00:03",
    phase = TimerPhase.HANG,
    currentSet = 1,
    totalSets = 6,
    isRunning = false,
)
