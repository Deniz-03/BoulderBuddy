package com.boulderbuddy.ui.viewmodel

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulderbuddy.data.db.entity.GhostAnalysisEntity
import com.boulderbuddy.data.db.entity.GradeEntity
import com.boulderbuddy.data.db.entity.hallenName
import com.boulderbuddy.data.db.entity.HangboardWorkoutMode
import com.boulderbuddy.data.db.entity.HangboardWorkoutWithSegments
import com.boulderbuddy.data.db.entity.RouteEntity
import com.boulderbuddy.data.db.entity.SessionEntity
import com.boulderbuddy.data.repository.GhostAnalysisRepository
import com.boulderbuddy.data.repository.GradeRepository
import com.boulderbuddy.data.repository.GymRepository
import com.boulderbuddy.data.repository.HangboardWorkoutRepository
import com.boulderbuddy.data.repository.RouteRepository
import com.boulderbuddy.data.repository.SessionRepository
import com.boulderbuddy.ghost.model.GhostViewMode
import com.boulderbuddy.ui.model.formatDayMonth
import com.boulderbuddy.ui.model.formatDurationShort
import com.boulderbuddy.ui.model.formatUhrzeit
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

/** Ein getracktes Hangboard-Workout innerhalb der Session (Kurzbeschreibung). */
data class HangboardWorkoutUi(
    val id: Int,
    val summary: String,
)

/**
 * Eine Ghost-Climber-Analyse, die in dieser Session entstanden ist.
 *
 * Uhrzeit statt Datum: innerhalb einer Session ist das Datum für jede Zeile dasselbe und
 * unterscheidet nichts. Die Uhrzeit ordnet die Analyse dem Versuch zu, an den man sich
 * erinnert.
 */
data class SessionGhostAnalyseUi(
    val id: Int,
    val zeitText: String,
    val modusLabel: String,
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
    /** Getrackte Hangboard-Workouts; leer = kein "Hangboard-Training"-Block anzeigen. */
    val hangboardWorkouts: List<HangboardWorkoutUi> = emptyList(),
    /**
     * Ghost-Analysen dieser Session. Anders als bei den Hangboard-Workouts blendet sich
     * der Block leer NICHT aus — er ist zugleich der Einstieg (siehe SessionGhostBlock).
     */
    val ghostAnalysen: List<SessionGhostAnalyseUi> = emptyList(),
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
    hangboardWorkoutRepository: HangboardWorkoutRepository,
    ghostAnalysisRepository: GhostAnalysisRepository,
    // Nur fürs Homescreen-Widget: nach dem Beenden soll es keine aktive Session mehr anbieten.
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(sessionId: Int): SessionViewModel
    }

    // Zwei verschachtelte `combine`, weil das typisierte `combine` bei fünf Flows endet und
    // die Ghost-Analysen der sechste wären. Die Verschachtelung ist zugleich die ehrlichere
    // Struktur: die Analysen gehen in keine Berechnung ein, sie werden nur angehängt.
    val uiState: StateFlow<SessionUiState> = combine(
        combine(
            // observeAll (statt eines observeById) macht die Ansicht reaktiv auf endSession:
            // sobald endedAt gesetzt wird, kippt istAktiv und der Dispatcher zeigt die
            // Alt-Ansicht.
            sessionRepository.observeAll(),
            routeRepository.observeBySession(sessionId),
            gymRepository.observeAll(),
            gradeRepository.observeAllGrades(),
            hangboardWorkoutRepository.observeBySession(sessionId),
        ) { sessions, routes, gyms, grades, hangboardWorkouts ->
            val session = sessions.firstOrNull { it.id == sessionId }
                ?: return@combine SessionUiState(loading = false, exists = false)
            buildState(session, routes, grades.associateBy { it.id }, hangboardWorkouts,
                gymName = session.hallenName { id -> gyms.firstOrNull { it.id == id }?.name }
                    ?: "Unbekannte Halle")
        },
        ghostAnalysisRepository.observeBySession(sessionId),
    ) { state, analysen ->
        state.copy(ghostAnalysen = analysen.map(::toGhostAnalyseUi))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SessionUiState(),
    )

    /**
     * Schreibt die Session-Notiz zurück. Leerer Text wird zu `null` normalisiert, damit
     * "keine Notiz" nur eine Repräsentation hat.
     *
     * Wird bei **jedem Tastendruck** gerufen (siehe `AlteSessionScreen`) und schreibt deshalb
     * mit einer einzelnen `UPDATE`-Anweisung, statt die Zeile zu laden und zurückzuschreiben.
     * Der frühere Weg — nur beim Fokusverlust speichern — sah sparsamer aus, verlor die Notiz
     * aber vollständig: den Screen verlässt man über Zurück, und dabei entsteht kein
     * Fokuswechsel mehr, der noch etwas hätte auslösen können.
     */
    fun updateNotes(notes: String) {
        viewModelScope.launch {
            sessionRepository.updateNotes(sessionId, notes.trim().takeIf { it.isNotEmpty() })
        }
    }

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
        hangboardWorkouts: List<HangboardWorkoutWithSegments>,
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
            hangboardWorkouts = hangboardWorkouts.map {
                HangboardWorkoutUi(id = it.workout.id, summary = workoutSummary(it))
            },
        )
    }

    private fun toGhostAnalyseUi(analyse: GhostAnalysisEntity) = SessionGhostAnalyseUi(
        id = analyse.id,
        zeitText = formatUhrzeit(analyse.createdAt),
        modusLabel = if (analyse.suggestedMode == GhostViewMode.SIDE_BY_SIDE.name) {
            "Side-by-Side"
        } else {
            "Overlay"
        },
    )

    // Kurzbeschreibung eines Workouts: manuell über die Plan-Werte, auto über die gemessene
    // Gesamt-Hängezeit (dort gibt es keine Vorgabe).
    private fun workoutSummary(workout: HangboardWorkoutWithSegments): String {
        val w = workout.workout
        return if (w.mode == HangboardWorkoutMode.MANUAL && w.plannedHangSec != null) {
            "${workout.segments.size} Sätze · ${w.plannedHangSec}s Hang / ${w.plannedRestSec ?: 0}s Pause"
        } else {
            val hangSeconds = workout.totalHangMs / 1000
            val hangTime = "%02d:%02d".format(hangSeconds / 60, hangSeconds % 60)
            "${workout.segments.size} Sätze · $hangTime Hängezeit (Auto)"
        }
    }
}
