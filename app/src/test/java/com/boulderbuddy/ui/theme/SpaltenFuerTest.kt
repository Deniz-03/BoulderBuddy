package com.boulderbuddy.ui.theme

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * [spaltenFuer] ist die einzige Stelle der adaptiven Layout-Arbeit, die sich ohne gerendertes
 * Bild prüfen lässt — sie ist eine reine Funktion über zwei Längen.
 *
 * **Warum es diesen Test gibt:** Vorher stand an drei Stellen `chunked(2)`, also eine feste
 * Spaltenzahl. Am Tablet ergaben sich daraus Karten von 615 dp für drei Wörter Inhalt. Die
 * Zahl 2 war eine Telefon-Entscheidung, die als Konstante im Code gelandet ist; dieser Test
 * hält fest, dass die Spaltenzahl jetzt aus der **gemessenen** Breite folgt.
 *
 * Was er ausdrücklich **nicht** prüft: ob die Breite, die hineingereicht wird, die richtige
 * ist. Genau da lag die eigentliche Falle — `aktuelleBreite()` liest die *Fenster*breite, die
 * im Zwei-Pane-Layout nicht mit dem übereinstimmt, was einem Pane zur Verfügung steht. Dass
 * der Aufrufer `BoxWithConstraints` benutzt, kann diese Funktion nicht erzwingen.
 */
class SpaltenFuerTest {

    @Test
    fun telefonBreite_ergibtZweiSpalten() {
        // Pixel 6a hochkant: 411 dp abzüglich 2 × 16 dp Rand ≈ 379 dp.
        assertThat(spaltenFuer(379.dp, minSpalte = 180.dp)).isEqualTo(2)
    }

    @Test
    fun tabletQuer_ergibtMehrSpaltenAlsDasTelefon() {
        val telefon = spaltenFuer(379.dp)
        val tablet = spaltenFuer(1248.dp)

        assertWithMessage(
            "Genau das war der Fehler: die Spaltenzahl war am Tablet dieselbe wie am Telefon.",
        ).that(tablet).isGreaterThan(telefon)
    }

    @Test
    fun spaltenSindNieSchmalerAlsDieMindestbreite() {
        // Für jede plausible Fensterbreite muss gelten: verfügbar / Spalten >= minSpalte.
        // Sonst hätte die Aufteilung Karten erzeugt, die schmaler sind als erlaubt.
        val min = Dimens.rasterSpalteMin
        for (breite in 200..2000 step 17) {
            val spalten = spaltenFuer(breite.dp, minSpalte = min)
            val spaltenBreite = breite.dp / spalten

            // Ausnahme: unterhalb der Mindestbreite gibt es nur eine Spalte, und die ist
            // zwangsläufig zu schmal — eine Spalte weniger als eine geht nicht.
            if (spalten > 1) {
                assertWithMessage("bei $breite dp: $spalten Spalten à $spaltenBreite")
                    .that(spaltenBreite.value).isAtLeast(min.value)
            }
        }
    }

    @Test
    fun nieNullSpalten() {
        // Ein Raster mit null Spalten wäre eine Division durch null im Aufrufer.
        assertThat(spaltenFuer(0.dp)).isEqualTo(1)
        assertThat(spaltenFuer(1.dp)).isEqualTo(1)
        assertThat(spaltenFuer(Dimens.rasterSpalteMin - 1.dp)).isEqualTo(1)
    }

    @Test
    fun spaltenzahlWaechstMonotonMitDerBreite() {
        // Ein breiteres Fenster darf nie WENIGER Spalten ergeben. Klingt selbstverständlich,
        // ist es bei Rundung aber nicht — und ein Sprung zurück wäre beim Drehen des Geräts
        // als Umbruch sichtbar.
        var vorher = 0
        for (breite in 100..2000 step 13) {
            val jetzt = spaltenFuer(breite.dp)
            assertWithMessage("bei $breite dp sinkt die Spaltenzahl von $vorher auf $jetzt")
                .that(jetzt).isAtLeast(vorher)
            vorher = jetzt
        }
    }
}
