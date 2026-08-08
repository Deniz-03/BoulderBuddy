package com.boulderbuddy.ui.theme

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * Begrenzt den Inhalt auf eine lesbare Spaltenbreite und zentriert ihn im Fenster.
 *
 * Auf dem Telefon wirkungslos: dort ist das Fenster schmaler als [max], `widthIn` greift also
 * nicht und `wrapContentWidth` hat nichts zu zentrieren. Genau deshalb kann der Modifier
 * bedingungslos gesetzt werden — er braucht keine Breitenabfrage und ändert das
 * Kompakt-Layout nicht.
 *
 * Die Reihenfolge ist nicht beliebig. `fillMaxWidth` nimmt die volle Fensterbreite,
 * `wrapContentWidth` lockert die Mindestbreite wieder und zentriert das Kind darin, und erst
 * `widthIn` deckelt das Kind. Stünde `widthIn` vor `wrapContentWidth`, gäbe es nichts mehr zu
 * zentrieren — der Inhalt bliebe am linken Rand kleben.
 */
fun Modifier.inhaltsBreite(
    max: Dp = Dimens.spaltenBreiteText,
    ausrichtung: Alignment.Horizontal = Alignment.CenterHorizontally,
): Modifier = this
    .fillMaxWidth()
    .wrapContentWidth(ausrichtung)
    .widthIn(max = max)

fun Modifier.dotPattern(
    dotColor: Color,
    dotRadius: Dp = Dimens.dotRadius,
    spacing: Dp = Dimens.dotSpacing,
): Modifier = this.drawBehind {
    val r = dotRadius.toPx()
    val s = spacing.toPx()
    var y = s / 2f
    while (y < size.height) {
        var x = s / 2f
        while (x < size.width) {
            drawCircle(dotColor, r, Offset(x, y))
            x += s
        }
        y += s
    }
}