package com.boulderbuddy.wear.presentation

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boulderbuddy.wear.data.PhoneConnector
import com.boulderbuddy.wear.data.PresetSyncClient
import com.boulderbuddy.wear.data.WearPreset
import com.boulderbuddy.wear.data.WearSettings
import com.boulderbuddy.wear.data.WearTimerConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Phase eines Hangboard-Satzes — spiegelt die Zustandsmaschine des Phone-ViewModels. */
enum class TimerPhase { HANG, REST, DONE }

/** Reiner UI-Zustand des Wear-Timers; der Screen rendert nur, was hier drinsteht. */
data class WearTimerUiState(
    val progress: Float,      // 0..1 – Füllgrad des Rings
    val timeText: String,     // mm:ss der laufenden Phase
    val phase: TimerPhase,
    val currentSet: Int,
    val totalSets: Int,
    val hangSec: Int,
    val restSec: Int,
    val isRunning: Boolean,
    val isConfigurable: Boolean, // true = Timer im Ausgangszustand → Sätze/Zeiten editierbar
    /** Vom Phone synchronisierte Presets (§0 Säule 4); leer, wenn (noch) keine bekannt. */
    val presets: List<WearPreset> = emptyList(),
)

/**
 * Treibt den Hangboard-Timer auf der Uhr: Countdown je Phase (HANG → REST → HANG …),
 * Satz-Hochzählen, Play/Pause/Reset — gleiche Zustandsmaschine wie das Phone-`HangboardTimerViewModel`,
 * aber eigenständig (getrennte Module, kein geteilter Code).
 *
 * Zusätzlich zur Phone-Version:
 * - **Vibrations-Feedback** bei jedem Phasenwechsel und am Ende (Uhr am Handgelenk).
 * - Ein bis zum Ende gelaufener Durchlauf wird via [PhoneConnector] ans gekoppelte Phone gemeldet,
 *   das ihn — falls dort eine Session aktiv ist — in genau diese trägt.
 *
 * Die Konfiguration wird lokal persistiert ([WearSettings], §0 Säule 4) — die Uhr merkt sich
 * die zuletzt genutzten Werte, statt bei jedem Öffnen auf 6/7/3 zurückzuspringen.
 */
class TimerViewModel(app: Application) : AndroidViewModel(app) {

    // Konfiguration (im UI per Stepper änderbar, solange nicht gestartet).
    private var totalSets = 6
    private var hangSec = 7
    private var restSec = 3

    // Vom Phone synchronisierte Presets (Data Layer, letzter gecachter Stand).
    private var presets: List<WearPreset> = emptyList()

    // Interner Lauf-Zustand (getrennt vom UI-State, damit Pause/Resume die Restsekunden behält).
    private var currentSet = 1
    private var phase = TimerPhase.HANG
    private var secondsLeft = hangSec
    private var running = false
    private var timerJob: Job? = null
    // Verhindert doppeltes Melden desselben abgeschlossenen Durchlaufs.
    private var reported = false

    private val _uiState = MutableStateFlow(snapshot())
    val uiState: StateFlow<WearTimerUiState> = _uiState.asStateFlow()

    private val vibrator: Vibrator? by lazy { resolveVibrator(app) }

    init {
        // Zuletzt genutzte Konfiguration laden (einmalig; Änderungen laufen über updateConfig).
        viewModelScope.launch {
            val config = WearSettings.timerConfig(app).first()
            totalSets = config.sets
            hangSec = config.hangSec
            restSec = config.restSec
            onReset()
        }
        // Presets vom Phone beobachten (ändern nur die Auswahl, nicht den Lauf-Zustand).
        viewModelScope.launch {
            PresetSyncClient.observePresets(app).collect {
                presets = it
                _uiState.value = snapshot()
            }
        }
    }

    /** Übernimmt ein synchronisiertes Preset als aktuelle Config (nur im Ausgangszustand). */
    fun applyPreset(preset: WearPreset) =
        updateConfig(sets = preset.sets, hang = preset.hangSec, rest = preset.restSec)

    fun onPlayPause() {
        if (running) pause() else start()
    }

    fun onReset() {
        timerJob?.cancel()
        currentSet = 1
        phase = TimerPhase.HANG
        secondsLeft = hangSec
        running = false
        reported = false
        _uiState.value = snapshot()
    }

    /** Sätze anpassen (nur im Ausgangszustand). Danach Reset, damit `secondsLeft` stimmt. */
    fun changeSets(delta: Int) = updateConfig(sets = totalSets + delta)

    /** Hängedauer anpassen (nur im Ausgangszustand). */
    fun changeHang(delta: Int) = updateConfig(hang = hangSec + delta)

    /** Pausendauer anpassen (nur im Ausgangszustand). */
    fun changeRest(delta: Int) = updateConfig(rest = restSec + delta)

    private fun updateConfig(
        sets: Int = totalSets,
        hang: Int = hangSec,
        rest: Int = restSec,
    ) {
        if (running || !isConfigurable()) return
        totalSets = sets.coerceIn(1, 30)
        hangSec = hang.coerceIn(1, 120)
        restSec = rest.coerceIn(0, 300)
        onReset()
        // Als „zuletzt genutzt" merken (§0 Säule 4).
        viewModelScope.launch {
            WearSettings.setTimerConfig(
                getApplication(),
                WearTimerConfig(sets = totalSets, hangSec = hangSec, restSec = restSec),
            )
        }
    }

    private fun start() {
        if (phase == TimerPhase.DONE) onReset()
        running = true
        _uiState.value = snapshot()
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (running && phase != TimerPhase.DONE) {
                delay(1000)
                secondsLeft--
                /*
                 * `while` statt `if`: eine Phase der Länge 0 darf keine Sekunde kosten.
                 *
                 * Bei Pause = 0 (erlaubt, `coerceIn(0, 300)`) setzte der Wechsel `secondsLeft`
                 * auf 0, und die Schleife wartete trotzdem erst die nächste volle Sekunde ab.
                 * Am Emulator gemessen: 10 Sätze à 1 s ohne Pause brauchten 20,7 s statt 10 —
                 * je Null-Pause eine Sekunde zu viel. Derselbe Fehler stand im Phone-ViewModel
                 * und ist dort am 08.08. behoben worden; die beiden Zustandsmaschinen sind
                 * bewusst parallel gebaut, und der Fehler ist mit ihnen mitgewandert.
                 *
                 * Terminiert immer, weil `hangSec` mindestens 1 ist (`coerceIn(1, 120)`).
                 */
                while (secondsLeft <= 0 && phase != TimerPhase.DONE) advancePhase()
                if (phase == TimerPhase.DONE && !reported) {
                    reported = true
                    reportCompletion()
                }
                _uiState.value = snapshot()
            }
        }
    }

    private fun pause() {
        running = false
        timerJob?.cancel()
        _uiState.value = snapshot()
    }

    // Phasenübergang, wenn die aktuelle Phase abgelaufen ist — inkl. Haptik-Feedback.
    private fun advancePhase() {
        when (phase) {
            TimerPhase.HANG ->
                if (currentSet >= totalSets) {
                    phase = TimerPhase.DONE
                    running = false
                    vibrate(VIBRATE_DONE)
                } else {
                    phase = TimerPhase.REST
                    secondsLeft = restSec
                    vibrate(VIBRATE_PHASE)
                }
            TimerPhase.REST -> {
                currentSet++
                phase = TimerPhase.HANG
                secondsLeft = hangSec
                vibrate(VIBRATE_PHASE)
            }
            TimerPhase.DONE -> running = false
        }
    }

    /** Meldet den abgeschlossenen Durchlauf ans Phone (dort ggf. Tracking in aktive Session). */
    private fun reportCompletion() {
        PhoneConnector.sendHangboardCompleted(
            context = getApplication(),
            completedSets = totalSets,
            totalSets = totalSets,
            hangSec = hangSec,
            restSec = restSec,
        )
    }

    private fun isConfigurable(): Boolean =
        !running && phase == TimerPhase.HANG && currentSet == 1 && secondsLeft == hangSec

    private fun snapshot(): WearTimerUiState {
        val duration = when (phase) {
            TimerPhase.HANG -> hangSec
            TimerPhase.REST -> restSec
            TimerPhase.DONE -> 1
        }.coerceAtLeast(1)
        return WearTimerUiState(
            progress = if (phase == TimerPhase.DONE) 1f else secondsLeft.toFloat() / duration,
            timeText = format(secondsLeft.coerceAtLeast(0)),
            phase = phase,
            currentSet = currentSet.coerceAtMost(totalSets),
            totalSets = totalSets,
            hangSec = hangSec,
            restSec = restSec,
            isRunning = running,
            isConfigurable = isConfigurable(),
            presets = presets,
        )
    }

    private fun format(totalSeconds: Int): String =
        "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)

    private fun vibrate(millis: Long) {
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(millis)
        }
    }

    private fun resolveVibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }

    private companion object {
        const val VIBRATE_PHASE = 150L  // kurzer Buzz bei HANG↔REST
        const val VIBRATE_DONE = 500L   // langer Buzz am Ende
    }
}
