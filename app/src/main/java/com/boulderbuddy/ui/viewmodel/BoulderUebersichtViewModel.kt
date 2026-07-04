package com.boulderbuddy.ui.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulderbuddy.data.repository.GradeRepository
import com.boulderbuddy.data.repository.RouteRepository
import com.boulderbuddy.ui.theme.routeColorForKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Eine Kachel der Boulder-Übersicht (alle Routen, sessionübergreifend). */
data class BoulderOverviewItemUi(
    val id: Int,
    val grade: String,
    val name: String,
    val meta: String,
    val accentColor: Color,
    /** Gradsystem des Boulders (für den System-Filter); `null` = Boulder ohne Grad. */
    val systemId: Int? = null,
    /** Grad des Boulders (für den Grade-Filter); `null` = Boulder ohne Grad. */
    val gradeId: Int? = null,
)

/** Ein wählbares Gradsystem im Filter (nur Systeme, die real Boulder haben). */
data class GradeSystemFilterUi(val id: Int, val name: String)

/** Ein wählbarer Grad im Filter eines Systems (in Grad-Reihenfolge). */
data class GradeFilterUi(val id: Int, val label: String)

data class BoulderUebersichtUiState(
    val boulders: List<BoulderOverviewItemUi> = emptyList(),
    /** Systeme, die unter den Bouldern vorkommen — Basis der ersten Filterebene. */
    val systems: List<GradeSystemFilterUi> = emptyList(),
    /** Je System die tatsächlich vorkommenden Grade (sortiert) — zweite Filterebene. */
    val gradesBySystem: Map<Int, List<GradeFilterUi>> = emptyMap(),
)

@HiltViewModel
class BoulderUebersichtViewModel @Inject constructor(
    routeRepository: RouteRepository,
    gradeRepository: GradeRepository,
) : ViewModel() {

    val uiState: StateFlow<BoulderUebersichtUiState> = combine(
        routeRepository.observeAll(),
        gradeRepository.observeAllGrades(),
        gradeRepository.observeAllSystems(),
    ) { routes, grades, systems ->
        val gradesById = grades.associateBy { it.id }

        val boulders = routes.map { route ->
            val grade = route.gradeId?.let(gradesById::get)
            BoulderOverviewItemUi(
                id = route.id,
                grade = grade?.label ?: "—",
                name = route.name.ifBlank { grade?.label ?: "Boulder" },
                meta = route.sektor?.let { "Sektor $it" }.orEmpty(),
                accentColor = routeColorForKey(route.color),
                systemId = grade?.systemId,
                gradeId = grade?.id,
            )
        }

        // Nur Systeme anbieten, die tatsächlich Boulder haben (keine leeren Filter-Chips).
        val presentSystemIds = boulders.mapNotNull { it.systemId }.toSet()
        val presentSystems = systems
            .filter { it.id in presentSystemIds }
            .map { GradeSystemFilterUi(it.id, it.name) }

        // Je System die vorkommenden Grade, in Grad-Reihenfolge (dynamisch, ohne tote Chips).
        val gradesBySystem = presentSystems.associate { system ->
            val occurringGradeIds = boulders
                .filter { it.systemId == system.id }
                .mapNotNull { it.gradeId }
                .toSet()
            val ordered = grades
                .filter { it.systemId == system.id && it.id in occurringGradeIds }
                .sortedBy { it.order }
                .map { GradeFilterUi(it.id, it.label) }
            system.id to ordered
        }

        BoulderUebersichtUiState(
            boulders = boulders,
            systems = presentSystems,
            gradesBySystem = gradesBySystem,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BoulderUebersichtUiState(),
    )
}
