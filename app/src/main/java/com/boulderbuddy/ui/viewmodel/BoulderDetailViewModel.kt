package com.boulderbuddy.ui.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.boulderbuddy.data.repository.GradeRepository
import com.boulderbuddy.data.repository.RouteRepository
import com.boulderbuddy.data.repository.SessionRepository
import com.boulderbuddy.ui.model.toBoulderStatus
import com.boulderbuddy.ui.navigation.BoulderDetail
import com.boulderbuddy.ui.theme.routeColorForKey
import com.boulderbuddy.ui.screens.BoulderStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    /** `true`, wenn die Session dieses Boulders noch läuft (endedAt == null). Steuert die
     *  Schnell-Versuche-Buttons — All-Time-Boulder aus beendeten Sessions zeigen sie nicht. */
    val isSessionActive: Boolean = false,
)

@HiltViewModel
class BoulderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val routeRepository: RouteRepository,
    gradeRepository: GradeRepository,
    sessionRepository: SessionRepository,
) : ViewModel() {

    private val boulderId: Int = savedStateHandle.toRoute<BoulderDetail>().boulderId

    val uiState: StateFlow<BoulderDetailUiState> = combine(
        routeRepository.observeAll(),
        gradeRepository.observeAllGrades(),
        sessionRepository.observeAll(),
    ) { routes, grades, sessions ->
        val route = routes.firstOrNull { it.id == boulderId }
            ?: return@combine BoulderDetailUiState(loading = false, exists = false)
        val grade = route.gradeId?.let { id -> grades.firstOrNull { it.id == id } }
        val session = sessions.firstOrNull { it.id == route.sessionId }
        BoulderDetailUiState(
            loading = false,
            exists = true,
            name = route.name.ifBlank { grade?.label ?: "Boulder" },
            sektor = route.sektor.orEmpty(),
            grade = grade?.label ?: "—",
            accentColor = routeColorForKey(route.color),
            status = route.status.toBoulderStatus(route.attempts),
            versuche = route.attempts,
            notiz = route.notes,
            fotoUri = route.mediaUri,
            isSessionActive = session != null && session.endedAt == null,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BoulderDetailUiState(),
    )

    /** Versuche schnell hochzählen (aktive Session). */
    fun incrementAttempts() = changeAttempts(+1)

    /** Versuche schnell runterzählen, min. 0 (aktive Session). */
    fun decrementAttempts() = changeAttempts(-1)

    private fun changeAttempts(delta: Int) {
        viewModelScope.launch {
            val route = routeRepository.getById(boulderId) ?: return@launch
            val newAttempts = (route.attempts + delta).coerceAtLeast(0)
            if (newAttempts != route.attempts) {
                routeRepository.update(route.copy(attempts = newAttempts))
            }
        }
    }
}
