package com.boulderbuddy.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test

/**
 * Die beiden Darstellungen, die am Tablet aus dem Ruder liefen — an einem gerenderten Frame
 * gemessen, nicht an Konstanten.
 *
 * **Warum es diesen Test gibt:** Beide Komponenten hatten eine Obergrenze im Code stehen, die
 * nichts bewirkt hat (`fillMaxWidth().widthIn(max = …)`). Der Balken ist danach beim Reparieren
 * auf Breite 0 zusammengefallen und war am Gerät **unsichtbar** — mit grünem Build und grünen
 * Tests. Ein Diagramm ohne Balken ist der schlimmere der beiden Fehler, deshalb prüft jeder
 * Test hier **beide** Richtungen: nicht zu breit *und* nicht verschwunden.
 *
 * `requiredWidth(1280.dp)` bildet das Pixel Tablet quer nach, unabhängig vom Testgerät.
 */
class DiagrammBreiteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val tabletBreite = 1280.dp

    private fun imTabletFenster(inhalt: @Composable () -> Unit) {
        composeRule.setContent {
            BoulderBuddyTheme {
                Box(Modifier.requiredWidth(tabletBreite)) { inhalt() }
            }
        }
    }

    /**
     * Gemessen wird die **Höhe**, nicht die Breite — und das ist kein Notbehelf, sondern
     * die genauere Messung.
     *
     * Die Breite des Heatmap-Knotens sagt nichts: `inhaltsBreite` macht ihn absichtlich
     * fensterbreit, um den Inhalt darin ausrichten zu können. Die Höhe dagegen folgt
     * ausschließlich aus der **Zellgröße** (die Zellen sind quadratisch) und ist damit ein
     * direkter Messwert für genau die Zahl, die am Tablet aus dem Ruder lief: 83 dp statt 16.
     *
     * 4 Zeilen à höchstens 32 dp plus 3 × 4 dp Abstand = 140 dp.
     * Mit den alten 83 dp wären es 4 × 83 + 12 = 344 dp gewesen.
     */
    @Test
    fun heatmapZellenBleibenGedeckelt() {
        imTabletFenster {
            ActivityHeatmap(
                intensities = List(28) { it / 28f },
                fillWidth = true,
                modifier = Modifier.testTag("heatmap"),
            )
        }

        val hoehe = composeRule.onNodeWithTag("heatmap").getUnclippedBoundsInRoot().height.value

        assertWithMessage(
            "Heatmap ist $hoehe dp hoch. Bei 32-dp-Zellen sind 140 dp zu erwarten; deutlich " +
                "mehr heißt, die Zellen wachsen wieder mit der Fensterbreite mit.",
        ).that(hoehe).isAtMost(145f)

        assertWithMessage("Heatmap hat keine Höhe — die Zellen sind verschwunden.")
            .that(hoehe).isGreaterThan(0f)
    }

    /**
     * Die eigentliche Regression in einem Bild: dieselben Daten in einem schmalen und einem
     * breiten Fenster müssen **dieselbe** Höhe ergeben. Vorher war die Zellgröße eine
     * Funktion der Fensterbreite — genau das ist hier ausgeschlossen.
     *
     * Beide Fenster stehen in **einer** Komposition; `setContent` lässt sich pro Test nur
     * einmal aufrufen.
     */
    @Test
    fun heatmapIstImSchmalenUndImBreitenFensterGleichHoch() {
        composeRule.setContent {
            BoulderBuddyTheme {
                Column {
                    listOf("schmal" to 400.dp, "breit" to tabletBreite).forEach { (tag, breite) ->
                        Box(Modifier.requiredWidth(breite)) {
                            ActivityHeatmap(
                                intensities = List(28) { it / 28f },
                                fillWidth = true,
                                modifier = Modifier.testTag(tag),
                            )
                        }
                    }
                }
            }
        }

        val schmal = composeRule.onNodeWithTag("schmal").getUnclippedBoundsInRoot().height.value
        val breit = composeRule.onNodeWithTag("breit").getUnclippedBoundsInRoot().height.value

        assertWithMessage(
            "Die Heatmap ist im 1280-dp-Fenster $breit dp hoch, im 400-dp-Fenster $schmal dp. " +
                "Die Zellgröße hängt also wieder an der Fensterbreite.",
        ).that(breit).isWithin(1f).of(schmal)
    }

    @Test
    fun balkenSindWederVerschwundenNochFlaechig() {
        // Zwei Balken: genau der Fall vom Gerät, in dem sie 615 dp breit wurden.
        imTabletFenster {
            BarChart(
                entries = listOf(
                    BarChartEntry("V1", 3f, Color.Red),
                    BarChartEntry("V3", 5f, Color.Green),
                ),
            )
        }

        val balken = composeRule.onAllNodesWithTag(BAR_TEST_TAG)
        balken.assertCountEquals(2)

        repeat(2) { i ->
            val breite = balken[i].getUnclippedBoundsInRoot().width.value

            assertWithMessage(
                "Balken $i hat Breite $breite — das Diagramm ist leer. Genau dieser Fehler " +
                    "fiel am Gerät auf, mit grünem Build und grünen Tests.",
            ).that(breite).isGreaterThan(0f)

            assertWithMessage(
                "Balken $i ist $breite dp breit — über 56 dp wird aus dem Balken eine " +
                    "Farbfläche, und das Verhältnis der Höhen geht daneben unter.",
            ).that(breite).isAtMost(57f)
        }
    }

    @Test
    fun balkenHoehenBildenDasVerhaeltnisAb() {
        // Die eigentliche Aussage des Diagramms. Wäre sie kaputt, wäre die Breitendeckelung
        // wertlos — der Test hält beides zusammen.
        imTabletFenster {
            BarChart(
                entries = listOf(
                    BarChartEntry("klein", 1f, Color.Red),
                    BarChartEntry("gross", 4f, Color.Green),
                ),
            )
        }

        val balken = composeRule.onAllNodesWithTag(BAR_TEST_TAG)
        val klein = balken[0].getUnclippedBoundsInRoot().height.value
        val gross = balken[1].getUnclippedBoundsInRoot().height.value

        assertWithMessage("Der Balken zum Wert 4 ist nicht höher als der zum Wert 1.")
            .that(gross).isGreaterThan(klein)
    }
}
