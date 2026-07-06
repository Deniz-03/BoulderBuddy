package com.boulderbuddy.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulderbuddy.data.db.entity.GradeEntity
import com.boulderbuddy.data.db.entity.RouteEntity
import com.boulderbuddy.data.db.entity.SessionEntity
import com.boulderbuddy.data.model.RouteStatus
import com.boulderbuddy.data.repository.GradeRepository
import com.boulderbuddy.data.repository.HangboardWorkoutRepository
import com.boulderbuddy.data.repository.RouteRepository
import com.boulderbuddy.data.repository.SessionRepository
import com.boulderbuddy.ui.components.BarChartEntry
import com.boulderbuddy.ui.model.formatDurationShort
import com.boulderbuddy.ui.model.istGetoppt
import com.boulderbuddy.ui.model.toLocalDate
import com.boulderbuddy.ui.theme.routeColorPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class StatistikUiState(
    val flashRate: String = "–",
    val totalTops: Int = 0,
    val totalSessions: Int = 0,
    /** Systeme, in denen getoppt wurde — Umschalter über der Grade-Verteilung. */
    val distributionSystems: List<GradeSystemFilterUi> = emptyList(),
    /** Je System die Grade-Verteilung (ein Balken je Grad, in Grad-Reihenfolge). */
    val distributionBySystem: Map<Int, List<BarChartEntry>> = emptyMap(),
    val activity: List<Float> = emptyList(),
    // --- Hangboard-Training ---
    /** Anzahl abgeschlossener Hangboard-Workouts (Phone+Uhr, manuell+auto, auch ohne Session). */
    val hangboardWorkouts: Int = 0,
    /** Summe aller absolvierten Sätze (= Segmente) über alle Workouts. */
    val hangboardSets: Int = 0,
    /** Gesamte Hängezeit (Summe der gemessenen Segment-Hängezeiten) als Kurzform. */
    val hangboardHangTime: String = "–",
)

// Anzahl der Tage in der Aktivitäts-Heatmap (4 Wochen à 7 Tage).
private const val ACTIVITY_DAYS = 28

@HiltViewModel
class StatistikViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    routeRepository: RouteRepository,
    gradeRepository: GradeRepository,
    hangboardWorkoutRepository: HangboardWorkoutRepository,
) : ViewModel() {

    val uiState: StateFlow<StatistikUiState> = combine(
        sessionRepository.observeAll(),
        routeRepository.observeAll(),
        gradeRepository.observeAllGrades(),
        gradeRepository.observeAllSystems(),
        hangboardWorkoutRepository.observeAll(),
    ) { sessions, routes, grades, systems, hangboardWorkouts ->
        val gradesById = grades.associateBy { it.id }
        val sessionsById = sessions.associateBy { it.id }

        val topped = routes.filter { it.status.istGetoppt }
        val flashes = topped.count { it.attempts <= 1 }
        val flashRate = if (topped.isEmpty()) "–" else "${flashes * 100 / topped.size}%"

        // Hängezeit = Summe der gemessenen Segment-Dauern (bei AUTO korrekt, bei MANUAL
        // identisch zur früheren Plan-Rechnung completedSets × hangSec).
        val hangboardSets = hangboardWorkouts.sumOf { it.segments.size }
        val hangboardHangMs = hangboardWorkouts.sumOf { it.totalHangMs }

        // Grade-Verteilung pro System (systemübergreifende Sortierung wäre bedeutungslos, da
        // `order` pro System zählt und Labels verschiedener Systeme nicht vergleichbar sind).
        val distributionBySystem = distributionBySystem(topped, gradesById)
        val distributionSystems = systems
            .filter { it.id in distributionBySystem.keys }
            .map { GradeSystemFilterUi(it.id, it.name) }

        StatistikUiState(
            flashRate = flashRate,
            totalTops = topped.size,
            totalSessions = sessions.size,
            distributionSystems = distributionSystems,
            distributionBySystem = distributionBySystem,
            activity = activity(routes, sessionsById),
            hangboardWorkouts = hangboardWorkouts.size,
            hangboardSets = hangboardSets,
            hangboardHangTime = if (hangboardWorkouts.isEmpty()) "–"
                else formatDurationShort(hangboardHangMs),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatistikUiState(),
    )

    // Getoppte Boulder je System nach Grad gruppiert; ein Balken je Grad, in Grad-Reihenfolge.
    // Die Farbe ist rein dekorativ (zyklisch aus der Route-Palette) — Grade sind bewusst nicht
    // farbcodiert. Nur Systeme mit getoppten Bouldern erscheinen als Schlüssel.
    private fun distributionBySystem(
        topped: List<RouteEntity>,
        gradesById: Map<Int, GradeEntity>,
    ): Map<Int, List<BarChartEntry>> =
        topped
            .mapNotNull { it.gradeId?.let(gradesById::get) }
            .groupBy { it.systemId }
            .mapValues { (_, systemGrades) ->
                systemGrades
                    .groupingBy { it }
                    .eachCount()
                    .entries
                    .sortedBy { it.key.order }
                    .mapIndexed { index, (grade, count) ->
                        BarChartEntry(
                            label = grade.label,
                            value = count.toFloat(),
                            color = routeColorPalette[index % routeColorPalette.size].second,
                        )
                    }
            }

    // Boulder pro Tag über die letzten [ACTIVITY_DAYS] Tage, auf 0f..1f normalisiert.
    // Jede Route wird über das Datum ihrer Session einem Tag zugeordnet.
    private fun activity(
        routes: List<RouteEntity>,
        sessionsById: Map<Int, SessionEntity>,
    ): List<Float> {
        val today = LocalDate.now()
        val startDay = today.minusDays((ACTIVITY_DAYS - 1).toLong())

        val countsByDay = HashMap<LocalDate, Int>()
        routes.forEach { route ->
            val session = sessionsById[route.sessionId] ?: return@forEach
            val day = session.date.toLocalDate()
            if (!day.isBefore(startDay) && !day.isAfter(today)) {
                countsByDay[day] = (countsByDay[day] ?: 0) + 1
            }
        }
        val max = (countsByDay.values.maxOrNull() ?: 0).coerceAtLeast(1)
        return (0 until ACTIVITY_DAYS).map { offset ->
            val day = startDay.plusDays(offset.toLong())
            (countsByDay[day] ?: 0).toFloat() / max
        }
    }
}
