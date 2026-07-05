package com.boulderbuddy.ghost.analysis

import com.boulderbuddy.ghost.GhostTuning
import com.boulderbuddy.ghost.model.GhostPoint
import com.boulderbuddy.ghost.model.GhostPoseTrack
import kotlin.math.roundToInt
import kotlin.math.sqrt

// =============================================================================
// Ghost Climber — Routenpfad + Fortschrittssignal (M3, P2/P3)
// =============================================================================
//
// Fortschritt ist NICHT die Y-Koordinate (versagt bei Traversen/Diagonalen, P2),
// sondern die Bogenlänge der auf den Routenpfad projizierten Hüftposition. Der Pfad
// ist eine Polylinie im Referenzraum: vorgeschlagen aus der Referenz-Trajektorie,
// vom Nutzer korrigierbar/verlängerbar (P3).

/** Polylinie mit vorberechneten kumulierten Segmentlängen für die Projektion. */
class RoutePolyline(val points: List<GhostPoint>) {

    init {
        require(points.size >= 2) { "Routenpfad braucht mindestens 2 Punkte" }
    }

    /** Kumulierte Bogenlänge bis zu jedem Stützpunkt; letzter Wert = Gesamtlänge. */
    private val cumulativeLengths: DoubleArray = DoubleArray(points.size).also { lengths ->
        for (i in 1 until points.size) {
            lengths[i] = lengths[i - 1] + distance(points[i - 1], points[i])
        }
    }

    val totalLength: Double get() = cumulativeLengths.last()

    /**
     * Bogenlänge des pfadnächsten Punkts zu [p] (globale Suche über alle Segmente).
     * Für selbstüberschneidende Routen sieht P8 eine lokale Suche um die letzte
     * Position vor — für den Abgabe-Umfang reicht die globale Projektion.
     */
    fun projectArcLength(p: GhostPoint): Double {
        var bestDist = Double.MAX_VALUE
        var bestArc = 0.0
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            val abx = (b.x - a.x).toDouble()
            val aby = (b.y - a.y).toDouble()
            val lenSq = abx * abx + aby * aby
            val t = if (lenSq == 0.0) {
                0.0
            } else {
                (((p.x - a.x) * abx + (p.y - a.y) * aby) / lenSq).coerceIn(0.0, 1.0)
            }
            val px = a.x + t * abx
            val py = a.y + t * aby
            val dx = p.x - px
            val dy = p.y - py
            val dist = dx * dx + dy * dy
            if (dist < bestDist) {
                bestDist = dist
                bestArc = cumulativeLengths[i] + t * sqrt(lenSq)
            }
        }
        return bestArc
    }

    /** Kürzester Abstand von [p] zur Polylinie — laterale Abweichung vom Pfad (P7). */
    fun distanceTo(p: GhostPoint): Double {
        var best = Double.MAX_VALUE
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            val abx = (b.x - a.x).toDouble()
            val aby = (b.y - a.y).toDouble()
            val lenSq = abx * abx + aby * aby
            val t = if (lenSq == 0.0) {
                0.0
            } else {
                (((p.x - a.x) * abx + (p.y - a.y) * aby) / lenSq).coerceIn(0.0, 1.0)
            }
            val dx = p.x - (a.x + t * abx)
            val dy = p.y - (a.y + t * aby)
            val dist = dx * dx + dy * dy
            if (dist < best) best = dist
        }
        return sqrt(best)
    }

    private fun distance(a: GhostPoint, b: GhostPoint): Double {
        val dx = (a.x - b.x).toDouble()
        val dy = (a.y - b.y).toDouble()
        return sqrt(dx * dx + dy * dy)
    }
}

/**
 * Pfad-Vorschlag (P3): die geglättete Hüfttrajektorie der Referenz, gleichmäßig auf
 * [pointCount] Stützpunkte reduziert — genug Form für die Projektion, wenig genug
 * Punkte für die Nutzer-Korrektur. null, wenn keine verwertbare Trajektorie existiert.
 */
fun suggestRoutePath(
    referenceTrack: GhostPoseTrack,
    pointCount: Int = GhostTuning.PATH_SUGGESTION_POINTS,
): List<GhostPoint>? {
    val trajectory = referenceTrack.smoothedHipTrajectory() ?: return null
    if (trajectory.size <= pointCount) return trajectory
    return List(pointCount) { i ->
        val idx = (i.toDouble() * (trajectory.size - 1) / (pointCount - 1)).roundToInt()
        trajectory[idx]
    }
}

/**
 * Fortschrittssignal des Versuchs (P2): je Sample-Frame die Bogenlänge der auf den
 * Pfad projizierten (geglätteten) Hüftposition, danach nochmals geglättet.
 * null = Pose in der ganzen Spur nie sicher erkannt.
 */
fun progressSignal(track: GhostPoseTrack, path: RoutePolyline): DoubleArray? {
    val trajectory = track.smoothedHipTrajectory() ?: return null
    val raw = DoubleArray(trajectory.size) { path.projectArcLength(trajectory[it]) }
    return gaussianSmooth(raw, GhostTuning.SMOOTHING_SIGMA_FRAMES)
}
