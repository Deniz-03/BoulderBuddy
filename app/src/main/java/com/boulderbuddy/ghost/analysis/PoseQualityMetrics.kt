package com.boulderbuddy.ghost.analysis

import com.boulderbuddy.ghost.GhostTuning
import com.boulderbuddy.ghost.model.GhostLandmarkTypes
import com.boulderbuddy.ghost.model.GhostPoseTrack
import kotlin.math.hypot

// =============================================================================
// Stufe 0 — Qualitäts-Kennzahlen der Pose-Erkennung (Diagnose-Doc §2, Stufe 0)
// =============================================================================
//
// An diesen drei Zahlen wird jede weitere Stabilisierungs-Stufe gemessen (Baseline
// zuerst mit ML Kit erheben, dann MediaPipe/Filter dagegen vergleichen):
//  - Jitter: MEDIAN der Frame-zu-Frame-Verschiebung sicher erkannter Landmarks in px.
//    Der Median statt Mittelwert approximiert "ruhiger Griff": echte Züge sind selten
//    und landen in den oberen Quantilen, das Grundzittern dominiert die Mitte.
//  - Dropout: Anteil fehlender/unsicherer Landmark-Slots (33 je Frame erwartet).
//  - Flip: Anteil der Frame-Übergänge, bei denen ein Links/Rechts-Paar überkreuzt
//    besser zum Vorgänger passt als gerade — das BlazePose-Vertauschungs-Symptom.

/** Kennzahlen einer kompletten Pose-Spur. Alle Quoten in [0,1]. */
data class PoseQualityMetrics(
    val jitterPx: Double,
    val dropoutRate: Double,
    val flipRate: Double,
    val meanConfidence: Double,
)

/** Dropout + mittlere Confidence innerhalb einer Wiedergabe-Sekunde (Debug-HUD). */
data class PoseSecondStats(
    val dropoutRate: Double,
    val meanConfidence: Double,
)

/** Kennzahlen über die gesamte Spur — einmal pro Track berechnen (remember/cache). */
fun GhostPoseTrack.qualityMetrics(): PoseQualityMetrics {
    var slots = 0L
    var confident = 0L
    var confidenceSum = 0.0
    var confidenceCount = 0L
    val displacements = ArrayList<Double>(frames.size * GhostLandmarkTypes.COUNT)
    var flipTransitions = 0
    var flipHits = 0

    frames.forEachIndexed { index, frame ->
        slots += GhostLandmarkTypes.COUNT
        frame.landmarks.forEach { lm ->
            confidenceSum += lm.confidence
            confidenceCount++
            if (lm.confidence >= GhostTuning.MIN_LANDMARK_CONFIDENCE) confident++
        }
        if (index == 0) return@forEachIndexed
        val prev = frames[index - 1].landmarks
            .filter { it.confidence >= GhostTuning.MIN_LANDMARK_CONFIDENCE }
            .associateBy { it.type }
        val curr = frame.landmarks
            .filter { it.confidence >= GhostTuning.MIN_LANDMARK_CONFIDENCE }
            .associateBy { it.type }
        curr.forEach { (type, lm) ->
            val p = prev[type] ?: return@forEach
            displacements += hypot((lm.x - p.x).toDouble(), (lm.y - p.y).toDouble())
        }
        GhostLandmarkTypes.LEFT_RIGHT_PAIRS.forEach { (leftType, rightType) ->
            val pl = prev[leftType] ?: return@forEach
            val pr = prev[rightType] ?: return@forEach
            val cl = curr[leftType] ?: return@forEach
            val cr = curr[rightType] ?: return@forEach
            fun dist(ax: Float, ay: Float, bx: Float, by: Float) =
                hypot((ax - bx).toDouble(), (ay - by).toDouble())
            val straight = dist(pl.x, pl.y, cl.x, cl.y) + dist(pr.x, pr.y, cr.x, cr.y)
            val crossed = dist(pl.x, pl.y, cr.x, cr.y) + dist(pr.x, pr.y, cl.x, cl.y)
            flipTransitions++
            if (crossed < straight) flipHits++
        }
    }

    displacements.sort()
    return PoseQualityMetrics(
        jitterPx = if (displacements.isEmpty()) 0.0 else displacements[displacements.size / 2],
        dropoutRate = if (slots == 0L) 0.0 else 1.0 - confident.toDouble() / slots,
        flipRate = if (flipTransitions == 0) 0.0 else flipHits.toDouble() / flipTransitions,
        meanConfidence = if (confidenceCount == 0L) 0.0 else confidenceSum / confidenceCount,
    )
}

/** Kennzahlen je Wiedergabe-Sekunde, Schlüssel = Sekunde (timeMs/1000). */
fun GhostPoseTrack.perSecondStats(): Map<Long, PoseSecondStats> =
    frames.groupBy { it.timeMs / 1000 }.mapValues { (_, frames) ->
        val slots = frames.size * GhostLandmarkTypes.COUNT
        val landmarks = frames.flatMap { it.landmarks }
        val confident = landmarks.count { it.confidence >= GhostTuning.MIN_LANDMARK_CONFIDENCE }
        PoseSecondStats(
            dropoutRate = if (slots == 0) 0.0 else 1.0 - confident.toDouble() / slots,
            meanConfidence = if (landmarks.isEmpty()) 0.0 else
                landmarks.sumOf { it.confidence.toDouble() } / landmarks.size,
        )
    }
