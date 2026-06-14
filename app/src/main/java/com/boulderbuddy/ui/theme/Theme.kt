package com.boulderbuddy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

// Starter-Schema mit unseren Werten.
// Nach M3 Theme Builder Export: generierten LightColorScheme hier einsetzen.
private val LightColorScheme = lightColorScheme(
    primary      = M3Seed,
    onPrimary    = M3OnPrimary,
    surface      = BoulderBuddySurfaceBackground,
    onSurface    = BoulderBuddySurfaceInverse,
    background   = BoulderBuddySurfaceBackground,
    onBackground = BoulderBuddySurfaceInverse,
)

@Composable
fun BoulderBuddyTheme(content: @Composable () -> Unit) {
    val customColors = BoulderBuddyColors(
        surfaceBackground = BoulderBuddySurfaceBackground,
        surfacePattern    = BoulderBuddySurfacePattern,
        surfaceCard       = BoulderBuddySurfaceCard,
        surfaceInverse    = BoulderBuddySurfaceInverse,
        textSecondary     = BoulderBuddyTextSecondary,
        textTertiary      = BoulderBuddyTextTertiary,
        borderSubtle      = BoulderBuddyBorderSubtle,
        navActive         = BoulderBuddyNavActive,
        routes = RouteColors(
            red    = RouteRed,
            orange = RouteOrange,
            yellow = RouteYellow,
            green  = RouteGreen,
            blue   = RouteBlue,
            purple = RoutePurple,
            pink   = RoutePink,
        ),
    )
    CompositionLocalProvider(LocalBoulderBuddyColors provides customColors) {
        MaterialTheme(
            colorScheme = LightColorScheme,
            typography  = Typography,       // aus Type.kt (Template)
            shapes      = BoulderBuddyShapes,  // aus Shape.kt (nächste Datei)
            content     = content,
        )
    }
}