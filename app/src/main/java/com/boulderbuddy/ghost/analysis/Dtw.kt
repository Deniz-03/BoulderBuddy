package com.boulderbuddy.ghost.analysis

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// =============================================================================
// Ghost Climber — Dynamic Time Warping (M3, P1)
// =============================================================================
//
// Klassisches DP über die beiden 1D-Fortschrittssignale. DTW behandelt Pausen und
// unterschiedliches Tempo automatisch (P1) — genau das, woran lineares Strecken
// scheitert. Selbst implementiert (keine Library nötig, Plan A.1), mit Sakoe-Chiba-
// Band gegen absurde Warps und für lineare statt quadratische Praxis-Laufzeit.

/**
 * DTW-Ergebnis: [path] = monoton wachsende Index-Paare (i aus a, j aus b) vom
 * Anfang beider Signale bis zu ihren Enden; [normalizedDistance] = Gesamtkosten
 * pro Alignment-Schritt (Basis der Ähnlichkeitsmetrik in P7/M4).
 */
class DtwResult(
    val path: List<Pair<Int, Int>>,
    val normalizedDistance: Double,
)

/**
 * Richtet [a] und [b] per DTW aufeinander aus. [bandFraction] ist die halbe Breite
 * des Sakoe-Chiba-Bands als Anteil der längeren Signallänge; das Band verläuft
 * entlang der (skalierten) Diagonalen, damit auch verschieden lange Signale
 * vollständig alignierbar bleiben.
 */
fun dtw(a: DoubleArray, b: DoubleArray, bandFraction: Double = 0.25): DtwResult {
    require(a.isNotEmpty() && b.isNotEmpty()) { "DTW braucht nicht-leere Signale" }
    val n = a.size
    val m = b.size
    val band = max(
        (max(n, m) * bandFraction).roundToInt(),
        // Band nie schmaler als der Längenunterschied, sonst ist das Ziel unerreichbar.
        abs(n - m) + 1,
    )

    // Kostenmatrix (n+1)×(m+1), flach; Unerreichbares bleibt +∞.
    val inf = Double.POSITIVE_INFINITY
    val cost = DoubleArray((n + 1) * (m + 1)) { inf }
    fun idx(i: Int, j: Int) = i * (m + 1) + j
    cost[idx(0, 0)] = 0.0

    for (i in 1..n) {
        // Bandgrenzen um die skalierte Diagonale j ≈ i·m/n.
        val center = (i.toDouble() * m / n).roundToInt()
        val jFrom = max(1, center - band)
        val jTo = min(m, center + band)
        for (j in jFrom..jTo) {
            val d = abs(a[i - 1] - b[j - 1])
            val best = min(
                cost[idx(i - 1, j - 1)],
                min(cost[idx(i - 1, j)], cost[idx(i, j - 1)]),
            )
            if (best < inf) cost[idx(i, j)] = d + best
        }
    }

    // Backtracking vom Ende zum Anfang.
    val path = ArrayList<Pair<Int, Int>>(max(n, m) * 2)
    var i = n
    var j = m
    check(cost[idx(n, m)] < inf) { "DTW-Band zu schmal — bandFraction erhöhen" }
    while (i > 0 && j > 0) {
        path += (i - 1) to (j - 1)
        val diag = cost[idx(i - 1, j - 1)]
        val up = cost[idx(i - 1, j)]
        val left = cost[idx(i, j - 1)]
        when {
            diag <= up && diag <= left -> { i--; j-- }
            up <= left -> i--
            else -> j--
        }
    }
    // Randfälle: entlang der ersten Zeile/Spalte zum Ursprung auffüllen.
    while (i > 0) { path += (i - 1) to 0; i-- }
    while (j > 0) { path += 0 to (j - 1); j-- }
    path.reverse()

    return DtwResult(
        path = path,
        normalizedDistance = cost[idx(n, m)] / path.size,
    )
}
