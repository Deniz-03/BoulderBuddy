package com.boulderbuddy.ui.theme

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.window.core.layout.WindowSizeClass

/**
 * Die Fensterbreite als das, was die UI davon braucht: drei benannte Stufen statt einer Zahl.
 *
 * Vorher gab es dafür ein `isWideLayout: Boolean`, das in `AppNavigation` aus der
 * `WindowSizeClass` abgeleitet und an genau zwei Stellen ausgewertet wurde. Ein Boolean kennt
 * zwei Zustände; Material kennt drei, und der Unterschied zwischen Mittel und Weit ist genau
 * der, an dem sich entscheidet, ob zwei Panes nebeneinander noch Sinn ergeben.
 *
 * Die Stufen entsprechen Materials Breakpoints:
 * - [Kompakt]: < 600 dp — Telefon hochkant. Eine Spalte, Navigation unten.
 * - [Mittel]: 600–839 dp — Tablet hochkant, großes Telefon quer. Navigation seitlich,
 *   Inhalt noch einspaltig, aber begrenzt.
 * - [Weit]: ≥ 840 dp — Tablet quer. Zwei Panes bzw. mehrspaltige Raster.
 */
enum class Breite {
    Kompakt,
    Mittel,
    Weit,
}

/**
 * Die aktuelle Fensterbreite.
 *
 * Liest `currentWindowAdaptiveInfo()`, das überall in der Composition verfügbar ist — die
 * Breite muss also nicht durch zwölf Screens gereicht werden. Screens nehmen sie trotzdem als
 * Parameter mit **Default** auf diese Funktion entgegen: sonst ließe sich in einer `@Preview`
 * kein Tablet-Layout zeigen, weil die Preview-Umgebung ihre eigene Fenstergröße meldet.
 */
@Composable
fun aktuelleBreite(): Breite {
    val sizeClass = currentWindowAdaptiveInfo().windowSizeClass
    return when {
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> Breite.Weit
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> Breite.Mittel
        else -> Breite.Kompakt
    }
}

/**
 * Wie viele Rasterspalten in [verfuegbar] passen, wenn jede mindestens [minSpalte] breit sein
 * soll. Immer mindestens eine.
 *
 * **Gedacht für Fälle, in denen [aktuelleBreite] die falsche Antwort gäbe.** Die liest die
 * Größe des *Fensters* — und die stimmt nicht mit dem überein, was einem Composable wirklich
 * zur Verfügung steht, sobald es in einem Pane sitzt. Im List-Detail-Layout des Sessions-Tabs
 * meldet das Fenster `Weit`, während die Liste links tatsächlich 360 dp hat. Ein Raster, das
 * sich auf die Fensterbreite verlässt, legt dort vier Spalten in 360 dp.
 *
 * Der Aufrufer besorgt [verfuegbar] aus einem `BoxWithConstraints` — also aus der Messung
 * statt aus einer Annahme. `LazyVerticalGrid` mit `GridCells.Adaptive` macht intern dasselbe;
 * diese Funktion ist für die Fälle, in denen ein Lazy-Raster nicht geht (etwa innerhalb eines
 * `LazyColumn`-Items, wo zwei Lazy-Container derselben Achse aufeinanderträfen).
 */
fun spaltenFuer(verfuegbar: Dp, minSpalte: Dp = Dimens.rasterSpalteMin): Int =
    (verfuegbar / minSpalte).toInt().coerceAtLeast(1)
