package com.boulderbuddy.wear.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boulderbuddy.wear.data.PhoneConnector
import com.boulderbuddy.wear.sensing.SensorLoggingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.io.File

/** UI-Zustand des Sensor-Logging-Screens (B.5.1 Debug-Modus). */
data class SensorLogUiState(
    val isRecording: Boolean = false,
    /** Aktives Label während der Aufnahme (HANG/REST/NONE). */
    val label: String = SensorLoggingService.LABEL_NONE,
    /** Name der letzten fertigen Aufnahme; null = noch keine vorhanden. */
    val lastLogName: String? = null,
    val lastLogSizeKb: Long = 0,
    /** Status des letzten Exports ("übertragen…", "an Phone übertragen", "fehlgeschlagen"). */
    val exportStatus: String? = null,
)

/**
 * Steuert die Sensor-Aufzeichnung (Start/Stop/Labels via Intents an den
 * [SensorLoggingService]) und den Export der letzten Aufnahme ans Phone.
 * Kein Hilt auf der Uhr (bewusst, wie [TimerViewModel]).
 */
class SensorLogViewModel(app: Application) : AndroidViewModel(app) {

    private val exportStatus = MutableStateFlow<String?>(null)
    // Letzte Aufnahme: beim Öffnen die neueste Datei aus dem Log-Verzeichnis, danach
    // hält der Service-Flow den Wert aktuell.
    private val initialLastLog: File? = File(app.filesDir, "sensorlogs")
        .listFiles()?.maxByOrNull { it.name }

    val uiState: StateFlow<SensorLogUiState> = combine(
        SensorLoggingService.isRecording,
        SensorLoggingService.currentLabel,
        SensorLoggingService.lastLogFile,
        exportStatus,
    ) { recording, label, lastFromService, export ->
        val last = lastFromService ?: initialLastLog
        SensorLogUiState(
            isRecording = recording,
            label = label,
            lastLogName = last?.name,
            lastLogSizeKb = last?.length()?.div(1024) ?: 0,
            exportStatus = export,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SensorLogUiState(),
    )

    fun onStartStop() {
        exportStatus.value = null
        if (SensorLoggingService.isRecording.value) {
            SensorLoggingService.stop(getApplication())
        } else {
            SensorLoggingService.start(getApplication())
        }
    }

    fun onLabelHang() =
        SensorLoggingService.label(getApplication(), SensorLoggingService.LABEL_HANG)

    fun onLabelRest() =
        SensorLoggingService.label(getApplication(), SensorLoggingService.LABEL_REST)

    fun onExport() {
        val file = SensorLoggingService.lastLogFile.value ?: initialLastLog ?: return
        exportStatus.value = "übertrage…"
        PhoneConnector.sendSensorLog(getApplication(), file) { ok ->
            exportStatus.value = if (ok) "an Phone übertragen" else "Export fehlgeschlagen"
        }
    }
}
