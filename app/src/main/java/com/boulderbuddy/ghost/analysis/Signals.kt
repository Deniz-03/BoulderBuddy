package com.boulderbuddy.ghost.analysis

import com.boulderbuddy.ghost.GhostTuning
import com.boulderbuddy.ghost.model.GhostPoint
import com.boulderbuddy.ghost.model.GhostPoseFrame
import com.boulderbuddy.ghost.model.GhostLandmarkTypes
import com.boulderbuddy.ghost.model.GhostPoseTrack
import kotlin.math.ceil
import kotlin.math.exp

// =============================================================================
// Ghost Climber — Signalverarbeitung (M3): Hüfttrajektorie, Lücken, Glättung
// =============================================================================
//
// Die Hüftmitte ist der robusteste Trackingpunkt (P8: Gliedmaßen werden verdeckt,
// die Hüfte selten). Rohsignale werden VOR jeder Analyse Gauss-geglättet (P8).

/**
 * Hüftmitte des Frames oder null, wenn beide Hüft-Landmarks unter der
 * Konfidenz-Schwelle liegen ("Pose verloren").
 */
fun GhostPoseFrame.hipCenterOrNull(
    minConfidence: Float = GhostTuning.MIN_LANDMARK_CONFIDENCE,
): GhostPoint? {
    val left = landmarks.firstOrNull {
        it.type == GhostLandmarkTypes.LEFT_HIP && it.confidence >= minConfidence
    }
    val right = landmarks.firstOrNull {
        it.type == GhostLandmarkTypes.RIGHT_HIP && it.confidence >= minConfidence
    }
    return when {
        left != null && right != null ->
            GhostPoint((left.x + right.x) / 2f, (left.y + right.y) / 2f)
        left != null -> GhostPoint(left.x, left.y)
        right != null -> GhostPoint(right.x, right.y)
        else -> null
    }
}

/**
 * Geglättete, lückenlose Hüfttrajektorie der Spur — Basis für Pfad-Vorschlag (P3)
 * und Fortschrittssignal (P2). Gibt null zurück, wenn die Pose nie sicher erkannt
 * wurde (dann ist keine Analyse möglich).
 */
fun GhostPoseTrack.smoothedHipTrajectory(
    sigma: Double = GhostTuning.SMOOTHING_SIGMA_FRAMES,
): List<GhostPoint>? {
    val raw = frames.map { it.hipCenterOrNull() }
    val filled = fillGaps(raw) ?: return null
    val xs = gaussianSmooth(DoubleArray(filled.size) { filled[it].x.toDouble() }, sigma)
    val ys = gaussianSmooth(DoubleArray(filled.size) { filled[it].y.toDouble() }, sigma)
    return List(filled.size) { GhostPoint(xs[it].toFloat(), ys[it].toFloat()) }
}

/**
 * Schließt Lücken (Pose verloren) per Vorwärts-/Rückwärts-Fill — Positionshalten
 * ist ehrlicher als Interpolation über lange Aussetzer hinweg. null = alles leer.
 */
internal fun fillGaps(points: List<GhostPoint?>): List<GhostPoint>? {
    if (points.none { it != null }) return null
    val result = arrayOfNulls<GhostPoint>(points.size)
    var last: GhostPoint? = null
    for (i in points.indices) {
        last = points[i] ?: last
        result[i] = last
    }
    var next: GhostPoint? = null
    for (i in points.indices.reversed()) {
        next = result[i] ?: next
        result[i] = next
    }
    @Suppress("UNCHECKED_CAST")
    return (result as Array<GhostPoint>).toList()
}

/** Gauss-Glättung mit auf 3σ gestutztem Kernel; Ränder werden auf gültige Indizes geklemmt. */
internal fun gaussianSmooth(values: DoubleArray, sigma: Double): DoubleArray {
    if (sigma <= 0 || values.size < 3) return values.clone()
    val radius = ceil(3 * sigma).toInt()
    val kernel = DoubleArray(2 * radius + 1) { i ->
        val d = (i - radius).toDouble()
        exp(-d * d / (2 * sigma * sigma))
    }
    val kernelSum = kernel.sum()
    return DoubleArray(values.size) { i ->
        var acc = 0.0
        for (k in kernel.indices) {
            val idx = (i + k - radius).coerceIn(0, values.size - 1)
            acc += values[idx] * kernel[k]
        }
        acc / kernelSum
    }
}
