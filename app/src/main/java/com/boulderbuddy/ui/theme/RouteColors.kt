package com.boulderbuddy.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Übersetzt einen gespeicherten Farb-Key ("red", "blue", …) in die zugehörige
// Routenfarbe. Zentral gehalten, damit die Zuordnung nicht in jedem Screen
// dupliziert wird (Session-Übersicht, Session-Detail, …).
@Composable
fun routeColorForKey(key: String): Color = when (key) {
    "red" -> BoulderBuddy.colors.routes.red
    "orange" -> BoulderBuddy.colors.routes.orange
    "yellow" -> BoulderBuddy.colors.routes.yellow
    "green" -> BoulderBuddy.colors.routes.green
    "blue" -> BoulderBuddy.colors.routes.blue
    "purple" -> BoulderBuddy.colors.routes.purple
    "pink" -> BoulderBuddy.colors.routes.pink
    else -> BoulderBuddy.colors.borderSubtle
}
