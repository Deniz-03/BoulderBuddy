package com.boulderbuddy.ghost.analysis

import com.boulderbuddy.ghost.model.GhostPoseFrame
import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import org.junit.Test

/**
 * JVM-Tests der Warp-Glättung (S1, 7.5d). Getestet wird die EIGENSCHAFT der
 * Warp-Funktion — Ruhe, Monotonie, Endpunkt-Treue — nicht einzelne Zahlenwerte:
 * die Tuning-Defaults sollen sich kalibrieren lassen, ohne dass Tests brechen.
 */
class GhostTimeMappingTest {

    /** 12 fps wie in der Extraktion: ein Sample-Frame alle ~83 ms. */
    private fun frames(count: Int) = List(count) { GhostPoseFrame(it * 83L, emptyList()) }

    /** Ungeglättetes Referenz-Verhalten (Stand vor S1) zum Vergleich. */
    private fun raw(
        path: List<Pair<Int, Int>>,
        ref: List<GhostPoseFrame>,
        cmp: List<GhostPoseFrame>,
    ) = buildTimeMapping(
        dtwPath = path,
        refFrames = ref,
        cmpFrames = cmp,
        smoothingSigmaFrames = 0.0,
        linearBlend = 0.0,
        minSlope = 0.0,
        maxSlope = Double.MAX_VALUE,
    )

    /** Lokale Steigungen dCmp/dRef an den Sample-Zeitpunkten der Referenz. */
    private fun slopes(mapping: GhostTimeMapping, ref: List<GhostPoseFrame>): List<Double> =
        ref.zipWithNext { a, b ->
            (mapping.mapToComparison(b.timeMs) - mapping.mapToComparison(a.timeMs)).toDouble() /
                (b.timeMs - a.timeMs)
        }

    /**
     * Der Kern von S1: ein DTW-Pfad mit Treppe (Plateau + Nachhol-Sprung) — genau das
     * Muster, das im Overlay als "laggy" auffällt — muss nach der Glättung deutlich
     * ruhiger laufen als vorher.
     */
    @Test
    fun `Treppe im DTW-Pfad wird zu einer ruhigen Warp-Funktion`() {
        val ref = frames(30)
        val cmp = frames(30)
        // Ref 10..14 hängen alle auf Cmp 10 (Plateau), danach holt Cmp auf.
        val path = buildList {
            for (i in 0 until 10) add(i to i)
            for (i in 10..14) add(i to 10)
            for (i in 15 until 30) add(i to i)
        }

        val smoothed = slopes(buildTimeMapping(path, ref, cmp), ref)
        val unsmoothed = slopes(raw(path, ref, cmp), ref)

        // Vorher: irgendwo steht der Geist komplett (Steigung 0).
        assertThat(unsmoothed.min()).isWithin(1e-9).of(0.0)
        // Nachher: er bleibt nie stehen und die Spitzensteigung sinkt spürbar.
        assertThat(smoothed.min()).isGreaterThan(0.0)
        assertThat(smoothed.max()).isLessThan(unsmoothed.max())
    }

    @Test
    fun `Mapping bleibt monoton und trifft die Endpunkte des rohen Warps`() {
        val ref = frames(30)
        val cmp = frames(30)
        val path = buildList {
            for (i in 0 until 10) add(i to i)
            for (i in 10..14) add(i to 10)
            for (i in 15 until 30) add(i to i)
        }
        val mapping = buildTimeMapping(path, ref, cmp)
        val rawMapping = raw(path, ref, cmp)

        var previous = Long.MIN_VALUE
        for (t in 0..ref.last().timeMs step 20) {
            val mapped = mapping.mapToComparison(t)
            assertThat(mapped).isAtLeast(previous)
            previous = mapped
        }
        // Anfang und Ende sind gepinnt — der Geist startet und endet dort, wo das
        // DTW ihn hinlegt, nur der Weg dazwischen ist geglättet.
        assertThat(mapping.mapToComparison(0L)).isEqualTo(rawMapping.mapToComparison(0L))
        assertThat(mapping.mapToComparison(ref.last().timeMs))
            .isEqualTo(rawMapping.mapToComparison(ref.last().timeMs))
    }

    @Test
    fun `Blend 1 ergibt exakt die lineare Zeitachse`() {
        val ref = frames(20)
        val cmp = frames(40)
        val path = List(40) { minOf(it / 2, 19) to it }
        val mapping = buildTimeMapping(path, ref, cmp, linearBlend = 1.0)

        val start = mapping.mapToComparison(ref.first().timeMs)
        val end = mapping.mapToComparison(ref.last().timeMs)
        val refSpan = (ref.last().timeMs - ref.first().timeMs).toDouble()
        ref.forEach { frame ->
            val expected = start + (frame.timeMs - ref.first().timeMs) / refSpan * (end - start)
            assertThat(abs(mapping.mapToComparison(frame.timeMs) - expected)).isLessThan(2.0)
        }
    }

    /**
     * Gegenprobe zur Glättung: eine ECHTE Pause (der Vergleich hängt mehrere Sekunden
     * am selben Griff) darf nicht wegglättet werden — sonst verliert das DTW seinen
     * Zweck und der Geist läuft davon.
     */
    @Test
    fun `Echte lange Pause wird weiterhin absorbiert`() {
        val ref = frames(60)
        val cmp = frames(60)
        // Ref 20..44 (~2 s) hängen auf Cmp 20 — eine echte Pause im Vergleichs-Versuch.
        val path = buildList {
            for (i in 0 until 20) add(i to i)
            for (i in 20..44) add(i to 20)
            for (i in 45 until 60) add(i to i)
        }
        val mapping = buildTimeMapping(path, ref, cmp)
        val all = slopes(mapping, ref)
        // Fenster bewusst im Kern der Pause: näher am Nachhol-Sprung (Ref 45) zieht ihn
        // die Glättung erwartungsgemäß in die Messung hinein.
        val inPause = all.subList(24, 32).average()
        val outsidePause = all.subList(0, 15).average()

        // In der Pause läuft der Geist deutlich langsamer als außerhalb — tiefer als
        // GhostTuning.WARP_MIN_SLOPE (0,4) geht es aber bewusst nicht: der Geist soll
        // auch in einer Pause kriechen statt einzufrieren.
        assertThat(inPause).isLessThan(outsidePause * 0.6)
    }

    @Test
    fun `Gleiches Tempo bleibt praktisch die Diagonale`() {
        val ref = frames(40)
        val cmp = frames(40)
        val mapping = buildTimeMapping(List(40) { it to it }, ref, cmp)
        slopes(mapping, ref).forEach { assertThat(abs(it - 1.0)).isLessThan(0.05) }
    }
}
