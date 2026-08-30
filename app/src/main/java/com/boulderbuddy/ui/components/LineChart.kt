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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens

/**
 * Ein Punkt der Verlaufskurve.
 *
 * [wert] ist `null`, wenn es in diesem Zeitabschnitt **keine Daten** gab — nicht 0. Der
 * Unterschied ist hier wesentlich: „in dieser Woche nicht geklettert" ist etwas anderes als
 * „in dieser Woche Grad 0 geklettert", und ein Nullpunkt in der Kurve würde einen Einbruch
 * behaupten, den es nicht gab.
 *
 * [wertLabel] ist die lesbare Form des Werts (z. B. „V4"). Die Kurve rechnet mit der
 * Ordnungszahl des Grades, aber niemand liest Ordnungszahlen.
 */
data class LinePoint(
    val label: String,
    val wert: Float?,
    val wertLabel: String? = null,
)

private val ChartHeight = 160.dp

// Breite der Wertespalte links. Fest, damit die Beschriftungen unter der Kurve exakt unter
// der Zeichenfläche liegen und nicht um die Textbreite verrutschen.
private val AchsenBreite = 40.dp

/** Testmarke der Zeichenfläche — eine Kurve hat keinen Text, an dem ein Test sie greifen könnte. */
const val LINE_CHART_TEST_TAG = "linechart_flaeche"

/**
 * Verlaufskurve über gleich breite Zeitabschnitte.
 *
 * Bewusst mit `Canvas` statt aus Compose-Boxen wie [BarChart]: ein Balken ist ein Rechteck
 * und lässt sich als `Box` ausdrücken, eine Linie zwischen beliebigen Punkten nicht. Die
 * Farben kommen trotzdem aus dem Theme und werden **vor** dem Zeichenblock gelesen — der
 * `DrawScope` ist kein Composable und kommt an `MaterialTheme` nicht heran.
 *
 * **Lücken:** Abschnitte ohne Daten bekommen keinen Punkt, die Linie überspringt sie und
 * verbindet die nächsten vorhandenen Werte. Die Alternative — die Linie abreißen zu lassen —
 * sähe nach einem Fehler aus, obwohl eine Trainingspause normal ist.
 */
@Composable
fun LineChart(
    points: List<LinePoint>,
    modifier: Modifier = Modifier,
    linienFarbe: Color = BoulderBuddy.colors.accentOnSurface,
) {
    val werte = points.mapNotNull { it.wert }
    val minWert = werte.minOrNull() ?: 0f
    val maxWert = werte.maxOrNull() ?: 0f
    // Bei genau einem vorkommenden Wert wäre die Spanne 0 → Division durch null. Dann liegt
    // die Linie auf halber Höhe, was auch inhaltlich stimmt: es gibt keine Entwicklung.
    val spanne = (maxWert - minWert).takeIf { it > 0f }

    val punktFarbe = linienFarbe
    val rasterFarbe = BoulderBuddy.colors.borderSubtle

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.paddingXS)) {
        Row(modifier = Modifier.fillMaxWidth().height(ChartHeight)) {
            // Wertespalte: nur höchster und niedrigster vorkommender Grad. Eine volle
            // Achsenbeschriftung wäre bei drei bis fünf Graden mehr Text als Diagramm.
            Column(
                modifier = Modifier.width(AchsenBreite).fillMaxHeight(),
                // Bei nur einem vorkommenden Wert liegt die Linie auf halber Höhe — dann
                // gehört auch seine Beschriftung dorthin. Oben wäre sie eine Behauptung über
                // eine Achse, die es nicht gibt.
                verticalArrangement = if (spanne == null) {
                    Arrangement.Center
                } else {
                    Arrangement.SpaceBetween
                },
                horizontalAlignment = Alignment.Start,
            ) {
                AchsenText(points.firstOrNull { it.wert == maxWert }?.wertLabel)
                if (spanne != null) {
                    AchsenText(points.firstOrNull { it.wert == minWert }?.wertLabel)
                }
            }

            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag(LINE_CHART_TEST_TAG),
            ) {
                if (points.isEmpty()) return@Canvas

                val radius = 4.dp.toPx()
                // Innenabstand nach oben und unten, damit die Punkte dort nicht
                // angeschnitten werden.
                val nutzHoehe = size.height - 2 * radius

                /*
                 * Waagerecht bekommt jeder Zeitabschnitt eine gleich breite Spalte, und sein
                 * Punkt sitzt in deren Mitte — dieselbe Aufteilung, die die Zeitachse
                 * darunter über `weight(1f)` ohnehin schon benutzt.
                 *
                 * Vorher liefen die Punkte von `radius` bis `Breite - radius`, die Labels
                 * dagegen in gleich breiten Zellen: zwei Rechnungen, die sich nur in der
                 * Mitte treffen. Je weiter außen ein Abschnitt lag, desto weiter stand seine
                 * Beschriftung daneben. Der Kommentar an der Zeitachse unten stimmte damit
                 * nur zur Hälfte — der Spacer allein richtet nichts aus, wenn die Punkte
                 * innerhalb der Zeichenfläche anders verteilt sind als die Zellen.
                 */
                val spaltenBreite = size.width / points.size

                fun xFuer(index: Int): Float = (index + 0.5f) * spaltenBreite

                fun yFuer(wert: Float): Float {
                    val anteil = if (spanne == null) 0.5f else (wert - minWert) / spanne
                    return radius + nutzHoehe * (1f - anteil)
                }

                // Grundlinie als leise Orientierung, damit die Kurve nicht im Nichts schwebt.
                drawLine(
                    color = rasterFarbe,
                    start = Offset(0f, size.height - radius),
                    end = Offset(size.width, size.height - radius),
                    strokeWidth = 1.dp.toPx(),
                )

                val vorhanden = points
                    .mapIndexedNotNull { index, punkt ->
                        punkt.wert?.let { index to Offset(xFuer(index), yFuer(it)) }
                    }

                if (vorhanden.size > 1) {
                    val pfad = Path().apply {
                        moveTo(vorhanden.first().second.x, vorhanden.first().second.y)
                        vorhanden.drop(1).forEach { (_, punkt) -> lineTo(punkt.x, punkt.y) }
                    }
                    drawPath(pfad, color = linienFarbe, style = Stroke(width = 2.dp.toPx()))
                }
                vorhanden.forEach { (_, punkt) -> drawCircle(punktFarbe, radius, punkt) }
            }
        }

        // Zeitachse. Zwei Dinge müssen stimmen, damit eine Beschriftung unter ihrem Punkt
        // steht: der führende Spacer hat exakt die Breite der Wertespalte (verschiebt die
        // ganze Zeile), und die Punkte oben sitzen in der Mitte derselben `weight(1f)`-
        // Spalten (siehe `xFuer` in der Zeichenfläche).
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(AchsenBreite))
            points.forEach { punkt ->
                Text(
                    text = punkt.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = BoulderBuddy.colors.textTertiary,
                    textAlign = TextAlign.Center,
                    // Einzeilig wie im BarChart: ein umgebrochenes Label ruiniert die
                    // Zeitachse; ein zu breites darf nur überstehen (Visible).
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AchsenText(text: String?) {
    Text(
        text = text.orEmpty(),
        style = MaterialTheme.typography.labelSmall,
        color = BoulderBuddy.colors.textTertiary,
        modifier = Modifier.padding(end = Dimens.paddingXS),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFCF6E4, widthDp = 360)
@Composable
private fun LineChartPreview() {
    BoulderBuddyTheme {
        LineChart(
            points = listOf(
                LinePoint("5.5.", 0f, "V1"),
                LinePoint("12.5.", 2f, "V3"),
                // Trainingspause — kein Punkt, die Linie überspringt.
                LinePoint("19.5.", null),
                LinePoint("26.5.", 3f, "V4"),
                LinePoint("2.6.", 2f, "V3"),
                LinePoint("9.6.", 4f, "V5"),
            ),
            modifier = Modifier.padding(Dimens.paddingL),
        )
    }
}
