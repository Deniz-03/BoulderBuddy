package com.boulderbuddy.ghost.model

import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import org.junit.Test

/**
 * JVM-Tests der Zwischenbild-Interpolation (S4c, 7.5e). Die Spur wird mit 12 fps
 * abgetastet, das Video läuft mit 30+ — dazwischen wird interpoliert. Linear traf jeden
 * Stützpunkt mit einem Knick, die Bewegungsrichtung sprang also alle 83 ms; das ist auch
 * bei kleinen Amplituden als Unruhe sichtbar.
 */
class PoseInterpolationTest {

    private fun track(vararg xs: Float) = GhostPoseTrack(
        videoUri = "test",
        frameWidth = 720,
        frameHeight = 1280,
        durationMs = xs.size * 83L,
        sampleFps = 12.0,
        frames = xs.mapIndexed { i, x ->
            GhostPoseFrame(
                timeMs = i * 83L,
                landmarks = listOf(
                    GhostLandmark(type = GhostLandmarkTypes.LEFT_WRIST, x = x, y = 0f, confidence = 0.9f),
                ),
            )
        },
    )

    private fun xAt(track: GhostPoseTrack, timeMs: Long): Float =
        track.landmarksAt(timeMs).single().x

    /** Nicht verhandelbar: an den Stützstellen bleiben die GEMESSENEN Werte stehen. */
    @Test
    fun `an den Stuetzstellen bleiben die Messwerte exakt`() {
        val values = floatArrayOf(0f, 10f, 40f, 45f, 100f)
        val t = track(*values)
        values.forEachIndexed { i, expected ->
            assertThat(xAt(t, i * 83L)).isWithin(0.01f).of(expected)
        }
    }

    /**
     * Der Kern von S4c: an einem Stützpunkt darf die Bewegungsrichtung nicht mehr
     * springen. Gemessen als Geschwindigkeit kurz vor und kurz nach dem Stützpunkt —
     * linear interpoliert wären das die beiden (verschiedenen) Segmentsteigungen.
     */
    @Test
    fun `die Geschwindigkeit springt am Stuetzpunkt nicht mehr`() {
        // Deutlicher Steigungswechsel: 10 px pro Frame, dann 50.
        val t = track(0f, 10f, 20f, 70f, 120f)
        val knot = 2 * 83L
        val before = xAt(t, knot) - xAt(t, knot - 5)
        val after = xAt(t, knot + 5) - xAt(t, knot)

        // Linear wäre der Sprung das volle Steigungsverhältnis (Faktor 5).
        assertThat(abs(after - before)).isLessThan(abs(before) * 1.5f)
    }

    /** Kein Überschwingen bei gleichförmiger Bewegung — sonst entstünde neue Unruhe. */
    @Test
    fun `gleichfoermige Bewegung bleibt exakt linear`() {
        val t = track(0f, 10f, 20f, 30f, 40f)
        for (ms in 0..(4 * 83) step 7) {
            val expected = ms * 10f / 83f
            assertThat(xAt(t, ms.toLong())).isWithin(0.05f).of(expected)
        }
    }

    @Test
    fun `am Spuranfang und -ende bleibt es stabil`() {
        val t = track(0f, 10f, 20f)
        assertThat(xAt(t, -100L)).isWithin(0.01f).of(0f)
        assertThat(xAt(t, 99_999L)).isWithin(0.01f).of(20f)
        // Erstes Intervall ohne linken Nachbarn: linear, kein Ausreißer.
        assertThat(xAt(t, 41L)).isWithin(1f).of(5f)
    }
}
