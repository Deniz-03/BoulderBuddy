package com.boulderbuddy.ui.components

import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import org.junit.Test

/**
 * JVM-Tests der Drift-Ausregelung im Side-by-Side-Modus. Vorher wurde bei > 200 ms
 * Drift hart geseekt — driften die Player systematisch, reißt diese Schwelle zyklisch
 * und der Vergleich springt jedes Mal sichtbar. Genau das war als „das DTW ist wieder
 * da" aufgefallen, obwohl in diesem Modus gar kein DTW läuft.
 */
class SideBySideSyncTest {

    @Test
    fun `ohne Drift laeuft der Folge-Player mit normalem Tempo`() {
        assertThat(followerSpeed(0L)).isWithin(1e-6f).of(1f)
    }

    /** Vorsprung → langsamer, Rückstand → schneller. */
    @Test
    fun `die Korrektur zeigt in die richtige Richtung`() {
        assertThat(followerSpeed(300L)).isLessThan(1f)
        assertThat(followerSpeed(-300L)).isGreaterThan(1f)
    }

    /**
     * Der Kern: die Korrektur muss klein genug bleiben, um unsichtbar zu sein. Eine
     * Tempoänderung von mehr als ~10 % liest sich als Zeitlupe bzw. Zeitraffer und
     * wäre nicht besser als der Sprung, den sie ersetzt.
     */
    @Test
    fun `die Korrektur bleibt unsichtbar klein`() {
        listOf(-999L, -500L, -200L, 0L, 200L, 500L, 999L).forEach { drift ->
            assertThat(abs(followerSpeed(drift) - 1f)).isLessThan(0.1f)
        }
    }

    @Test
    fun `die Korrektur ist auch bei absurder Drift begrenzt`() {
        assertThat(followerSpeed(60_000L)).isAtLeast(0.9f)
        assertThat(followerSpeed(-60_000L)).isAtMost(1.1f)
    }

    /**
     * Normale Lauf-Drift wird ausgeregelt, ein echter Sprung (Scrub, Neustart) dagegen
     * hart nachgezogen — dort erwartet man den Schnitt ohnehin, und eine Tempokorrektur
     * bräuchte dafür unangenehm lange.
     */
    @Test
    fun `nur echte Spruenge werden hart nachgezogen`() {
        assertThat(needsHardResync(0L)).isFalse()
        assertThat(needsHardResync(200L)).isFalse()
        assertThat(needsHardResync(-900L)).isFalse()
        assertThat(needsHardResync(5_000L)).isTrue()
        assertThat(needsHardResync(-5_000L)).isTrue()
    }

    /**
     * Konvergenz: aus 200 ms Drift muss die Regelung in wenigen Sekunden herauskommen,
     * ohne zu überschwingen — sonst pendelt der Folge-Player um die Referenz.
     */
    @Test
    fun `die Regelung konvergiert ohne Ueberschwingen`() {
        var drift = 200.0
        val intervalS = 0.2
        repeat(50) {
            val speed = followerSpeed(drift.toLong())
            // Pro Intervall verschiebt sich die Drift um die Tempodifferenz.
            drift += (speed - 1f) * intervalS * 1000
            // Nie über null hinaus auf die andere Seite kippen.
            assertThat(drift).isGreaterThan(-20.0)
        }
        assertThat(abs(drift)).isLessThan(20.0)
    }
}
