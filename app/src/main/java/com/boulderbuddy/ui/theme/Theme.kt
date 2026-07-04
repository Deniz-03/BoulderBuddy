package com.boulderbuddy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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

// Dark-Gegenpart (7.4a). onSurface/onBackground sind NICHT surfaceInverse (die bleibt eine
// dunkle Marken-Füllfläche), sondern ein eigenes warmes Off-White — sonst kollidiert die
// Polarität mit TopBar/Buttons, die surfaceInverse als dunklen Grund nutzen.
private val DarkColorScheme = darkColorScheme(
    primary      = BoulderBuddyNavActive,   // Rosé-Akzent, hell genug als Primary auf Dunkel
    onPrimary    = BoulderBuddySurfaceInverse,
    surface      = BoulderBuddyDarkBackground,
    onSurface    = BoulderBuddyDarkOnSurface,
    background   = BoulderBuddyDarkBackground,
    onBackground = BoulderBuddyDarkOnSurface,
)

// Custom-Farbsätze passend zum jeweiligen M3-Schema. surfaceInverse + Route-Akzente bleiben
// in beiden Themes (dunkle Fläche mit cremefarbenem Inhalt), nur Flächen/Text/Border flippen.
private val LightBoulderBuddyColors = BoulderBuddyColors(
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

private val DarkBoulderBuddyColors = LightBoulderBuddyColors.copy(
    surfaceBackground = BoulderBuddyDarkBackground,
    surfacePattern    = BoulderBuddyDarkPattern,
    surfaceCard       = BoulderBuddyDarkCard,
    surfaceInverse    = BoulderBuddyDarkSurfaceInverse,
    textSecondary     = BoulderBuddyDarkTextSecondary,
    textTertiary      = BoulderBuddyDarkTextTertiary,
    borderSubtle      = BoulderBuddyDarkBorderSubtle,
    // navActive + routes bleiben identisch (Akzente funktionieren auf beiden Untergründen).
)

@Composable
fun BoulderBuddyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val customColors = if (darkTheme) DarkBoulderBuddyColors else LightBoulderBuddyColors
    CompositionLocalProvider(LocalBoulderBuddyColors provides customColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography  = Typography,       // aus Type.kt (Template)
            shapes      = BoulderBuddyShapes,  // aus Shape.kt (nächste Datei)
            content     = content,
        )
    }
}
