package com.boulderbuddy.ghost.pose

import com.boulderbuddy.ghost.model.GhostLandmark
import com.boulderbuddy.ghost.model.GhostLandmarkTypes as T
import com.boulderbuddy.ghost.model.GhostPoseFrame
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.hypot

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
    fun `knochenlaengen-ausreisser ohne messbaren rumpf faellt auf die alt-toleranz zurueck`() {
        // Ohne Rumpf-Landmarks ist keine Körpergröße messbar — enforceRigidSkeleton
        // fällt dann auf den Median der ABSOLUTEN Länge mit
        // BONE_LENGTH_TOLERANCE_FACTOR zurück: Median 100 px · 1,5 = 150 → y = 250.
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
        val result = enforceRigidSkeleton(frames)
        assertThat(result[5].landmarks).hasSize(2)
        val elbow = result[5].landmarks.single { it.type == T.LEFT_ELBOW }
        assertThat(elbow.y).isWithin(0.5f).of(250f)
        assertThat(result[4].landmarks).hasSize(2)
        assertThat(result[6].landmarks).hasSize(2)
    }

    @Test
    fun `teleport-sprung wird auf das geschwindigkeitslimit geklemmt`() {
        // Handgelenk springt in 83 ms um ~721 px (weit über Limit 2,5 Frame-Höhen/s ·
        // 720 px · 0,083 s = 149,4 px) — nicht verworfen, sondern auf die maximal
        // mögliche Distanz zum Vorframe geklemmt (Richtung beibehalten).
        val frames = listOf(
            GhostPoseFrame(0L, listOf(landmark(T.LEFT_WRIST, 100f, 600f))),
            GhostPoseFrame(83L, listOf(landmark(T.LEFT_WRIST, 100f, 600f))),
            GhostPoseFrame(166L, listOf(landmark(T.LEFT_WRIST, 700f, 200f))),
        )
        val result = applyAnatomicalPlausibility(frames, frameHeight = 720)
        val wrist = result[2].landmarks.single()
        val step = hypot((wrist.x - 100f).toDouble(), (wrist.y - 600f).toDouble())
        assertThat(step).isWithin(0.5).of(2.5 * 720 * 0.083)
        assertThat(wrist.x).isLessThan(700f)
    }

    // --- Pose-Skalen-Konsistenz -----------------------------------------------

    @Test
    fun `kollabierte pose wird per interpolation auf plausible groesse ersetzt`() {
        // Rumpf-Landmarks: Schultern bei y, Hüften bei y+torso (Rumpfgröße = torso).
        fun frame(timeMs: Long, y: Float, torso: Float) = GhostPoseFrame(
            timeMs = timeMs,
            landmarks = listOf(
                landmark(T.LEFT_SHOULDER, 80f, y),
                landmark(T.RIGHT_SHOULDER, 120f, y),
                landmark(T.LEFT_HIP, 80f, y + torso),
                landmark(T.RIGHT_HIP, 120f, y + torso),
            ),
        )
        // 4 normale Frames (Rumpf 100), Frame 2 kollabiert (Rumpf 10, irgendwo weit weg).
        val frames = listOf(
            frame(0L, 0f, 100f),
            frame(83L, 100f, 100f),
            frame(166L, 999f, 10f),
            frame(249L, 300f, 100f),
            frame(332L, 400f, 100f),
        )
        val result = enforcePoseConsistency(frames)
        val shoulder = result[2].landmarks.single { it.type == T.LEFT_SHOULDER }
        val hip = result[2].landmarks.single { it.type == T.LEFT_HIP }
        // Rumpf wieder ~100 statt 10 (interpoliert zwischen Frame 1 und 3).
        assertThat(hip.y - shoulder.y).isWithin(0.5f).of(100f)
        // Position bei t=0.5 zwischen y=100 (Frame 1) und y=300 (Frame 3) → y≈200.
        assertThat(shoulder.y).isWithin(0.5f).of(200f)
    }

    @Test
    fun `normale groessenschwankung bleibt unveraendert`() {
        fun frame(timeMs: Long, torso: Float) = GhostPoseFrame(
            timeMs = timeMs,
            landmarks = listOf(
                landmark(T.LEFT_SHOULDER, 80f, 0f),
                landmark(T.RIGHT_SHOULDER, 120f, 0f),
                landmark(T.LEFT_HIP, 80f, torso),
                landmark(T.RIGHT_HIP, 120f, torso),
            ),
        )
        // ±15 % Schwankung — innerhalb der Toleranz, nichts wird ersetzt.
        val frames = listOf(
            frame(0L, 100f), frame(83L, 110f), frame(166L, 90f),
            frame(249L, 105f), frame(332L, 95f),
        )
        assertThat(enforcePoseConsistency(frames)).isEqualTo(frames)
    }

    @Test
    fun `isolierter positionssprung wird korrigiert`() {
        // Ganze Pose bewegt sich langsam; Frame 2 springt als Ganzes weit weg (Größe/Form
        // normal) → nur das Positions-Gate greift, der Frame wird interpoliert.
        fun frame(timeMs: Long, cx: Float) = GhostPoseFrame(
            timeMs = timeMs,
            landmarks = listOf(
                landmark(T.LEFT_SHOULDER, cx - 20f, 100f),
                landmark(T.RIGHT_SHOULDER, cx + 20f, 100f),
                landmark(T.LEFT_HIP, cx - 20f, 200f),
                landmark(T.RIGHT_HIP, cx + 20f, 200f),
            ),
        )
        val frames = listOf(
            frame(0L, 100f), frame(83L, 110f), frame(166L, 400f),
            frame(249L, 130f), frame(332L, 140f),
        )
        val result = enforcePoseConsistency(frames)
        // Frame 2 zurück auf die Interpolation zwischen Frame 1 (cx=110) und Frame 3
        // (cx=130) → cx≈120, linke Schulter bei cx−20 = 100.
        assertThat(result[2].landmarks.single { it.type == T.LEFT_SHOULDER }.x)
            .isWithin(1f).of(100f)
        // Unverschobene Nachbarn bleiben unangetastet.
        assertThat(result[1].landmarks.single { it.type == T.LEFT_SHOULDER }.x).isEqualTo(90f)
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
