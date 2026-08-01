package com.boulderbuddy.ghost.analysis

import com.boulderbuddy.ghost.GhostTuning
import com.boulderbuddy.ghost.model.GhostPoseFrame
import kotlin.math.floor
import kotlin.math.roundToLong

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
 * Verdichtet den DTW-Pfad zu einem monotonen, GEGLÄTTETEN Zeit-Mapping (S1, 7.5d).
 *
 * Der rohe DTW-Pfad ist eine Treppe: pro Referenz-Frame der Mittelwert der zugeordneten
 * Vergleichs-Frames (ein Ref-Frame kann bei Pausen des Vergleichs auf mehrere Cmp-Frames
 * zeigen). Bei fast gleichem Tempo warpt das DTW nur noch Rauschen weg — die Warp-Funktion
 * bekommt Plateaus (Geist steht) und Sprünge (Geist rast). Deshalb vier Stufen:
 *
 *  1. **Sub-Frame-Auflösung**: der gemittelte Cmp-Index wird zwischen den Frame-Zeiten
 *     interpoliert statt gerundet — sonst quantisiert das Mapping auf ~83 ms (12 fps).
 *  2. **Gauss-Glättung** ([smoothingSigmaFrames]) des Residuums gegen die lineare
 *     Zeitachse: verschleift die Treppenstufen, ohne den Trend anzutasten.
 *  3. **Blend gegen die lineare Zeitachse** ([linearBlend]): dämpft das verbleibende
 *     Residuum — 0 = pur DTW, 1 = pures Strecken Ref-Dauer→Cmp-Dauer.
 *  4. **Steigungs-Limiter** ([minSlope]/[maxSlope]) auf dCmp/dRef als Sicherheitsnetz,
 *     danach affine Re-Normierung auf die ursprünglichen Endpunkte — der Geist kommt
 *     am Ende des Referenz-Videos garantiert am Ende des Vergleichs-Videos an.
 *
 * Die Defaults kommen aus [GhostTuning]; die Parameter existieren, damit Tests und ein
 * späteres Debug-Panel andere Werte durchreichen können ([linearBlend] = 0.0 und
 * [smoothingSigmaFrames] = 0.0 reproduzieren das ungeglättete Verhalten vor S1).
 */
fun buildTimeMapping(
    dtwPath: List<Pair<Int, Int>>,
    refFrames: List<GhostPoseFrame>,
    cmpFrames: List<GhostPoseFrame>,
    smoothingSigmaFrames: Double = GhostTuning.WARP_SMOOTHING_SIGMA_FRAMES,
    linearBlend: Double = GhostTuning.WARP_LINEAR_BLEND,
    minSlope: Double = GhostTuning.WARP_MIN_SLOPE,
    maxSlope: Double = GhostTuning.WARP_MAX_SLOPE,
): GhostTimeMapping {
    val cmpByRef = dtwPath.groupBy({ it.first }, { it.second })
    val refIndices = cmpByRef.keys.sorted()
    val refTimes = LongArray(refIndices.size) { refFrames[refIndices[it]].timeMs }

    // Stufe 1: rohe Warp-Funktion in ms, sub-frame-genau. Der DTW-Pfad ist monoton,
    // also ist auch der Index-Mittelwert je Ref-Frame monoton nicht-fallend.
    val warp = DoubleArray(refIndices.size) { i ->
        val meanCmpIdx = cmpByRef.getValue(refIndices[i]).average()
        cmpTimeAtIndex(cmpFrames, meanCmpIdx)
    }
    if (warp.size == 1) return GhostTimeMapping(refTimes, longArrayOf(warp[0].roundToLong()))

    // Endpunkte des ROHEN Warps sind das Ziel — Glättung und Limiter dürfen sie
    // verschieben, die Re-Normierung holt sie am Ende exakt zurück.
    val targetStart = warp.first()
    val targetEnd = warp.last()

    // Stufen 2+3 in einem Zug, auf dem RESIDUUM gegen die lineare Zeitachse:
    //
    //   warp = linear + residuum   →   blended = linear + (1−λ)·glättung(residuum)
    //
    // Direkt auf dem Warp zu glätten wäre falsch: der Gauss-Kernel klemmt an den Rändern
    // ([gaussianSmooth]) und würde dort die Steigung flachdrücken — der Geist bremste in
    // der ersten und letzten Sekunde. Das Residuum ist an beiden Enden per Konstruktion 0,
    // dort richtet die Randbehandlung also nichts an, und der lineare Trend bleibt exakt
    // erhalten. Der Blend ist dann nur noch eine Dämpfung des Residuums.
    val refSpan = (refTimes.last() - refTimes.first()).toDouble()
    val linear = DoubleArray(warp.size) { i ->
        val t = if (refSpan <= 0.0) 0.0 else (refTimes[i] - refTimes.first()) / refSpan
        targetStart + t * (targetEnd - targetStart)
    }
    val residual = gaussianSmooth(
        DoubleArray(warp.size) { warp[it] - linear[it] },
        smoothingSigmaFrames,
    )
    val damping = 1.0 - linearBlend.coerceIn(0.0, 1.0)
    val blended = DoubleArray(warp.size) { linear[it] + damping * residual[it] }

    // Stufe 4a: Steigungs-Limiter, RELATIV zum globalen Tempo-Verhältnis der beiden
    // Versuche — bei einem insgesamt 1,5× langsameren Vergleich ist "doppelt so schnell"
    // eben 3,0 und nicht 2,0. minSlope > 0 garantiert, dass der Geist nie steht.
    val meanSlope = if (refSpan > 0.0) (targetEnd - targetStart) / refSpan else 1.0
    val limited = DoubleArray(blended.size)
    limited[0] = blended[0]
    for (i in 1 until blended.size) {
        val dRef = (refTimes[i] - refTimes[i - 1]).toDouble().coerceAtLeast(0.0)
        val dCmp = blended[i] - blended[i - 1]
        limited[i] = limited[i - 1] +
            dCmp.coerceIn(minSlope * meanSlope * dRef, maxSlope * meanSlope * dRef)
    }

    // Stufe 4b: affin auf [targetStart, targetEnd] zurückziehen. Der Skalierungsfaktor
    // liegt nahe 1 (der Limiter greift nur punktuell); da er positiv ist, bleibt die
    // Monotonie erhalten. Der maxOf ist reine Absicherung gegen Rundung.
    //
    // Bewusste Unschärfe: Endpunkt-Treue und harte Steigungsgrenze sind nicht gleichzeitig
    // erfüllbar — hat der Limiter viel weggeschnitten, skaliert 4b die Steigungen wieder
    // etwas über die Grenze. Der Limiter ist ein Sicherheitsnetz gegen Ausreißer, die
    // Grundruhe liefern Glättung (Stufe 2) und Blend (Stufe 3).
    val span = limited.last() - limited.first()
    val scale = if (span > 0.0) (targetEnd - targetStart) / span else 0.0
    val cmpTimes = LongArray(limited.size)
    var previous = Long.MIN_VALUE
    for (i in limited.indices) {
        val value = targetStart + (limited[i] - limited[0]) * scale
        previous = maxOf(previous, value.roundToLong())
        cmpTimes[i] = previous
    }
    return GhostTimeMapping(refTimes, cmpTimes)
}

/**
 * Zeit des (gebrochenen) Cmp-Frame-Index [index], linear zwischen den Frame-Zeiten
 * interpoliert — hält die Warp-Funktion frei von der 83-ms-Quantisierung der Abtastung.
 */
private fun cmpTimeAtIndex(cmpFrames: List<GhostPoseFrame>, index: Double): Double {
    val clamped = index.coerceIn(0.0, (cmpFrames.size - 1).toDouble())
    val lower = floor(clamped).toInt()
    if (lower >= cmpFrames.size - 1) return cmpFrames.last().timeMs.toDouble()
    val t = clamped - lower
    val a = cmpFrames[lower].timeMs.toDouble()
    val b = cmpFrames[lower + 1].timeMs.toDouble()
    return a + t * (b - a)
}
