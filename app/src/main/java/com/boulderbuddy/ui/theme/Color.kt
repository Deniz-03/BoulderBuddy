package com.boulderbuddy.ui.theme

import androidx.compose.ui.graphics.Color

// Compose-Farben des Designsystems. Die Werte selbst stehen in PaletteHex.kt — hier wird
// ausschließlich verpackt. So prüft PaletteContrastTest dieselben Zahlen, die die App zeichnet.

// --- Light: warmes Creme ------------------------------------------------------
val BoulderBuddySurfaceLowest     = Color(HEX_LIGHT_SURFACE_LOWEST)
val BoulderBuddySurfaceCard       = Color(HEX_LIGHT_CARD)
val BoulderBuddySurfaceBackground = Color(HEX_LIGHT_BACKGROUND)
val BoulderBuddySurfaceContainer  = Color(HEX_LIGHT_SURFACE_CONTAINER)
val BoulderBuddySurfaceHigh       = Color(HEX_LIGHT_SURFACE_HIGH)
val BoulderBuddySurfaceHighest    = Color(HEX_LIGHT_SURFACE_HIGHEST)
val BoulderBuddySurfacePattern    = Color(HEX_LIGHT_PATTERN)
val BoulderBuddyOnSurface         = Color(HEX_LIGHT_ON_SURFACE)
val BoulderBuddyTextSecondary     = Color(HEX_LIGHT_TEXT_SECONDARY)
val BoulderBuddyTextTertiary      = Color(HEX_LIGHT_TEXT_TERTIARY)
val BoulderBuddyBorderSubtle      = Color(HEX_LIGHT_BORDER)
val BoulderBuddyChrome            = Color(HEX_LIGHT_CHROME)
val BoulderBuddyOnChrome          = Color(HEX_LIGHT_ON_CHROME)
val BoulderBuddyFillStrong        = Color(HEX_LIGHT_FILL_STRONG)
val BoulderBuddyOnFillStrong      = Color(HEX_LIGHT_ON_FILL_STRONG)
val BoulderBuddyAccent            = Color(HEX_LIGHT_ACCENT)
val BoulderBuddyAccentOnSurface   = Color(HEX_LIGHT_ACCENT_ON_SURFACE)
val BoulderBuddyError             = Color(HEX_LIGHT_ERROR)
val BoulderBuddyOnError           = Color(HEX_LIGHT_ON_ERROR)
val BoulderBuddyErrorContainer    = Color(HEX_LIGHT_ERROR_CONTAINER)
val BoulderBuddyOnErrorContainer  = Color(HEX_LIGHT_ON_ERROR_CONTAINER)
val BoulderBuddyInversePrimary    = Color(HEX_LIGHT_INVERSE_PRIMARY)

// --- Dark: warmes Fast-Schwarz ------------------------------------------------
val BoulderBuddyDarkSurfaceLowest   = Color(HEX_DARK_SURFACE_LOWEST)
val BoulderBuddyDarkBackground      = Color(HEX_DARK_BACKGROUND)
val BoulderBuddyDarkSurfaceLow      = Color(HEX_DARK_SURFACE_LOW)
val BoulderBuddyDarkCard            = Color(HEX_DARK_CARD)
val BoulderBuddyDarkSurfaceHigh     = Color(HEX_DARK_SURFACE_HIGH)
val BoulderBuddyDarkSurfaceHighest  = Color(HEX_DARK_SURFACE_HIGHEST)
val BoulderBuddyDarkPattern         = Color(HEX_DARK_PATTERN)
val BoulderBuddyDarkOnSurface       = Color(HEX_DARK_ON_SURFACE)
val BoulderBuddyDarkTextSecondary   = Color(HEX_DARK_TEXT_SECONDARY)
val BoulderBuddyDarkTextTertiary    = Color(HEX_DARK_TEXT_TERTIARY)
val BoulderBuddyDarkBorderSubtle    = Color(HEX_DARK_BORDER)
val BoulderBuddyDarkChrome          = Color(HEX_DARK_CHROME)
val BoulderBuddyDarkOnChrome        = Color(HEX_DARK_ON_CHROME)
val BoulderBuddyDarkFillStrong      = Color(HEX_DARK_FILL_STRONG)
val BoulderBuddyDarkOnFillStrong    = Color(HEX_DARK_ON_FILL_STRONG)
val BoulderBuddyDarkAccent          = Color(HEX_DARK_ACCENT)
val BoulderBuddyDarkAccentOnSurface = Color(HEX_DARK_ACCENT_ON_SURFACE)
val BoulderBuddyDarkError           = Color(HEX_DARK_ERROR)
val BoulderBuddyDarkOnError         = Color(HEX_DARK_ON_ERROR)
val BoulderBuddyDarkErrorContainer  = Color(HEX_DARK_ERROR_CONTAINER)
val BoulderBuddyDarkOnErrorContainer = Color(HEX_DARK_ON_ERROR_CONTAINER)
val BoulderBuddyDarkInversePrimary  = Color(HEX_DARK_INVERSE_PRIMARY)

/**
 * Heller Inhalt auf dunkler Markenfläche — nur noch als M3-`onPrimary` in Gebrauch.
 *
 * **Nicht mehr für Icons in der TopBar verwenden.** Das Chrome dreht seit der Light-Mode-Runde
 * mit dem Theme; ein fest cremefarbenes Icon wäre im Light Mode creme auf creme. Dafür gibt es
 * `BoulderBuddy.colors.onChrome`.
 */
val M3OnPrimary = Color(HEX_LIGHT_ON_FILL_STRONG)

// Route-Akzente – 7 Grifffarben. Immer als Fläche oder Rand, nie als Textfarbe auf hellem
// Grund: Gelb erreicht auf der Card nur 2,3:1 und wäre als Text unlesbar.
val RouteRed    = Color(HEX_ROUTE_RED)
val RouteOrange = Color(HEX_ROUTE_ORANGE)
val RouteYellow = Color(HEX_ROUTE_YELLOW)
val RouteGreen  = Color(HEX_ROUTE_GREEN)
val RouteBlue   = Color(HEX_ROUTE_BLUE)
val RoutePurple = Color(HEX_ROUTE_PURPLE)
val RoutePink   = Color(HEX_ROUTE_PINK)
