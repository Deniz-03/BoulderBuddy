package com.boulderbuddy.ui.navigation

// =============================================================================
// AppNavigation — der NavHost: verbindet jede Route aus Destinations.kt
//                  mit dem passenden Screen
// =============================================================================
//
// Noch NICHTS implementiert — erst mit Deniz besprechen, sobald er die Screens
// gesehen hat. Hier steht nur, WAS rein muss.
//
// -----------------------------------------------------------------------------
// 1) Grundgerüst:
// -----------------------------------------------------------------------------
//
//   @Composable
//   fun AppNavigation() {
//       val navController = rememberNavController()
//       NavHost(navController, startDestination = <Home>) {
//           composable(<Home>)             { HomeScreen(...) }
//           composable(<Sessions>)         { SessionUebersichtScreen(...) }
//           composable(<Stats>)            { StatistikScreen(...) }
//           composable(<Timer>)            { HangboardTimerScreen(...) }
//           composable(<Einstellungen>)    { EinstellungenScreen(...) }
//           composable(<SessionErstellen>) { SessionErstellenScreen(...) }
//           composable(<BoulderUebersicht>){ BoulderUebersichtScreen(...) }
//           composable(<RouteHinzufuegen>) { RouteHinzufuegenScreen(...) }
//           composable(<BoulderDetail>)    { -> boulderId auslesen, BoulderDetailScreen(...) }
//           composable(<Session>)          { -> sessionId auslesen, SessionRoute(sessionId) }
//       }
//   }
//
// -----------------------------------------------------------------------------
// 2) WICHTIG — Screens müssen erst "ausstatten" werden:
// -----------------------------------------------------------------------------
//
//   Die Screens haben aktuell KEINE Navigations-Parameter. Jede Navigation steckt
//   als "/* TODO: Navigation ... */" direkt im Screen-Code. Bevor der NavHost
//   wirklich navigieren kann, müssen die Screens Callbacks bekommen, z.B.:
//       HomeScreen(
//           onOpenSettings = { navController.navigate(<Einstellungen>) },
//           onSelectTab    = { tab -> ... },
//           onStartSession = { navController.navigate(<SessionErstellen>) },
//           onOpenAllBoulders = { navController.navigate(<BoulderUebersicht>) },
//           onOpenLastSession = { id -> navController.navigate(session(id)) },
//       )
//   -> Genau dieses "Ausstatten" passiert schrittweise, beginnend mit dem Home-Screen.
//
// -----------------------------------------------------------------------------
// 3) Offene Entscheidung — Bottom-Nav (mit Deniz besprechen):
// -----------------------------------------------------------------------------
//
//   Die BottomNav wird in 4 Screens gerendert (Home, Sessions, Stats, Timer),
//   jeweils mit hartem selectedTab + leerem onTabSelect. Zwei Wege:
//     a) Jeder Screen behält seine eigene BottomNav und bekommt nur ein
//        onTabSelect-Callback (wenig Umbau).
//     b) BottomNav hierher ins Navigations-Gerüst hochziehen (z.B. gemeinsames
//        Scaffold), Screens rendern sie nicht mehr selbst (sauberer, aber Umbau
//        aller 4 Screens).
//   Beim Tab-Wechsel an launchSingleTop + State-Erhalt (saveState/restoreState)
//   denken, damit Tabs sich nicht stapeln.
//
// -----------------------------------------------------------------------------
// 4) Verdrahtung in MainActivity:
// -----------------------------------------------------------------------------
//
//   MainActivity rendert aktuell nichts. Dort im setContent { BoulderBuddyTheme { ... } }
//   muss AppNavigation() aufgerufen werden.
//
// =============================================================================
