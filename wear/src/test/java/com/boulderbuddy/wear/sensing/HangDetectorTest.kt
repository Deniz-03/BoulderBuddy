package com.boulderbuddy.wear.sensing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * State-Machine der Auto-Hang-Erkennung (B.2), abgespielt gegen eine **echte Aufnahme**
 * (B.5.2/B.5.4) statt gegen synthetische Ströme: `hangboard_garmin_2026-08-02.csv`,
 * 187 s Hangboard-Training, 25 Hz, von einer Garmin-Uhr am rechten Handgelenk
 * aufgezeichnet und auf die Android-Wear-Achsen umgerechnet. Herkunft, Umrechnung und
 * Label-Ableitung stehen im Kopf der Fixture.
 *
 * Die Aufnahme enthält 8 Hängephasen, davon 6 länger als
 * [HangDetectionConfig.minHangMs]; die erste (1,9 s, Blick auf die Uhr beim Starten) und
 * die letzte (2,8 s, vom Aufnahmeende abgeschnitten) liegen darunter.
 */
class HangDetectorTest {

    private val config = HangDetectionConfig()

    // --- Fixture ------------------------------------------------------------------------

    private val log: ParsedSensorLog by lazy {
        val text = checkNotNull(javaClass.getResourceAsStream(FIXTURE)) {
            "Fixture $FIXTURE fehlt im Test-Klassenpfad"
        }.bufferedReader().readText()
        SensorLogParser.parse(text)
    }

    /** Die gelabelten Hängephasen als `[von, bis)` — Bodenwahrheit aus der Aufnahme. */
    private fun labeledHangPhases(): List<LongRange> =
        log.labels.mapIndexedNotNull { i, mark ->
            if (mark.label != "HANG") return@mapIndexedNotNull null
            val end = log.labels.getOrNull(i + 1)?.timeMs ?: log.samples.last().timeMs
            mark.timeMs until end
        }

    private fun run(
        detector: HangDetector,
        samples: List<SensorSample> = log.samples,
    ): List<HangDetectionEvent> = samples.mapNotNull { detector.onSample(it) }

    // --- Aufnahme selbst ----------------------------------------------------------------

    @Test
    fun `Aufnahme liefert den erwarteten Sensorstrom und Label-Zeitstrahl`() {
        assertThat(log.samples).hasSize(9_350) // 4675 GRAV + 4675 LIN, 25 Hz über 187 s
        assertThat(log.samples.count { it.type == SampleType.GRAVITY }).isEqualTo(4_675)
        assertThat(log.samples.last().timeMs).isEqualTo(186_960)

        // Sauberer Wechsel HANG/REST über die ganze Aufnahme, beginnend mit dem Blick
        // auf die Uhr beim Starten (Hand oben) — 8 Hänge-, 7 Pausenphasen.
        assertThat(log.labels.map { it.label })
            .containsExactlyElementsIn(List(15) { if (it % 2 == 0) "HANG" else "REST" })
            .inOrder()
    }

    @Test
    fun `Aufnahme enthaelt sechs Haengephasen oberhalb von minHangMs`() {
        val phases = labeledHangPhases()
        assertThat(phases).hasSize(8)
        assertThat(phases.filter { it.count() >= config.minHangMs }).hasSize(6)
    }

    // --- Replay -------------------------------------------------------------------------

    @Test
    fun `Replay erkennt genau die Saetze oberhalb von minHangMs`() {
        val detector = HangDetector(config)
        val events = run(detector)
        val segments = detector.finish(log.samples.last().timeMs)

        assertThat(events.filterIsInstance<HangDetectionEvent.HangStarted>()).hasSize(6)
        assertThat(events.filterIsInstance<HangDetectionEvent.HangEnded>()).hasSize(6)
        assertThat(segments).hasSize(6)

        // Gemessene Werte der Aufnahme. Die Pause des letzten Satzes ist 0 — nach ihm
        // beginnt kein weiterer, genau wie beim manuellen Timer.
        assertThat(segments).containsExactly(
            HangSegment(hangMs = 7_400, restMs = 17_160),
            HangSegment(hangMs = 9_240, restMs = 18_240),
            HangSegment(hangMs = 10_480, restMs = 20_080),
            HangSegment(hangMs = 7_200, restMs = 23_920),
            HangSegment(hangMs = 12_000, restMs = 17_240),
            HangSegment(hangMs = 9_760, restMs = 0),
        ).inOrder()
    }

    @Test
    fun `jeder erkannte Satz liegt innerhalb seiner gelabelten Haengephase`() {
        val detector = HangDetector(config)
        val events = run(detector)
        val starts = events.filterIsInstance<HangDetectionEvent.HangStarted>().map { it.atMs }
        val ends = events.filterIsInstance<HangDetectionEvent.HangEnded>().map { it.atMs }
        val phases = labeledHangPhases().filter { it.count() >= config.minHangMs }

        // Kein erkannter Satz ragt in eine Pause hinein: Start frühestens mit der Phase,
        // Ende spätestens mit ihr. Das prüft die Erkennung gegen die Aufnahme, nicht
        // gegen sich selbst — die Labels stammen aus der Orientierung, die Segmente aus
        // Orientierung *und* Bewegungsvarianz *und* Entprellung.
        starts.zip(ends).zip(phases).forEach { (segment, phase) ->
            val (start, end) = segment
            assertThat(start).isAtLeast(phase.first)
            assertThat(end).isAtMost(phase.last + 1)
        }
    }

    @Test
    fun `zu kurze Haengephasen der Aufnahme erzeugen keinen Satz`() {
        val short = labeledHangPhases().filter { it.count() < config.minHangMs }
        assertThat(short).hasSize(2) // 1,9 s beim Starten + 2,8 s am abgeschnittenen Ende

        val detector = HangDetector(config)
        val starts = run(detector).filterIsInstance<HangDetectionEvent.HangStarted>()
        short.forEach { phase ->
            assertThat(starts.none { it.atMs in phase }).isTrue()
        }
    }

    @Test
    fun `Arme unten und ruhig erzeugt trotz Stille keinen Satz`() {
        // Eine reine Pausenphase (Arm hängt seitlich, σ ≈ 0,1 m/s² — ruhiger als beim
        // Hängen). Nur die Orientierung verhindert hier den Fehlstart.
        val restOnly = log.samples.filter { it.timeMs in 53_000..66_000 }
        val detector = HangDetector(config)

        assertThat(run(detector, restOnly)).isEmpty()
        assertThat(detector.state).isEqualTo(HangState.IDLE)
    }

    @Test
    fun `finish waehrend eines laufenden Satzes schliesst ihn ab`() {
        // Abbruch bei 105 s — mitten im vierten Satz (erkannter Beginn: 100,56 s).
        val untilCut = log.samples.filter { it.timeMs < 105_000 }
        val detector = HangDetector(config)
        run(detector, untilCut)
        assertThat(detector.state).isEqualTo(HangState.HANGING)

        val segments = detector.finish(105_000)
        assertThat(segments).hasSize(4)
        assertThat(segments.last().hangMs).isEqualTo(4_440)
        assertThat(detector.state).isEqualTo(HangState.ENDED)
    }

    // --- Kalibrierung (B.5.3) -----------------------------------------------------------

    @Test
    fun `invertierte Orientierungsachse erkennt die Pausen statt der Saetze`() {
        // Absicherung gegen ein Vorzeichen-Vertauschen in [HangDetectionConfig]: mit
        // orientationSign = -1 findet die Heuristik 7 saubere „Sätze" — die aber allesamt
        // in Pausenphasen beginnen. Der Fehler wäre ohne echte Aufnahme unsichtbar, weil
        // beide Zustände „Arm senkrecht und still" sind.
        val inverted = config.copy(orientationSign = -1)
        val detector = HangDetector(inverted)
        val starts = run(detector).filterIsInstance<HangDetectionEvent.HangStarted>()

        assertThat(starts).hasSize(7)
        starts.forEach { start ->
            val label = log.labels.last { it.timeMs <= start.atMs }.label
            assertThat(label).isEqualTo("REST")
        }
    }

    @Test
    fun `gemessene Haengedauer bleibt systematisch hinter der gelabelten zurueck`() {
        // Dokumentiert den offenen Kalibrierpunkt (B.5.3/M5), statt ihn zu verstecken:
        // Der Detektor misst jeden Satz zu kurz — im Mittel um ~4,3 s, im Extremfall um
        // 8,8 s (Satz 4: eine Armbewegung bei ~108 s beendet ihn vorzeitig, und der Rest
        // der Phase wird nicht wieder aufgenommen, weil σ beim Hängen über stillStdDev
        // von 0,8 m/s² liegt). Ursachen: hangConfirmMs (1,5 s) verzögert den Beginn, und
        // stillStdDev ist für echtes Hängen (σ bis ~2,0 m/s²) zu eng.
        val detector = HangDetector(config)
        run(detector)
        val segments = detector.finish(log.samples.last().timeMs)
        val phases = labeledHangPhases().filter { it.count() >= config.minHangMs }

        val shortfalls = segments.zip(phases).map { (segment, phase) ->
            phase.count() - segment.hangMs
        }
        assertThat(shortfalls.all { it > 0 }).isTrue()
        assertThat(shortfalls.max()).isEqualTo(8_800)
        assertThat(shortfalls.sum() / shortfalls.size).isIn(4_000L..4_600L)
    }

    private companion object {
        const val FIXTURE = "/hangboard_garmin_2026-08-02.csv"
    }
}
