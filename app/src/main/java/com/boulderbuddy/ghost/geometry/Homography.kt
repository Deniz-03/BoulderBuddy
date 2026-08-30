package com.boulderbuddy.ghost.geometry

import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.random.Random

// =============================================================================
// Ghost Climber — Homographie in reinem Kotlin (Phase 7.5, M2)
// =============================================================================
//
// Bewusste Entscheidung (FABLE_GHOSTCLIMBER_START §2): KEIN OpenCV. Eine planare
// Wand + feste Kamera brauchen genau eine 3×3-Homographie aus ≥4 Punktkorrespondenzen
// — das ist normalisierte DLT (Hartley/Zisserman) + optional RANSAC gegen Ausreißer,
// beides gut in ~200 Zeilen Kotlin machbar und JVM-unit-testbar.

/** 2D-Punkt in Double-Präzision für die Geometrie-Pipeline. */
data class Vec2(val x: Double, val y: Double)

/**
 * 3×3-Homographie (zeilenweise als 9 Werte). [map] ist das "perspectiveTransform":
 * homogener Punkt × Matrix, danach durch w teilen.
 */
class Homography(private val h: DoubleArray) {

    init {
        require(h.size == 9) { "Homographie braucht 9 Werte" }
    }

    fun map(p: Vec2): Vec2 {
        val w = h[6] * p.x + h[7] * p.y + h[8]
        return Vec2(
            (h[0] * p.x + h[1] * p.y + h[2]) / w,
            (h[3] * p.x + h[4] * p.y + h[5]) / w,
        )
    }

    fun map(x: Float, y: Float): Vec2 = map(Vec2(x.toDouble(), y.toDouble()))

    /** Die 9 Matrixwerte (zeilenweise) — für die Persistenz als JSON (M5). */
    fun values(): DoubleArray = h.clone()

    companion object {

        /**
         * Schätzt die Homographie src→dst aus ≥4 Korrespondenzen.
         *
         * Bei exakt 4 Punkten: direkte normalisierte DLT. Bei mehr Punkten: RANSAC
         * über 4er-Stichproben (Schutz gegen einzelne falsch getippte Anker), danach
         * Refit per DLT über alle Inlier. Deterministisch dank festem Seed —
         * gleiche Anker ⇒ gleiches Ergebnis.
         *
         * @throws IllegalArgumentException bei zu wenigen oder degenerierten Punkten.
         */
        fun estimate(
            src: List<Vec2>,
            dst: List<Vec2>,
            ransacIterations: Int = 200,
            inlierThresholdPx: Double = 4.0,
        ): Homography {
            require(src.size == dst.size) { "src und dst müssen gleich viele Punkte haben" }
            require(src.size >= 4) { "Mindestens 4 Punktkorrespondenzen nötig" }
            if (src.size == 4) return dlt(src, dst)

            val random = Random(42)
            var bestInliers: List<Int> = emptyList()
            repeat(ransacIterations) {
                val sample = sampleIndices(random, src.size)
                val candidate = runCatching {
                    dlt(sample.map { src[it] }, sample.map { dst[it] })
                }.getOrNull() ?: return@repeat
                val inliers = src.indices.filter { i ->
                    distance(candidate.map(src[i]), dst[i]) < inlierThresholdPx
                }
                if (inliers.size > bestInliers.size) bestInliers = inliers
            }
            // Weniger als 4 Inlier heißt: kein konsistentes Modell gefunden —
            // dann ehrlich über alle Punkte fitten statt Zufallsergebnis zu liefern.
            return if (bestInliers.size >= 4) {
                dlt(bestInliers.map { src[it] }, bestInliers.map { dst[it] })
            } else {
                dlt(src, dst)
            }
        }

        private fun distance(a: Vec2, b: Vec2): Double {
            val dx = a.x - b.x
            val dy = a.y - b.y
            return sqrt(dx * dx + dy * dy)
        }

        private fun sampleIndices(random: Random, size: Int): List<Int> {
            val picked = mutableSetOf<Int>()
            while (picked.size < 4) picked += random.nextInt(size)
            return picked.toList()
        }

        /**
         * Normalisierte DLT: Punkte konditionieren (Schwerpunkt → Ursprung, mittlerer
         * Abstand → √2), 2n×9-Gleichungssystem Ah=0 aufstellen, h = Eigenvektor zum
         * kleinsten Eigenwert von AᵀA (Jacobi), zurück-denormalisieren.
         */
        internal fun dlt(src: List<Vec2>, dst: List<Vec2>): Homography {
            val tSrc = normalization(src)
            val tDst = normalization(dst)
            val nSrc = src.map { tSrc.apply(it) }
            val nDst = dst.map { tDst.apply(it) }

            // AᵀA direkt akkumulieren (9×9) statt A zu materialisieren.
            val m = Array(9) { DoubleArray(9) }
            fun accumulate(row: DoubleArray) {
                for (i in 0 until 9) {
                    if (row[i] == 0.0) continue
                    for (j in i until 9) m[i][j] += row[i] * row[j]
                }
            }
            for (k in src.indices) {
                val (x, y) = nSrc[k]
                val (u, v) = nDst[k]
                accumulate(doubleArrayOf(0.0, 0.0, 0.0, -x, -y, -1.0, v * x, v * y, v))
                accumulate(doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, -u))
            }
            for (i in 0 until 9) for (j in 0 until i) m[i][j] = m[j][i]

            val hNorm = smallestEigenvector(m)

            // H = T_dst⁻¹ · Ĥ · T_src
            val result = matMul(matMul(tDst.inverse(), hNorm), tSrc.matrix())
            val scale = result[8]
            require(abs(scale) > 1e-12) { "Degenerierte Punktlage — Anker prüfen" }
            for (i in result.indices) result[i] /= scale
            return Homography(result)
        }

        /** Ähnlichkeitstransformation der Hartley-Normalisierung. */
        private class Normalization(val s: Double, val cx: Double, val cy: Double) {
            fun apply(p: Vec2) = Vec2(s * (p.x - cx), s * (p.y - cy))
            fun matrix() = doubleArrayOf(s, 0.0, -s * cx, 0.0, s, -s * cy, 0.0, 0.0, 1.0)
            fun inverse() = doubleArrayOf(1 / s, 0.0, cx, 0.0, 1 / s, cy, 0.0, 0.0, 1.0)
        }

        private fun normalization(points: List<Vec2>): Normalization {
            val cx = points.sumOf { it.x } / points.size
            val cy = points.sumOf { it.y } / points.size
            val meanDist = points.sumOf {
                val dx = it.x - cx
                val dy = it.y - cy
                sqrt(dx * dx + dy * dy)
            } / points.size
            require(meanDist > 1e-9) { "Alle Punkte identisch — Anker prüfen" }
            return Normalization(sqrt(2.0) / meanDist, cx, cy)
        }

        private fun matMul(a: DoubleArray, b: DoubleArray): DoubleArray {
            val out = DoubleArray(9)
            for (r in 0 until 3) for (c in 0 until 3) {
                var sum = 0.0
                for (k in 0 until 3) sum += a[r * 3 + k] * b[k * 3 + c]
                out[r * 3 + c] = sum
            }
            return out
        }

        /**
         * Eigenvektor zum kleinsten Eigenwert einer symmetrischen 9×9-Matrix via
         * zyklischem Jacobi-Verfahren — für diese feste, kleine Größe völlig
         * ausreichend und erspart eine ganze Lineare-Algebra-Bibliothek.
         */
        private fun smallestEigenvector(matrix: Array<DoubleArray>): DoubleArray {
            val n = 9
            val a = Array(n) { matrix[it].clone() }
            val v = Array(n) { i -> DoubleArray(n) { j -> if (i == j) 1.0 else 0.0 } }

            repeat(50) {
                var off = 0.0
                for (i in 0 until n) for (j in i + 1 until n) off += a[i][j] * a[i][j]
                if (off < 1e-20) return@repeat

                for (p in 0 until n - 1) {
                    for (q in p + 1 until n) {
                        if (abs(a[p][q]) < 1e-15) continue
                        val theta = (a[q][q] - a[p][p]) / (2 * a[p][q])
                        val t = sign(theta).let { if (it == 0.0) 1.0 else it } /
                            (abs(theta) + sqrt(theta * theta + 1))
                        val c = 1 / sqrt(t * t + 1)
                        val s = t * c

                        val app = a[p][p]
                        val aqq = a[q][q]
                        val apq = a[p][q]
                        a[p][p] = c * c * app - 2 * s * c * apq + s * s * aqq
                        a[q][q] = s * s * app + 2 * s * c * apq + c * c * aqq
                        a[p][q] = 0.0
                        a[q][p] = 0.0
                        for (k in 0 until n) {
                            if (k == p || k == q) continue
                            val akp = a[k][p]
                            val akq = a[k][q]
                            a[k][p] = c * akp - s * akq
                            a[p][k] = a[k][p]
                            a[k][q] = s * akp + c * akq
                            a[q][k] = a[k][q]
                        }
                        for (k in 0 until n) {
                            val vkp = v[k][p]
                            val vkq = v[k][q]
                            v[k][p] = c * vkp - s * vkq
                            v[k][q] = s * vkp + c * vkq
                        }
                    }
                }
            }

            var minIdx = 0
            for (i in 1 until n) if (a[i][i] < a[minIdx][minIdx]) minIdx = i
            return DoubleArray(n) { v[it][minIdx] }
        }
    }
}
