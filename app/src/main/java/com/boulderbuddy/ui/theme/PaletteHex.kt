package com.boulderbuddy.ui.theme

/**
 * Die Farbwerte des Designsystems als rohe Hex-Konstanten — **die** Quelle der Wahrheit.
 *
 * Warum als `const val Long` und nicht direkt als `Color`: `Color` gehört zu Compose und wäre
 * in einem JVM-Unit-Test nicht sicher benutzbar. Hier stehen reine Zahlen, die der Compiler
 * einsetzt. Dadurch kann `PaletteContrastTest` **genau die Werte** nachrechnen, die die App
 * verwendet, statt eine Kopie davon — die auseinanderlaufen könnte.
 *
 * Der Anlass ist konkret: der Dark Mode paarte eine dunkle Füllfläche mit dunklem Text
 * (gemessen 1,42:1, praktisch unsichtbar). Der Fehler war aus dem Code nicht zu sehen, weil
 * beide Farben einzeln plausibel aussahen — nur ihr *Verhältnis* war falsch. Ein Test, der
 * Verhältnisse prüft, hätte ihn beim Anlegen gefangen.
 *
 * Ordnung: erst die Flächen (hell → dunkel), dann Text, dann Ränder, dann die gefüllten
 * Elemente. Jede Zeile im Light-Block hat eine Entsprechung im Dark-Block.
 */

// --- Light: warmes Creme ------------------------------------------------------
// Der Hintergrund ist gegenüber dem früheren #F9F4E3 leicht vertieft. Grund: die Card lag bei
// 1,07:1 auf dem Hintergrund und war als eigene Fläche praktisch nicht wahrnehmbar. Jetzt
// trennen Flächenstufe *und* ein Rand, der die 3:1-Schwelle für Bedienelement-Grenzen hält.
const val HEX_LIGHT_BACKGROUND = 0xFFF3ECD6
const val HEX_LIGHT_PATTERN = 0xFFE7DFC3
const val HEX_LIGHT_CARD = 0xFFFFFDF7
const val HEX_LIGHT_BORDER = 0xFF958657
const val HEX_LIGHT_ON_SURFACE = 0xFF23211B
const val HEX_LIGHT_TEXT_SECONDARY = 0xFF574E31
const val HEX_LIGHT_TEXT_TERTIARY = 0xFF746947

// Chrome = TopBar und Bottom-Nav. Bleibt in beiden Themes dunkel: es rahmt den Inhalt,
// und ein im Dark Mode plötzlich helles Chrome würde blenden.
const val HEX_LIGHT_CHROME = 0xFF262521
const val HEX_LIGHT_ON_CHROME = 0xFFF7F2E2

// FillStrong = primäre Aktionen (Button, gewählter Chip, aktiver Timer-Knopf, Schalter an).
// Anders als das Chrome **dreht** dieses Paar im Dark Mode um — siehe Dark-Block.
const val HEX_LIGHT_FILL_STRONG = 0xFF262521
const val HEX_LIGHT_ON_FILL_STRONG = 0xFFF7F2E2

// --- Dark: warmes Fast-Schwarz ------------------------------------------------
const val HEX_DARK_BACKGROUND = 0xFF14110C
const val HEX_DARK_PATTERN = 0xFF221E15
const val HEX_DARK_CARD = 0xFF201C14
const val HEX_DARK_BORDER = 0xFF706851
const val HEX_DARK_ON_SURFACE = 0xFFF2ECDA
const val HEX_DARK_TEXT_SECONDARY = 0xFFBFB392
const val HEX_DARK_TEXT_TERTIARY = 0xFF9A9074

const val HEX_DARK_CHROME = 0xFF201C14
const val HEX_DARK_ON_CHROME = 0xFFF2ECDA

/**
 * Hier dreht das Paar um: im Dark Mode ist die **Füllung hell** und der Text darauf dunkel.
 *
 * Das ist der eigentliche Fix des gemeldeten Fehlers. Vorher trug ein einziges Token
 * (`surfaceInverse`) beide Aufgaben — Chrome *und* primäre Aktion. Im Light Mode fällt das
 * nicht auf, weil beides dunkel sein darf. Im Dark Mode ziehen die Aufgaben auseinander: ein
 * dunkler Button auf dunklem Grund ist keine primäre Aktion mehr, er verschwindet.
 */
const val HEX_DARK_FILL_STRONG = 0xFFE8DEBE
const val HEX_DARK_ON_FILL_STRONG = 0xFF1B1811

// --- In beiden Themes gleich --------------------------------------------------
// Rosé-Akzent der aktiven Bottom-Nav. Sitzt immer auf dem Chrome und hält dort in beiden
// Themes deutlich über 3:1 — deshalb muss er nicht mitdrehen.
const val HEX_NAV_ACTIVE = 0xFFC9A89A

// Route-Akzente. Reine Wiedererkennung der Grifffarbe, nie Textträger.
const val HEX_ROUTE_RED = 0xFFE53935
const val HEX_ROUTE_ORANGE = 0xFFFB8C00
const val HEX_ROUTE_YELLOW = 0xFFC8A800
const val HEX_ROUTE_GREEN = 0xFF43A047
const val HEX_ROUTE_BLUE = 0xFF1E88E5
const val HEX_ROUTE_PURPLE = 0xFF8E24AA
const val HEX_ROUTE_PINK = 0xFFE91E63
