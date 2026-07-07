package com.boulderbuddy.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.boulderbuddy.data.db.entity.GymEntity
import com.boulderbuddy.data.db.entity.GymVisitEntity
import com.boulderbuddy.data.db.entity.SessionEntity
import com.boulderbuddy.data.repository.GradeRepository
import com.boulderbuddy.data.repository.GymRepository
import com.boulderbuddy.data.repository.GymVisitRepository
import com.boulderbuddy.data.repository.SessionRepository
import com.boulderbuddy.ui.navigation.SessionErstellen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Anzeige-Zustand des "Neue Session"-Screens: die wählbaren Grading-Systeme. */
data class SessionErstellenUiState(
    val systems: List<GradeSystemUi> = emptyList(),
    /**
     * Vorbefüllung des Ort-Felds (Gym-Näherungs-Push M4): Name der Halle aus dem
     * Notification-Deep-Link (`SessionErstellen(gymId)`); `null` = normaler Flow.
     */
    val prefillOrt: String? = null,
)

@HiltViewModel
class SessionErstellenViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val gymRepository: GymRepository,
    private val gymVisitRepository: GymVisitRepository,
    gradeRepository: GradeRepository,
) : ViewModel() {

    // Gym-Name für die Vorbefüllung (Deep-Link aus der Näherungs-Notification, M4).
    private val prefillOrtFlow = MutableStateFlow<String?>(null)

    init {
        val argGymId = savedStateHandle.toRoute<SessionErstellen>().gymId
        if (argGymId != null) {
            viewModelScope.launch {
                prefillOrtFlow.value = gymRepository.getById(argGymId)?.name
            }
        }
    }

    // Reale Grading-Systeme (Standards + Custom) für die Auswahl beim Session-Anlegen.
    val uiState: StateFlow<SessionErstellenUiState> = combine(
        gradeRepository.observeAllSystems(),
        gradeRepository.observeAllGrades(),
        prefillOrtFlow,
    ) { systems, grades, prefillOrt ->
        val countBySystem = grades.groupingBy { it.systemId }.eachCount()
        SessionErstellenUiState(
            systems = systems.map {
                GradeSystemUi(id = it.id, name = it.name, gradeCount = countBySystem[it.id] ?: 0)
            },
            prefillOrt = prefillOrt,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SessionErstellenUiState(),
    )

    /**
     * Legt eine neue, aktive Session an (`endedAt = null`) und meldet die neue ID zurück.
     *
     * Halle: "find-or-create" über den Namen (case-insensitive). [gradeSystemId] wird auf der
     * Session gespeichert und steuert später die Grade-Auswahl beim Boulder-Anlegen.
     */
    fun createSession(ort: String, gradeSystemId: Int?, notiz: String, onCreated: (Int) -> Unit) {
        viewModelScope.launch {
            val name = ort.trim().ifBlank { "Meine Halle" }
            val existing = gymRepository.observeAll().first()
                .firstOrNull { it.name.equals(name, ignoreCase = true) }
            val gymId = existing?.id ?: gymRepository.create(GymEntity(name = name))

            val startedAt = System.currentTimeMillis()
            val newId = sessionRepository.create(
                SessionEntity(
                    gymId = gymId,
                    gradeSystemId = gradeSystemId,
                    date = startedAt,
                    notes = notiz.trim().ifBlank { null },
                    endedAt = null,
                )
            )
            // Gym-Näherungs-Push (M3): Session-Start zählt als Besuch fürs Besuchsmuster
            // (Tages-Dedupe im Repository — war heute schon ein Geofence-Besuch da, passiert nichts).
            gymVisitRepository.logVisit(
                gymId = gymId,
                timestamp = startedAt,
                source = GymVisitEntity.SOURCE_SESSION,
            )
            onCreated(newId)
        }
    }
}
