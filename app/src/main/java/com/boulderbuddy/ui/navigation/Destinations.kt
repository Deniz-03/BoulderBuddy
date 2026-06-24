package com.boulderbuddy.ui.navigation

// =============================================================================
// Destinations — zentrale Definition aller Navigations-Ziele (Routen)
// =============================================================================
//
// Noch NICHTS implementiert — erst mit Deniz besprechen. Hier wird nur
// festgehalten, WAS rein muss.
//
// Aufgabe dieser Datei:
//   Jede Route, die der NavHost (AppNavigation.kt) kennt, an EINER Stelle
//   definieren — statt Route-Strings wie "home" überall im Code zu verstreuen.
//   Empfehlung: eine sealed class / sealed interface mit je einem Objekt pro Ziel.
//
// -----------------------------------------------------------------------------
// 1) Die Screens, die als Routen gebraucht werden:
// -----------------------------------------------------------------------------
//
//   Bottom-Nav-Tabs (Hauptebene):
//     - Home              -> HomeScreen
//     - Sessions          -> SessionUebersichtScreen
//     - Stats             -> StatistikScreen
//     - Timer             -> HangboardTimerScreen
//     - GhostClimber      -> Post-MVP, nur Platzhalter (noch kein Screen)
//
//   Push-Ziele OHNE Argument:
//     - Einstellungen     -> EinstellungenScreen
//     - SessionErstellen  -> SessionErstellenScreen
//     - BoulderUebersicht -> BoulderUebersichtScreen
//
//   Push-Ziele MIT Argument:
//     - BoulderDetail(boulderId)   -> BoulderDetailScreen
//     - Session(sessionId)         -> SessionRoute (entscheidet selbst:
//                                       aktiv -> SessionDetailScreen,
//                                       beendet -> AlteSessionScreen)
//     - RouteHinzufuegen(sessionId?) -> RouteHinzufuegenScreen
//                                       (Boulder wird zu einer Session gespeichert
//                                        -> braucht vermutlich die sessionId.
//                                        MIT DENIZ KLÄREN.)
//
// -----------------------------------------------------------------------------
// 2) Offene Entscheidungen (mit Deniz besprechen):
// -----------------------------------------------------------------------------
//
//   a) Routen-Stil:
//        - String-basiert: sealed class mit route = "boulder_detail/{boulderId}"
//          (keine neuen Dependencies, passt zum bisherigen Code-Stil)
//        - ODER type-safe @Serializable-Objekte
//          (moderner, braucht aber zusätzlich kotlinx-serialization im Gradle)
//
//   b) Wie werden Argumente übergeben? (boulderId, sessionId)
//        - bei String-Routen: Hilfsfunktionen zum Bauen der konkreten Route,
//          z.B. fun boulderDetail(boulderId: Int) = "boulder_detail/$boulderId"
//
//   c) Start-Ziel des NavHost = Home (zur Bestätigung).
//
// =============================================================================
