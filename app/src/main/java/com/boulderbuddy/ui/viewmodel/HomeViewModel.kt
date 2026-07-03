package com.boulderbuddy.ui.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulderbuddy.data.db.entity.GradeEntity
import com.boulderbuddy.data.db.entity.GymEntity
import com.boulderbuddy.data.db.entity.RouteEntity
import com.boulderbuddy.data.db.entity.SessionEntity
import com.boulderbuddy.data.repository.GradeRepository
import com.boulderbuddy.data.repository.GymRepository
import com.boulderbuddy.data.repository.RouteRepository
import com.boulderbuddy.data.repository.SessionRepository
import com.boulderbuddy.ui.model.formatRelativeDay
import com.boulderbuddy.ui.model.istGetoppt
import com.boulderbuddy.ui.model.parseHexColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Zusammengefasste "Letzte Session"-Karte auf dem Home-Screen. */
data class LastSessionUi(
    val sessionId: Int,
    val gym: String,
    val subtitle: String,
    val accentColor: Color,
    val isActive: Boolean,
)

/** Reiner Anzeige-Zustand des Home-Screens (aus Room abgeleitet). */
data class HomeUiState(
    val userName: String = "Deniz",
    val sessionsPerWeek: Int = 0,
    val totalTops: Int = 0,
    val topGrade: String = "–",
    val hasActiveSession: Boolean = false,
    val activeSessionId: Int? = null,
    val lastSession: LastSessionUi? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    routeRepository: RouteRepository,
    gymRepository: GymRepository,
    gradeRepository: GradeRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        sessionRepository.observeAll(),
        sessionRepository.observeActive(),
        routeRepository.observeAll(),
        gymRepository.observeAll(),
        gradeRepository.observeAllGrades(),
    ) { sessions, active, routes, gyms, grades ->
        buildState(sessions, active, routes, gyms, grades)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    private fun buildState(
        sessions: List<SessionEntity>,
        active: SessionEntity?,
        routes: List<RouteEntity>,
        gyms: List<GymEntity>,
        grades: List<GradeEntity>,
    ): HomeUiState {
        val gradesById = grades.associateBy { it.id }
        val gymsById = gyms.associateBy { it.id }

        // Sessions der letzten 7 Tage (heute inklusive).
        val weekAgoMillis = LocalDate.now()
            .minusDays(6)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val sessionsPerWeek = sessions.count { it.date >= weekAgoMillis }

        val toppedRoutes = routes.filter { it.status.istGetoppt }
        val totalTops = toppedRoutes.size

        // Höchster getoppter Grad = größte sortOrder (grobe, systemübergreifende Näherung).
        val topGrade = toppedRoutes
            .mapNotNull { it.gradeId?.let(gradesById::get) }
            .maxByOrNull { it.order }
            ?.label
            ?: "–"

        // Letzte Session = neueste nach Datum (observeAll ist DESC → erste).
        val lastSession = sessions.firstOrNull()?.let { session ->
            val sessionRoutes = routes.filter { it.sessionId == session.id }
            LastSessionUi(
                sessionId = session.id,
                gym = gymsById[session.gymId]?.name ?: "Unbekannte Halle",
                subtitle = buildSubtitle(session, sessionRoutes.size),
                accentColor = accentColorFor(sessionRoutes, gradesById),
                isActive = session.endedAt == null,
            )
        }

        return HomeUiState(
            sessionsPerWeek = sessionsPerWeek,
            totalTops = totalTops,
            topGrade = topGrade,
            hasActiveSession = active != null,
            activeSessionId = active?.id,
            lastSession = lastSession,
        )
    }

    private fun buildSubtitle(session: SessionEntity, boulderCount: Int): String {
        val prefix = if (session.endedAt == null) "Heute · läuft gerade" else formatRelativeDay(session.date)
        return "$prefix · $boulderCount Boulder"
    }
}

/**
 * Akzentfarbe einer Session = häufigste Grade-Farbe ihrer Routen.
 * Fällt auf ein neutrales Grau zurück, wenn keine Route einen Grad hat.
 */
internal fun accentColorFor(
    routes: List<RouteEntity>,
    gradesById: Map<Int, GradeEntity>,
): Color {
    val hex = routes
        .mapNotNull { it.gradeId?.let(gradesById::get)?.color }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
    return hex?.let(::parseHexColor) ?: Color(0xFF888888)
}
