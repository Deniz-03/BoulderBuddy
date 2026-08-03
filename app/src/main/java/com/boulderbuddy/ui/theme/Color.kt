package com.boulderbuddy.ui.theme

import androidx.compose.ui.graphics.Color

// Compose-Farben des Designsystems. Die Werte selbst stehen in PaletteHex.kt — hier wird
// ausschließlich verpackt. So prüft PaletteContrastTest dieselben Zahlen, die die App zeichnet.

// --- Light: warmes Creme ------------------------------------------------------
val BoulderBuddySurfaceBackground = Color(HEX_LIGHT_BACKGROUND)
val BoulderBuddySurfacePattern    = Color(HEX_LIGHT_PATTERN)
val BoulderBuddySurfaceCard       = Color(HEX_LIGHT_CARD)
val BoulderBuddyBorderSubtle      = Color(HEX_LIGHT_BORDER)
val BoulderBuddyOnSurface         = Color(HEX_LIGHT_ON_SURFACE)
val BoulderBuddyTextSecondary     = Color(HEX_LIGHT_TEXT_SECONDARY)
val BoulderBuddyTextTertiary      = Color(HEX_LIGHT_TEXT_TERTIARY)
val BoulderBuddyChrome            = Color(HEX_LIGHT_CHROME)
val BoulderBuddyOnChrome          = Color(HEX_LIGHT_ON_CHROME)
val BoulderBuddyFillStrong        = Color(HEX_LIGHT_FILL_STRONG)
val BoulderBuddyOnFillStrong      = Color(HEX_LIGHT_ON_FILL_STRONG)

// --- Dark: warmes Fast-Schwarz ------------------------------------------------
val BoulderBuddyDarkBackground    = Color(HEX_DARK_BACKGROUND)
val BoulderBuddyDarkPattern       = Color(HEX_DARK_PATTERN)
val BoulderBuddyDarkCard          = Color(HEX_DARK_CARD)
val BoulderBuddyDarkBorderSubtle  = Color(HEX_DARK_BORDER)
val BoulderBuddyDarkOnSurface     = Color(HEX_DARK_ON_SURFACE)
val BoulderBuddyDarkTextSecondary = Color(HEX_DARK_TEXT_SECONDARY)
val BoulderBuddyDarkTextTertiary  = Color(HEX_DARK_TEXT_TERTIARY)
val BoulderBuddyDarkChrome        = Color(HEX_DARK_CHROME)
val BoulderBuddyDarkOnChrome      = Color(HEX_DARK_ON_CHROME)
val BoulderBuddyDarkFillStrong    = Color(HEX_DARK_FILL_STRONG)
val BoulderBuddyDarkOnFillStrong  = Color(HEX_DARK_ON_FILL_STRONG)

// --- In beiden Themes gleich --------------------------------------------------
val BoulderBuddyNavActive = Color(HEX_NAV_ACTIVE)

// Seed für die von Material selbst gefärbten Bausteine (Ripple, Auswahlfarben in
// AlertDialog/Switch). Die App färbt ihre eigenen Flächen über die Tokens oben.
val M3Seed = Color(0xFF7A6E6A)

/**
 * Heller Inhalt auf dunkler Markenfläche.
 *
 * Früher wurde dafür `surfaceBackground` zweckentfremdet — solange der Hintergrund creme war,
 * ergab das zufällig fast dieselbe Farbe. Im Dark Mode drehte der Hintergrund mit, der Text
 * wurde dunkel auf dunkel (gemessen 1,42:1). Jetzt ein eigener Wert, der nicht mitdreht.
 */
val M3OnPrimary = Color(HEX_LIGHT_ON_CHROME)

// Route-Akzente – 7 Grifffarben. Immer als Fläche oder Rand, nie als Textfarbe auf hellem
// Grund: Gelb erreicht auf der Card nur 2,3:1 und wäre als Text unlesbar.
val RouteRed    = Color(HEX_ROUTE_RED)
val RouteOrange = Color(HEX_ROUTE_ORANGE)
val RouteYellow = Color(HEX_ROUTE_YELLOW)
val RouteGreen  = Color(HEX_ROUTE_GREEN)
val RouteBlue   = Color(HEX_ROUTE_BLUE)
val RoutePurple = Color(HEX_ROUTE_PURPLE)
val RoutePink   = Color(HEX_ROUTE_PINK)
