package com.boulderbuddy.ghost.analysis

import com.boulderbuddy.ghost.GhostTuning
import com.boulderbuddy.ghost.model.GhostPoint
import com.boulderbuddy.ghost.model.GhostViewMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

// =============================================================================
// Ghost Climber — Modus-Vorschlag Overlay vs. Side-by-Side (M4, P7)
// =============================================================================
//
// Ein unsynchronisierbares Overlay ist irreführend (suggeriert Korrespondenz, die
// nicht existiert, P6). Drei Indizien für "fundamental andere Beta", jedes reicht:
//  1. hohe DTW-Restdistanz  → die Fortschrittssignale passen nie gut aufeinander,
//  2. stark unterschiedliche laterale Streuung um den Pfad → einer klettert eine
//     andere Linie,
//  3. abweichende PCA-Hauptrichtung der Trajektorien → strukturell andere Bewegung.
// Alle Schwellen: GhostTuning (empirisch zu kalibrieren, A.6). Der Nutzer kann den
// Vorschlag immer überstimmen.

data class GhostModeSuggestion(
    val mode: GhostViewMode,
    /** Kurze Begründung für die UI ("warum schlägt die App das vor?"). */
    val reason: String,
)

fun suggestViewMode(
    refTrajectory: List<GhostPoint>,
    cmpTrajectory: List<GhostPoint>,
    path: RoutePolyline,
    dtwNormalizedDistance: Double,
): GhostModeSuggestion {
    val pathLength = path.totalLength
    if (pathLength <= 0.0) {
        return GhostModeSuggestion(GhostViewMode.SIDE_BY_SIDE, "Routenpfad ohne Länge")
    }

    val dtwFraction = dtwNormalizedDistance / pathLength
    if (dtwFraction > GhostTuning.MODE_DTW_DISTANCE_MAX_FRACTION) {
        return GhostModeSuggestion(
            GhostViewMode.SIDE_BY_SIDE,
            "Die Fortschrittsverläufe passen kaum aufeinander — vermutlich andere Beta.",
        )
    }

    val lateralDiff =
        abs(lateralDeviation(refTrajectory, path) - lateralDeviation(cmpTrajectory, path))
    if (lateralDiff / pathLength > GhostTuning.MODE_LATERAL_STD_DIFF_MAX_FRACTION) {
        return GhostModeSuggestion(
            GhostViewMode.SIDE_BY_SIDE,
            "Die Versuche folgen unterschiedlich weit dem Routenpfad — andere Linie.",
        )
    }

    val angleDeg = mainDirectionDifferenceDeg(refTrajectory, cmpTrajectory)
    if (angleDeg > GhostTuning.MODE_MAIN_DIRECTION_MAX_DEG) {
        return GhostModeSuggestion(
            GhostViewMode.SIDE_BY_SIDE,
            "Die Bewegungsrichtungen weichen deutlich voneinander ab.",
        )
    }

    return GhostModeSuggestion(
        GhostViewMode.OVERLAY,
        "Beide Versuche folgen derselben Linie — Overlay ist aussagekräftig.",
    )
}

/**
 * Laterale Abweichung der Trajektorie als RMS-Abstand zum Pfad (die "laterale Varianz"
 * aus P7, mit dem Pfad als Mittellinie). Bewusst NICHT die Streuung der Abstände:
 * eine Linie, die konstant 150 px neben dem Pfad verläuft, hätte Streuung 0, weicht
 * aber massiv ab.
 */
internal fun lateralDeviation(trajectory: List<GhostPoint>, path: RoutePolyline): Double {
    if (trajectory.isEmpty()) return 0.0
    return sqrt(trajectory.sumOf { val d = path.distanceTo(it); d * d } / trajectory.size)
}

/**
 * Winkel (0–90°) zwischen den PCA-Hauptrichtungen beider Trajektorien.
 * Hauptrichtung = Eigenvektor der 2×2-Kovarianz, geschlossen lösbar über
 * θ = ½·atan2(2·cov_xy, cov_xx − cov_yy); Richtungen sind vorzeichenlos (Achsen).
 */
internal fun mainDirectionDifferenceDeg(
    a: List<GhostPoint>,
    b: List<GhostPoint>,
): Double {
    val angleA = principalAngleRad(a) ?: return 0.0
    val angleB = principalAngleRad(b) ?: return 0.0
    var diff = abs(angleA - angleB) % PI
    if (diff > PI / 2) diff = PI - diff
    return Math.toDegrees(diff)
}

private fun principalAngleRad(points: List<GhostPoint>): Double? {
    if (points.size < 2) return null
    val mx = points.sumOf { it.x.toDouble() } / points.size
    val my = points.sumOf { it.y.toDouble() } / points.size
    var covXx = 0.0
    var covYy = 0.0
    var covXy = 0.0
    points.forEach {
        val dx = it.x - mx
        val dy = it.y - my
        covXx += dx * dx
        covYy += dy * dy
        covXy += dx * dy
    }
    // Entartet (alle Punkte identisch): keine Richtung bestimmbar.
    if (covXx + covYy < 1e-12) return null
    return 0.5 * atan2(2 * covXy, covXx - covYy)
}
