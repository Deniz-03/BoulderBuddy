package com.boulderbuddy.ui.theme

/**
 * Die Farbwerte des Designsystems als rohe Hex-Konstanten — **die** Quelle der Wahrheit.
 *
 * Warum als `const val Long` und nicht direkt als `Color`: `Color` gehört zu Compose und wäre
 * in einem JVM-Unit-Test nicht sicher benutzbar. Hier stehen reine Zahlen, die der Compiler
 * einsetzt. Dadurch kann `PaletteContrastTest` **genau die Werte** nachrechnen, die die App
 * verwendet, statt eine Kopie davon — die auseinanderlaufen könnte.
 *
 * ## Die Flächen-Rampe
 *
 * Material zeichnet Dialoge, Menüs und Blätter nicht auf `surface`, sondern auf die
 * `surfaceContainer*`-Rollen. Waren die nicht gesetzt, nahm Material seine **eigene,
 * violettstichige Standardpalette** — mitten in einer warmen Creme-App, und mit Textfarben
 * gepaart, die für ganz andere Untergründe gewählt worden waren. Deshalb gehört die ganze
 * Rampe hierher, und deshalb prüft der Test jede Textfarbe gegen **jede** Fläche, auf der sie
 * landen kann, statt nur gegen Hintergrund und Karte.
 */

// --- Light: warme Creme-Rampe, hell nach dunkel ------------------------------
const val HEX_LIGHT_SURFACE_LOWEST = 0xFFFFFFFF
const val HEX_LIGHT_CARD = 0xFFFFFDF7          // = surfaceContainerLow
const val HEX_LIGHT_BACKGROUND = 0xFFF3ECD6    // = surface
const val HEX_LIGHT_SURFACE_CONTAINER = 0xFFF8F1DC
const val HEX_LIGHT_SURFACE_HIGH = 0xFFF1E9D0  // Dialoge, Menüs, Bottom-Sheets
const val HEX_LIGHT_SURFACE_HIGHEST = 0xFFE9E0C2
const val HEX_LIGHT_PATTERN = 0xFFE7DFC3

// Textfarben. Jede hält 4,5:1 auf **allen** Flächen oben. Der bindende Fall ist nicht der
// Hintergrund, sondern das dunkle Ende der Rampe — eine Textfarbe, die nur gegen `surface`
// geprüft wurde, ist genau die Sorte Lücke, die der erste Kontrast-Test hatte.
//
// Die Werte sind kräftiger, als sie es sein müssten, wenn nur Hintergrund und Karte zählten.
// Das ist der bewusste Tausch: die Rampe braucht Spannweite (sonst sind Dialog und höchste
// Fläche nicht unterscheidbar), also muss der Text sie tragen können.
const val HEX_LIGHT_ON_SURFACE = 0xFF23211B
const val HEX_LIGHT_TEXT_SECONDARY = 0xFF574E31
const val HEX_LIGHT_TEXT_TERTIARY = 0xFF6C6242

// Rand: hält 3:1 auf allen Light-Flächen (WCAG 1.4.11, Bedienelement-Grenzen).
const val HEX_LIGHT_BORDER = 0xFF8A7D51

/**
 * Chrome = TopBar und Bottom-Nav.
 *
 * **Dreht mit dem Theme.** Früher war es in beiden Themes fast-schwarz — mit dem Ergebnis, dass
 * der Light Mode nicht nach Light Mode aussah: der Inhalt war creme, aber Kopf- und Fußleiste
 * waren schwarz, also das Auffälligste am Bildschirm. Jetzt ist das Chrome im Light Mode eine
 * helle Fläche und trennt sich vom Inhalt über den Rand statt über die Farbe.
 */
const val HEX_LIGHT_CHROME = 0xFFFFFDF7
const val HEX_LIGHT_ON_CHROME = 0xFF23211B

// Primäre Aktionen: dunkle Füllung, heller Text.
const val HEX_LIGHT_FILL_STRONG = 0xFF262521
const val HEX_LIGHT_ON_FILL_STRONG = 0xFFF7F2E2

/**
 * Markenakzent: aktiver Nav-Eintrag und die Beschriftung von Textbuttons in Dialogen.
 *
 * Im Light Mode ein tiefes Terrakotta statt des früheren hellen Rosé. Grund: das Rosé saß auf
 * schwarzem Chrome und hatte dort 6,4:1. Auf hellem Chrome wären es 2,2:1 gewesen — dieselbe
 * Farbe, anderer Untergrund, und aus einem guten Wert wird ein unbrauchbarer.
 */
const val HEX_LIGHT_ACCENT = 0xFFAD5F47
const val HEX_LIGHT_ACCENT_ON_SURFACE = 0xFF954E38

// --- Dark: warme Fast-Schwarz-Rampe, dunkel nach hell ------------------------
const val HEX_DARK_SURFACE_LOWEST = 0xFF0E0C08
const val HEX_DARK_BACKGROUND = 0xFF14110C     // = surface
const val HEX_DARK_SURFACE_LOW = 0xFF1A160F
const val HEX_DARK_CARD = 0xFF201C14           // = surfaceContainer
const val HEX_DARK_SURFACE_HIGH = 0xFF2A251A   // Dialoge, Menüs, Bottom-Sheets
const val HEX_DARK_SURFACE_HIGHEST = 0xFF352F22
const val HEX_DARK_PATTERN = 0xFF221E15

const val HEX_DARK_ON_SURFACE = 0xFFF2ECDA
const val HEX_DARK_TEXT_SECONDARY = 0xFFBFB392
const val HEX_DARK_TEXT_TERTIARY = 0xFFA1987D

// Rand: der bindende Fall ist hier die **hellste** Fläche, nicht die dunkelste.
const val HEX_DARK_BORDER = 0xFF837A5F

// Chrome bleibt im Dark Mode dunkel — hier ist es die Umgebung, die dunkel ist.
const val HEX_DARK_CHROME = 0xFF201C14
const val HEX_DARK_ON_CHROME = 0xFFF2ECDA

/**
 * Hier dreht das Paar um: im Dark Mode ist die **Füllung hell** und der Text darauf dunkel.
 *
 * Vorher trug ein einziges Token (`surfaceInverse`) beide Aufgaben — Chrome *und* primäre
 * Aktion. Im Dark Mode ziehen die Aufgaben auseinander: ein dunkler Button auf dunklem Grund
 * ist keine primäre Aktion mehr, er verschwindet.
 */
const val HEX_DARK_FILL_STRONG = 0xFFE8DEBE
const val HEX_DARK_ON_FILL_STRONG = 0xFF1B1811

const val HEX_DARK_ACCENT = 0xFFC9A89A
const val HEX_DARK_ACCENT_ON_SURFACE = 0xFFCB8975

// --- Route-Akzente (in beiden Themes gleich) ----------------------------------
// Reine Wiedererkennung der Grifffarbe, nie Textträger.
const val HEX_ROUTE_RED = 0xFFE53935
const val HEX_ROUTE_ORANGE = 0xFFFB8C00
const val HEX_ROUTE_YELLOW = 0xFFC8A800
const val HEX_ROUTE_GREEN = 0xFF43A047
const val HEX_ROUTE_BLUE = 0xFF1E88E5
const val HEX_ROUTE_PURPLE = 0xFF8E24AA
const val HEX_ROUTE_PINK = 0xFFE91E63
