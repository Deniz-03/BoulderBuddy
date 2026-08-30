package com.boulderbuddy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.boulderbuddy.R
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.viewmodel.TagesBoulderUi

private val ChartHeight = 150.dp

// Wie beim [LineChart]: feste Breite der Wertespalte, damit die Beschriftungen darunter
// exakt unter ihren Punkten stehen und nicht um die Textbreite verrutschen.
private val AchsenBreite = 40.dp

/**
 * Ab wie vielen Bouldern die Positionsziffern unter der Kurve wegfallen.
 *
 * Bis etwa zehn stehen „1. 2. 3. …" auf Telefonbreite bequem; darüber rücken sie zusammen
 * und tragen ohnehin nichts bei — dass es von links nach rechts vorangeht, sieht man der
 * Kurve an. Die Zahl im Untertitel sagt dann, um wie viele Boulder es ging.
 */
private const val MAX_ZIFFERN = 10

const val TAGES_VERLAUF_TEST_TAG = "tagesverlauf_flaeche"

/**
 * Der Verlauf eines Klettertages: je Boulder ein Punkt, in der Reihenfolge des Kletterns.
 *
 * Bewusst **nicht** [LineChart], obwohl beide eine Linie zeichnen. Dort ist die x-Achse
 * Zeit und ein fehlender Wert bedeutet „nicht geklettert"; hier ist sie die Reihenfolge, und
 * jeder Punkt existiert. Vor allem aber unterscheidet dieser Chart drei Punktarten, und die
 * Linie verbindet nur die getoppten:
 *
 *  - **Gefüllt** = Flash, im ersten Versuch getoppt.
 *  - **Ring** = getoppt, aber mit mehreren Versuchen.
 *  - **Offen und blass** = Projekt, nicht getoppt. Es hängt bewusst NICHT an der Linie:
 *    die Linie erzählt, was der Tag hergegeben hat, und ein Sturz gehört nicht hinein.
 *    Sichtbar bleibt er trotzdem — meist ist er der eigentliche Punkt des Tages.
 *
 * Diese drei Fälle in [LineChart] unterzubringen hätte dessen Zweck verwässert: er wird von
 * beiden Verlaufs-Diagrammen der Statistik benutzt, und dort gibt es keine Punktarten.
 */
@Composable
fun TagesVerlaufChart(
    boulder: List<TagesBoulderUi>,
    modifier: Modifier = Modifier,
    linienFarbe: Color = BoulderBuddy.colors.accentOnSurface,
) {
    if (boulder.isEmpty()) return

    val ordnungen = boulder.map { it.gradOrder }
    val minWert = ordnungen.min()
    val maxWert = ordnungen.max()
    // Nur ein vorkommender Grad ⇒ keine Spanne, sonst Division durch null. Die Kurve liegt
    // dann auf halber Höhe, was auch inhaltlich stimmt: an diesem Tag ging es weder hoch
    // noch runter.
    val spanne = (maxWert - minWert).takeIf { it > 0 }?.toFloat()

    val projektFarbe = BoulderBuddy.colors.textTertiary
    val rasterFarbe = BoulderBuddy.colors.borderSubtle
    // Der Grund, auf dem das Diagramm liegt. Beide nicht-gefüllten Punktarten stanzen sich
    // damit aus der Linie aus — siehe unten.
    val flaechenFarbe = BoulderBuddy.colors.surfaceBackground

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.paddingXS)) {
        Row(modifier = Modifier.fillMaxWidth().height(ChartHeight)) {
            Column(
                modifier = Modifier.width(AchsenBreite).fillMaxHeight(),
                verticalArrangement = if (spanne == null) Arrangement.Center else Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.Start,
            ) {
                AchsenText(boulder.first { it.gradOrder == maxWert }.gradLabel)
                if (spanne != null) {
                    AchsenText(boulder.first { it.gradOrder == minWert }.gradLabel)
                }
            }

            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag(TAGES_VERLAUF_TEST_TAG),
            ) {
                val radius = 5.dp.toPx()
                // Innenabstand nach oben und unten, sonst wären die Punkte an den Rändern
                // angeschnitten.
                val nutzHoehe = size.height - 2 * radius

                /*
                 * Waagerecht bekommt jeder Boulder eine gleich breite Spalte, und der Punkt
                 * sitzt in deren Mitte.
                 *
                 * Das ist keine Geschmacksfrage, sondern die Bedingung dafür, dass die
                 * Ziffern darunter stimmen: die Beschriftungszeile ist eine `Row` aus
                 * `weight(1f)`-Zellen, jede Ziffer steht also in der Mitte ihrer Spalte.
                 * Vorher verteilte diese Funktion die Punkte stattdessen von `radius` bis
                 * `Breite - radius` — zwei verschiedene Rechnungen, die sich nur in der
                 * Mitte treffen. Am Gerät stand der erste Punkt rund 110 px links neben
                 * seiner „1.", der letzte ebenso weit rechts neben seiner „3.", und
                 * ausgerechnet der mittlere passte.
                 */
                val spaltenBreite = size.width / boulder.size

                fun xFuer(index: Int): Float = (index + 0.5f) * spaltenBreite

                fun yFuer(order: Int): Float {
                    val anteil = if (spanne == null) 0.5f else (order - minWert) / spanne
                    return radius + nutzHoehe * (1f - anteil)
                }

                // Grundlinie als leise Orientierung, damit die Punkte nicht im Nichts schweben.
                drawLine(
                    color = rasterFarbe,
                    start = Offset(0f, size.height - radius),
                    end = Offset(size.width, size.height - radius),
                    strokeWidth = 1.dp.toPx(),
                )

                val punkte = boulder.mapIndexed { index, b ->
                    b to Offset(xFuer(index), yFuer(b.gradOrder))
                }

                // Die Linie führt nur über die getoppten Boulder — und überspringt ein
                // Projekt dazwischen, statt abzureißen.
                val getoppte = punkte.filter { (b, _) -> b.getoppt }.map { it.second }
                if (getoppte.size > 1) {
                    val pfad = Path().apply {
                        moveTo(getoppte.first().x, getoppte.first().y)
                        getoppte.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(pfad, color = linienFarbe, style = Stroke(width = 2.dp.toPx()))
                }

                punkte.forEach { (b, punkt) ->
                    when {
                        b.flash -> drawCircle(linienFarbe, radius, punkt)
                        b.getoppt -> {
                            // Erst die Fläche füllen, dann der Rand: ein Ring über der Linie
                            // wäre sonst durchsichtig und die Linie liefe mitten hindurch.
                            drawCircle(flaechenFarbe, radius, punkt)
                            drawCircle(
                                color = linienFarbe,
                                radius = radius,
                                center = punkt,
                                style = Stroke(width = 2.dp.toPx()),
                            )
                        }
                        else -> {
                            // Auch das Projekt wird ausgestanzt, nicht nur umrandet. Liegt
                            // sein Grad zwischen zwei Tops, läuft die Verbindungslinie sonst
                            // mitten durch den offenen Kreis — und dann liest man ihn als
                            // Teil der Kurve, obwohl er ausdrücklich keiner ist. Am Gerät
                            // gesehen und genau deshalb hier.
                            drawCircle(flaechenFarbe, radius, punkt)
                            drawCircle(
                                color = projektFarbe,
                                radius = radius,
                                center = punkt,
                                style = Stroke(width = 1.5.dp.toPx()),
                            )
                        }
                    }
                }
            }
        }

        if (boulder.size <= MAX_ZIFFERN) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(AchsenBreite))
                boulder.forEach { b ->
                    Text(
                        text = "${b.position}.",
                        style = MaterialTheme.typography.labelSmall,
                        color = BoulderBuddy.colors.textTertiary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.tag_legende),
            style = MaterialTheme.typography.labelSmall,
            color = BoulderBuddy.colors.textTertiary,
        )
    }
}

@Composable
private fun AchsenText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = BoulderBuddy.colors.textTertiary,
        modifier = Modifier.padding(end = Dimens.paddingXS),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFCF6E4, widthDp = 360)
@Composable
private fun TagesVerlaufChartPreview() {
    BoulderBuddyTheme {
        TagesVerlaufChart(
            boulder = listOf(
                TagesBoulderUi(1, "V3", 3, getoppt = true, flash = true, versuche = 1),
                TagesBoulderUi(2, "V4", 4, getoppt = true, flash = false, versuche = 2),
                TagesBoulderUi(3, "V5", 5, getoppt = true, flash = false, versuche = 3),
                TagesBoulderUi(4, "V6", 6, getoppt = true, flash = false, versuche = 5),
                TagesBoulderUi(5, "V7", 7, getoppt = false, flash = false, versuche = 12),
                TagesBoulderUi(6, "V4", 4, getoppt = true, flash = true, versuche = 1),
            ),
            modifier = Modifier.padding(Dimens.paddingL),
        )
    }
}
