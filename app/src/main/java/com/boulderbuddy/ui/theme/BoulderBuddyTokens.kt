package com.boulderbuddy.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class RouteColors(
    val red: Color,
    val orange: Color,
    val yellow: Color,
    val green: Color,
    val blue: Color,
    val purple: Color,
    val pink: Color,
)

/**
 * Farb-Tokens der App.
 *
 * Die wichtigste Regel steht in den Namen: **jede Fläche bringt die Farbe ihres Inhalts mit.**
 * Vorher gab es ein Token `surfaceInverse` für zwei verschiedene Aufgaben — das Chrome
 * (TopBar, Bottom-Nav) *und* die primären Aktionen (Button, gewählter Chip). Im Light Mode
 * fiel das nicht auf, weil beides dunkel sein darf. Im Dark Mode ziehen die Aufgaben
 * auseinander, und das eine Token konnte nur eine von beiden bedienen. Ergebnis war ein
 * Primärbutton mit 1,42:1 zwischen Füllung und Beschriftung.
 *
 * Deshalb jetzt zwei Paare statt eines Tokens — und jedes Paar wird **zusammen** benutzt.
 */
@Immutable
data class BoulderBuddyColors(
    /** Grundfläche des Screens. */
    val surfaceBackground: Color,

    /** Lochstruktur auf dem Hintergrund (dekorativ, nie Textträger). */
    val surfacePattern: Color,

    /** Erhöhte Fläche: Karten, Eingabefelder, ungewählte Chips. */
    val surfaceCard: Color,

    /**
     * Chrome: TopBar und Bottom-Nav.
     *
     * **Dreht mit dem Theme.** Vorher war es in beiden Themes fast-schwarz, und der Light Mode
     * sah deshalb nicht nach Light Mode aus: der Inhalt war creme, aber Kopf- und Fußleiste
     * waren das Dunkelste am Bildschirm. Jetzt hell im Light Mode, dunkel im Dark Mode; die
     * Trennung zum Inhalt leistet in beiden Fällen [borderSubtle], nicht die Farbe.
     * Inhalt darauf: [onChrome].
     */
    val surfaceChrome: Color,

    /** Beschriftungen und Icons auf [surfaceChrome]. Dreht mit [surfaceChrome]. */
    val onChrome: Color,

    /**
     * Füllung primärer Aktionen: `PrimaryButton`, gewählter `SelectableChip`, aktiver
     * Timer-Knopf, eingeschalteter `ToggleSwitch`.
     *
     * Dieses Paar **dreht** zwischen den Themes: im Light Mode dunkle Füllung mit hellem Text,
     * im Dark Mode helle Füllung mit dunklem Text. Nur so bleibt die primäre Aktion in beiden
     * Themes das auffälligste Element auf dem Screen. Inhalt darauf: [onFillStrong].
     */
    val fillStrong: Color,

    /** Beschriftungen und Icons auf [fillStrong]. */
    val onFillStrong: Color,

    /** Zweite Textebene: erklärende Sätze, Werte in Listenzeilen. Hält 4,5:1. */
    val textSecondary: Color,

    /** Dritte Textebene: Labels, Platzhalter, Zeitangaben. Hält ebenfalls 4,5:1. */
    val textTertiary: Color,

    /**
     * Rand von Karten, Feldern und ungewählten Chips. Hält 3:1 gegen **beide** Flächen —
     * er trägt die Abgrenzung, weil die Flächenstufe allein (1,16:1) zu fein dafür ist.
     */
    val borderSubtle: Color,

    /**
     * Markenakzent auf dem Chrome: der aktive Bottom-Nav-Eintrag.
     *
     * Muss mitdrehen, seit das Chrome mitdreht. Der frühere helle Rosé hatte auf schwarzem
     * Chrome 6,4:1 — auf hellem Chrome wären es 2,2:1 gewesen. Gleiche Farbe, anderer
     * Untergrund, unbrauchbarer Wert.
     */
    val navActive: Color,

    /**
     * Derselbe Akzent für Flächen des Inhalts statt des Chromes — die Beschriftung von
     * Textbuttons in Dialogen. Eigener Wert, weil Dialoge auf einer anderen Fläche liegen
     * als das Chrome.
     */
    val accentOnSurface: Color,

    val routes: RouteColors,
)

val LocalBoulderBuddyColors = staticCompositionLocalOf<BoulderBuddyColors> {
    error("BoulderBuddyColors nicht gefunden — ist BoulderBuddyTheme im Composable-Baum?")
}

// Zugriff aus jedem Composable: BoulderBuddy.colors.routes.red
object BoulderBuddy {
    val colors: BoulderBuddyColors
        @Composable @ReadOnlyComposable
        get() = LocalBoulderBuddyColors.current
}
