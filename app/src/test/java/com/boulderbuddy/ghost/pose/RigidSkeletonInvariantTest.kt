package com.boulderbuddy.ghost.pose

import com.boulderbuddy.ghost.analysis.qualityMetrics
import com.boulderbuddy.ghost.model.GhostLandmark
import com.boulderbuddy.ghost.model.GhostLandmarkTypes as T
import com.boulderbuddy.ghost.model.GhostPoseFrame
import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import org.junit.Test

/**
 * Invarianten-Test (7.5e): nach [enforceRigidSkeleton] darf KEIN Knochen mehr über
 * seiner Sollgrenze liegen — die Überlängen-Quote muss also praktisch 0 sein. Auf dem
 * Gerät wurde 26,5 % gemessen; dieser Test klärt, ob die Rekonstruktion nicht greift
 * oder die Kennzahl etwas anderes misst als gedacht.
 */
class RigidSkeletonInvariantTest {

    private fun landmark(type: Int, x: Float, y: Float) =
        GhostLandmark(type = type, x = x, y = y, confidence = 0.9f, presence = 0.9f)

    /** Spur mit realistischer Streuung: Körpergröße driftet, Knochenlängen rauschen. */
    private fun noisyTrack(count: Int = 200): List<GhostPoseFrame> {
        val random = Random(42)
        return (0 until count).map { i ->
            val scale = 100f - i * 0.1f
            val half = scale / 2
            // Oberarm streut um 0,8 Körpergrößen mit ~20 % Rauschen.
            val upperArm = scale * 0.8f * (1f + (random.nextFloat() - 0.5f) * 0.4f)
            val foreArm = scale * 0.8f * (1f + (random.nextFloat() - 0.5f) * 0.4f)
            GhostPoseFrame(
                timeMs = i * 83L,
                landmarks = listOf(
                    landmark(T.LEFT_SHOULDER, 200f - half, 200f),
                    landmark(T.RIGHT_SHOULDER, 200f + half, 200f),
                    landmark(T.LEFT_HIP, 200f - half, 200f + scale),
                    landmark(T.RIGHT_HIP, 200f + half, 200f + scale),
                    landmark(T.LEFT_ELBOW, 200f - half, 200f + upperArm),
                    landmark(T.LEFT_WRIST, 200f - half, 200f + upperArm + foreArm),
                ),
            )
        }
    }

    @Test
    fun `nach der Rekonstruktion liegt kein Knochen mehr ueber der Sollgrenze`() {
        val before = noisyTrack().qualityMetrics()
        val after = enforceRigidSkeleton(noisyTrack()).qualityMetrics()

        // Ausgangslage: die Streuung erzeugt eine deutliche Überlängen-Quote.
        assertThat(before.boneOverExtensionRate).isGreaterThan(0.2)
        // Und danach darf davon nichts übrig sein.
        assertThat(after.boneOverExtensionRate).isLessThan(0.005)
    }

    /** Mehr Durchläufe dürfen das Ergebnis nicht verschlechtern (Konvergenz). */
    @Test
    fun `zusaetzliche Durchlaeufe verschlechtern nichts`() {
        val single = enforceRigidSkeleton(noisyTrack(), iterations = 1).qualityMetrics()
        val converged = enforceRigidSkeleton(noisyTrack()).qualityMetrics()

        assertThat(converged.boneOverExtensionRate)
            .isAtMost(single.boneOverExtensionRate)
    }
}
