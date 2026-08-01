package com.boulderbuddy.ghost.analysis

import com.boulderbuddy.ghost.model.GhostPoint
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** JVM-Tests für die Sturz-/Abbrucherkennung (M5, P5). Y wächst nach unten. */
class FallDetectionTest {

    /** Gleichmäßiger Anstieg: 10 px/Frame nach oben. */
    private fun steadyClimb(frames: Int = 60): List<GhostPoint> =
        List(frames) { GhostPoint(300f, 1000f - it * 10f) }

    @Test
    fun `gleichmaessiges Klettern loest keinen Abbruch aus`() {
        assertThat(detectAbortFrame(steadyClimb())).isNull()
    }

    @Test
    fun `Sturz nach halber Route wird nahe dem Sturzbeginn erkannt`() {
        // 30 Frames hoch (10 px/Frame), dann 10 Frames Absturz (80 px/Frame abwärts).
        val climb = steadyClimb(30)
        val fallStartY = climb.last().y
        val fall = List(10) { GhostPoint(300f, fallStartY + (it + 1) * 80f) }
        val abortIdx = detectAbortFrame(climb + fall)
        assertThat(abortIdx).isNotNull()
        // Erkennung darf erst im Sturz liegen, nicht im normalen Klettern davor.
        assertThat(abortIdx!!).isAtLeast(28)
        assertThat(abortIdx).isAtMost(35)
    }

    @Test
    fun `einzelner Ausreisser-Frame ohne anhaltendes Fallen ist kein Abbruch`() {
        // Ein einzelner Mess-Spike nach unten (1 Frame), danach geht es normal weiter
        // hoch — die Mindestdauer-Bedingung muss das entprellen.
        val climb = steadyClimb(40).toMutableList()
        climb[20] = GhostPoint(300f, climb[20].y + 120f)
        assertThat(detectAbortFrame(climb)).isNull()
    }

    @Test
    fun `dynamischer Zug nach OBEN ist kein Abbruch`() {
        // Schneller Zug: 5 Frames mit 60 px/Frame nach oben — Spike ja, abwärts nein.
        val before = steadyClimb(20)
        val startY = before.last().y
        val dyno = List(5) { GhostPoint(300f, startY - (it + 1) * 60f) }
        val after = List(20) { GhostPoint(300f, dyno.last().y - (it + 1) * 10f) }
        assertThat(detectAbortFrame(before + dyno + after)).isNull()
    }

    @Test
    fun `zu kurze Trajektorie ergibt null`() {
        assertThat(detectAbortFrame(steadyClimb(3))).isNull()
    }
}
