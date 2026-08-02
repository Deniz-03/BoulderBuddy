package com.boulderbuddy.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulderbuddy.data.db.entity.GradeEntity
import com.boulderbuddy.data.db.entity.GradeSystemEntity
import com.boulderbuddy.data.db.entity.GymEntity
import com.boulderbuddy.data.export.SessionExporter
import com.boulderbuddy.data.repository.GradeRepository
import com.boulderbuddy.data.repository.GymRepository
import com.boulderbuddy.data.settings.SettingsRepository
import com.boulderbuddy.wearsync.WearConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Ein Gradsystem in der Verwaltungs-Liste (Name + Anzahl seiner Grade). */
data class GradeSystemUi(
    val id: Int,
    val name: String,
    val gradeCount: Int,
    /** `true`, wenn löschbar. Globale Standards (V-Scale/Französisch, `gymId == null`) nicht. */
    val deletable: Boolean = false,
)

data class EinstellungenUiState(
    val systems: List<GradeSystemUi> = emptyList(),
    /** ID des als "Standard-Grading" gewählten Systems; steuert das Grade-Dropdown. */
    val selectedGradeSystemId: Int? = null,
    /** Expliziter Dark-Mode-Override; `null` = dem System folgen (7.4a). */
    val darkModeOverride: Boolean? = null,
    /** Vibration bei Timer-Phasenwechseln. */
    val hapticFeedback: Boolean = true,
    /** Anzeigename für die Home-Begrüßung; leer = neutrale Begrüßung. */
    val userName: String = "",
    /** Ob gerade eine Uhr mit dem Phone verbunden ist (nur Anzeige, nicht schaltbar). */
    val watchConnected: Boolean = false,
)

@HiltViewModel
class EinstellungenViewModel @Inject constructor(
    private val gymRepository: GymRepository,
    private val gradeRepository: GradeRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionExporter: SessionExporter,
    wearConnection: WearConnection,
) : ViewModel() {

    // Einmalige Rückmeldung zum Export (Erfolg/Fehler); vom UI als Toast angezeigt und danach
    // via [consumeExportMessage] geleert. `null` = keine offene Meldung.
    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage.asStateFlow()

    // Die Geräte-/App-Einstellungen als ein Flow gebündelt, damit das äußere combine
    // innerhalb der typsicheren 5-Flow-Grenze bleibt (wie im HomeViewModel).
    private data class Praeferenzen(
        val darkMode: Boolean?,
        val hapticFeedback: Boolean,
        val userName: String,
        val watchConnected: Boolean,
    )

    val uiState: StateFlow<EinstellungenUiState> = combine(
        gradeRepository.observeAllSystems(),
        gradeRepository.observeAllGrades(),
        settingsRepository.selectedGradeSystemId,
        combine(
            settingsRepository.darkMode,
            settingsRepository.hapticFeedback,
            settingsRepository.userName,
            wearConnection.connected,
        ) { darkMode, haptic, name, watch -> Praeferenzen(darkMode, haptic, name, watch) },
    ) { systems, grades, selectedId, praeferenzen ->
        val countBySystem = grades.groupingBy { it.systemId }.eachCount()
        EinstellungenUiState(
            systems = systems.map {
                GradeSystemUi(
                    id = it.id,
                    name = it.name,
                    gradeCount = countBySystem[it.id] ?: 0,
                    // Globale Standards (gymId == null) sind geschützt, alles andere löschbar.
                    deletable = it.gymId != null,
                )
            },
            selectedGradeSystemId = selectedId,
            darkModeOverride = praeferenzen.darkMode,
            hapticFeedback = praeferenzen.hapticFeedback,
            userName = praeferenzen.userName,
            watchConnected = praeferenzen.watchConnected,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EinstellungenUiState(),
    )

    /** Merkt das gewählte Standard-Grading-System (persistent via DataStore). */
    fun selectGradeSystem(systemId: Int) {
        viewModelScope.launch { settingsRepository.setSelectedGradeSystem(systemId) }
    }

    /** Setzt den Dark-Mode-Override (persistent via DataStore); steuert das App-Theme (7.4a). */
    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDarkMode(enabled) }
    }

    /** Schaltet die Vibration bei Timer-Phasenwechseln ein/aus (persistent via DataStore). */
    fun setHapticFeedback(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHapticFeedback(enabled) }
    }

    /** Speichert den Anzeigenamen für die Home-Begrüßung. Leer = neutrale Begrüßung. */
    fun setUserName(name: String) {
        viewModelScope.launch { settingsRepository.setUserName(name) }
    }

    /**
     * Exportiert alle Sessions als CSV in das per SAF gewählte Dokument [uri]. Setzt eine
     * Ergebnis-Meldung ([exportMessage]) für das UI.
     */
    fun exportSessions(uri: Uri) {
        viewModelScope.launch {
            _exportMessage.value = try {
                val count = sessionExporter.exportCsv(uri)
                "$count Sessions als CSV exportiert"
            } catch (e: Exception) {
                "Export fehlgeschlagen: ${e.message}"
            }
        }
    }

    /** Bestätigt, dass die Export-Meldung angezeigt wurde (leert sie). */
    fun consumeExportMessage() {
        _exportMessage.value = null
    }

    /**
     * Löscht ein Custom-Grading-System. Globale Standards (V-Scale/Französisch) sind geschützt
     * und werden hier ignoriert. Boulder dieses Systems behalten ihre Farbe, verlieren aber die
     * Grad-Zuordnung (FK SET_NULL).
     */
    fun deleteGradeSystem(systemId: Int) {
        viewModelScope.launch { gradeRepository.deleteSystem(systemId) }
    }

    /**
     * Legt ein neues Custom-Grading-System an. Jedes Label wird zu einem Grad (reine
     * Schwierigkeit, Reihenfolge = Eingabe-Index). Leere Labels werden ignoriert. Fehlt eine
     * Halle, wird eine Standard-Halle angelegt, damit das System einen FK-Anker hat.
     */
    fun createGradeSystem(name: String, labels: List<String>) {
        val cleanName = name.trim()
        val cleanLabels = labels.map { it.trim() }.filter { it.isNotBlank() }
        if (cleanName.isBlank() || cleanLabels.isEmpty()) return
        viewModelScope.launch {
            val gymId = gymRepository.observeAll().first().firstOrNull()?.id
                ?: gymRepository.create(GymEntity(name = "Meine Halle"))
            val systemId = gradeRepository.createSystem(
                GradeSystemEntity(gymId = gymId, name = cleanName)
            )
            gradeRepository.createGrades(
                cleanLabels.mapIndexed { index, label ->
                    GradeEntity(systemId = systemId, label = label, order = index)
                }
            )
        }
    }
}
