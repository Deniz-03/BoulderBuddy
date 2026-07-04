package com.boulderbuddy.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulderbuddy.data.db.entity.HangboardSessionEntity
import com.boulderbuddy.data.db.entity.HangboardTemplateEntity
import com.boulderbuddy.data.repository.HangboardRepository
import com.boulderbuddy.data.repository.HangboardSessionRepository
import com.boulderbuddy.data.repository.SessionRepository
import com.boulderbuddy.data.settings.SettingsRepository
import com.boulderbuddy.data.settings.TimerConfig
import com.boulderbuddy.ui.screens.HangboardTimerUiState
import com.boulderbuddy.ui.screens.TimerPhase
import com.boulderbuddy.ui.screens.TimerPreset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Treibt den Hangboard-Timer: Countdown je Phase (HANG → REST → HANG …), Satz-Hochzählen,
 * Play/Pause/Reset. Reine Logik im ViewModel — der Screen bleibt stateless und rendert nur
 * den [HangboardTimerUiState].
 *
 * Konfiguration (Sätze/Hang/Pause) kommt aus dem [SettingsRepository] und wird über
 * [updateConfig] persistiert (letzte Einstellung gemerkt). Benannte Voreinstellungen
 * (Presets) liegen dagegen im [HangboardRepository] und werden hier eingebunden
 * ([savePreset]/[deletePreset]/[applyPreset]). Ein bis zum Ende absolvierter Durchlauf
 * wird in der aktiven Kletter-Session getrackt (siehe [recordWorkout]).
 */
@HiltViewModel
class HangboardTimerViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: SessionRepository,
    private val hangboardSessionRepository: HangboardSessionRepository,
    private val hangboardRepository: HangboardRepository,
) : ViewModel() {

    // Konfiguration (var, da über updateConfig änderbar). Defaults bis DataStore geladen ist.
    private var totalSets = 6
    private var hangSec = 7
    private var restSec = 3

    // Benannte Voreinstellungen aus der DB (Room-Flow), für die Preset-Auswahl im UI.
    private var presets: List<HangboardTemplateEntity> = emptyList()

    // Interner Lauf-Zustand (getrennt vom UI-State, damit Pause/Resume die Restsekunden behält).
    private var currentSet = 1
    private var phase = TimerPhase.HANG
    private var secondsLeft = hangSec
    private var running = false
    private var timerJob: Job? = null
    // Verhindert doppeltes Tracken desselben abgeschlossenen Durchlaufs.
    private var recorded = false

    private val _uiState = MutableStateFlow(snapshot())
    val uiState: StateFlow<HangboardTimerUiState> = _uiState.asStateFlow()

    init {
        // Zuletzt genutzte Konfiguration laden und anwenden.
        viewModelScope.launch {
            applyConfig(settingsRepository.timerConfig.first())
        }
        // Presets beobachten und in den UI-State spiegeln (ändern nicht den Lauf-Zustand).
        viewModelScope.launch {
            hangboardRepository.observeAll().collect {
                presets = it
                _uiState.value = snapshot()
            }
        }
    }

    fun onPlayPause() {
        if (running) pause() else start()
    }

    fun onReset() {
        timerJob?.cancel()
        currentSet = 1
        phase = TimerPhase.HANG
        secondsLeft = hangSec
        running = false
        recorded = false
        _uiState.value = snapshot()
    }

    /**
     * Übernimmt eine neue Timer-Konfiguration: persistiert sie (letzte Einstellung gemerkt)
     * und setzt den Timer mit den neuen Werten zurück.
     */
    fun updateConfig(sets: Int, hangSec: Int, restSec: Int) {
        val config = TimerConfig(
            sets = sets.coerceAtLeast(1),
            hangSec = hangSec.coerceAtLeast(1),
            restSec = restSec.coerceAtLeast(0),
        )
        viewModelScope.launch { settingsRepository.setTimerConfig(config) }
        applyConfig(config)
    }

    private fun applyConfig(config: TimerConfig) {
        totalSets = config.sets
        hangSec = config.hangSec
        restSec = config.restSec
        onReset()
    }

    /**
     * Übernimmt eine Voreinstellung (Preset) als aktuelle Config: wie [updateConfig], nur mit
     * den Werten des gewählten Templates. Persistiert die Config als „zuletzt genutzt".
     */
    fun applyPreset(presetId: Int) {
        val preset = presets.find { it.id == presetId } ?: return
        updateConfig(preset.sets, preset.hangSec, preset.restSec)
    }

    /**
     * Speichert die übergebene Konfiguration als benanntes Preset. Leerer Name wird ignoriert.
     * (Die Preset-Liste aktualisiert sich über den beobachteten Room-Flow von selbst.)
     */
    fun savePreset(name: String, sets: Int, hangSec: Int, restSec: Int) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            hangboardRepository.create(
                HangboardTemplateEntity(
                    name = trimmed,
                    sets = sets.coerceAtLeast(1),
                    hangSec = hangSec.coerceAtLeast(1),
                    restSec = restSec.coerceAtLeast(0),
                    // repRestSec (Rep-Pause) wird vom Timer aktuell nicht genutzt → = Pause.
                    repRestSec = restSec.coerceAtLeast(0),
                )
            )
        }
    }

    /** Löscht ein Preset anhand seiner ID. */
    fun deletePreset(presetId: Int) {
        val preset = presets.find { it.id == presetId } ?: return
        viewModelScope.launch { hangboardRepository.delete(preset) }
    }

    private fun start() {
        // Nach dem Ende neu starten: erst zurücksetzen.
        if (phase == TimerPhase.DONE) onReset()
        running = true
        _uiState.value = snapshot()
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (running && phase != TimerPhase.DONE) {
                delay(1000)
                secondsLeft--
                if (secondsLeft <= 0) advancePhase()
                if (phase == TimerPhase.DONE && !recorded) {
                    recorded = true
                    recordWorkout()
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

    // Phasenübergang, wenn die aktuelle Phase abgelaufen ist.
    private fun advancePhase() {
        when (phase) {
            TimerPhase.HANG ->
                if (currentSet >= totalSets) {
                    phase = TimerPhase.DONE
                    running = false
                } else {
                    phase = TimerPhase.REST
                    secondsLeft = restSec
                }
            TimerPhase.REST -> {
                currentSet++
                phase = TimerPhase.HANG
                secondsLeft = hangSec
            }
            TimerPhase.DONE -> running = false
        }
    }

    /**
     * Trackt einen abgeschlossenen Durchlauf in der aktuell aktiven Kletter-Session.
     * Gibt es keine aktive Session, wird nichts gespeichert (kein FK-Ziel, kein Clutter).
     */
    private fun recordWorkout() {
        viewModelScope.launch {
            val sessionId = sessionRepository.observeActive().first()?.id ?: return@launch
            hangboardSessionRepository.create(
                HangboardSessionEntity(
                    sessionId = sessionId,
                    completedSets = totalSets,
                    totalSets = totalSets,
                    hangSec = hangSec,
                    restSec = restSec,
                    date = System.currentTimeMillis(),
                )
            )
        }
    }

    private fun snapshot(): HangboardTimerUiState {
        val duration = when (phase) {
            TimerPhase.HANG -> hangSec
            TimerPhase.REST -> restSec
            TimerPhase.DONE -> 1
        }.coerceAtLeast(1)  // schützt vor Division durch 0 (Pause = 0 erlaubt)
        return HangboardTimerUiState(
            progress = if (phase == TimerPhase.DONE) 1f else secondsLeft.toFloat() / duration,
            time = format(secondsLeft.coerceAtLeast(0)),
            restTime = format(restSec),
            phase = phase,
            currentSet = currentSet.coerceAtMost(totalSets),
            totalSets = totalSets,
            hangSec = hangSec,
            restSec = restSec,
            isRunning = running,
            presets = presets.map {
                TimerPreset(
                    id = it.id,
                    name = it.name,
                    sets = it.sets,
                    hangSec = it.hangSec,
                    restSec = it.restSec,
                )
            },
        )
    }

    private fun format(totalSeconds: Int): String =
        "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
