package com.boulderbuddy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

// M3-Schema für die von Material selbst gefärbten Bausteine (Ripple, AlertDialog, Switch).
// Die eigenen Flächen laufen über LocalBoulderBuddyColors.
private val LightColorScheme = lightColorScheme(
    primary      = M3Seed,
    onPrimary    = M3OnPrimary,
    surface      = BoulderBuddySurfaceBackground,
    onSurface    = BoulderBuddyOnSurface,
    background   = BoulderBuddySurfaceBackground,
    onBackground = BoulderBuddyOnSurface,
)

private val DarkColorScheme = darkColorScheme(
    primary      = BoulderBuddyNavActive,   // Rosé-Akzent, hell genug als Primary auf Dunkel
    onPrimary    = BoulderBuddyDarkOnFillStrong,
    surface      = BoulderBuddyDarkBackground,
    onSurface    = BoulderBuddyDarkOnSurface,
    background   = BoulderBuddyDarkBackground,
    onBackground = BoulderBuddyDarkOnSurface,
)

// Der cremefarbene Grundton ist die Identität der App und bleibt. Was sich gegenüber dem
// früheren Stand geändert hat, sind die Abstände zwischen den Werten: der Hintergrund ist
// leicht vertieft (die Card lag bei 1,07:1 darauf und war als Fläche nicht wahrnehmbar), die
// dritte Textebene ist dunkler (sie riss mit 2,93:1 sogar die 3:1-Schwelle) und der Rand
// trägt jetzt die Abgrenzung mit 3:1. Alle Werte stehen in PaletteHex.kt und werden von
// PaletteContrastTest nachgerechnet.
private val LightBoulderBuddyColors = BoulderBuddyColors(
    surfaceBackground = BoulderBuddySurfaceBackground,
    surfacePattern    = BoulderBuddySurfacePattern,
    surfaceCard       = BoulderBuddySurfaceCard,
    surfaceChrome     = BoulderBuddyChrome,
    onChrome          = BoulderBuddyOnChrome,
    fillStrong        = BoulderBuddyFillStrong,
    onFillStrong      = BoulderBuddyOnFillStrong,
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

// Kein mechanisches Umdrehen: `fillStrong`/`onFillStrong` **tauschen** die Polarität (helle
// Füllung, dunkler Text), damit die primäre Aktion auf dunklem Grund weiterhin das
// auffälligste Element ist. Das Chrome bleibt dagegen dunkel. Genau diese Unterscheidung
// fehlte vorher und war die Ursache des unlesbaren Buttons.
private val DarkBoulderBuddyColors = LightBoulderBuddyColors.copy(
    surfaceBackground = BoulderBuddyDarkBackground,
    surfacePattern    = BoulderBuddyDarkPattern,
    surfaceCard       = BoulderBuddyDarkCard,
    surfaceChrome     = BoulderBuddyDarkChrome,
    onChrome          = BoulderBuddyDarkOnChrome,
    fillStrong        = BoulderBuddyDarkFillStrong,
    onFillStrong      = BoulderBuddyDarkOnFillStrong,
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
            typography  = Typography,
            shapes      = BoulderBuddyShapes,
            content     = content,
        )
    }
}
