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
/*
 * Der Hintergrund lag bei L* 93,6 und das Chrome bei L* 90,3 — 1,08:1. Zwei Flächen, die sich
 * um so wenig unterscheiden, liest das Auge als eine; die Leisten rahmten den Inhalt nicht,
 * sie verschwanden in ihm. Jetzt L* 96,9, also 1,18:1 — nach oben statt das Chrome nach unten,
 * weil das Chrome bereits die Textfarben des Light Mode trägt und jede Verdunklung dort direkt
 * an den Kontrastschwellen zerrt.
 *
 * **Nach oben ist hier fast Schluss, und der Deckel ist die Card.** Sie liegt bei L* 99,3, quasi
 * auf Weiß — viel Luft hat sie nicht mehr. Je heller der Hintergrund wird, desto schwächer wird
 * die Stufe zwischen beiden: von 1,16:1 über 1,09:1 auf jetzt 1,06:1. Das ist tragbar, weil die
 * Card ihre Grenze ohnehin über den Rand zieht und nicht über die Füllung (siehe
 * `flaechenstufe_istBewusstFeinUndDarfDieSchwelleReissen`) — aber es ist die Zahl, die als
 * nächstes reißt, nicht irgendeine Textschwelle. Wer hier weiter aufhellen will, muss zuerst
 * die Card lösen.
 *
 * Das Anheben zwingt die zwei Stufen darunter mit: `surfaceContainer` lag bei L* 95,0 und wäre
 * damit HELLER gewesen als der Hintergrund — eine Rampe, die in der Mitte zurückspringt, ist
 * keine mehr. Beide Stufen rücken deshalb nach unten und die Reihenfolge stimmt wieder:
 *
 *   weiß > Card > Hintergrund > Container > Dialog > Chrome > höchste > Muster
 *
 * Das untere Ende ist dabei UNVERÄNDERT geblieben, und zwar nicht aus Bequemlichkeit: die
 * dritte Textebene hält auf dem Punkte-Muster 4,55:1. Fünf Hundertstel über der Schwelle —
 * die dunklen Stufen sind ausgereizt, Spielraum gibt es nur nach oben.
 */
const val HEX_LIGHT_SURFACE_LOWEST = 0xFFFFFFFF
const val HEX_LIGHT_CARD = 0xFFFFFDF7          // = surfaceContainerLow
const val HEX_LIGHT_BACKGROUND = 0xFFFCF6E4    // = surface
const val HEX_LIGHT_SURFACE_CONTAINER = 0xFFF4EDD6
const val HEX_LIGHT_SURFACE_HIGH = 0xFFEFE7CD  // Dialoge, Menüs, Bottom-Sheets
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

// --- Dark: warmes Anthrazit, gedämpfte Sättigung ------------------------------
/*
 * DAS EIGENTLICHE PROBLEM WAR NICHT DIE PALETTE — ES WAR, DASS SIE NIE GEZEICHNET WURDE.
 *
 * `HEX_DARK_BACKGROUND` stand seit jeher hier, aber kein Composable hat ihn je auf die
 * Fläche gebracht: das Scaffold zeichnete mit `dotPattern` NUR die Punkte, und `MaterialTheme`
 * malt von sich aus überhaupt nichts. Sichtbar war deshalb der Fenster-Hintergrund von
 * `android:Theme.Material.Light` — ein Fast-Weiß (#FAFAFA), in BEIDEN Themes. Der Dark Mode
 * setzte also seinen cremefarbenen Text auf Weiß: 1,21:1.
 *
 * Der Kontrast-Test war dabei nicht falsch, er war ARGLOS. Er prüfte die Werte, die die
 * Palette vorsah, und konnte nicht wissen, dass die Fläche darunter eine andere war. Die
 * einzige Farbe, die das Scaffold tatsächlich auftrug, war die Punktfarbe. Behoben an drei
 * Stellen: Fenster-Hintergrund (themes.xml + values-night), Wurzel-`Surface` im Theme und
 * die Grundfläche im Scaffold.
 *
 * ERST DANACH LÄSST SICH ÜBER DIE RAMPE REDEN — und da waren zwei Dinge zu korrigieren.
 *
 * ZU HELL. Der Hintergrund lag bei L* 14,9. Das war die Antwort auf eine frühere Fassung mit
 * L* 5,2, die wegen Halation (helle Buchstaben blühen auf nahezu schwarzem Grund aus) zu
 * Recht verworfen wurde — die Korrektur schoss aber über das Ziel hinaus. L* 11,5 ist die
 * Höhe, auf der Material 3 seine eigene dunkle Fläche ansetzt: dunkel genug, dass es nach
 * Dark Mode aussieht, deutlich über der Halations-Zone.
 *
 * ZU BUNT. Das ist der Grund, warum es „schmutzig" wirkte. Die Flächen hatten eine Buntheit
 * von C* 11–13 — im Light Mode bei L* 93 liest sich das als Creme, bei L* 15 als Oliv. Farbe
 * braucht Helligkeit, um als Farbe gelesen zu werden; unten wird aus Sättigung Schlamm.
 * Deshalb jetzt die Regel: **große Flächen fast neutral (C* 3,5–8), die Wärme tragen Text,
 * Rand und Akzent** — die liegen hoch genug, um Buntheit zu vertragen.
 *
 * Alle Flächen liegen auf demselben Farbwinkel (H 82°, warmes Gelbbraun). Eine Rampe mit
 * driftendem Farbton wirkt fleckig, auch wenn jeder Wert für sich stimmt.
 *
 * Reihenfolge nach Helligkeit: das Chrome liegt UNTER dem Hintergrund — gespiegelt zum
 * Light Mode, wo es ebenfalls darunter liegt und den Inhalt rahmt.
 */
const val HEX_DARK_SURFACE_LOWEST = 0xFF15120C  // L*  5,6
const val HEX_DARK_CHROME = 0xFF1C1811          // L*  8,5 — eigener Wert, nicht = Card
const val HEX_DARK_BACKGROUND = 0xFF231E16      // L* 11,6 = surface
const val HEX_DARK_SURFACE_LOW = 0xFF28231B     // L* 14,0
const val HEX_DARK_PATTERN = 0xFF2E281F         // L* 16,5 — Punkte
const val HEX_DARK_CARD = 0xFF302A21            // L* 17,4 = surfaceContainer
const val HEX_DARK_SURFACE_HIGH = 0xFF373127    // L* 20,7 — Dialoge, Menüs
const val HEX_DARK_SURFACE_HIGHEST = 0xFF3D362B // L* 23,0 — bindend für alle Texte

/*
 * Die drei Textebenen. Der Deckel der Flächen-Rampe bestimmt sie: auf L* 23 braucht es für
 * 4,5:1 mindestens L* 65,5 — die dritte Ebene kann also gar nicht tiefer. Dass die Rampe
 * jetzt bei 23 endet statt bei 26,9, ist genau deshalb kein Detail: es sind rund vier
 * Helligkeitsstufen zusätzlicher Spielraum für die Hierarchie.
 *
 * Der Primärtext ist ein warmes Off-White, KEIN Weiß — reines Weiß auf dunklem Grund ist die
 * Halation in Reinform.
 */
const val HEX_DARK_ON_SURFACE = 0xFFE6DFCE      // L* 88,9
const val HEX_DARK_TEXT_SECONDARY = 0xFFC7BDAC  // L* 77,0
const val HEX_DARK_TEXT_TERTIARY = 0xFFADA28F   // L* 67,1

// Rand: bindend ist die hellste Fläche, nicht die dunkelste.
const val HEX_DARK_BORDER = 0xFF918572          // L* 56,1

const val HEX_DARK_ON_CHROME = 0xFFE6DFCE

/**
 * Hier dreht das Paar um: im Dark Mode ist die **Füllung hell** und der Text darauf dunkel.
 *
 * Vorher trug ein einziges Token (`surfaceInverse`) beide Aufgaben — Chrome *und* primäre
 * Aktion. Im Dark Mode ziehen die Aufgaben auseinander: ein dunkler Button auf dunklem Grund
 * ist keine primäre Aktion mehr, er verschwindet.
 */
const val HEX_DARK_FILL_STRONG = 0xFFE4D9C1     // L* 87,0
const val HEX_DARK_ON_FILL_STRONG = 0xFF29231A  // L* 14,1

// Zwei Akzentwerte, weil Chrome und Inhaltsflächen verschieden hell sind.
const val HEX_DARK_ACCENT = 0xFFD0927B          // L* 66,1 — auf dem Chrome
const val HEX_DARK_ACCENT_ON_SURFACE = 0xFFD99D88 // L* 69,9 — auf Card und Dialog

/*
 * --- Fehlerfarben: eigene Werte statt der Route-Farbe -------------------------
 *
 * `error` war in beiden Themes `RouteRed` (#E53935). Das ist eine GRIFFFARBE — gewählt, um
 * auf einem Foto als roter Griff erkennbar zu sein, nicht um als Text zu funktionieren. Als
 * `error` landet sie aber genau dort: Material setzt die Rolle als Textfarbe von
 * Fehlermeldungen und Feld-Labels ein. Im Light Mode erreichte sie auf den Inhaltsflächen
 * 3,2–4,2:1 — durchweg unter der Schwelle, und ausgerechnet an der Stelle, an der Text am
 * dringendsten gelesen werden muss.
 *
 * Dieselbe Verwechslung wie damals bei `surfaceInverse`: eine Farbe, zwei Aufgaben. Deshalb
 * hier eigene Werte, und die Route-Farben bleiben, wofür sie da sind.
 */
const val HEX_LIGHT_ERROR = 0xFFAA3337
const val HEX_LIGHT_ON_ERROR = 0xFFF7F2E2
const val HEX_LIGHT_ERROR_CONTAINER = 0xFFFFD5D1
const val HEX_LIGHT_ON_ERROR_CONTAINER = 0xFF8B272A

const val HEX_DARK_ERROR = 0xFFEB837A
const val HEX_DARK_ON_ERROR = 0xFF311F1D
const val HEX_DARK_ERROR_CONTAINER = 0xFF4C2523
const val HEX_DARK_ON_ERROR_CONTAINER = 0xFFEFC1BC

/**
 * `inversePrimary` — die Aktion in der Snackbar, die auf der *invertierten* Fläche liegt.
 *
 * Eigene Werte, weil die invertierte Fläche die Polarität des Themes umdreht: im Dark Mode
 * ist `inverseSurface` hell, ein heller Akzent wäre dort unsichtbar. Genau die Falle, in der
 * der Nav-Akzent schon einmal saß.
 */
const val HEX_LIGHT_INVERSE_PRIMARY = 0xFFD99D88
const val HEX_DARK_INVERSE_PRIMARY = 0xFF8B3D25

// --- Route-Akzente (in beiden Themes gleich) ----------------------------------
// Reine Wiedererkennung der Grifffarbe, nie Textträger.
const val HEX_ROUTE_RED = 0xFFE53935
const val HEX_ROUTE_ORANGE = 0xFFFB8C00
const val HEX_ROUTE_YELLOW = 0xFFC8A800
const val HEX_ROUTE_GREEN = 0xFF43A047
const val HEX_ROUTE_BLUE = 0xFF1E88E5
const val HEX_ROUTE_PURPLE = 0xFF8E24AA
const val HEX_ROUTE_PINK = 0xFFE91E63
