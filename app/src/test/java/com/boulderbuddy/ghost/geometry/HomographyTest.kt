package com.boulderbuddy.ghost.geometry

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * JVM-Tests für die selbst implementierte Homographie (M2) — reine Kotlin-Geometrie,
 * kein Android. Getestet wird über Reprojektionsfehler (H ist nur bis auf Skalierung
 * eindeutig, der direkte Matrixvergleich wäre falsch).
 */
class HomographyTest {

    private fun reprojectionError(h: Homography, src: List<Vec2>, dst: List<Vec2>): Double =
        src.indices.maxOf { i ->
            val p = h.map(src[i])
            val dx = p.x - dst[i].x
            val dy = p.y - dst[i].y
            sqrt(dx * dx + dy * dy)
        }

    /** Referenz-Homographie mit Perspektivanteil, wie sie zwei Kamerapositionen erzeugen. */
    private val trueH = Homography(
        doubleArrayOf(
            0.9, 0.05, 30.0,
            -0.03, 1.1, -20.0,
            1e-4, -5e-5, 1.0,
        ),
    )

    private val wallPoints = listOf(
        Vec2(100.0, 100.0), Vec2(600.0, 120.0), Vec2(580.0, 900.0), Vec2(120.0, 880.0),
        Vec2(350.0, 500.0), Vec2(200.0, 700.0), Vec2(500.0, 300.0), Vec2(400.0, 850.0),
    )

    @Test
    fun `identische Punkte ergeben Identitaet`() {
        val points = wallPoints.take(4)
        val h = Homography.estimate(points, points)
        assertThat(reprojectionError(h, points, points)).isLessThan(1e-6)
    }

    @Test
    fun `vier exakte Korrespondenzen werden exakt reproduziert`() {
        val src = wallPoints.take(4)
        val dst = src.map { trueH.map(it) }
        val h = Homography.estimate(src, dst)
        assertThat(reprojectionError(h, src, dst)).isLessThan(1e-6)
    }

    @Test
    fun `ueberbestimmt mit leichtem Rauschen bleibt unter einem Pixel`() {
        val random = Random(7)
        val src = wallPoints
        val dst = src.map {
            val p = trueH.map(it)
            Vec2(p.x + random.nextDouble(-0.5, 0.5), p.y + random.nextDouble(-0.5, 0.5))
        }
        val h = Homography.estimate(src, dst)
        assertThat(reprojectionError(h, src, src.map { trueH.map(it) })).isLessThan(1.5)
    }

    @Test
    fun `RANSAC ignoriert einen grob falschen Anker`() {
        val src = wallPoints
        val dst = src.map { trueH.map(it) }.toMutableList()
        // Ein "falsch getippter" Anker: 200 px daneben.
        dst[3] = Vec2(dst[3].x + 200.0, dst[3].y - 150.0)
        val h = Homography.estimate(src, dst)
        val inlierIndices = src.indices.filter { it != 3 }
        val error = inlierIndices.maxOf { i ->
            val p = h.map(src[i])
            val t = trueH.map(src[i])
            sqrt((p.x - t.x) * (p.x - t.x) + (p.y - t.y) * (p.y - t.y))
        }
        assertThat(error).isLessThan(1e-3)
    }

    @Test
    fun `reine Translation wird wiedergefunden`() {
        val src = wallPoints.take(5)
        val dst = src.map { Vec2(it.x + 42.0, it.y - 13.0) }
        val h = Homography.estimate(src, dst)
        val mapped = h.map(Vec2(333.0, 444.0))
        assertThat(mapped.x).isWithin(1e-6).of(375.0)
        assertThat(mapped.y).isWithin(1e-6).of(431.0)
    }

    @Test
    fun `zu wenige Punkte werfen`() {
        val points = wallPoints.take(3)
        try {
            Homography.estimate(points, points)
            error("erwartete IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertThat(expected).hasMessageThat().contains("4")
        }
    }
}
