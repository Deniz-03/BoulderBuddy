package com.boulderbuddy.ui.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulderbuddy.data.repository.GradeRepository
import com.boulderbuddy.data.repository.RouteRepository
import com.boulderbuddy.data.repository.SessionRepository
import com.boulderbuddy.ui.model.toBoulderStatus
import com.boulderbuddy.ui.theme.routeColorForKey
import com.boulderbuddy.ui.screens.BoulderStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// =============================================================================
// Ein einzelner Boulder in der Detailansicht
// =============================================================================
//
// Die boulderId kommt per Assisted Injection herein und nicht aus den Nav-Argumenten: der
// Screen läuft am Tablet auch im Detail-Pane des Zwei-Spalten-Layouts, und dort gibt es
// keine eigenen Nav-Argumente.
//
// Gelesen wird über `observeAll`-Flows und nicht über ein `observeById`. Das klingt
// verschwenderisch, ist aber der Grund, dass die Ansicht sich von selbst aktualisiert, wenn
// nebenan etwas geändert wird — und die Datenmengen sind die einer Trainings-App.

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

// Assisted Injection: die boulderId wird explizit übergeben statt aus den Nav-Argumenten
// gelesen. Genau wie beim SessionViewModel — derselbe ViewModel bedient damit sowohl die
// klassische BoulderDetail-Route als auch den Detail-Pane der Boulder-Übersicht auf dem
// Tablet, wo es keine eigenen Nav-Argumente gibt.
@HiltViewModel(assistedFactory = BoulderDetailViewModel.Factory::class)
class BoulderDetailViewModel @AssistedInject constructor(
    @Assisted private val boulderId: Int,
    private val routeRepository: RouteRepository,
    gradeRepository: GradeRepository,
    sessionRepository: SessionRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(boulderId: Int): BoulderDetailViewModel
    }

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
