package com.boulderbuddy.wear.sensing

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.sin
import kotlin.random.Random

/**
 * State-Machine der Auto-Hang-Erkennung (B.2/B.5.4), abgespielt gegen **synthetische**
 * Sensorströme im Format des Sensor-Loggings (50 Hz, gemeinsame Zeitbasis). Die Generatoren
 * bilden die kalibrierrelevanten Situationen nach (Hängen = Arm über Kopf + still; Pause =
 * Arm unten + Bewegung; Zappeln = Spikes am Griff). Sobald echte Aufnahmen existieren
 * (B.5.2), kommen Replay-Tests über [SensorLogParser] mit denselben Assertions dazu (M5).
 */
class HangDetectorTest {

    private val config = HangDetectionConfig()

    // Entprellung + Fensterträgheit verschieben erkannte Übergänge um bis zu ~2,5 s.
    private fun assertNear(actual: Long, expected: Long, toleranceMs: Long = 2_500) {
        assertThat(actual).isAtLeast(expected - toleranceMs)
        assertThat(actual).isAtMost(expected + toleranceMs)
    }

    // --- Synthetischer Sensorstrom -----------------------------------------------------

    /** Baut einen 50-Hz-Strom aus Phasen; jede Phase liefert Gravitation + lineare Beschl. */
    private class StreamBuilder {
        val samples = mutableListOf<SensorSample>()
        var timeMs = 0L
            private set
        private val random = Random(42)

        /** Hängen: Gravitation entlang -Y (Arm über Kopf), nur Sensorrauschen (~0.1 m/s²). */
        fun hang(durationMs: Long) = phase(durationMs, gravityY = -9.6f, noise = 0.1f)

        /** Pause: Arm unten (Gravitation +Y), deutliche Bewegung (Schütteln, Gehen). */
        fun rest(durationMs: Long) = phase(durationMs, gravityY = 9.4f, noise = 4f)

        /** Zappeln am Griff: Orientierung bleibt „über Kopf", aber starke Bewegungs-Spikes. */
        fun wiggleWhileGripping(durationMs: Long) =
            phase(durationMs, gravityY = -9.6f, noise = 5f)

        /** Arm unten und ruhig (z.B. Uhr abgelegt): still, aber falsche Orientierung. */
        fun armDownStill(durationMs: Long) = phase(durationMs, gravityY = 9.6f, noise = 0.1f)

        private fun phase(durationMs: Long, gravityY: Float, noise: Float) {
            val end = timeMs + durationMs
            while (timeMs < end) {
                samples.add(SensorSample(timeMs, SampleType.GRAVITY, 0.5f, gravityY, 1f))
                // Sinus + Rauschen: bewegte Phasen bekommen echte Varianz, stille fast keine.
                val wobble = (noise * sin(timeMs / 60.0)).toFloat() +
                    (random.nextFloat() - 0.5f) * noise
                samples.add(SensorSample(timeMs, SampleType.LINEAR, wobble, wobble / 2, wobble))
                timeMs += 20 // 50 Hz
            }
        }
    }

    private fun run(detector: HangDetector, samples: List<SensorSample>): List<HangDetectionEvent> =
        samples.mapNotNull { detector.onSample(it) }

    // --- Tests --------------------------------------------------------------------------

    @Test
    fun `zwei Saetze mit Pause ergeben zwei Segmente mit gemessenen Dauern`() {
        val stream = StreamBuilder().apply {
            rest(3_000)     // Anlaufen zum Board
            hang(10_000)    // Satz 1
            rest(30_000)    // Pause
            hang(8_000)     // Satz 2
            rest(2_000)     // Abtreten vor dem Beenden
        }
        val detector = HangDetector(config)
        val events = run(detector, stream.samples)
        val segments = detector.finish(stream.timeMs)

        assertThat(events.filterIsInstance<HangDetectionEvent.HangStarted>()).hasSize(2)
        assertThat(events.filterIsInstance<HangDetectionEvent.HangEnded>()).hasSize(2)
        assertThat(segments).hasSize(2)

        assertNear(segments[0].hangMs, 10_000)
        assertNear(segments[0].restMs, 30_000)
        assertNear(segments[1].hangMs, 8_000)
        assertThat(segments[1].restMs).isEqualTo(0) // letzter Satz: keine Pause danach
    }

    @Test
    fun `kurzes Antippen der Leiste erzeugt keinen Satz`() {
        val stream = StreamBuilder().apply {
            rest(3_000)
            hang(2_000)     // kürzer als minHangMs (3 s) → verwerfen
            rest(5_000)
        }
        val detector = HangDetector(config)
        run(detector, stream.samples)
        val segments = detector.finish(stream.timeMs)

        assertThat(segments).isEmpty()
    }

    @Test
    fun `Zappeln am Griff beendet den Satz nicht - die echte Pause schon`() {
        // Der Bewegungs-Spike bleibt (inkl. Fensterträgheit) unter der Entprell-Zeit —
        // hier bewusst mit großzügigem Debounce, das Kalibrieren der echten Werte ist M5.
        val wiggleTolerantConfig = config.copy(releaseConfirmMs = 2_000)
        val stream = StreamBuilder().apply {
            rest(3_000)
            hang(6_000)
            wiggleWhileGripping(400)  // Ausschütteln, deutlich unter dem Debounce
            hang(6_000)
            rest(10_000)
        }
        val detector = HangDetector(wiggleTolerantConfig)
        val events = run(detector, stream.samples)
        val segments = detector.finish(stream.timeMs)

        assertThat(events.filterIsInstance<HangDetectionEvent.HangStarted>()).hasSize(1)
        assertThat(segments).hasSize(1)
        // Beide Hang-Teile + Wackler zählen als EIN Satz von ~12,4 s.
        assertNear(segments[0].hangMs, 12_400, toleranceMs = 3_500)
    }

    @Test
    fun `finish waehrend eines laufenden Satzes schliesst ihn ab`() {
        val stream = StreamBuilder().apply {
            rest(3_000)
            hang(7_000)     // Nutzer beendet die Session, während er noch hängt
        }
        val detector = HangDetector(config)
        run(detector, stream.samples)
        assertThat(detector.state).isEqualTo(HangState.HANGING)

        val segments = detector.finish(stream.timeMs)
        assertThat(segments).hasSize(1)
        assertNear(segments[0].hangMs, 7_000)
        assertThat(detector.state).isEqualTo(HangState.ENDED)
    }

    @Test
    fun `ohne Haenge-Orientierung startet trotz Stille kein Satz`() {
        val stream = StreamBuilder().apply {
            armDownStill(10_000)
        }
        val detector = HangDetector(config)
        val events = run(detector, stream.samples)

        assertThat(events).isEmpty()
        assertThat(detector.state).isEqualTo(HangState.IDLE)
    }

    // --- Log-Replay (Format B.5.1) ------------------------------------------------------

    @Test
    fun `Parser liest das Logging-Format und Replay erkennt den Satz`() {
        // Miniatur-Log im exakten Format des SensorLoggingService, inkl. Kommentar,
        // Label-Zeilen und einer defekten Zeile (abgerissene Aufnahme).
        val csv = buildString {
            appendLine("# BoulderBuddy Sensor-Log v1 — tMs;GRAV|LIN;x;y;z bzw. tMs;LABEL;<label>")
            appendLine("0;LABEL;NONE")
            var t = 0L
            while (t < 4_000) {         // Pause: Arm unten, Bewegung
                appendLine("$t;GRAV;0.5;9.4;1.0")
                appendLine("$t;LIN;${3.0 * sin(t / 60.0)};1.0;0.5")
                t += 20
            }
            appendLine("$t;LABEL;HANG")
            while (t < 14_000) {        // Hängen: Arm über Kopf, still
                appendLine("$t;GRAV;0.5;-9.6;1.0")
                appendLine("$t;LIN;0.05;0.02;0.04")
                t += 20
            }
            appendLine("$t;LABEL;REST")
            while (t < 18_000) {        // wieder Pause
                appendLine("$t;GRAV;0.5;9.4;1.0")
                appendLine("$t;LIN;${3.0 * sin(t / 60.0)};1.0;0.5")
                t += 20
            }
            appendLine("$t;LIN;0.1;0.1")   // defekte Zeile → überspringen
        }

        val log = SensorLogParser.parse(csv)
        assertThat(log.labels.map { it.label }).containsExactly("NONE", "HANG", "REST").inOrder()

        val detector = HangDetector(config)
        run(detector, log.samples)
        val segments = detector.finish(18_000)

        assertThat(segments).hasSize(1)
        assertNear(segments[0].hangMs, 10_000)
    }
}
