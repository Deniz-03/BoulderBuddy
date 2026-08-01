package com.boulderbuddy.ghost.pose

import com.boulderbuddy.ghost.GhostTuning
import com.boulderbuddy.ghost.model.GhostLandmark
import com.boulderbuddy.ghost.model.GhostLandmarkTypes
import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import org.junit.Test

/**
 * JVM-Tests der ROI-Box (A1, 7.5e). Die Box entscheidet, was das Modell überhaupt zu
 * sehen bekommt — fällt ihre Rückkopplungs-Bremse still zurück, kollabieren die
 * Skelette wieder. Deshalb sind die Bremsen hier einzeln festgenagelt.
 */
class RoiTrackingTest {

    private val frameWidth = 720
    private val frameHeight = 1280

    private fun landmark(type: Int, x: Float, y: Float) =
        GhostLandmark(type = type, x = x, y = y, confidence = 0.9f, presence = 0.9f)

    /** Nur die vier Rumpfpunkte um ([cx], [cy]) mit Schulterbreite [scale]. */
    private fun torsoOnly(cx: Float, cy: Float, scale: Float): List<GhostLandmark> = listOf(
        landmark(GhostLandmarkTypes.LEFT_SHOULDER, cx - scale / 2, cy - scale / 2),
        landmark(GhostLandmarkTypes.RIGHT_SHOULDER, cx + scale / 2, cy - scale / 2),
        landmark(GhostLandmarkTypes.LEFT_HIP, cx - scale / 2, cy + scale / 2),
        landmark(GhostLandmarkTypes.RIGHT_HIP, cx + scale / 2, cy + scale / 2),
        // Zwei weitere Punkte, damit ROI_MIN_CONFIDENT_LANDMARKS (6) erreicht ist.
        landmark(GhostLandmarkTypes.LEFT_ELBOW, cx - scale, cy),
        landmark(GhostLandmarkTypes.RIGHT_ELBOW, cx + scale, cy),
    )

    /**
     * Der Kern von A1: werden nur die Rumpfpunkte erkannt, darf die Box trotzdem NICHT
     * auf den Rumpf zusammenfallen — sonst schneidet der nächste Crop die Gliedmaßen ab
     * und die Rückkopplung läuft los.
     */
    @Test
    fun `Box um einen erkannten Rumpf umfasst trotzdem eine ganze Person`() {
        val scale = 60f
        val step = nextRoi(null, torsoOnly(360f, 640f, scale), frameWidth, frameHeight)
        val roi = checkNotNull(step.roi)

        assertThat(step.outcome).isEqualTo(RoiOutcome.FRESH)
        // Mindestens das Körpergrößen-Vielfache auf beiden Seiten …
        val minSide = scale * GhostTuning.ROI_MIN_BODY_MULTIPLE
        assertThat(roi.width).isAtLeast(minSide)
        assertThat(roi.height).isAtLeast(minSide)
        // … und deutlich größer als die reine Rumpf-Bounding-Box (2·scale breit).
        assertThat(roi.width).isGreaterThan(scale * 2f)
    }

    @Test
    fun `Box hat immer das Seitenverhaeltnis des Frames`() {
        val roi = checkNotNull(
            nextRoi(null, torsoOnly(360f, 640f, 60f), frameWidth, frameHeight).roi,
        )
        val frameAspect = frameWidth.toFloat() / frameHeight
        assertThat(abs(roi.width / roi.height - frameAspect)).isLessThan(1e-3f)
    }

    @Test
    fun `Box bleibt vollstaendig im Frame - auch am Rand`() {
        listOf(0f to 0f, 719f to 1279f, 0f to 1279f).forEach { (cx, cy) ->
            val roi = checkNotNull(
                nextRoi(null, torsoOnly(cx, cy, 60f), frameWidth, frameHeight).roi,
            )
            assertThat(roi.left).isAtLeast(0f)
            assertThat(roi.top).isAtLeast(0f)
            assertThat(roi.right).isAtMost(frameWidth.toFloat())
            assertThat(roi.bottom).isAtMost(frameHeight.toFloat())
        }
    }

    // --- Geweitete Prüf-Box (S7b) ----------------------------------------------

    /**
     * Die Prüf-Box ersetzt den früheren Vollbild-Reset. Sie muss deshalb zweierlei
     * leisten: deutlich mehr Umgebung zeigen als die laufende Box (sonst findet sie eine
     * verrutschte Person nie), und dabei dieselbe Geometrie behalten (sonst ist sie genau
     * der Maßstabssprung, dessen Beseitigung ihr ganzer Zweck ist).
     */
    @Test
    fun `Pruef-Box ist groesser und behaelt die Frame-Geometrie`() {
        val roi = checkNotNull(
            nextRoi(null, torsoOnly(360f, 640f, 60f), frameWidth, frameHeight).roi,
        )
        val wide = roi.widened(2f, frameWidth, frameHeight)

        assertThat(wide.width).isGreaterThan(roi.width)
        assertThat(wide.centerX).isWithin(1e-3f).of(roi.centerX)
        assertThat(wide.centerY).isWithin(1e-3f).of(roi.centerY)
        val frameAspect = frameWidth.toFloat() / frameHeight
        assertThat(abs(wide.width / wide.height - frameAspect)).isLessThan(1e-3f)
    }

    @Test
    fun `Pruef-Box bleibt im Frame und wird hoechstens das Vollbild`() {
        // Box am Rand und mit einem Faktor, der jeden Rahmen sprengen würde.
        val roi = checkNotNull(
            nextRoi(null, torsoOnly(40f, 1240f, 100f), frameWidth, frameHeight).roi,
        )
        val wide = roi.widened(20f, frameWidth, frameHeight)

        assertThat(wide.left).isAtLeast(0f)
        assertThat(wide.top).isAtLeast(0f)
        assertThat(wide.right).isAtMost(frameWidth.toFloat())
        assertThat(wide.bottom).isAtMost(frameHeight.toFloat())
    }

    /** Die Kollaps-Bremse: eine plötzlich einbrechende Box wird verworfen. */
    @Test
    fun `Einbrechende Box wird verworfen und die alte behalten`() {
        val large = checkNotNull(
            nextRoi(null, torsoOnly(360f, 640f, 120f), frameWidth, frameHeight).roi,
        )
        // Nächster Frame: die Pose ist auf ein Drittel kollabiert.
        val step = nextRoi(large, torsoOnly(360f, 640f, 40f), frameWidth, frameHeight)

        assertThat(step.outcome).isEqualTo(RoiOutcome.REJECTED)
        assertThat(step.roi).isEqualTo(large)
    }

    /** Die Sprung-Bremse: eine Box, die auf etwas anderes springt, wird verworfen. */
    @Test
    fun `Wegspringende Box wird verworfen`() {
        val scale = 60f
        val first = checkNotNull(
            nextRoi(null, torsoOnly(200f, 300f, scale), frameWidth, frameHeight).roi,
        )
        val jump = scale * GhostTuning.ROI_MAX_CENTER_JUMP_BODY_FRACTION * 3f
        val step = nextRoi(first, torsoOnly(200f, 300f + jump, scale), frameWidth, frameHeight)

        assertThat(step.outcome).isEqualTo(RoiOutcome.REJECTED)
        assertThat(step.roi).isEqualTo(first)
    }

    /** Eine normale Bewegung darf NICHT an den Bremsen hängenbleiben. */
    @Test
    fun `Normale Bewegung wird uebernommen und geglaettet`() {
        val scale = 60f
        val first = checkNotNull(
            nextRoi(null, torsoOnly(360f, 640f, scale), frameWidth, frameHeight).roi,
        )
        val step = nextRoi(first, torsoOnly(370f, 620f, scale), frameWidth, frameHeight)

        assertThat(step.outcome).isEqualTo(RoiOutcome.SMOOTHED)
        // Geglättet: die neue Box liegt zwischen alter und gemessener Position.
        assertThat(checkNotNull(step.roi).centerY).isLessThan(first.centerY)
    }

    /**
     * Präsenz statt nur visibility: MediaPipe meldet auch für erfundene Positionen hohe
     * visibility. Landmarks ohne Präsenz dürfen die Box nicht aufspannen.
     */
    @Test
    fun `Landmarks ohne Praesenz spannen keine Box auf`() {
        val hallucinated = torsoOnly(360f, 640f, 60f).map { it.copy(presence = 0.1f) }
        val step = nextRoi(null, hallucinated, frameWidth, frameHeight)

        assertThat(step.outcome).isEqualTo(RoiOutcome.LOST)
        assertThat(step.roi).isNull()
    }

    @Test
    fun `Zu wenige Landmarks bedeuten Person verloren`() {
        val step = nextRoi(
            null,
            torsoOnly(360f, 640f, 60f).take(GhostTuning.ROI_MIN_CONFIDENT_LANDMARKS - 1),
            frameWidth,
            frameHeight,
        )
        assertThat(step.outcome).isEqualTo(RoiOutcome.LOST)
        assertThat(step.roi).isNull()
    }

    /**
     * Regressionstest gegen die Rückkopplung als GANZES: wiederholt man den Schritt mit
     * immer nur dem Rumpf, darf die Box nicht von Frame zu Frame weiter schrumpfen.
     */
    @Test
    fun `Box schrumpft ueber viele Frames nicht weiter`() {
        var roi = nextRoi(null, torsoOnly(360f, 640f, 60f), frameWidth, frameHeight).roi
        val firstArea = checkNotNull(roi).area
        repeat(50) {
            roi = nextRoi(roi, torsoOnly(360f, 640f, 60f), frameWidth, frameHeight).roi
        }
        assertThat(checkNotNull(roi).area).isWithin(1f).of(firstArea)
    }
}
