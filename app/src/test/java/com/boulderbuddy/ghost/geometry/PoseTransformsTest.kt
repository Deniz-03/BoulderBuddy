package com.boulderbuddy.ghost.geometry

import com.boulderbuddy.ghost.model.GhostLandmark
import com.boulderbuddy.ghost.model.GhostLandmarkTypes as T
import com.boulderbuddy.ghost.model.GhostPoseFrame
import com.boulderbuddy.ghost.model.GhostPoseTrack
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * JVM-Tests der Homographie-Transformation der Pose-Spur. Kernpunkt: die ROH-Spur muss
 * denselben Raumwechsel mitmachen wie die gefilterte, sonst zeichnet das Debug-Overlay
 * die Roh-Keypoints des Geists an falscher Stelle und die HUD-Gegenüberstellung
 * „gefiltert vs. roh" vergleicht zwei verschiedene Koordinatenräume.
 */
class PoseTransformsTest {

    private fun landmark(type: Int, x: Float, y: Float) =
        GhostLandmark(type = type, x = x, y = y, confidence = 0.9f, presence = 0.9f)

    /** Rumpf plus Oberarm, damit die Rekonstruktion überhaupt etwas zu tun hat. */
    private fun frame(timeMs: Long, upperArm: Float) = GhostPoseFrame(
        timeMs = timeMs,
        landmarks = listOf(
            landmark(T.LEFT_SHOULDER, 150f, 200f),
            landmark(T.RIGHT_SHOULDER, 250f, 200f),
            landmark(T.LEFT_HIP, 150f, 300f),
            landmark(T.RIGHT_HIP, 250f, 300f),
            landmark(T.LEFT_ELBOW, 150f, 200f + upperArm),
        ),
    )

    private fun track(frames: List<GhostPoseFrame>) = GhostPoseTrack(
        videoUri = "cmp",
        frameWidth = 480,
        frameHeight = 640,
        durationMs = frames.size * 83L,
        sampleFps = 12.0,
        frames = frames,
        rawFrames = frames,
    )

    private val target = GhostPoseTrack(
        videoUri = "ref",
        frameWidth = 720,
        frameHeight = 1280,
        durationMs = 1000L,
        sampleFps = 12.0,
        frames = emptyList(),
    )

    /** Reine Verschiebung um (+100, +50) — leicht nachrechenbar. */
    private val shift = Homography(
        doubleArrayOf(
            1.0, 0.0, 100.0,
            0.0, 1.0, 50.0,
            0.0, 0.0, 1.0,
        ),
    )

    @Test
    fun `die Roh-Spur wird mittransformiert`() {
        val source = track((0 until 10).map { frame(it * 83L, upperArm = 80f) })
        val result = source.transformedBy(shift, target)

        val raw = checkNotNull(result.rawFrames)[0].landmarks
            .single { it.type == T.LEFT_SHOULDER }
        assertThat(raw.x).isWithin(0.01f).of(250f)
        assertThat(raw.y).isWithin(0.01f).of(250f)
    }

    /**
     * Die Roh-Spur darf NUR transformiert werden. Liefe die Rekonstruktion auch über
     * sie, vergliche das HUD „gefiltert vs. gefiltert" und die Filterwirkung wäre
     * per Konstruktion null.
     */
    @Test
    fun `die Roh-Spur bleibt unkorrigiert`() {
        // Frame 5 hat einen deutlich zu langen Oberarm.
        val source = track(
            (0 until 10).map { frame(it * 83L, upperArm = if (it == 5) 240f else 80f) },
        )
        val result = source.transformedBy(shift, target)

        fun armLength(frames: List<GhostPoseFrame>): Float {
            val f = frames[5]
            val shoulder = f.landmarks.single { it.type == T.LEFT_SHOULDER }
            val elbow = f.landmarks.single { it.type == T.LEFT_ELBOW }
            return elbow.y - shoulder.y
        }

        // Roh: unverändert lang. Gefiltert: auf die Sollgrenze zurückgezogen.
        assertThat(armLength(checkNotNull(result.rawFrames))).isWithin(0.5f).of(240f)
        assertThat(armLength(result.frames)).isLessThan(120f)
    }

    @Test
    fun `beide Spuren liegen im Frame-Raum des Ziels`() {
        val source = track((0 until 10).map { frame(it * 83L, upperArm = 80f) })
        val result = source.transformedBy(shift, target)

        assertThat(result.frameWidth).isEqualTo(target.frameWidth)
        assertThat(result.frameHeight).isEqualTo(target.frameHeight)
        // Gleiche Verschiebung in beiden Spuren — sonst driften sie im Overlay auseinander.
        val filtered = result.frames[0].landmarks.single { it.type == T.LEFT_SHOULDER }
        val raw = checkNotNull(result.rawFrames)[0].landmarks
            .single { it.type == T.LEFT_SHOULDER }
        assertThat(filtered.x).isWithin(0.01f).of(raw.x)
        assertThat(filtered.y).isWithin(0.01f).of(raw.y)
    }

    @Test
    fun `fehlende Roh-Spur bleibt null`() {
        val source = track((0 until 10).map { frame(it * 83L, upperArm = 80f) })
            .copy(rawFrames = null)
        assertThat(source.transformedBy(shift, target).rawFrames).isNull()
    }
}
