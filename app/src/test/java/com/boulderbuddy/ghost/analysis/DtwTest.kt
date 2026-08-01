package com.boulderbuddy.ghost.analysis

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** JVM-Tests für das selbst implementierte DTW (M3) — reine Kotlin-Logik. */
class DtwTest {

    @Test
    fun `identische Signale alignieren auf der Diagonalen mit Distanz 0`() {
        val signal = DoubleArray(50) { it.toDouble() }
        val result = dtw(signal, signal)
        assertThat(result.normalizedDistance).isWithin(1e-9).of(0.0)
        assertThat(result.path.first()).isEqualTo(0 to 0)
        assertThat(result.path.last()).isEqualTo(49 to 49)
        // Diagonale: jedes Paar ist (i, i).
        result.path.forEach { (i, j) -> assertThat(i).isEqualTo(j) }
    }

    @Test
    fun `gestrecktes Signal wird korrekt gemappt`() {
        // b ist a mit halbem Tempo (jeder Wert doppelt) — der klassische DTW-Fall.
        val a = DoubleArray(30) { it.toDouble() }
        val b = DoubleArray(60) { (it / 2).toDouble() }
        val result = dtw(a, b)
        assertThat(result.normalizedDistance).isWithin(1e-9).of(0.0)
        assertThat(result.path.first()).isEqualTo(0 to 0)
        assertThat(result.path.last()).isEqualTo(29 to 59)
    }

    @Test
    fun `Pause im zweiten Signal wird absorbiert`() {
        // b macht in der Mitte 20 Frames Pause (konstanter Fortschritt).
        val a = DoubleArray(40) { it.toDouble() }
        val b = (0 until 20).map { it.toDouble() } +
            List(20) { 19.0 } +
            (20 until 40).map { it.toDouble() }
        val result = dtw(a, b.toDoubleArray())
        // Pause kostet fast nichts: die 20 Pausen-Frames mappen auf a≈19.
        assertThat(result.normalizedDistance).isLessThan(0.5)
        assertThat(result.path.last()).isEqualTo(39 to 59)
    }

    @Test
    fun `Pfad ist monoton nicht-fallend`() {
        val a = doubleArrayOf(0.0, 1.0, 5.0, 6.0, 9.0, 12.0)
        val b = doubleArrayOf(0.0, 2.0, 3.0, 7.0, 8.0, 11.0, 12.0)
        val path = dtw(a, b).path
        path.zipWithNext().forEach { (prev, next) ->
            assertThat(next.first).isAtLeast(prev.first)
            assertThat(next.second).isAtLeast(prev.second)
        }
    }

    @Test
    fun `Mapping aus DTW-Pfad ist monoton und klemmt an den Raendern`() {
        val a = DoubleArray(10) { it.toDouble() }
        val b = DoubleArray(20) { (it / 2).toDouble() }
        val refFrames = List(10) { com.boulderbuddy.ghost.model.GhostPoseFrame(it * 100L, emptyList()) }
        val cmpFrames = List(20) { com.boulderbuddy.ghost.model.GhostPoseFrame(it * 100L, emptyList()) }
        val mapping = buildTimeMapping(dtw(a, b).path, refFrames, cmpFrames)

        assertThat(mapping.mapToComparison(-500L)).isEqualTo(mapping.mapToComparison(0L))
        assertThat(mapping.mapToComparison(99_999L)).isEqualTo(mapping.mapToComparison(900L))
        var previous = Long.MIN_VALUE
        for (t in 0..900L step 50) {
            val mapped = mapping.mapToComparison(t)
            assertThat(mapped).isAtLeast(previous)
            previous = mapped
        }
        // Halbes Tempo: Referenz-Ende (900 ms) ≈ Vergleichs-Ende. Der letzte Ref-Frame
        // ist beiden Cmp-Frames 18 und 19 zugeordnet; seit S1 wird der Mittelwert
        // sub-frame-genau interpoliert (18,5 → 1850 ms) statt auf 19 gerundet.
        assertThat(mapping.mapToComparison(900L)).isEqualTo(1850L)
    }
}
