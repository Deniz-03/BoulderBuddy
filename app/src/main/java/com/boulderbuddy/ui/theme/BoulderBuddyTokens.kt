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
     * Chrome: TopBar und Bottom-Nav. Bleibt in **beiden** Themes dunkel — es rahmt den Inhalt,
     * und ein im Dark Mode plötzlich helles Chrome würde blenden. Inhalt darauf: [onChrome].
     */
    val surfaceChrome: Color,

    /** Beschriftungen und Icons auf [surfaceChrome]. */
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

    /** Akzent des aktiven Bottom-Nav-Eintrags. Sitzt immer auf [surfaceChrome]. */
    val navActive: Color,

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
