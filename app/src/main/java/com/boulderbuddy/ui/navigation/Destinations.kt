package com.boulderbuddy.ui.navigation

import com.boulderbuddy.ui.components.BottomNavTab
import kotlinx.serialization.Serializable

// =============================================================================
// Destinations — zentrale, type-safe Definition aller Navigations-Ziele
// =============================================================================
//
// Statt Route-Strings ("home", "boulder_detail/{id}") überall im Code zu
// verstreuen, ist jede Route ein @Serializable-Typ. Der NavHost (AppNavigation.kt)
// referenziert diese Typen direkt (composable<Home> { ... }); Argumente werden
// typsicher aus toRoute() gelesen. Braucht kotlinx-serialization (Phase 0).
//
// -----------------------------------------------------------------------------
// Bottom-Nav-Tabs (Hauptebene) — zugleich Einträge der NavBar (siehe unten):
// -----------------------------------------------------------------------------

@Serializable
object Home            // -> HomeScreen

@Serializable
object Sessions       // -> SessionUebersichtScreen

@Serializable
object Stats          // -> StatistikScreen

@Serializable
object Timer          // -> HangboardTimerScreen

// -----------------------------------------------------------------------------
// Push-Ziele OHNE Argument:
// -----------------------------------------------------------------------------

@Serializable
object Einstellungen      // -> EinstellungenScreen

@Serializable
object SessionErstellen   // -> SessionErstellenScreen

@Serializable
object BoulderUebersicht  // -> BoulderUebersichtScreen

// Ghost Climber (7.5): bewusst Push-Ziel aus den Einstellungen ("Experimental"),
// KEIN 5. Bottom-Tab — hält den MVP-Kernfluss stabil (Plan A.4).
@Serializable
object GhostClimber       // -> GhostClimberScreen

// Hangboard-Historie (Phase 7 Anhang B, §0 Säule 5): alle Workouts inkl. eigenständiger
// Trainings; Einstieg über den Hangboard-Block im Statistik-Screen.
@Serializable
object HangboardHistorie  // -> HangboardHistorieScreen

// Geräte abgleichen (Sync-Plan S7): Push-Ziel aus den Einstellungen. Bewusst kein Tab —
// es ist eine Wartungsaufgabe, keine tägliche.
@Serializable
object Abgleich           // -> AbgleichScreen

// -----------------------------------------------------------------------------
// Push-Ziele MIT Argument:
// -----------------------------------------------------------------------------

// Eigener Aufnahme-Screen auf CameraX (7.4d). `auftrag` ist der Name eines
// CaptureAuftrag-Eintrags — als String, weil @Serializable-Routen keine Enums tragen.
// Das Ergebnis geht NICHT über diese Route zurück, sondern über den savedStateHandle des
// vorigen Back-Stack-Eintrags (siehe KAMERA_ERGEBNIS in AppNavigation).
@Serializable
data class KameraAufnahme(val auftrag: String)   // -> KameraScreen

@Serializable
data class BoulderDetail(val boulderId: Int)   // -> BoulderDetailScreen

@Serializable
data class Session(val sessionId: Int)         // -> SessionRoute (aktiv/beendet)

// sessionId nullable: Annahme laut Plan ist "mit sessionId", aber die offene Frage
// (Boulder ohne aktive Session anlegen?) bleibt bewusst offen -> default null.
// boulderId gesetzt = Bearbeiten-Modus (Formular vorbefüllen + bestehende Route aktualisieren),
// null = neuen Boulder anlegen.
@Serializable
data class RouteHinzufuegen(
    val sessionId: Int? = null,
    val boulderId: Int? = null,
) // -> RouteHinzufuegenScreen

// -----------------------------------------------------------------------------
// Tab-Liste für die BottomNav (Route + zugehöriger BottomNavTab mit Icon/Label).
// GhostClimber = Post-MVP -> bewusst NICHT enthalten.
// Reihenfolge = Reihenfolge in der NavBar.
// -----------------------------------------------------------------------------

// Verknüpft ein Top-Level-Ziel (Route-Objekt) mit seinem BottomNavTab.
// Icon + Label liefert der BottomNavTab selbst (siehe BottomNav.kt).
data class TopLevelDestination(
    val route: Any,
    val tab: BottomNavTab,
)

val topLevelDestinations: List<TopLevelDestination> = listOf(
    TopLevelDestination(Home, BottomNavTab.Home),
    TopLevelDestination(Sessions, BottomNavTab.Sessions),
    TopLevelDestination(Stats, BottomNavTab.Stats),
    TopLevelDestination(Timer, BottomNavTab.Timer),
)
