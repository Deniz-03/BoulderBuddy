package com.boulderbuddy.ui.viewmodel

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulderbuddy.data.db.entity.GradeEntity
import com.boulderbuddy.data.db.entity.HangboardSessionEntity
import com.boulderbuddy.data.db.entity.RouteEntity
import com.boulderbuddy.data.db.entity.SessionEntity
import com.boulderbuddy.data.repository.GradeRepository
import com.boulderbuddy.data.repository.GymRepository
import com.boulderbuddy.data.repository.HangboardSessionRepository
import com.boulderbuddy.data.repository.RouteRepository
import com.boulderbuddy.data.repository.SessionRepository
import com.boulderbuddy.ui.model.formatDayMonth
import com.boulderbuddy.ui.model.formatDurationShort
import com.boulderbuddy.ui.model.istGetoppt
import com.boulderbuddy.ui.model.toBoulderStatus
import com.boulderbuddy.ui.theme.routeColorForKey
import com.boulderbuddy.ui.screens.BoulderStatus
import com.boulderbuddy.widget.refreshBoulderWidget
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Eine Boulder-Kachel/-Zeile innerhalb einer Session (aktiv wie abgeschlossen). */
data class SessionBoulderUi(
    val id: Int,
    val grade: String,
    val name: String,
    val accentColor: Color,
    val status: BoulderStatus,
    val versuche: Int,
)

/** Ein getrackter Hangboard-Durchlauf innerhalb der Session (Kurzbeschreibung). */
data class HangboardSessionUi(
    val id: Int,
    val summary: String,
)

/**
 * Gemeinsamer Zustand für die beiden Session-Ansichten. [istAktiv] entscheidet,
 * ob [com.boulderbuddy.ui.screens.SessionDetailScreen] (aktiv) oder
 * [com.boulderbuddy.ui.screens.AlteSessionScreen] (read-only) gerendert wird.
 */
data class SessionUiState(
    val loading: Boolean = true,
    val exists: Boolean = false,
    val istAktiv: Boolean = false,
    val gym: String = "",
    val dateSubtitle: String = "",
    val startMillis: Long = 0L,
    val durationText: String = "",
    val topGrade: String = "–",
    val notes: String = "",
    val boulders: List<SessionBoulderUi> = emptyList(),
    /** Getrackte Hangboard-Durchläufe; leer = kein "Hangboard-Training"-Block anzeigen. */
    val hangboardSessions: List<HangboardSessionUi> = emptyList(),
)

// Assisted Injection: die sessionId wird explizit übergeben (nicht mehr aus den
// Nav-Argumenten gelesen). So funktioniert derselbe ViewModel sowohl an der klassischen
// Session-Route als auch im Detail-Pane des Tablet-ListDetailPaneScaffold (Phase 7.1),
// wo es keine eigenen Nav-Argumente gibt.
@HiltViewModel(assistedFactory = SessionViewModel.Factory::class)
class SessionViewModel @AssistedInject constructor(
    @Assisted private val sessionId: Int,
    private val sessionRepository: SessionRepository,
    routeRepository: RouteRepository,
    gymRepository: GymRepository,
    gradeRepository: GradeRepository,
    hangboardSessionRepository: HangboardSessionRepository,
    // Nur fürs Homescreen-Widget: nach dem Beenden soll es keine aktive Session mehr anbieten.
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(sessionId: Int): SessionViewModel
    }

    val uiState: StateFlow<SessionUiState> = combine(
        // observeAll (statt eines observeById) macht die Ansicht reaktiv auf endSession:
        // sobald endedAt gesetzt wird, kippt istAktiv und der Dispatcher zeigt die Alt-Ansicht.
        sessionRepository.observeAll(),
        routeRepository.observeBySession(sessionId),
        gymRepository.observeAll(),
        gradeRepository.observeAllGrades(),
        hangboardSessionRepository.observeBySession(sessionId),
    ) { sessions, routes, gyms, grades, hangboardSessions ->
        val session = sessions.firstOrNull { it.id == sessionId }
            ?: return@combine SessionUiState(loading = false, exists = false)
        buildState(session, routes, grades.associateBy { it.id }, hangboardSessions,
            gymName = gyms.firstOrNull { it.id == session.gymId }?.name ?: "Unbekannte Halle")
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SessionUiState(),
    )

    fun endSession() {
        viewModelScope.launch {
            sessionRepository.endSession(sessionId)
            // Widget-Snapshot nachziehen: es darf jetzt nicht mehr in diese Session springen.
            refreshBoulderWidget(appContext)
        }
    }

    private fun buildState(
        session: SessionEntity,
        routes: List<RouteEntity>,
        gradesById: Map<Int, GradeEntity>,
        hangboardSessions: List<HangboardSessionEntity>,
        gymName: String,
    ): SessionUiState {
        val istAktiv = session.endedAt == null
        val boulders = routes.map { route ->
            val grade = route.gradeId?.let(gradesById::get)
            SessionBoulderUi(
                id = route.id,
                grade = grade?.label ?: "—",
                name = route.name.ifBlank { grade?.label ?: "Boulder" },
                accentColor = routeColorForKey(route.color),
                status = route.status.toBoulderStatus(route.attempts),
                versuche = route.attempts,
            )
        }
        // Top-Grade nur innerhalb des Gradsystems dieser Session — systemübergreifend wäre der
        // Vergleich über `order` bedeutungslos. Ohne Session-System: alle getoppten Grade.
        val systemId = session.gradeSystemId
        val topGrade = routes
            .filter { it.status.istGetoppt }
            .mapNotNull { it.gradeId?.let(gradesById::get) }
            .filter { systemId == null || it.systemId == systemId }
            .maxByOrNull { it.order }
            ?.label
            ?: "–"
        val durationText = session.endedAt
            ?.let { formatDurationShort(it - session.date) }
            .orEmpty()

        return SessionUiState(
            loading = false,
            exists = true,
            istAktiv = istAktiv,
            gym = gymName,
            dateSubtitle = if (istAktiv) "" else "${formatDayMonth(session.date)} · abgeschlossen",
            startMillis = session.date,
            durationText = durationText,
            topGrade = topGrade,
            notes = session.notes.orEmpty(),
            boulders = boulders,
            hangboardSessions = hangboardSessions.map {
                HangboardSessionUi(
                    id = it.id,
                    summary = "${it.completedSets} Sätze · ${it.hangSec}s Hang / ${it.restSec}s Pause",
                )
            },
        )
    }
}
