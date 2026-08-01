package com.boulderbuddy.ghost.pose

import com.boulderbuddy.ghost.analysis.qualityMetrics
import com.boulderbuddy.ghost.model.GhostLandmark
import com.boulderbuddy.ghost.model.GhostLandmarkTypes as T
import com.boulderbuddy.ghost.model.GhostPoseFrame
import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import org.junit.Test

/**
 * Invarianten-Test (7.5e): nach [enforceRigidSkeleton] darf KEIN Knochen mehr über
 * seiner Sollgrenze liegen — die Überlängen-Quote muss also praktisch 0 sein. Auf dem
 * Gerät wurde 26,5 % gemessen; dieser Test klärt, ob die Rekonstruktion nicht greift
 * oder die Kennzahl etwas anderes misst als gedacht.
 */
class RigidSkeletonInvariantTest {

    private fun landmark(type: Int, x: Float, y: Float) =
        GhostLandmark(type = type, x = x, y = y, confidence = 0.9f, presence = 0.9f)

    /** Spur mit realistischer Streuung: Körpergröße driftet, Knochenlängen rauschen. */
    private fun noisyTrack(count: Int = 200): List<GhostPoseFrame> {
        val random = Random(42)
        return (0 until count).map { i ->
            val scale = 100f - i * 0.1f
            val half = scale / 2
            // Oberarm streut um 0,8 Körpergrößen mit ~20 % Rauschen.
            val upperArm = scale * 0.8f * (1f + (random.nextFloat() - 0.5f) * 0.4f)
            val foreArm = scale * 0.8f * (1f + (random.nextFloat() - 0.5f) * 0.4f)
            GhostPoseFrame(
                timeMs = i * 83L,
                landmarks = listOf(
                    landmark(T.LEFT_SHOULDER, 200f - half, 200f),
                    landmark(T.RIGHT_SHOULDER, 200f + half, 200f),
                    landmark(T.LEFT_HIP, 200f - half, 200f + scale),
                    landmark(T.RIGHT_HIP, 200f + half, 200f + scale),
                    landmark(T.LEFT_ELBOW, 200f - half, 200f + upperArm),
                    landmark(T.LEFT_WRIST, 200f - half, 200f + upperArm + foreArm),
                ),
            )
        }
    }

    @Test
    fun `nach der Rekonstruktion liegt kein Knochen mehr ueber der Sollgrenze`() {
        val before = noisyTrack().qualityMetrics()
        val after = enforceRigidSkeleton(noisyTrack()).qualityMetrics()

        // Ausgangslage: die Streuung erzeugt eine deutliche Überlängen-Quote.
        assertThat(before.boneOverExtensionRate).isGreaterThan(0.2)
        // Und danach darf davon nichts übrig sein.
        assertThat(after.boneOverExtensionRate).isLessThan(0.005)
    }

    /**
     * S4a: eine zappelnde Körpergrößen-Messung darf sich NICHT in die Knochenlängen
     * übertragen. Ohne Glättung skaliert jede Soll-Länge mit dem Rauschen der
     * Körpergröße — die Rekonstruktion würde Jitter erzeugen statt entfernen.
     */
    @Test
    fun `zappelnde Koerpergroesse traegt sich nicht in die Knochenlaengen`() {
        val random = Random(7)
        // Konstante Pose, aber die Rumpfbreite rauscht um +-8 % (Messrauschen).
        val frames = (0 until 100).map { i ->
            val scale = 100f * (1f + (random.nextFloat() - 0.5f) * 0.16f)
            val half = scale / 2
            GhostPoseFrame(
                timeMs = i * 83L,
                landmarks = listOf(
                    landmark(T.LEFT_SHOULDER, 200f - half, 200f),
                    landmark(T.RIGHT_SHOULDER, 200f + half, 200f),
                    landmark(T.LEFT_HIP, 200f - half, 200f + scale),
                    landmark(T.RIGHT_HIP, 200f + half, 200f + scale),
                    // Oberarm bewusst KONSTANT — er darf nicht mitzappeln.
                    landmark(T.LEFT_ELBOW, 200f - half, 200f + 80f),
                ),
            )
        }
        val result = enforceRigidSkeleton(frames)
        val lengths = result.map { f ->
            val s = f.landmarks.single { it.type == T.LEFT_SHOULDER }
            val e = f.landmarks.single { it.type == T.LEFT_ELBOW }
            com.boulderbuddy.ghost.model.distance(s, e)
        }
        // Der Oberarm war konstant 80 px und muss es bleiben.
        lengths.forEach { assertThat(it).isWithin(1.0).of(80.0) }
    }

    /**
     * S6a: die Rekonstruktion darf die FORM ändern, niemals die LAGE. Sie läuft als
     * letzte Stufe, ihre Korrekturen werden also von keiner Glättung mehr aufgefangen —
     * verschiebt sie das Rumpfzentrum, wandert das ganze Skelett pro Frame ein Stück
     * neben den Körper. Genau das war nach S5a sichtbar geworden, als die Rumpfkanten
     * dazukamen und die Rekonstruktion erstmals Schultern und Hüften bewegte.
     */
    @Test
    fun `die Rekonstruktion verschiebt die Pose nicht`() {
        val random = Random(23)
        fun noisy() = 100f * (1f + (random.nextFloat() - 0.5f) * 0.4f)
        val frames = (0 until 100).map { i ->
            val shoulders = noisy()
            val hips = noisy()
            val height = noisy()
            // Das Rumpfzentrum bewegt sich gleichmäßig nach oben (echte Kletterbewegung).
            val cy = 400f - i * 2f
            GhostPoseFrame(
                timeMs = i * 83L,
                landmarks = listOf(
                    landmark(T.LEFT_SHOULDER, 200f - shoulders / 2, cy - height / 2),
                    landmark(T.RIGHT_SHOULDER, 200f + shoulders / 2, cy - height / 2),
                    landmark(T.LEFT_HIP, 200f - hips / 2, cy + height / 2),
                    landmark(T.RIGHT_HIP, 200f + hips / 2, cy + height / 2),
                    landmark(T.LEFT_ELBOW, 200f - shoulders / 2, cy),
                ),
            )
        }
        val result = enforceRigidSkeleton(frames)

        frames.indices.forEach { i ->
            val before = checkNotNull(com.boulderbuddy.ghost.model.coreCentroid(frames[i].landmarks))
            val after = checkNotNull(com.boulderbuddy.ghost.model.coreCentroid(result[i].landmarks))
            assertThat(after.first).isWithin(0.01).of(before.first)
            assertThat(after.second).isWithin(0.01).of(before.second)
        }
    }

    /** Mehr Durchläufe dürfen das Ergebnis nicht verschlechtern (Konvergenz). */
    @Test
    fun `zusaetzliche Durchlaeufe verschlechtern nichts`() {
        val single = enforceRigidSkeleton(noisyTrack(), iterations = 1).qualityMetrics()
        val converged = enforceRigidSkeleton(noisyTrack()).qualityMetrics()

        assertThat(converged.boneOverExtensionRate)
            .isAtMost(single.boneOverExtensionRate)
    }

    /**
     * S5a: der Rumpf ist die Referenz, aus der die Körpergröße stammt — rauscht er,
     * muss die Rekonstruktion ihn mit beruhigen. Vorher blieb er unangetastet, und die
     * gegen die GEGLÄTTETE Körpergröße korrigierten Gliedmaßen passten dann nicht mehr
     * zum roh gezeichneten Rumpf desselben Frames (auf dem Gerät: Überlang 0,0 % → 13,8 %).
     */
    /** Anteil der Rumpfkanten-Messungen über dem Median-Verhältnis · Faktor. Normiert
     *  gegen dieselbe Körpergrößen-Referenz wie Pass und Kennzahlen (S5b). */
    private fun torsoOverExtension(frames: List<GhostPoseFrame>): Double {
        var measurements = 0
        var over = 0
        val scales = com.boulderbuddy.ghost.model.personScales(frames)
        com.boulderbuddy.ghost.model.TORSO_EDGES.forEach { edge ->
            val ratios = frames.mapIndexedNotNull { i, f ->
                val scale = scales[i] ?: return@mapIndexedNotNull null
                val a = f.landmarks.firstOrNull { it.type == edge.first } ?: return@mapIndexedNotNull null
                val b = f.landmarks.firstOrNull { it.type == edge.second } ?: return@mapIndexedNotNull null
                com.boulderbuddy.ghost.model.distance(a, b) / scale
            }
            if (ratios.size < 3) return@forEach
            val median = ratios.sorted()[ratios.size / 2]
            measurements += ratios.size
            over += ratios.count { it > median * 1.1 * 1.001 }
        }
        return if (measurements == 0) 0.0 else over.toDouble() / measurements
    }

    @Test
    fun `rauschender Rumpf wird mit beruhigt`() {
        val random = Random(11)
        // Wichtig: die Kanten rauschen UNABHÄNGIG voneinander. Ein gleichförmig
        // skalierter Rumpf wäre der legitime Fall "Person bewegt sich zur Kamera" und
        // dürfte gerade nicht korrigiert werden.
        fun noisy() = 100f * (1f + (random.nextFloat() - 0.5f) * 0.4f)
        val frames = (0 until 100).map { i ->
            val shoulders = noisy()
            val hips = noisy()
            val height = noisy()
            GhostPoseFrame(
                timeMs = i * 83L,
                landmarks = listOf(
                    landmark(T.LEFT_SHOULDER, 200f - shoulders / 2, 200f),
                    landmark(T.RIGHT_SHOULDER, 200f + shoulders / 2, 200f),
                    landmark(T.LEFT_HIP, 200f - hips / 2, 200f + height),
                    landmark(T.RIGHT_HIP, 200f + hips / 2, 200f + height),
                ),
            )
        }
        val before = torsoOverExtension(frames)
        val after = torsoOverExtension(enforceRigidSkeleton(frames))

        // Vorher blieb der Rumpf unangetastet — er war in keiner Kantenliste. (Der Wert
        // liegt unter dem Rohrauschen, weil die Körpergröße der MEDIAN der vier Kanten
        // ist und damit selbst mitrauscht.)
        assertThat(before).isGreaterThan(0.05)
        assertThat(after).isLessThan(0.02)
    }

    /**
     * Gegenprobe: eine sich drehende Person wird projiziert schmaler — das ist echte
     * Perspektive und darf NICHT auseinandergezogen werden (asymmetrische Grenzen).
     */
    @Test
    fun `perspektivisch schmalerer Rumpf bleibt schmal`() {
        val frames = (0 until 40).map { i ->
            // Schulterbreite geht auf die Hälfte zurück (Drehung zur Kamera).
            val shoulders = if (i >= 20) 50f else 100f
            GhostPoseFrame(
                timeMs = i * 83L,
                landmarks = listOf(
                    landmark(T.LEFT_SHOULDER, 200f - shoulders / 2, 200f),
                    landmark(T.RIGHT_SHOULDER, 200f + shoulders / 2, 200f),
                    landmark(T.LEFT_HIP, 200f - 50f, 300f),
                    landmark(T.RIGHT_HIP, 200f + 50f, 300f),
                ),
            )
        }
        val result = enforceRigidSkeleton(frames)
        val last = result.last()
        val width = com.boulderbuddy.ghost.model.distance(
            last.landmarks.single { it.type == T.LEFT_SHOULDER },
            last.landmarks.single { it.type == T.RIGHT_SHOULDER },
        )
        assertThat(width).isLessThan(60.0)
    }
}
