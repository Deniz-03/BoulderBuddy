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
 * **Dreht mit dem Theme.** Ursprünglich war es in beiden Themes fast-schwarz — der Light Mode
 * sah deshalb nicht nach Light Mode aus. Der erste Versuch drehte es auf ein warmes Fast-Weiß
 * und lag damit **heller als der Inhalt**; die Leiste wirkte dadurch nicht wie ein Rahmen,
 * sondern wie eine besonders helle Stelle. Jetzt eine gesättigtere Creme-Stufe **unter** dem
 * Hintergrund: das Chrome liegt farblich am unteren Ende der hellen Rampe und rahmt den
 * Inhalt, statt aus ihm herauszustechen. Die Kante zieht zusätzlich eine Linie.
 */
const val HEX_LIGHT_CHROME = 0xFFEDE4C6
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
const val HEX_LIGHT_ACCENT = 0xFF9C4E37
const val HEX_LIGHT_ACCENT_ON_SURFACE = 0xFF954E38

// --- Dark: warme, angehobene Rampe -------------------------------------------
/*
 * Diese Rampe lag bis zum 03.08.2026 rund zehn Helligkeitsstufen tiefer — der Hintergrund
 * war #14110C, in CIE-L* gerechnet 5,2 von 100, also praktisch Schwarz. Auf dem Papier war
 * das die beste Variante: der Primärtext erreichte dort 15,9:1, mehr als im Light Mode.
 * Gelesen hat es sich trotzdem schlechter, und beides hat dieselbe Ursache.
 *
 * HALATION. Auf einem nahezu schwarzen Grund blühen helle Buchstaben aus und werden weich.
 * Der Effekt wird durch hohen Kontrast schlimmer, nicht besser — deshalb sieht die Zahl gut
 * aus, während das Auge sich anstrengt. Ein Kontrastwert kann das nicht abbilden; er kennt
 * nur zwei Farben, nicht die absolute Helligkeit, gegen die sie stehen.
 *
 * UND DIE FARBE WAR WEG. Farbigkeit ist bei so geringer Helligkeit physikalisch kaum
 * darstellbar: der Hintergrund hatte eine RGB-Spanne von 8 gegenüber 29 im Light Mode. Von
 * dem warmen Creme, das die App ausmacht, blieb Schwarz übrig.
 *
 * Jetzt liegt der Hintergrund bei 14,9 mit einer Spanne von 21. Die Punkte darauf sind
 * wieder ein Muster statt Schwarz auf Schwarz, und das Chrome hat einen eigenen Wert —
 * vorher war es ZEICHENGLEICH MIT DER CARD (beide #201C14), sodass TopBar und Bottom-Nav
 * sich nicht als Rahmen lasen.
 *
 * DER PREIS, OFFEN BENANNT: ein angehobener Grund kostet Spielraum nach oben. Damit jede
 * Textebene auf JEDER Fläche 4,5:1 hält, rücken die drei Ebenen enger zusammen — ihre
 * Abstände sind jetzt 9,9 und 10,6 statt 20,3 und 10,2. Die Hierarchie ist dadurch flacher,
 * jede einzelne Ebene aber besser lesbar. Das war die Abwägung.
 *
 * Reihenfolge nach wahrgenommener Helligkeit: das Chrome liegt UNTER dem Hintergrund —
 * gespiegelt zum Light Mode, wo es ebenfalls darunter liegt und den Inhalt rahmt.
 */
const val HEX_DARK_SURFACE_LOWEST = 0xFF1B170D  // Helligkeit  7,9
const val HEX_DARK_CHROME = 0xFF221D11          // Helligkeit 11,0 — eigener Wert, nicht = Card
const val HEX_DARK_BACKGROUND = 0xFF2B2516      // Helligkeit 14,9 = surface
const val HEX_DARK_SURFACE_LOW = 0xFF322C1B     // Helligkeit 18,2
const val HEX_DARK_PATTERN = 0xFF36301D         // Helligkeit 20,0 — Punkte, jetzt sichtbar
const val HEX_DARK_CARD = 0xFF393220            // Helligkeit 21,0 = surfaceContainer
const val HEX_DARK_SURFACE_HIGH = 0xFF403925    // Helligkeit 24,2 — Dialoge, Menüs
const val HEX_DARK_SURFACE_HIGHEST = 0xFF473F2B // Helligkeit 26,9 — bindend für alle Texte

// Der Primärtext ist ein warmes Off-White, KEIN Weiß: reines Weiß auf dunklem Grund ist die
// Halation in Reinform. Die erste Rechnung lief genau dorthin und wurde deshalb verworfen.
const val HEX_DARK_ON_SURFACE = 0xFFECE5D1      // Helligkeit 91,0
const val HEX_DARK_TEXT_SECONDARY = 0xFFD3C9AD  // Helligkeit 81,1
const val HEX_DARK_TEXT_TERTIARY = 0xFFB7AC8E   // Helligkeit 70,5

// Rand: bindend ist die hellste Fläche, nicht die dunkelste.
const val HEX_DARK_BORDER = 0xFF978B69

const val HEX_DARK_ON_CHROME = 0xFFECE5D1

/**
 * Hier dreht das Paar um: im Dark Mode ist die **Füllung hell** und der Text darauf dunkel.
 *
 * Vorher trug ein einziges Token (`surfaceInverse`) beide Aufgaben — Chrome *und* primäre
 * Aktion. Im Dark Mode ziehen die Aufgaben auseinander: ein dunkler Button auf dunklem Grund
 * ist keine primäre Aktion mehr, er verschwindet.
 */
const val HEX_DARK_FILL_STRONG = 0xFFE7DCBF
const val HEX_DARK_ON_FILL_STRONG = 0xFF241F12

// Zwei Akzentwerte, weil Chrome und Inhaltsflächen verschieden hell sind.
const val HEX_DARK_ACCENT = 0xFFC8866E
const val HEX_DARK_ACCENT_ON_SURFACE = 0xFFD9A08C

// --- Route-Akzente (in beiden Themes gleich) ----------------------------------
// Reine Wiedererkennung der Grifffarbe, nie Textträger.
const val HEX_ROUTE_RED = 0xFFE53935
const val HEX_ROUTE_ORANGE = 0xFFFB8C00
const val HEX_ROUTE_YELLOW = 0xFFC8A800
const val HEX_ROUTE_GREEN = 0xFF43A047
const val HEX_ROUTE_BLUE = 0xFF1E88E5
const val HEX_ROUTE_PURPLE = 0xFF8E24AA
const val HEX_ROUTE_PINK = 0xFFE91E63
