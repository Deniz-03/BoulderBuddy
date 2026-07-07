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
object BoulderUebersicht  // -> BoulderUebersichtScreen

// Ghost Climber (7.5): bewusst Push-Ziel aus den Einstellungen ("Experimental"),
// KEIN 5. Bottom-Tab — hält den MVP-Kernfluss stabil (Plan A.4).
@Serializable
object GhostClimber       // -> GhostClimberScreen

// Gym-Verwaltung (Näherungs-Push M1): Liste der Hallen, erreichbar aus den Einstellungen.
@Serializable
object GymVerwaltung      // -> GymVerwaltungScreen

// -----------------------------------------------------------------------------
// Push-Ziele MIT Argument:
// -----------------------------------------------------------------------------

// gymId gesetzt = Ort-Feld mit dieser Halle vorbefüllen (Gym-Näherungs-Push M4:
// Notification-Deep-Link). null = normaler Flow (Home/Widget) — bestehende
// argumentlose Aufrufe laufen dank Default weiter.
@Serializable
data class SessionErstellen(val gymId: Int? = null)   // -> SessionErstellenScreen

@Serializable
data class BoulderDetail(val boulderId: Int)   // -> BoulderDetailScreen

@Serializable
data class Session(val sessionId: Int)         // -> SessionRoute (aktiv/beendet)

// Gym-Editor (Näherungs-Push M1): Name/Adresse + Koordinaten (Standort-Button),
// Geofence-Radius und Pro-Gym-Erinnerungs-Toggle.
@Serializable
data class GymBearbeiten(val gymId: Int)       // -> GymBearbeitenScreen

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
