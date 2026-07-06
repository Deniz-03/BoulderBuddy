package com.boulderbuddy.ghost.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Interpolation + Blink-Fix in [landmarksAt] (Stufe 1.3, 7.5b). */
class GhostPoseTest {

    private fun track(vararg frames: GhostPoseFrame) = GhostPoseTrack(
        videoUri = "test",
        frameWidth = 405,
        frameHeight = 720,
        durationMs = 1000L,
        sampleFps = 12.0,
        frames = frames.toList(),
    )

    private fun landmark(type: Int, x: Float, confidence: Float = 0.9f) =
        GhostLandmark(type = type, x = x, y = 50f, confidence = confidence)

    @Test
    fun `interpoliert linear zwischen nachbar-frames`() {
        val track = track(
            GhostPoseFrame(0L, listOf(landmark(15, x = 100f))),
            GhostPoseFrame(100L, listOf(landmark(15, x = 200f))),
        )
        val result = track.landmarksAt(50L)
        assertThat(result.single().x).isEqualTo(150f)
    }

    @Test
    fun `haelt landmark bei einseitigem aussetzer statt zu blinken`() {
        // Landmark 15 fehlt nur im rechten Nachbarn — vor dem Fix verschwand es
        // fürs ganze Intervall (Root Cause B "Blink-Bug").
        val track = track(
            GhostPoseFrame(0L, listOf(landmark(15, x = 100f), landmark(16, x = 300f))),
            GhostPoseFrame(100L, listOf(landmark(16, x = 320f))),
        )
        val result = track.landmarksAt(50L)
        assertThat(result.map { it.type }).containsExactly(15, 16)
        assertThat(result.single { it.type == 15 }.x).isEqualTo(100f)
        assertThat(result.single { it.type == 16 }.x).isEqualTo(310f)
    }

    @Test
    fun `landmark taucht frueh auf wenn nur im rechten nachbarn vorhanden`() {
        val track = track(
            GhostPoseFrame(0L, emptyList()),
            GhostPoseFrame(100L, listOf(landmark(15, x = 200f))),
        )
        val result = track.landmarksAt(50L)
        assertThat(result.single().x).isEqualTo(200f)
    }

    @Test
    fun `echte luecke bleibt leer`() {
        val track = track(
            GhostPoseFrame(0L, emptyList()),
            GhostPoseFrame(100L, emptyList()),
        )
        assertThat(track.landmarksAt(50L)).isEmpty()
    }
}
