package com.boulderbuddy.ui.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulderbuddy.data.db.entity.GradeEntity
import com.boulderbuddy.data.db.entity.GradeSystemEntity
import com.boulderbuddy.data.db.entity.GymEntity
import com.boulderbuddy.data.db.entity.hallenName
import com.boulderbuddy.data.db.entity.RouteEntity
import com.boulderbuddy.data.db.entity.SessionEntity
import com.boulderbuddy.data.repository.GradeRepository
import com.boulderbuddy.data.repository.GymRepository
import com.boulderbuddy.data.repository.RouteRepository
import com.boulderbuddy.data.repository.SessionRepository
import com.boulderbuddy.data.settings.SettingsRepository
import com.boulderbuddy.ui.model.formatRelativeDay
import com.boulderbuddy.ui.model.istGetoppt
import com.boulderbuddy.ui.theme.routeColorForKey
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
    /** Anzeigename aus den Einstellungen; leer = neutrale Begrüßung ohne Namen. */
    val userName: String = "",
    val sessionsPerWeek: Int = 0,
    val totalTops: Int = 0,
    /** Höchster getoppter Grad im meistgenutzten System (systemintern verglichen). */
    val topGrade: String = "–",
    /** Name des Systems, aus dem [topGrade] stammt (für das Karten-Label); leer = keins. */
    val topGradeSystemName: String = "",
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
    settingsRepository: SettingsRepository,
) : ViewModel() {

    // Innerer Kern-Zustand (5 Flows), damit die Systeme als 6. Flow außen kombiniert werden
    // können (combine unterstützt typsicher max. 5 Flows).
    private data class HomeCore(
        val sessions: List<SessionEntity>,
        val active: SessionEntity?,
        val routes: List<RouteEntity>,
        val gyms: List<GymEntity>,
        val grades: List<GradeEntity>,
    )

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            sessionRepository.observeAll(),
            sessionRepository.observeActive(),
            routeRepository.observeAll(),
            gymRepository.observeAll(),
            gradeRepository.observeAllGrades(),
        ) { sessions, active, routes, gyms, grades ->
            HomeCore(sessions, active, routes, gyms, grades)
        },
        gradeRepository.observeAllSystems(),
        settingsRepository.selectedGradeSystemId,
        settingsRepository.userName,
    ) { core, systems, selectedSystemId, userName ->
        buildState(
            core.sessions, core.active, core.routes, core.gyms, core.grades,
            systems, selectedSystemId, userName,
        )
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
        systems: List<GradeSystemEntity>,
        selectedSystemId: Int?,
        userName: String,
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

        // Top-Grade im Standard-Grading-System (aus den Einstellungen). Ist keins gewählt, greift
        // als Fallback das meistgenutzte System. Systemübergreifendes maxBy(order) wäre
        // bedeutungslos (order zählt pro System), daher immer innerhalb genau eines Systems.
        val toppedGrades = toppedRoutes.mapNotNull { it.gradeId?.let(gradesById::get) }
        val targetSystemId = selectedSystemId?.takeIf { id -> systems.any { it.id == id } }
            ?: toppedGrades.groupBy { it.systemId }.maxByOrNull { it.value.size }?.key
        val topGrade = toppedGrades
            .filter { it.systemId == targetSystemId }
            .maxByOrNull { it.order }
            ?.label
            ?: "–"
        val topGradeSystemName = targetSystemId
            ?.let { sid -> systems.firstOrNull { it.id == sid }?.name }
            .orEmpty()

        // Letzte Session = neueste nach Datum (observeAll ist DESC → erste).
        val lastSession = sessions.firstOrNull()?.let { session ->
            val sessionRoutes = routes.filter { it.sessionId == session.id }
            LastSessionUi(
                sessionId = session.id,
                gym = session.hallenName { gymsById[it]?.name } ?: "Unbekannte Halle",
                subtitle = buildSubtitle(session, sessionRoutes.size),
                accentColor = accentColorFor(sessionRoutes),
                isActive = session.endedAt == null,
            )
        }

        return HomeUiState(
            userName = userName,
            sessionsPerWeek = sessionsPerWeek,
            totalTops = totalTops,
            topGrade = topGrade,
            topGradeSystemName = topGradeSystemName,
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
 * Akzentfarbe einer Session = häufigste Routenfarbe ihrer Boulder.
 * Fällt auf ein neutrales Grau zurück, wenn keine Route eine Farbe hat.
 */
internal fun accentColorFor(routes: List<RouteEntity>): Color {
    val key = routes
        .mapNotNull { it.color }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
    return routeColorForKey(key)
}
