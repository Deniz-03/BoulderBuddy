package com.boulderbuddy.ghost.pose

import com.boulderbuddy.ghost.model.GhostLandmark
import com.boulderbuddy.ghost.model.GhostLandmarkTypes as T
import com.boulderbuddy.ghost.model.GhostPoseFrame
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Stufe 2 (7.5b): Links/Rechts-Konsistenz + anatomische Plausibilität. */
class PosePlausibilityTest {

    private fun landmark(type: Int, x: Float, y: Float) =
        GhostLandmark(type = type, x = x, y = y, confidence = 0.9f)

    /** Einfache Standard-Pose: linkes Handgelenk links (x=100), rechtes rechts (x=300). */
    private fun pose(leftX: Float = 100f, rightX: Float = 300f) = listOf(
        landmark(T.LEFT_WRIST, leftX, 100f),
        landmark(T.RIGHT_WRIST, rightX, 100f),
        landmark(T.LEFT_SHOULDER, leftX + 40f, 200f),
        landmark(T.RIGHT_SHOULDER, rightX - 40f, 200f),
    )

    // --- Links/Rechts-Konsistenz ----------------------------------------------

    @Test
    fun `ganzkoerper-flip wird zurueckgetauscht`() {
        // Frame 2 hat links/rechts vertauscht (BlazePose-Flip): das "linke" Handgelenk
        // liegt ploetzlich bei x=300. Überkreuzt passt es klar besser auf Frame 1.
        val flipped = listOf(
            landmark(T.LEFT_WRIST, 300f, 100f),
            landmark(T.RIGHT_WRIST, 100f, 100f),
            landmark(T.LEFT_SHOULDER, 260f, 200f),
            landmark(T.RIGHT_SHOULDER, 140f, 200f),
        )
        val result = enforceLeftRightConsistency(
            listOf(
                GhostPoseFrame(0L, pose()),
                GhostPoseFrame(83L, flipped),
            ),
        )
        val corrected = result[1].landmarks.associateBy { it.type }
        assertThat(corrected.getValue(T.LEFT_WRIST).x).isEqualTo(100f)
        assertThat(corrected.getValue(T.RIGHT_WRIST).x).isEqualTo(300f)
    }

    @Test
    fun `konsistente frames bleiben unveraendert`() {
        val frames = listOf(
            GhostPoseFrame(0L, pose()),
            GhostPoseFrame(83L, pose(leftX = 110f, rightX = 310f)),
        )
        val result = enforceLeftRightConsistency(frames)
        assertThat(result).isEqualTo(frames)
    }

    // --- Anatomische Plausibilität --------------------------------------------

    @Test
    fun `knochenlaengen-ausreisser verliert das distale gelenk`() {
        // Oberarm (Schulter→Ellbogen) ist in 9 Frames ~100 px lang, in einem 300 px —
        // der halluzinierte Ellbogen fliegt raus, die Schulter bleibt.
        val frames = (0 until 10).map { i ->
            val elbowY = if (i == 5) 400f else 200f
            GhostPoseFrame(
                timeMs = i * 83L,
                landmarks = listOf(
                    landmark(T.LEFT_SHOULDER, 100f, 100f),
                    landmark(T.LEFT_ELBOW, 100f, elbowY),
                ),
            )
        }
        val result = applyAnatomicalPlausibility(frames, frameHeight = 720)
        assertThat(result[5].landmarks.map { it.type }).containsExactly(T.LEFT_SHOULDER)
        assertThat(result[4].landmarks).hasSize(2)
        assertThat(result[6].landmarks).hasSize(2)
    }

    @Test
    fun `teleport-sprung wird verworfen`() {
        // Handgelenk springt in 83 ms um 700 px (~8400 px/s bei 720er-Frame ≈ 11,7
        // Frame-Höhen/s > Limit 2,5) — physisch unmöglich.
        val frames = listOf(
            GhostPoseFrame(0L, listOf(landmark(T.LEFT_WRIST, 100f, 600f))),
            GhostPoseFrame(83L, listOf(landmark(T.LEFT_WRIST, 100f, 600f))),
            GhostPoseFrame(166L, listOf(landmark(T.LEFT_WRIST, 700f, 200f))),
        )
        val result = applyAnatomicalPlausibility(frames, frameHeight = 720)
        assertThat(result[2].landmarks).isEmpty()
    }

    @Test
    fun `normale bewegung passiert den geschwindigkeits-check`() {
        // 20 px pro 83-ms-Frame ≈ 0,33 Frame-Höhen/s — weit unter dem Limit.
        val frames = (0 until 5).map { i ->
            GhostPoseFrame(i * 83L, listOf(landmark(T.LEFT_WRIST, 100f + i * 20f, 300f)))
        }
        val result = applyAnatomicalPlausibility(frames, frameHeight = 720)
        result.forEach { assertThat(it.landmarks).hasSize(1) }
    }
}
