package com.boulderbuddy.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class RouteColors(
    val red: Color,
    val orange: Color,
    val yellow: Color,
    val green: Color,
    val blue: Color,
    val purple: Color,
    val pink: Color,
)

@Immutable
data class BoulderBuddyColors(
    val surfaceBackground: Color,
    val surfacePattern: Color,
    val surfaceCard: Color,
    val surfaceInverse: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val borderSubtle: Color,
    val navActive: Color,
    val routes: RouteColors,
)

val LocalBoulderBuddyColors = staticCompositionLocalOf<BoulderBuddyColors> {
    error("BoulderBuddyColors nicht gefunden — ist BoulderBuddyTheme im Composable-Baum?")
}

// Zugriff aus jedem Composable: BoulderBuddy.colors.routes.red
object BoulderBuddy {
    val colors: BoulderBuddyColors
        @Composable @ReadOnlyComposable
        get() = LocalBoulderBuddyColors.current
}
