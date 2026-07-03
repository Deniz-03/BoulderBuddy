package com.boulderbuddy.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulderbuddy.data.db.entity.RouteEntity
import com.boulderbuddy.data.db.entity.SessionEntity
import com.boulderbuddy.data.model.RouteStatus
import com.boulderbuddy.data.repository.GradeRepository
import com.boulderbuddy.data.repository.RouteRepository
import com.boulderbuddy.data.repository.SessionRepository
import com.boulderbuddy.ui.components.BarChartEntry
import com.boulderbuddy.ui.model.istGetoppt
import com.boulderbuddy.ui.model.parseHexColor
import com.boulderbuddy.ui.model.toLocalDate
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
    val gradeDistribution: List<BarChartEntry> = emptyList(),
    val activity: List<Float> = emptyList(),
)

// Anzahl der Tage in der Aktivitäts-Heatmap (4 Wochen à 7 Tage).
private const val ACTIVITY_DAYS = 28

@HiltViewModel
class StatistikViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    routeRepository: RouteRepository,
    gradeRepository: GradeRepository,
) : ViewModel() {

    val uiState: StateFlow<StatistikUiState> = combine(
        sessionRepository.observeAll(),
        routeRepository.observeAll(),
        gradeRepository.observeAllGrades(),
    ) { sessions, routes, grades ->
        val gradesById = grades.associateBy { it.id }
        val sessionsById = sessions.associateBy { it.id }

        val topped = routes.filter { it.status.istGetoppt }
        val flashes = topped.count { it.attempts <= 1 }
        val flashRate = if (topped.isEmpty()) "–" else "${flashes * 100 / topped.size}%"

        StatistikUiState(
            flashRate = flashRate,
            totalTops = topped.size,
            totalSessions = sessions.size,
            gradeDistribution = gradeDistribution(topped, gradesById),
            activity = activity(routes, sessionsById),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatistikUiState(),
    )

    // Getoppte Boulder nach Grad gruppiert; ein Balken je Grad, in Grad-Reihenfolge, in Grad-Farbe.
    private fun gradeDistribution(
        topped: List<RouteEntity>,
        gradesById: Map<Int, com.boulderbuddy.data.db.entity.GradeEntity>,
    ): List<BarChartEntry> =
        topped
            .mapNotNull { it.gradeId?.let(gradesById::get) }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedBy { it.key.order }
            .map { (grade, count) ->
                BarChartEntry(
                    label = grade.label,
                    value = count.toFloat(),
                    color = parseHexColor(grade.color),
                )
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
