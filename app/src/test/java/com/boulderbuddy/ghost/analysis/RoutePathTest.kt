package com.boulderbuddy.ghost.analysis

import com.boulderbuddy.ghost.model.GhostPoint
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** JVM-Tests für Routenpfad-Projektion und Signalverarbeitung (M3). */
class RoutePathTest {

    @Test
    fun `Projektion auf vertikale Linie liefert Bogenlaenge = Hoehe`() {
        val path = RoutePolyline(listOf(GhostPoint(100f, 0f), GhostPoint(100f, 1000f)))
        assertThat(path.projectArcLength(GhostPoint(140f, 250f))).isWithin(1e-6).of(250.0)
        assertThat(path.projectArcLength(GhostPoint(60f, 990f))).isWithin(1e-6).of(990.0)
    }

    @Test
    fun `Punkte hinter den Enden werden geklemmt`() {
        val path = RoutePolyline(listOf(GhostPoint(0f, 0f), GhostPoint(0f, 100f)))
        assertThat(path.projectArcLength(GhostPoint(0f, -50f))).isWithin(1e-6).of(0.0)
        assertThat(path.projectArcLength(GhostPoint(0f, 200f))).isWithin(1e-6).of(100.0)
        assertThat(path.totalLength).isWithin(1e-6).of(100.0)
    }

    @Test
    fun `Traverse zaehlt horizontalen Fortschritt (P2)`() {
        // L-förmiger Pfad: 100 hoch, dann 100 nach rechts. Reine Y-Logik würde
        // auf dem horizontalen Teil keinen Fortschritt sehen — Bogenlänge schon.
        val path = RoutePolyline(
            listOf(GhostPoint(0f, 100f), GhostPoint(0f, 0f), GhostPoint(100f, 0f)),
        )
        val halfTraverse = path.projectArcLength(GhostPoint(50f, 5f))
        assertThat(halfTraverse).isWithin(1e-6).of(150.0)
    }

    @Test
    fun `Gauss-Glaettung erhaelt konstante Signale`() {
        val constant = DoubleArray(30) { 7.5 }
        val smoothed = gaussianSmooth(constant, 2.0)
        smoothed.forEach { assertThat(it).isWithin(1e-9).of(7.5) }
    }

    @Test
    fun `Gauss-Glaettung reduziert Rauschen`() {
        val noisy = DoubleArray(60) { it + if (it % 2 == 0) 3.0 else -3.0 }
        val smoothed = gaussianSmooth(noisy, 2.0)
        // Rauschamplitude ±3 um die Rampe; nach Glättung deutlich näher an der Rampe.
        val maxError = (5 until 55).maxOf { kotlin.math.abs(smoothed[it] - it) }
        assertThat(maxError).isLessThan(1.0)
    }

    @Test
    fun `Luecken werden vorwaerts und rueckwaerts gefuellt`() {
        val filled = fillGaps(
            listOf(null, GhostPoint(1f, 1f), null, null, GhostPoint(4f, 4f), null),
        )
        assertThat(filled).isNotNull()
        assertThat(filled!![0]).isEqualTo(GhostPoint(1f, 1f)) // rückwärts gefüllt
        assertThat(filled[2]).isEqualTo(GhostPoint(1f, 1f))   // vorwärts gefüllt
        assertThat(filled[3]).isEqualTo(GhostPoint(1f, 1f))
        assertThat(filled[5]).isEqualTo(GhostPoint(4f, 4f))
    }

    @Test
    fun `komplett leere Trajektorie ergibt null`() {
        assertThat(fillGaps(listOf(null, null, null))).isNull()
    }
}
