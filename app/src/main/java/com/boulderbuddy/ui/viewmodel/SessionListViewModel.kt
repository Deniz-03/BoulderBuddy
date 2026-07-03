package com.boulderbuddy.ui.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulderbuddy.data.repository.GradeRepository
import com.boulderbuddy.data.repository.GymRepository
import com.boulderbuddy.data.repository.RouteRepository
import com.boulderbuddy.data.repository.SessionRepository
import com.boulderbuddy.ui.model.formatRelativeDay
import com.boulderbuddy.ui.model.istGetoppt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Eine Zeile der Session-Übersicht. */
data class SessionListItemUi(
    val id: Int,
    val gym: String,
    val date: String,
    val accentColor: Color,
    val badges: List<String>,
    val isActive: Boolean,
)

data class SessionListUiState(
    val sessions: List<SessionListItemUi> = emptyList(),
)

@HiltViewModel
class SessionListViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    routeRepository: RouteRepository,
    gymRepository: GymRepository,
    gradeRepository: GradeRepository,
) : ViewModel() {

    val uiState: StateFlow<SessionListUiState> = combine(
        sessionRepository.observeAll(),
        routeRepository.observeAll(),
        gymRepository.observeAll(),
        gradeRepository.observeAllGrades(),
    ) { sessions, routes, gyms, grades ->
        val gradesById = grades.associateBy { it.id }
        val gymsById = gyms.associateBy { it.id }
        val routesBySession = routes.groupBy { it.sessionId }

        // Aktive Session (endedAt == null) immer zuerst, danach observeAll-Reihenfolge (Datum DESC).
        val ordered = sessions.sortedByDescending { it.endedAt == null }

        val items = ordered.map { session ->
            val sessionRoutes = routesBySession[session.id].orEmpty()
            val isActive = session.endedAt == null
            val topCount = sessionRoutes.count { it.status.istGetoppt }
            val badges = buildList {
                add("${sessionRoutes.size} Boulder")
                if (!isActive && topCount > 0) add("$topCount Tops")
            }
            SessionListItemUi(
                id = session.id,
                gym = gymsById[session.gymId]?.name ?: "Unbekannte Halle",
                date = if (isActive) "Heute · läuft gerade" else formatRelativeDay(session.date),
                accentColor = accentColorFor(sessionRoutes, gradesById),
                badges = badges,
                isActive = isActive,
            )
        }
        SessionListUiState(sessions = items)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SessionListUiState(),
    )
}
