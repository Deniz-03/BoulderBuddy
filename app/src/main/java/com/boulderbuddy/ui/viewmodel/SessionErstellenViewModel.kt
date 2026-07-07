package com.boulderbuddy.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulderbuddy.data.db.entity.GymEntity
import com.boulderbuddy.data.db.entity.GymVisitEntity
import com.boulderbuddy.data.db.entity.SessionEntity
import com.boulderbuddy.data.repository.GradeRepository
import com.boulderbuddy.data.repository.GymRepository
import com.boulderbuddy.data.repository.GymVisitRepository
import com.boulderbuddy.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
)

@HiltViewModel
class SessionErstellenViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val gymRepository: GymRepository,
    private val gymVisitRepository: GymVisitRepository,
    gradeRepository: GradeRepository,
) : ViewModel() {

    // Reale Grading-Systeme (Standards + Custom) für die Auswahl beim Session-Anlegen.
    val uiState: StateFlow<SessionErstellenUiState> = combine(
        gradeRepository.observeAllSystems(),
        gradeRepository.observeAllGrades(),
    ) { systems, grades ->
        val countBySystem = grades.groupingBy { it.systemId }.eachCount()
        SessionErstellenUiState(
            systems = systems.map {
                GradeSystemUi(id = it.id, name = it.name, gradeCount = countBySystem[it.id] ?: 0)
            },
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
