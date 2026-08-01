package com.boulderbuddy.ghost.model

import com.boulderbuddy.ghost.GhostTuning
import kotlin.math.hypot

// =============================================================================
// Gemeinsame Pose-Geometrie (7.5e)
// =============================================================================
//
// Körpergröße, Rumpf-Zentrum und die Liste der starren Knochen werden inzwischen an
// drei Stellen gebraucht: von den Plausibilitäts-Filtern (ghost.pose), von der ROI-Box
// der Extraktion und von den Qualitäts-Kennzahlen (ghost.analysis). Bewusst hier im
// model-Paket, das beide anderen ohnehin importieren — statt dreimal dieselbe Messung.

/**
 * Knochen (proximal → distal), deren Länge im Verhältnis zur Körpergröße anatomisch
 * konstant ist. Reihenfolge proximal zuerst: wer den Ellbogen korrigiert, korrigiert
 * den davon abhängigen Unterarm gleich mit.
 */
val RIGID_BONES: List<Pair<Int, Int>> = listOf(
    GhostLandmarkTypes.LEFT_SHOULDER to GhostLandmarkTypes.LEFT_ELBOW,
    GhostLandmarkTypes.LEFT_ELBOW to GhostLandmarkTypes.LEFT_WRIST,
    GhostLandmarkTypes.RIGHT_SHOULDER to GhostLandmarkTypes.RIGHT_ELBOW,
    GhostLandmarkTypes.RIGHT_ELBOW to GhostLandmarkTypes.RIGHT_WRIST,
    GhostLandmarkTypes.LEFT_HIP to GhostLandmarkTypes.LEFT_KNEE,
    GhostLandmarkTypes.LEFT_KNEE to GhostLandmarkTypes.LEFT_ANKLE,
    GhostLandmarkTypes.RIGHT_HIP to GhostLandmarkTypes.RIGHT_KNEE,
    GhostLandmarkTypes.RIGHT_KNEE to GhostLandmarkTypes.RIGHT_ANKLE,
)

/** Die vier Rumpfpunkte — die stabilsten Landmarks einer Kletterpose. */
private val CORE_TYPES = listOf(
    GhostLandmarkTypes.LEFT_SHOULDER,
    GhostLandmarkTypes.RIGHT_SHOULDER,
    GhostLandmarkTypes.LEFT_HIP,
    GhostLandmarkTypes.RIGHT_HIP,
)

/**
 * Robuste Körpergröße in Pixeln: Median mehrerer struktureller Maße (Schulter-/Hüft-
 * breite + Rumpfseiten). Robust gegen einzelne Fehlpunkte UND gegen perspektivische
 * Rumpfverkürzung — die Breiten bleiben dabei stabil.
 *
 * [minPresence] > 0 verlangt zusätzlich echte Präsenz: MediaPipe meldet für erfundene
 * Positionen durchaus hohe visibility, presence unterscheidet "verdeckt" von
 * "halluziniert". Wird für die ROI-Box gebraucht, wo eine Fehlmessung teuer ist.
 */
fun bodyScale(
    landmarks: List<GhostLandmark>,
    minConfidence: Float = GhostTuning.VISIBILITY_SHOW_THRESHOLD,
    minPresence: Float = 0f,
): Double? {
    fun at(type: Int) = landmarks.firstOrNull {
        it.type == type && it.confidence >= minConfidence && it.presence >= minPresence
    }
    val ls = at(GhostLandmarkTypes.LEFT_SHOULDER)
    val rs = at(GhostLandmarkTypes.RIGHT_SHOULDER)
    val lh = at(GhostLandmarkTypes.LEFT_HIP)
    val rh = at(GhostLandmarkTypes.RIGHT_HIP)
    val cues = ArrayList<Double>(4)
    if (ls != null && rs != null) cues += distance(ls, rs)
    if (lh != null && rh != null) cues += distance(lh, rh)
    if (ls != null && lh != null) cues += distance(ls, lh)
    if (rs != null && rh != null) cues += distance(rs, rh)
    if (cues.isEmpty()) return null
    cues.sort()
    return cues[cues.size / 2]
}

/** Zentrum der Rumpfpunkte, oder null bei weniger als drei brauchbaren Punkten. */
fun coreCentroid(
    landmarks: List<GhostLandmark>,
    minConfidence: Float = GhostTuning.VISIBILITY_SHOW_THRESHOLD,
    minPresence: Float = 0f,
): Pair<Double, Double>? {
    val core = CORE_TYPES.mapNotNull { type ->
        landmarks.firstOrNull {
            it.type == type && it.confidence >= minConfidence && it.presence >= minPresence
        }
    }
    if (core.size < 3) return null
    return core.sumOf { it.x.toDouble() } / core.size to
        core.sumOf { it.y.toDouble() } / core.size
}

fun distance(a: GhostLandmark, b: GhostLandmark): Double =
    hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
