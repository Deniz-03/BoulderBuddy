package com.boulderbuddy.ui.theme

import androidx.compose.ui.graphics.Color

// Surfaces
val BoulderBuddySurfaceBackground = Color(0xFFF9F4E3)
val BoulderBuddySurfacePattern    = Color(0xFFECE6CE)
val BoulderBuddySurfaceCard       = Color(0xFFFFFCF2)
val BoulderBuddySurfaceInverse    = Color(0xFF2B2B2B)

// Text & Borders
val BoulderBuddyTextSecondary = Color(0xFF6B6040)
val BoulderBuddyTextTertiary  = Color(0xFF9A8F6A)
val BoulderBuddyBorderSubtle  = Color(0xFFC8BB8A)
val BoulderBuddyNavActive     = Color(0xFFC9A89A)

// M3 Seed (nach Theme Builder ggf. durch generierten LightColorScheme ersetzen)
val M3Seed      = Color(0xFF7A6E6A)
val M3OnPrimary = Color(0xFFF9F4E3)

// --- Dark Mode (7.4a) --------------------------------------------------------
// Warmer, dunkler Gegenpart zum cremefarbenen Light-Schema. Nur Flächen/Text/Border
// flippen; die dunkle Marken-Füllfläche (surfaceInverse) + Route-Akzente bleiben,
// weil sie überall mit cremefarbenem Inhalt gepaart sind (TopBar, Buttons, Chips).
val BoulderBuddyDarkBackground = Color(0xFF15120D) // warmes Fast-Schwarz
val BoulderBuddyDarkPattern    = Color(0xFF221E16) // dezente Punkte auf dem Hintergrund
val BoulderBuddyDarkCard       = Color(0xFF221E16)
val BoulderBuddyDarkOnSurface  = Color(0xFFF2ECDC) // warmes Off-White als Primärtext
val BoulderBuddyDarkTextSecondary = Color(0xFFC0B594)
val BoulderBuddyDarkTextTertiary  = Color(0xFF8C8264)
val BoulderBuddyDarkBorderSubtle  = Color(0xFF3A342A)
// surfaceInverse bleibt eine dunkle Fläche, im Dark Mode minimal aufgehellt, damit
// Buttons/TopBar sich vom noch dunkleren Hintergrund abheben.
val BoulderBuddyDarkSurfaceInverse = Color(0xFF33302A)

// Route Accents – 7 Grifffarben (immer als 2dp BorderStroke verwenden)
val RouteRed    = Color(0xFFE53935)
val RouteOrange = Color(0xFFFB8C00)
val RouteYellow = Color(0xFFC8A800) // gedunkelt – reines Gelb zu wenig Kontrast
val RouteGreen  = Color(0xFF43A047)
val RouteBlue   = Color(0xFF1E88E5)
val RoutePurple = Color(0xFF8E24AA)
val RoutePink   = Color(0xFFE91E63)