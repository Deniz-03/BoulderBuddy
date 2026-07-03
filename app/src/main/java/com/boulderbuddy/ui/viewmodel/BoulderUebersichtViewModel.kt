package com.boulderbuddy.ui.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulderbuddy.data.repository.GradeRepository
import com.boulderbuddy.data.repository.RouteRepository
import com.boulderbuddy.ui.model.parseHexColor
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
)

data class BoulderUebersichtUiState(
    val boulders: List<BoulderOverviewItemUi> = emptyList(),
)

@HiltViewModel
class BoulderUebersichtViewModel @Inject constructor(
    routeRepository: RouteRepository,
    gradeRepository: GradeRepository,
) : ViewModel() {

    val uiState: StateFlow<BoulderUebersichtUiState> = combine(
        routeRepository.observeAll(),
        gradeRepository.observeAllGrades(),
    ) { routes, grades ->
        val gradesById = grades.associateBy { it.id }
        BoulderUebersichtUiState(
            boulders = routes.map { route ->
                val grade = route.gradeId?.let(gradesById::get)
                BoulderOverviewItemUi(
                    id = route.id,
                    grade = grade?.label ?: "—",
                    name = route.name.ifBlank { grade?.label ?: "Boulder" },
                    meta = route.sektor?.let { "Sektor $it" }.orEmpty(),
                    accentColor = grade?.color?.let(::parseHexColor) ?: Color(0xFF888888),
                )
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BoulderUebersichtUiState(),
    )
}
