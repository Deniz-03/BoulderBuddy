package com.boulderbuddy.ui.theme

import androidx.compose.ui.graphics.Color

// Kanonische Route-Farbpalette: stabiler Key ("red", "green", …) → feste Akzentfarbe.
// EINE Quelle für den ColorPicker (Boulder-Formular) und die Auflösung eines gespeicherten
// Farb-Keys (Route.color) in den ViewModels — damit die Zuordnung nicht dupliziert wird.
//
// Die Farben sind feste Konstanten (theme-unabhängig), daher ist die Auflösung bewusst
// NICHT @Composable und funktioniert auch außerhalb des Compose-Baums (ViewModels).
val routeColorPalette: List<Pair<String, Color>> = listOf(
    "red" to RouteRed,
    "orange" to RouteOrange,
    "yellow" to RouteYellow,
    "green" to RouteGreen,
    "blue" to RouteBlue,
    "purple" to RoutePurple,
    "pink" to RoutePink,
)

// Vorauswahl im ColorPicker, damit jeder Boulder immer eine Farbe hat.
const val DefaultRouteColorKey: String = "green"

// Neutraler Fallback, wenn kein/kein gültiger Farb-Key gesetzt ist.
private val FallbackAccent = Color(0xFF888888)

/**
 * Übersetzt einen gespeicherten Farb-Key ("red", "blue", …) in die zugehörige Routenfarbe.
 * Fällt bei `null`/unbekanntem Key auf ein neutrales Grau zurück.
 */
fun routeColorForKey(key: String?): Color =
    routeColorPalette.firstOrNull { it.first == key }?.second ?: FallbackAccent

/** Umkehrung: Farbe → gespeicherter Key (für den ColorPicker, der in [Color] denkt). */
fun keyForRouteColor(color: Color): String? =
    routeColorPalette.firstOrNull { it.second == color }?.first
