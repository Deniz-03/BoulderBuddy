package com.boulderbuddy.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Hält fest, dass die fünf Material-Stufen **feste** Radien tragen.
 *
 * **Warum es diesen Test gibt:** `extraLarge` stand auf `RoundedCornerShape(percent = 50)`,
 * gedacht als Pillenform für Chips. Material greift für `AlertDialog`, `ModalBottomSheet` und
 * die Picker auf dieselbe Stufe zurück — ein prozentualer Radius nimmt dort die halbe kürzere
 * Kante des Dialogs. Bei einem Dialog von 300 × 400 dp waren das 150 dp Eckenradius; die Ecken
 * schnitten Eingabefelder und Listenzeilen an den Rändern ab, und zwar in **jedem** Dialog der
 * App gleichzeitig.
 *
 * Aus dem Code war das nicht zu sehen: an der Zeile stand „Chips", und Chips sahen richtig aus.
 * Sichtbar wurde der Fehler erst dort, wo Material die Stufe ungefragt selbst benutzt.
 *
 * Der Test misst deshalb nicht den Wert, sondern die **Eigenschaft**, die kaputt war: der
 * Radius darf nicht mit der Fläche wachsen. Eine Pillenform ist weiterhin erlaubt — nur eben
 * als [PillShape], außerhalb der Skala, wo nur unsere eigenen Komponenten sie abholen.
 */
class ShapeScaleTest {

    // Ein durchschnittlicher Dialog. Klein genug, dass ein prozentualer Radius auffällt,
    // groß genug, dass ein fester Radius (≤ 28 dp) unauffällig bleibt.
    private val dialogGroesse = Size(width = 300f, height = 400f)
    private val dichte = Density(density = 1f)

    private fun eckenRadius(shape: CornerBasedShape): Float =
        shape.topStart.toPx(dialogGroesse, dichte)

    @Test
    fun keineStufeDerSkala_waechstMitDerFlaeche() {
        val stufen = mapOf(
            "extraSmall" to BoulderBuddyShapes.extraSmall,
            "small" to BoulderBuddyShapes.small,
            "medium" to BoulderBuddyShapes.medium,
            "large" to BoulderBuddyShapes.large,
            "extraLarge" to BoulderBuddyShapes.extraLarge,
        )
        stufen.forEach { (name, shape) ->
            val radius = eckenRadius(shape)
            // 40 dp ist großzügig — es geht nicht um den exakten Wert, sondern darum, dass
            // hier nicht wieder ein Anteil der Dialogbreite landet (das wären 150).
            assertWithMessage(
                "$name ergibt auf einem 300×400-Dialog %.0f px Eckenradius — das ist ein " +
                    "Anteil der Fläche, kein fester Radius.".format(radius)
            ).that(radius).isAtMost(40f)
        }
    }

    @Test
    fun dieSkala_steigtStrengMonoton() {
        // Vor der Typo-Runde waren `medium` und `large` beide 14 dp. Eine Dublette macht aus
        // fünf Stufen vier und lässt eine Komponente, die bewusst „eine Stufe runder" sein
        // wollte, wie ihre Nachbarin aussehen.
        val reihe = listOf(
            BoulderBuddyShapes.extraSmall,
            BoulderBuddyShapes.small,
            BoulderBuddyShapes.medium,
            BoulderBuddyShapes.large,
            BoulderBuddyShapes.extraLarge,
        ).map { eckenRadius(it) }

        reihe.zipWithNext().forEach { (kleiner, groesser) ->
            assertThat(groesser).isGreaterThan(kleiner)
        }
    }

    @Test
    fun pillenform_istWeiterhinVollGerundet() {
        // Die Gegenprobe: die Chips haben ihre Form nicht verloren, sie ist nur umgezogen.
        // Auf 300 × 400 ist die kürzere Kante 300, die halbe also 150.
        assertThat(eckenRadius(PillShape)).isWithin(0.01f).of(150f)
    }
}
