package com.boulderbuddy.wear.sensing

import kotlin.math.sqrt

// =============================================================================
// Detektions-State-Machine der Auto-Hang-Erkennung (B.2) — bewusst ANDROID-FREI:
// reine Kotlin-Logik f(sensorSample, state) -> event, damit sie auf der JVM gegen
// synthetische und aufgezeichnete Sensor-Logs (B.5.1/B.5.4) unit-testbar ist.
// =============================================================================

/** Sensortyp eines Samples — gespiegelt aus den Android-Sensortypen, ohne Android-Import. */
enum class SampleType { GRAVITY, LINEAR }

/** Ein Sensor-Sample im gemeinsamen Zeitstrahl (ms, monotone Quelle wie elapsedRealtime). */
data class SensorSample(
    val timeMs: Long,
    val type: SampleType,
    val x: Float,
    val y: Float,
    val z: Float,
)

/**
 * Zentrale, benannte Schwellen-Konfiguration (B.2/B.5.3). Die Default-Werte sind der
 * dokumentierte **Startpunkt der Heuristik** (Q2: Uhr am hängenden Arm, Arm über Kopf) —
 * verbindlich werden sie erst durch die Offline-Kalibrierung an echten Aufnahmen (M5).
 */
data class HangDetectionConfig(
    /**
     * Achse des Gravitationsvektors, die beim Hängen dominiert (0=x, 1=y, 2=z).
     * Uhr am gehobenen Arm: die Y-Achse des Geräts zeigt entlang des Unterarms —
     * hängt der Arm über Kopf, zeigt die Gravitation in Richtung -Y.
     */
    val orientationAxis: Int = 1,
    /** Vorzeichen der Achse beim Hängen (-1 = Gravitation entlang negativer Achse). */
    val orientationSign: Int = -1,
    /** Mindestanteil der Gravitation (m/s²) entlang der Achse, damit „Arm über Kopf" gilt. */
    val orientationMinG: Float = 6f,
    /** Gleitendes Varianz-Fenster über die lineare Beschleunigung (ms). */
    val windowMs: Long = 1_000,
    /** σ_still: Std-Abweichung (m/s²) der linearen Beschleunigung, unter der „still" gilt. */
    val stillStdDev: Float = 0.8f,
    /** σ_move: Std-Abweichung (m/s²), über der ein Bewegungs-Spike (Loslassen) gilt. */
    val moveStdDev: Float = 2.5f,
    /** t_min_hang: Hänge-Bedingung muss so lange stabil sein, bevor ein Satz startet (ms). */
    val hangConfirmMs: Long = 1_500,
    /** Debounce Loslassen: Nicht-Hängen muss so lange anhalten, bevor der Satz endet (ms). */
    val releaseConfirmMs: Long = 800,
    /** Sätze kürzer als das werden verworfen (Antippen/Umgreifen zählt nicht). */
    val minHangMs: Long = 3_000,
)

/** Zustände der Erkennung. ENDED erreicht die Machine nur über [HangDetector.finish]. */
enum class HangState { IDLE, HANGING, RESTING, ENDED }

/** Von der State-Machine abgeleitete Ereignisse (Basis für Haptik + Live-UI, M3). */
sealed interface HangDetectionEvent {
    /** Ein Satz hat begonnen (rückdatiert auf den Beginn der stabilen Hänge-Bedingung). */
    data class HangStarted(val atMs: Long) : HangDetectionEvent

    /** Ein Satz ist zu Ende; [hangMs] = gemessene Hängedauer. */
    data class HangEnded(val atMs: Long, val hangMs: Long) : HangDetectionEvent
}

/** Ein erkannter Satz: Hängedauer + anschließende Pause (letzter Satz: 0). */
data class HangSegment(val hangMs: Long, val restMs: Long)

/**
 * Die Detektions-State-Machine: `IDLE → HANGING ⇄ RESTING → ENDED`.
 *
 * Heuristik (B.2): **Hängen** = Gravitationsvektor entlang der konfigurierten Achse
 * („Arm über Kopf") UND geringe Bewegungsvarianz (< σ_still) über das gleitende Fenster.
 * **Loslassen** = Bewegungs-Spike (> σ_move) ODER Orientierungsverlust. Beide Übergänge
 * sind entprellt ([HangDetectionConfig.hangConfirmMs] / [HangDetectionConfig.releaseConfirmMs]),
 * zu kurze Sätze (< [HangDetectionConfig.minHangMs]) werden verworfen — Zappeln, Umgreifen
 * oder Antippen der Leiste erzeugen so keine Fehl-Segmente.
 *
 * Ein Sample pro Aufruf über [onSample]; abgeschlossene Sätze sammeln sich als
 * [HangSegment]s (Pause = Lücke bis zum nächsten Satz) und werden mit [finish] abgeholt.
 */
class HangDetector(private val config: HangDetectionConfig = HangDetectionConfig()) {

    var state: HangState = HangState.IDLE
        private set

    /** Beginn der aktuellen Phase (Satz bzw. Pause) — für die Live-Anzeige (M3). */
    var phaseStartMs: Long = 0L
        private set

    /** Anzahl bisher abgeschlossener (gültiger) Sätze. */
    val completedSets: Int get() = segments.size

    private val segments = mutableListOf<HangSegment>()

    // Letzter bekannter Gravitationsvektor (Orientierung ändert sich langsam → letzter Wert reicht).
    private var gravity: FloatArray? = null

    // Gleitendes Fenster der linearen Beschleunigungs-Magnituden für die Varianz.
    private val window = ArrayDeque<Pair<Long, Float>>()

    // Entprellung: Beginn der aktuell stabilen Hänge- bzw. Loslass-Bedingung (null = nicht aktiv).
    private var hangCandidateSince: Long? = null
    private var releaseCandidateSince: Long? = null

    // Zeitanker des zuletzt beendeten Satzes (für die Pausenmessung bis zum nächsten).
    private var lastHangEndMs: Long? = null

    /**
     * Verarbeitet ein Sample und liefert das ggf. ausgelöste Ereignis (sonst null).
     * Samples müssen zeitlich aufsteigend eintreffen (ein Sensorstrom, eine Zeitbasis).
     */
    fun onSample(sample: SensorSample): HangDetectionEvent? {
        if (state == HangState.ENDED) return null
        when (sample.type) {
            SampleType.GRAVITY -> {
                gravity = floatArrayOf(sample.x, sample.y, sample.z)
                return null // Übergänge werden vom (häufigeren) Bewegungsstrom getrieben.
            }
            SampleType.LINEAR -> {
                updateWindow(sample)
                return evaluate(sample.timeMs)
            }
        }
    }

    /**
     * Beendet die Session (Nutzer-Aktion, M3): schließt einen ggf. laufenden Satz ab und
     * liefert alle erkannten Segmente. Pause des letzten Satzes = 0 (wie beim manuellen Timer).
     */
    fun finish(nowMs: Long): List<HangSegment> {
        if (state == HangState.HANGING) {
            val hangMs = nowMs - phaseStartMs
            if (hangMs >= config.minHangMs) segments.add(HangSegment(hangMs, restMs = 0))
        }
        state = HangState.ENDED
        return segments.toList()
    }

    private fun updateWindow(sample: SensorSample) {
        val magnitude = sqrt(sample.x * sample.x + sample.y * sample.y + sample.z * sample.z)
        window.addLast(sample.timeMs to magnitude)
        while (window.isNotEmpty() && window.first().first < sample.timeMs - config.windowMs) {
            window.removeFirst()
        }
    }

    private fun evaluate(nowMs: Long): HangDetectionEvent? {
        val oriented = isArmOverhead()
        val stdDev = windowStdDev()

        return when (state) {
            HangState.IDLE, HangState.RESTING -> {
                val hangingCondition = oriented && stdDev < config.stillStdDev
                if (hangingCondition) {
                    val since = hangCandidateSince ?: nowMs.also { hangCandidateSince = it }
                    if (nowMs - since >= config.hangConfirmMs) startHang(since) else null
                } else {
                    hangCandidateSince = null
                    null
                }
            }
            HangState.HANGING -> {
                val releaseCondition = !oriented || stdDev > config.moveStdDev
                if (releaseCondition) {
                    val since = releaseCandidateSince ?: nowMs.also { releaseCandidateSince = it }
                    if (nowMs - since >= config.releaseConfirmMs) endHang(since) else null
                } else {
                    releaseCandidateSince = null
                    null
                }
            }
            HangState.ENDED -> null
        }
    }

    private fun startHang(atMs: Long): HangDetectionEvent {
        // Pause des Vorgänger-Satzes = Lücke zwischen Satzende und diesem Satzbeginn.
        lastHangEndMs?.let { endedAt ->
            val lastIndex = segments.lastIndex
            if (lastIndex >= 0) {
                segments[lastIndex] = segments[lastIndex].copy(restMs = atMs - endedAt)
            }
        }
        state = HangState.HANGING
        phaseStartMs = atMs
        hangCandidateSince = null
        releaseCandidateSince = null
        return HangDetectionEvent.HangStarted(atMs)
    }

    private fun endHang(atMs: Long): HangDetectionEvent? {
        val hangMs = atMs - phaseStartMs
        state = HangState.RESTING
        hangCandidateSince = null
        releaseCandidateSince = null
        return if (hangMs >= config.minHangMs) {
            segments.add(HangSegment(hangMs, restMs = 0))
            lastHangEndMs = atMs
            phaseStartMs = atMs
            HangDetectionEvent.HangEnded(atMs, hangMs)
        } else {
            // Zu kurz (Antippen/Umgreifen): kein Segment, Pausenanker des Vorgängers bleibt.
            phaseStartMs = atMs
            null
        }
    }

    private fun isArmOverhead(): Boolean {
        val g = gravity ?: return false
        return g[config.orientationAxis] * config.orientationSign >= config.orientationMinG
    }

    // Std-Abweichung der Magnituden im Fenster. Zu wenige Samples (< 5) = "keine Aussage" →
    // Maximalwert, damit ein leeres/frisches Fenster nie fälschlich als "still" gilt.
    private fun windowStdDev(): Float {
        if (window.size < 5) return Float.MAX_VALUE
        val mean = window.sumOf { it.second.toDouble() } / window.size
        val variance = window.sumOf {
            val d = it.second.toDouble() - mean
            d * d
        } / window.size
        return sqrt(variance).toFloat()
    }
}
