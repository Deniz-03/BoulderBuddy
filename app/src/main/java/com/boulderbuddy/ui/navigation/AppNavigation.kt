package com.boulderbuddy.ui.navigation

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
        ) {
            // --- Bottom-Nav-Tabs -------------------------------------------------
            composable<Home> { HomeScreen() }
            composable<Sessions> { SessionUebersichtScreen() }
            composable<Stats> { StatistikScreen() }
            composable<Timer> {
                HangboardTimerScreen(
                    // Platzhalter-State, bis das HangboardTimerViewModel steht (Phase 6.9).
                    state = placeholderTimerState,
                    onPlayPause = { /* TODO: Phase 6.9 */ },
                    onReset = { /* TODO: Phase 6.9 */ },
                    onSettings = { /* TODO: Phase 2 – Navigation */ },
                )
            }

            // --- Push-Ziele ohne Argument ---------------------------------------
            composable<Einstellungen> { EinstellungenScreen() }
            composable<SessionErstellen> { SessionErstellenScreen() }
            composable<BoulderUebersicht> { BoulderUebersichtScreen() }
            composable<RouteHinzufuegen> { RouteHinzufuegenScreen() }

            // --- Push-Ziele mit Argument (typsicher aus toRoute()) --------------
            composable<BoulderDetail> { entry ->
                val args = entry.toRoute<BoulderDetail>()
                BoulderDetailScreen(boulderId = args.boulderId)
            }
            composable<Session> { entry ->
                val args = entry.toRoute<Session>()
                // Dispatcher: entscheidet selbst aktiv (SessionDetail) vs. beendet (AlteSession).
                SessionRoute(sessionId = args.sessionId)
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
private fun NavDestination?.toBottomNavTabOrNull(): BottomNavTab? =
    this?.let { dest ->
        topLevelDestinations.firstOrNull { dest.hasRoute(it.route::class) }?.tab
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
