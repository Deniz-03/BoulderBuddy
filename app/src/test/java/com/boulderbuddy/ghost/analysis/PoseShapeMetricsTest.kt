package com.boulderbuddy.ghost.analysis

import com.boulderbuddy.ghost.model.GhostLandmark
import com.boulderbuddy.ghost.model.GhostLandmarkTypes
import com.boulderbuddy.ghost.model.GhostPoseFrame
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * JVM-Tests der Form-Kennzahlen (A7, 7.5e). Sie sind das Messinstrument für „morpht
 * es noch?" — wenn sie selbst falsch messen, tunt man danach im Nebel.
 */
class PoseShapeMetricsTest {

    private fun landmark(type: Int, x: Float, y: Float) =
        GhostLandmark(type = type, x = x, y = y, confidence = 0.9f, presence = 0.9f)

    /**
     * Starre Pose der Größe [scale] um ([cx], [cy]): Rumpf plus je ein Ober- und
     * Unterarm/-bein mit festen Verhältnissen zur Körpergröße.
     */
    private fun rigidPose(
        cx: Float,
        cy: Float,
        scale: Float,
        upperArmRatio: Float = 0.8f,
    ): GhostPoseFrame {
        val half = scale / 2
        return GhostPoseFrame(
            timeMs = 0L,
            landmarks = listOf(
                landmark(GhostLandmarkTypes.LEFT_SHOULDER, cx - half, cy - half),
                landmark(GhostLandmarkTypes.RIGHT_SHOULDER, cx + half, cy - half),
                landmark(GhostLandmarkTypes.LEFT_HIP, cx - half, cy + half),
                landmark(GhostLandmarkTypes.RIGHT_HIP, cx + half, cy + half),
                landmark(GhostLandmarkTypes.LEFT_ELBOW, cx - half, cy - half + scale * upperArmRatio),
                landmark(GhostLandmarkTypes.RIGHT_ELBOW, cx + half, cy - half + scale * upperArmRatio),
                landmark(GhostLandmarkTypes.LEFT_KNEE, cx - half, cy + half + scale),
                landmark(GhostLandmarkTypes.RIGHT_KNEE, cx + half, cy + half + scale),
            ),
        )
    }

    /**
     * Der Kern der Normierung: dieselbe Pose in wechselnder GRÖSSE (Kletterer entfernt
     * sich von der Kamera) ist kein Morphen. Absolute Pixellängen würden hier stark
     * streuen — das Verhältnis zur Körpergröße nicht.
     */
    @Test
    fun `Global skalierte Pose zaehlt nicht als Morphen`() {
        val frames = (0 until 20).map { i ->
            rigidPose(cx = 360f, cy = 640f, scale = 100f - i * 2f).copy(timeMs = i * 83L)
        }
        val metrics = frames.qualityMetrics()

        assertThat(metrics.boneLengthCv).isLessThan(0.01)
        // Die Körpergröße selbst schwankt hier real — das misst scaleCv, und genau
        // diese Trennung ist der Zweck der beiden Kennzahlen.
        assertThat(metrics.scaleCv).isGreaterThan(0.05)
    }

    @Test
    fun `Wechselnde Knochenlaenge wird als Morphen erkannt`() {
        val frames = (0 until 20).map { i ->
            // Oberarm pendelt zwischen 0,5 und 1,1 Körpergrößen — anatomisch unmöglich.
            val ratio = if (i % 2 == 0) 0.5f else 1.1f
            rigidPose(cx = 360f, cy = 640f, scale = 100f, upperArmRatio = ratio)
                .copy(timeMs = i * 83L)
        }
        assertThat(frames.qualityMetrics().boneLengthCv).isGreaterThan(0.1)
    }

    @Test
    fun `Ganzkoerper-Kollaps schlaegt auf die Kollaps-Metrik durch`() {
        val stable = (0 until 20).map { rigidPose(360f, 640f, 100f).copy(timeMs = it * 83L) }
        val collapsing = stable.mapIndexed { i, frame ->
            if (i == 10) rigidPose(360f, 640f, 30f).copy(timeMs = i * 83L) else frame
        }

        assertThat(stable.qualityMetrics().scaleCv).isLessThan(0.01)
        assertThat(collapsing.qualityMetrics().scaleCv)
            .isGreaterThan(stable.qualityMetrics().scaleCv)
    }

    @Test
    fun `Leere Spur liefert neutrale Kennzahlen statt NaN`() {
        val metrics = List(5) { GhostPoseFrame(it * 83L, emptyList()) }.qualityMetrics()
        assertThat(metrics.boneLengthCv).isEqualTo(0.0)
        assertThat(metrics.scaleCv).isEqualTo(0.0)
    }
}
