package com.boulderbuddy.wear.presentation

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boulderbuddy.wear.sensing.AutoHangService
import com.boulderbuddy.wear.sensing.HangState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** Zusammenfassung nach dem Beenden (§0 Säule 3 auf der Uhr). */
data class AutoResultUi(
    val sets: Int,
    val hangTimeText: String,
)

/** UI-Zustand des Auto-Screens: Live-Status während der Erkennung bzw. Ergebnis danach. */
data class AutoHangUiState(
    val active: Boolean = false,
    /** Großer Live-Status: BEREIT (wartet auf ersten Griff) / HÄNGT / PAUSE. */
    val statusText: String = "BEREIT",
    /** Hochzählende Laufzeit der aktuellen Phase (mm:ss). */
    val timeText: String = "00:00",
    /** Aktueller Satz-Index (während HÄNGT: laufender Satz, sonst abgeschlossene). */
    val setText: String = "",
    val result: AutoResultUi? = null,
)

/**
 * Bindet den Auto-Screen an den [AutoHangService]: spiegelt Detektions-Zustand + Satz-Zähler,
 * zählt die laufende Phase sekündlich hoch und liefert nach dem Beenden die Zusammenfassung.
 */
class AutoHangViewModel(app: Application) : AndroidViewModel(app) {

    // Sekündlicher Tick, solange der Screen zuschaut — treibt die hochzählende Anzeige.
    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(1_000)
        }
    }

    val uiState: StateFlow<AutoHangUiState> = combine(
        AutoHangService.tracking,
        AutoHangService.result,
        ticker,
    ) { tracking, result, _ ->
        val elapsed = if (tracking.active) {
            (SystemClock.elapsedRealtime() - tracking.phaseStartElapsedMs).coerceAtLeast(0)
        } else 0
        AutoHangUiState(
            active = tracking.active,
            statusText = when {
                !tracking.active -> "BEREIT"
                tracking.state == HangState.HANGING -> "HÄNGT"
                tracking.state == HangState.RESTING -> "PAUSE"
                else -> "BEREIT"
            },
            timeText = format(elapsed / 1_000),
            setText = when {
                !tracking.active -> ""
                tracking.state == HangState.HANGING -> "Satz ${tracking.completedSets + 1}"
                tracking.completedSets > 0 -> "${tracking.completedSets} Sätze"
                else -> "Warte auf ersten Griff"
            },
            result = result?.let {
                AutoResultUi(
                    sets = it.segments.size,
                    hangTimeText = format(it.segments.sumOf { s -> s.hangMs } / 1_000),
                )
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AutoHangUiState(),
    )

    fun onStart() = AutoHangService.start(getApplication())

    fun onFinish() = AutoHangService.finish(getApplication())

    fun onDismissResult() = AutoHangService.clearResult()

    private fun format(totalSeconds: Long): String =
        "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
