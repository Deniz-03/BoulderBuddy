package com.boulderbuddy.ghost.pose

import com.boulderbuddy.ghost.model.GhostLandmark
import com.boulderbuddy.ghost.model.GhostPoseFrame
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/**
 * One-Euro-Glättung (Stufe 1, 7.5b): reduziert Zittern bei ruhender Position,
 * folgt schneller Bewegung ohne großen Lag und startet nach langen Lücken neu.
 */
class PoseSmoothingTest {

    private fun frames(
        positions: List<Float?>,
        stepMs: Long = 83L, // ~12 fps
        type: Int = 15,
    ): List<GhostPoseFrame> = positions.mapIndexed { index, x ->
        GhostPoseFrame(
            timeMs = index * stepMs,
            landmarks = if (x == null) {
                emptyList()
            } else {
                listOf(GhostLandmark(type = type, x = x, y = 100f, confidence = 0.9f))
            },
        )
    }

    @Test
    fun `daempft rauschen um ruhende position`() {
        // Ruhender Griff bei x=200 mit +-3 px Sensor-Zittern (deterministisch).
        val noisy = List(60) { i -> 200f + 3f * sin(i * 2.7f) }
        val smoothed = smoothPoseFrames(frames(noisy))

        val rawDeviation = noisy.drop(10).map { abs(it - 200f) }.average()
        val filteredDeviation = smoothed.drop(10)
            .map { abs(it.landmarks.single().x - 200f).toDouble() }
            .average()
        assertThat(filteredDeviation).isLessThan(rawDeviation * 0.5)
    }

    @Test
    fun `folgt schneller bewegung ohne grossen lag`() {
        // Schneller Zug: 20 px pro Frame (~240 px/s bei 12 fps).
        val moving = List(30) { i -> 100f + i * 20f }
        val smoothed = smoothPoseFrames(frames(moving))

        val lag = abs(smoothed.last().landmarks.single().x - moving.last())
        // Lag deutlich unter einer Frame-Bewegung — der Filter "klebt" nicht.
        assertThat(lag).isLessThan(20f)
    }

    @Test
    fun `filter startet nach langer luecke neu statt nachzuziehen`() {
        // 10 Frames bei x=100, dann 5 Frames Lücke (> FILTER_RESET_GAP_FRAMES),
        // dann taucht das Landmark bei x=500 wieder auf.
        val positions = List(10) { 100f } + List(5) { null } + List(3) { 500f }
        val smoothed = smoothPoseFrames(frames(positions))

        val reappeared = smoothed[15].landmarks.single()
        // Neustart: exakt die neue Position, kein Schleifen von x=100 herüber.
        assertThat(reappeared.x).isEqualTo(500f)
    }

    @Test
    fun `confidence und presence bleiben unveraendert`() {
        val input = frames(List(5) { 100f })
        val smoothed = smoothPoseFrames(input)
        smoothed.forEach { frame ->
            assertThat(frame.landmarks.single().confidence).isEqualTo(0.9f)
            assertThat(frame.landmarks.single().presence).isEqualTo(1f)
        }
    }
}
