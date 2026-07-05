package com.boulderbuddy.ghost.analysis

import com.boulderbuddy.ghost.model.GhostPoseFrame
import kotlin.math.roundToInt

/**
 * Zeit-Mapping Referenz→Vergleich aus dem DTW-Pfad (M3): zu jeder Wiedergabezeit des
 * Referenz-Videos die Zeit, an der der Vergleichs-Versuch an derselben Stelle der
 * Route war. Damit steuert der Referenz-Scrubber beide Skelette (A.4 Schritt 5).
 */
class GhostTimeMapping(
    private val refTimesMs: LongArray,
    private val cmpTimesMs: LongArray,
) {

    init {
        require(refTimesMs.size == cmpTimesMs.size && refTimesMs.isNotEmpty()) {
            "Mapping braucht gleich lange, nicht-leere Stützstellen"
        }
    }

    /** Linear zwischen den Stützstellen interpoliert; außerhalb geklemmt. */
    fun mapToComparison(refTimeMs: Long): Long {
        if (refTimeMs <= refTimesMs.first()) return cmpTimesMs.first()
        if (refTimeMs >= refTimesMs.last()) return cmpTimesMs.last()
        var lo = 0
        var hi = refTimesMs.size - 1
        while (lo + 1 < hi) {
            val mid = (lo + hi) / 2
            if (refTimesMs[mid] <= refTimeMs) lo = mid else hi = mid
        }
        val span = refTimesMs[hi] - refTimesMs[lo]
        if (span == 0L) return cmpTimesMs[lo]
        val t = (refTimeMs - refTimesMs[lo]).toDouble() / span
        return (cmpTimesMs[lo] + t * (cmpTimesMs[hi] - cmpTimesMs[lo])).toLong()
    }
}

/**
 * Verdichtet den DTW-Pfad zu einem monotonen Zeit-Mapping: pro Referenz-Frame der
 * Mittelwert der zugeordneten Vergleichs-Frames (ein Ref-Frame kann bei Pausen des
 * Vergleichs auf mehrere Cmp-Frames zeigen), Monotonie wird abschließend erzwungen.
 */
fun buildTimeMapping(
    dtwPath: List<Pair<Int, Int>>,
    refFrames: List<GhostPoseFrame>,
    cmpFrames: List<GhostPoseFrame>,
): GhostTimeMapping {
    val cmpByRef = dtwPath.groupBy({ it.first }, { it.second })
    val refIndices = cmpByRef.keys.sorted()
    val refTimes = LongArray(refIndices.size)
    val cmpTimes = LongArray(refIndices.size)
    var previousCmp = 0L
    refIndices.forEachIndexed { i, refIdx ->
        refTimes[i] = refFrames[refIdx].timeMs
        val meanCmpIdx = cmpByRef.getValue(refIdx).average().roundToInt()
            .coerceIn(0, cmpFrames.size - 1)
        previousCmp = maxOf(previousCmp, cmpFrames[meanCmpIdx].timeMs)
        cmpTimes[i] = previousCmp
    }
    return GhostTimeMapping(refTimes, cmpTimes)
}
