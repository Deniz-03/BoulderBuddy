package com.boulderbuddy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/*
 * Das M3-Schema war lange nur zu einem Fünftel gefüllt — sechs Rollen von rund dreißig. Alles
 * Übrige nahm Material aus seiner eigenen Standardpalette, und die ist violettstichiges Grau.
 *
 * Sichtbar wurde das dort, wo Material selbst zeichnet: **Dialoge, Menüs, Trennlinien,
 * Schalter**. Ein AlertDialog liegt nicht auf `surface`, sondern auf `surfaceContainerHigh` —
 * war die Rolle ungesetzt, erschien ein kaltes Grau mitten in einer warmen Creme-App, und
 * unsere Textfarben trafen dort auf einen Untergrund, für den sie nie gewählt worden waren
 * (die dritte Textebene landete so bei 4,45:1).
 *
 * Deshalb sind hier jetzt alle Rollen gesetzt, die in dieser App vorkommen können. Was Material
 * zeichnet, zeichnet es ab sofort auf unseren Flächen.
 */

private val LightColorScheme = lightColorScheme(
    primary            = BoulderBuddyAccentOnSurface,
    onPrimary          = BoulderBuddyOnFillStrong,
    primaryContainer   = BoulderBuddySurfaceHigh,
    onPrimaryContainer = BoulderBuddyOnSurface,
    secondary          = BoulderBuddyTextSecondary,
    onSecondary        = BoulderBuddyOnFillStrong,
    tertiary           = BoulderBuddyAccentOnSurface,
    onTertiary         = BoulderBuddyOnFillStrong,

    background   = BoulderBuddySurfaceBackground,
    onBackground = BoulderBuddyOnSurface,
    surface      = BoulderBuddySurfaceBackground,
    onSurface    = BoulderBuddyOnSurface,

    // Die Rampe, auf der Material seine eigenen Bausteine absetzt. `surfaceContainerHigh`
    // ist die Dialogfläche — die wichtigste Zeile in diesem Block.
    surfaceContainerLowest  = BoulderBuddySurfaceLowest,
    surfaceContainerLow     = BoulderBuddySurfaceCard,
    surfaceContainer        = BoulderBuddySurfaceContainer,
    surfaceContainerHigh    = BoulderBuddySurfaceHigh,
    surfaceContainerHighest = BoulderBuddySurfaceHighest,
    surfaceVariant          = BoulderBuddySurfaceContainer,
    onSurfaceVariant        = BoulderBuddyTextSecondary,

    outline        = BoulderBuddyBorderSubtle,
    outlineVariant = BoulderBuddyBorderSubtle,

    inverseSurface   = BoulderBuddyFillStrong,
    inverseOnSurface = BoulderBuddyOnFillStrong,

    error     = RouteRed,
    onError   = BoulderBuddyOnFillStrong,
)

private val DarkColorScheme = darkColorScheme(
    primary            = BoulderBuddyDarkAccentOnSurface,
    onPrimary          = BoulderBuddyDarkOnFillStrong,
    primaryContainer   = BoulderBuddyDarkSurfaceHigh,
    onPrimaryContainer = BoulderBuddyDarkOnSurface,
    secondary          = BoulderBuddyDarkTextSecondary,
    onSecondary        = BoulderBuddyDarkOnFillStrong,
    tertiary           = BoulderBuddyDarkAccentOnSurface,
    onTertiary         = BoulderBuddyDarkOnFillStrong,

    background   = BoulderBuddyDarkBackground,
    onBackground = BoulderBuddyDarkOnSurface,
    surface      = BoulderBuddyDarkBackground,
    onSurface    = BoulderBuddyDarkOnSurface,

    surfaceContainerLowest  = BoulderBuddyDarkSurfaceLowest,
    surfaceContainerLow     = BoulderBuddyDarkSurfaceLow,
    surfaceContainer        = BoulderBuddyDarkCard,
    surfaceContainerHigh    = BoulderBuddyDarkSurfaceHigh,
    surfaceContainerHighest = BoulderBuddyDarkSurfaceHighest,
    surfaceVariant          = BoulderBuddyDarkSurfaceHigh,
    onSurfaceVariant        = BoulderBuddyDarkTextSecondary,

    outline        = BoulderBuddyDarkBorderSubtle,
    outlineVariant = BoulderBuddyDarkBorderSubtle,

    inverseSurface   = BoulderBuddyDarkFillStrong,
    inverseOnSurface = BoulderBuddyDarkOnFillStrong,

    error     = RouteRed,
    onError   = BoulderBuddyDarkOnFillStrong,
)

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
    navActive         = BoulderBuddyAccent,
    accentOnSurface   = BoulderBuddyAccentOnSurface,
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

// Kein mechanisches Umdrehen. Drei Tokens verhalten sich unterschiedlich, und das ist der
// ganze Punkt:
//   - `surfaceChrome` dreht (hell im Light, dunkel im Dark) — sonst sieht der Light Mode
//     nicht nach Light Mode aus.
//   - `fillStrong` dreht die **Polarität** (dunkel-auf-hell vs. hell-auf-dunkel) — sonst
//     verschwindet die primäre Aktion im Dark Mode.
//   - Die Route-Akzente drehen **nicht** — eine rote Route ist in beiden Themes rot.
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
    navActive         = BoulderBuddyDarkAccent,
    accentOnSurface   = BoulderBuddyDarkAccentOnSurface,
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
