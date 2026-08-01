package com.boulderbuddy.ghost.analysis

import com.boulderbuddy.ghost.model.GhostPoint
import com.boulderbuddy.ghost.model.GhostViewMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** JVM-Tests für die Ähnlichkeitsmetrik des Modus-Vorschlags (M4, P7). */
class ModeSuggestionTest {

    // Vertikaler Pfad, 1000 px lang — typische Boulder-Linie im Referenzraum.
    private val path = RoutePolyline(listOf(GhostPoint(300f, 1000f), GhostPoint(300f, 0f)))

    private fun verticalClimb(xOffset: Float = 0f): List<GhostPoint> =
        List(50) { GhostPoint(300f + xOffset, 1000f - it * 20f) }

    @Test
    fun `gleiche Linie und gutes DTW ergibt Overlay`() {
        val suggestion = suggestViewMode(
            refTrajectory = verticalClimb(),
            cmpTrajectory = verticalClimb(xOffset = 10f),
            path = path,
            dtwNormalizedDistance = 10.0, // 1% der Pfadlänge
        )
        assertThat(suggestion.mode).isEqualTo(GhostViewMode.OVERLAY)
    }

    @Test
    fun `hohe DTW-Restdistanz ergibt Side-by-Side`() {
        val suggestion = suggestViewMode(
            refTrajectory = verticalClimb(),
            cmpTrajectory = verticalClimb(),
            path = path,
            dtwNormalizedDistance = 200.0, // 20% der Pfadlänge
        )
        assertThat(suggestion.mode).isEqualTo(GhostViewMode.SIDE_BY_SIDE)
    }

    @Test
    fun `stark abweichende laterale Streuung ergibt Side-by-Side`() {
        // Vergleich pendelt ±150 px um den Pfad (andere Linie), Referenz bleibt drauf.
        val zigzag = List(50) {
            GhostPoint(300f + if (it % 2 == 0) 150f else -150f, 1000f - it * 20f)
        }
        val suggestion = suggestViewMode(
            refTrajectory = verticalClimb(),
            cmpTrajectory = zigzag,
            path = path,
            dtwNormalizedDistance = 10.0,
        )
        assertThat(suggestion.mode).isEqualTo(GhostViewMode.SIDE_BY_SIDE)
    }

    @Test
    fun `orthogonale Hauptrichtung ergibt Side-by-Side`() {
        // Traverse (horizontal) gegen vertikale Referenz — PCA-Winkel ≈ 90°.
        // Nah am Pfad (x um 300) damit nicht schon die laterale Streuung greift:
        // beide Trajektorien haben ähnliche Distanz-Streuung, aber andere Richtung.
        val traverse = List(50) { GhostPoint(280f + it * 1f, 500f) }
        val angle = mainDirectionDifferenceDeg(verticalClimb(), traverse)
        assertThat(angle).isGreaterThan(80.0)
    }

    @Test
    fun `laterale Abweichung einer pfadtreuen Linie ist nahe 0`() {
        assertThat(lateralDeviation(verticalClimb(), path)).isWithin(1e-6).of(0.0)
    }

    @Test
    fun `konstanter Versatz neben dem Pfad wird als Abweichung erkannt`() {
        // Genau der Fall, an dem eine reine Streuungs-Metrik scheitert:
        // konstante 150 px Abstand ⇒ Streuung 0, Abweichung 150.
        assertThat(lateralDeviation(verticalClimb(xOffset = 150f), path))
            .isWithin(1e-3).of(150.0)
    }
}
