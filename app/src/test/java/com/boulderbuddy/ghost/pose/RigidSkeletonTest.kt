package com.boulderbuddy.ghost.pose

import com.boulderbuddy.ghost.model.GhostLandmark
import com.boulderbuddy.ghost.model.GhostLandmarkTypes as T
import com.boulderbuddy.ghost.model.GhostPoseFrame
import com.boulderbuddy.ghost.model.distance
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * JVM-Tests der rigiden Rekonstruktion (S2a, 7.5e). Der Kern ist die ASYMMETRIE der
 * Grenzen: eine Projektion kann durch Verkürzung nur kürzer erscheinen als der echte
 * Knochen, niemals länger — Überlängen sind Halluzination und werden hart geklemmt,
 * Unterlängen bleiben erlaubt.
 */
class RigidSkeletonTest {

    private fun landmark(type: Int, x: Float, y: Float, confidence: Float = 0.9f) =
        GhostLandmark(type = type, x = x, y = y, confidence = confidence, presence = 0.9f)

    /**
     * Frame mit Rumpf (Schulterbreite = [scale]) und linkem Oberarm der Länge
     * [upperArm]; die Körpergröße ist damit messbar und der Bezug definiert.
     */
    private fun frame(timeMs: Long, scale: Float, upperArm: Float): GhostPoseFrame {
        val half = scale / 2
        return GhostPoseFrame(
            timeMs = timeMs,
            landmarks = listOf(
                landmark(T.LEFT_SHOULDER, 100f - half, 100f),
                landmark(T.RIGHT_SHOULDER, 100f + half, 100f),
                landmark(T.LEFT_HIP, 100f - half, 100f + scale),
                landmark(T.RIGHT_HIP, 100f + half, 100f + scale),
                landmark(T.LEFT_ELBOW, 100f - half, 100f + upperArm),
            ),
        )
    }

    private fun upperArmLength(frame: GhostPoseFrame): Double {
        val shoulder = frame.landmarks.single { it.type == T.LEFT_SHOULDER }
        val elbow = frame.landmarks.single { it.type == T.LEFT_ELBOW }
        return distance(shoulder, elbow)
    }

    @Test
    fun `ueberlanger Knochen wird auf die Sollgrenze geklemmt`() {
        // Oberarm normal 80 px bei Körpergröße 100 (Verhältnis 0,8), in einem Frame 200.
        val frames = (0 until 10).map { i ->
            frame(i * 83L, scale = 100f, upperArm = if (i == 5) 200f else 80f)
        }
        val result = enforceRigidSkeleton(frames)

        // Soll 0,8 · 100 = 80, Obergrenze 80 · 1,1 = 88.
        assertThat(upperArmLength(result[5])).isWithin(0.5).of(88.0)
        // Die unauffälligen Frames bleiben unangetastet.
        assertThat(upperArmLength(result[4])).isWithin(0.5).of(80.0)
    }

    /**
     * S8a: die Füße werden gezeichnet und müssen deshalb genauso beschränkt sein wie
     * jeder andere Knochen. Dass die Liste am Knöchel endete, während das Overlay bis zur
     * Fußspitze weiterzeichnet, machte ausgerechnet den sichtbaren Teil des Skeletts zum
     * einzigen unbeschränkten — gemessen mit drei- bis vierfachem Morph.
     */
    @Test
    fun `ueberlanger Fussknochen wird geklemmt`() {
        // Fuß normal 30 px bei Körpergröße 100 (Verhältnis 0,3), in einem Frame 90.
        fun footFrame(timeMs: Long, foot: Float) = GhostPoseFrame(
            timeMs = timeMs,
            landmarks = listOf(
                landmark(T.LEFT_SHOULDER, 50f, 100f),
                landmark(T.RIGHT_SHOULDER, 150f, 100f),
                landmark(T.LEFT_HIP, 50f, 200f),
                landmark(T.RIGHT_HIP, 150f, 200f),
                landmark(T.LEFT_ANKLE, 50f, 400f),
                landmark(T.LEFT_HEEL, 50f, 400f + foot),
            ),
        )
        val frames = (0 until 10).map { i ->
            footFrame(i * 83L, foot = if (i == 5) 90f else 30f)
        }
        val result = enforceRigidSkeleton(frames)

        fun heelDistance(frame: GhostPoseFrame) = distance(
            frame.landmarks.single { it.type == T.LEFT_ANKLE },
            frame.landmarks.single { it.type == T.LEFT_HEEL },
        )
        // Soll 0,3 · 100 = 30, Obergrenze 33.
        assertThat(heelDistance(result[5])).isWithin(0.5).of(33.0)
        assertThat(heelDistance(result[4])).isWithin(0.5).of(30.0)
    }

    /** Verkürzung ist echte Perspektive — sie darf NICHT herausgezogen werden. */
    @Test
    fun `verkuerzter Knochen bleibt verkuerzt`() {
        val frames = (0 until 10).map { i ->
            frame(i * 83L, scale = 100f, upperArm = if (i == 5) 45f else 80f)
        }
        val result = enforceRigidSkeleton(frames)
        assertThat(upperArmLength(result[5])).isWithin(0.5).of(45.0)
    }

    /** Erst unterhalb der weiten Untergrenze wird auch nach unten geklemmt. */
    @Test
    fun `absurd kurzer Knochen wird auf die Untergrenze gehoben`() {
        val frames = (0 until 10).map { i ->
            frame(i * 83L, scale = 100f, upperArm = if (i == 5) 5f else 80f)
        }
        val result = enforceRigidSkeleton(frames)
        // Untergrenze 80 · 0,35 = 28.
        assertThat(upperArmLength(result[5])).isWithin(0.5).of(28.0)
    }

    /**
     * Der Kern der Normierung: schrumpft die ganze Person (Abstand zur Kamera), müssen
     * die Knochen MITschrumpfen dürfen. Gegen eine absolute Median-Länge wäre das ein
     * Verstoß gewesen — genau der Grund, warum die alte Prüfung nichts fing.
     *
     * Die Rate ist bewusst physikalisch gewählt (−15 % über ~8 s): die Körpergrößen-
     * Referenz ist ein rollierender Median über ~2 s, damit eine DREHUNG des Kletterers
     * sie nicht mitzieht. Eine Größenänderung, die schneller wäre als das, gibt es bei
     * fester Kamera nicht — siehe GhostTuning.PERSON_SCALE_WINDOW.
     */
    @Test
    fun `global kleinere Pose wird nicht als Verstoss behandelt`() {
        val frames = (0 until 100).map { i ->
            val scale = 100f - i * 0.15f
            frame(i * 83L, scale = scale, upperArm = scale * 0.8f)
        }
        val result = enforceRigidSkeleton(frames)
        result.forEachIndexed { i, f ->
            assertThat(upperArmLength(f)).isWithin(0.5).of(upperArmLength(frames[i]))
        }
    }

    @Test
    fun `Richtung des Knochens bleibt erhalten`() {
        val frames = (0 until 10).map { i ->
            val armY = if (i == 5) 300f else 80f
            GhostPoseFrame(
                timeMs = i * 83L,
                landmarks = listOf(
                    landmark(T.LEFT_SHOULDER, 50f, 100f),
                    landmark(T.RIGHT_SHOULDER, 150f, 100f),
                    landmark(T.LEFT_HIP, 50f, 200f),
                    landmark(T.RIGHT_HIP, 150f, 200f),
                    // Ellbogen diagonal: gleiche Anteile in x und y.
                    landmark(T.LEFT_ELBOW, 50f + armY, 100f + armY),
                ),
            )
        }
        val result = enforceRigidSkeleton(frames)
        val shoulder = result[5].landmarks.single { it.type == T.LEFT_SHOULDER }
        val elbow = result[5].landmarks.single { it.type == T.LEFT_ELBOW }
        // Diagonale Richtung (dx == dy) bleibt, nur die Länge ist korrigiert.
        assertThat(elbow.x - shoulder.x).isWithin(0.5f).of(elbow.y - shoulder.y)
    }

    @Test
    fun `Kette wird proximal nach distal aufgebaut`() {
        // Ober- UND Unterarm zu lang: der korrigierte Ellbogen muss Ansatzpunkt des
        // Unterarms sein, sonst bliebe das Handgelenk falsch weit weg.
        val frames = (0 until 10).map { i ->
            val factor = if (i == 5) 3f else 1f
            GhostPoseFrame(
                timeMs = i * 83L,
                landmarks = listOf(
                    landmark(T.LEFT_SHOULDER, 50f, 100f),
                    landmark(T.RIGHT_SHOULDER, 150f, 100f),
                    landmark(T.LEFT_HIP, 50f, 200f),
                    landmark(T.RIGHT_HIP, 150f, 200f),
                    landmark(T.LEFT_ELBOW, 50f, 100f + 80f * factor),
                    landmark(T.LEFT_WRIST, 50f, 100f + 160f * factor),
                ),
            )
        }
        val result = enforceRigidSkeleton(frames)
        val elbow = result[5].landmarks.single { it.type == T.LEFT_ELBOW }
        val wrist = result[5].landmarks.single { it.type == T.LEFT_WRIST }
        // Beide Knochen auf ihrer Obergrenze 80 · 1,1 = 88.
        assertThat((elbow.y - 100f).toDouble()).isWithin(0.5).of(88.0)
        assertThat(distance(elbow, wrist)).isWithin(0.5).of(88.0)
    }

    @Test
    fun `leere und unsichere Frames bleiben unveraendert`() {
        val frames = listOf(
            GhostPoseFrame(0L, emptyList()),
            GhostPoseFrame(83L, listOf(landmark(T.LEFT_SHOULDER, 10f, 10f, confidence = 0.1f))),
        )
        assertThat(enforceRigidSkeleton(frames)).isEqualTo(frames)
    }
}
