package com.boulderbuddy.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulderbuddy.ui.screens.HangboardTimerUiState
import com.boulderbuddy.ui.screens.TimerPhase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Treibt den Hangboard-Timer: Countdown je Phase (HANG → REST → HANG …), Satz-Hochzählen,
 * Play/Pause/Reset. Reine Logik im ViewModel — der Screen bleibt stateless und rendert nur
 * den [HangboardTimerUiState].
 *
 * Konfiguration vorerst fest (7s Hang / 3s Rest / 6 Sätze).
 * TODO(6.9+): Templates (HangboardRepository) laden und wählbar machen.
 */
@HiltViewModel
class HangboardTimerViewModel @Inject constructor() : ViewModel() {

    private val totalSets = 6
    private val hangSec = 7
    private val restSec = 3

    // Interner Lauf-Zustand (getrennt vom UI-State, damit Pause/Resume die Restsekunden behält).
    private var currentSet = 1
    private var phase = TimerPhase.HANG
    private var secondsLeft = hangSec
    private var running = false
    private var timerJob: Job? = null

    private val _uiState = MutableStateFlow(snapshot())
    val uiState: StateFlow<HangboardTimerUiState> = _uiState.asStateFlow()

    fun onPlayPause() {
        if (running) pause() else start()
    }

    fun onReset() {
        timerJob?.cancel()
        currentSet = 1
        phase = TimerPhase.HANG
        secondsLeft = hangSec
        running = false
        _uiState.value = snapshot()
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

    private fun snapshot(): HangboardTimerUiState {
        val duration = when (phase) {
            TimerPhase.HANG -> hangSec
            TimerPhase.REST -> restSec
            TimerPhase.DONE -> 1
        }
        return HangboardTimerUiState(
            progress = if (phase == TimerPhase.DONE) 1f else secondsLeft.toFloat() / duration,
            time = format(secondsLeft.coerceAtLeast(0)),
            restTime = format(restSec),
            phase = phase,
            currentSet = currentSet.coerceAtMost(totalSets),
            totalSets = totalSets,
            isRunning = running,
        )
    }

    private fun format(totalSeconds: Int): String =
        "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
