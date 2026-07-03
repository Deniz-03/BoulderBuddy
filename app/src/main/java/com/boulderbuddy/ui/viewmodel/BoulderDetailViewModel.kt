package com.boulderbuddy.ui.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.boulderbuddy.data.repository.GradeRepository
import com.boulderbuddy.data.repository.RouteRepository
import com.boulderbuddy.ui.model.parseHexColor
import com.boulderbuddy.ui.model.toBoulderStatus
import com.boulderbuddy.ui.navigation.BoulderDetail
import com.boulderbuddy.ui.screens.BoulderStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class BoulderDetailUiState(
    val loading: Boolean = true,
    val exists: Boolean = false,
    val name: String = "",
    val sektor: String = "",
    val grade: String = "—",
    val accentColor: Color = Color(0xFF888888),
    val status: BoulderStatus = BoulderStatus.PROJEKT,
    val versuche: Int = 0,
    val notiz: String? = null,
    val fotoUri: String? = null,
)

@HiltViewModel
class BoulderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    routeRepository: RouteRepository,
    gradeRepository: GradeRepository,
) : ViewModel() {

    private val boulderId: Int = savedStateHandle.toRoute<BoulderDetail>().boulderId

    val uiState: StateFlow<BoulderDetailUiState> = combine(
        routeRepository.observeAll(),
        gradeRepository.observeAllGrades(),
    ) { routes, grades ->
        val route = routes.firstOrNull { it.id == boulderId }
            ?: return@combine BoulderDetailUiState(loading = false, exists = false)
        val grade = route.gradeId?.let { id -> grades.firstOrNull { it.id == id } }
        BoulderDetailUiState(
            loading = false,
            exists = true,
            name = route.name.ifBlank { grade?.label ?: "Boulder" },
            sektor = route.sektor.orEmpty(),
            grade = grade?.label ?: "—",
            accentColor = grade?.color?.let(::parseHexColor) ?: Color(0xFF888888),
            status = route.status.toBoulderStatus(route.attempts),
            versuche = route.attempts,
            notiz = route.notes,
            fotoUri = route.mediaUri,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BoulderDetailUiState(),
    )
}
