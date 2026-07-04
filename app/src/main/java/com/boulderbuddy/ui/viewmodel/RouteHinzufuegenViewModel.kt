package com.boulderbuddy.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.boulderbuddy.data.db.entity.RouteEntity
import com.boulderbuddy.data.model.RouteStatus
import com.boulderbuddy.data.repository.GradeRepository
import com.boulderbuddy.data.repository.RouteRepository
import com.boulderbuddy.data.repository.SessionRepository
import com.boulderbuddy.data.settings.SettingsRepository
import com.boulderbuddy.ui.navigation.RouteHinzufuegen
import com.boulderbuddy.ui.theme.DefaultRouteColorKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Eingabe des "Boulder hinzufügen/bearbeiten"-Formulars. */
data class RouteFormInput(
    val gradeId: Int?,
    /** Gewählter Route-Farb-Key ("red", "green", …); von der Schwierigkeit entkoppelt. */
    val color: String,
    val sektor: String,
    val name: String,
    val attempts: Int,
    val status: RouteStatus,
    val mediaUri: String?,
    val notiz: String,
)

/** Ein wählbarer Grad im Grade-Dropdown (reines Label — die Farbe hängt an der Route). */
data class GradeOption(
    val id: Int,
    val label: String,
)

/** Vorbefüllte Startwerte des Formulars (Add: Defaults, Edit: bestehender Boulder). */
data class RouteFormInitial(
    val gradeId: Int? = null,
    val color: String = DefaultRouteColorKey,
    val sektor: String = "",
    val name: String = "",
    val attempts: Int = 1,
    val status: RouteStatus = RouteStatus.SENT,
    val mediaUri: String? = null,
    val notiz: String = "",
)

data class RouteFormUiState(
    /** `false`, solange die (Edit-)Startwerte noch geladen werden. */
    val ready: Boolean = false,
    val isEditing: Boolean = false,
    val grades: List<GradeOption> = emptyList(),
    val initial: RouteFormInitial = RouteFormInitial(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RouteHinzufuegenViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val routeRepository: RouteRepository,
    gradeRepository: GradeRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<RouteHinzufuegen>()
    private val argSessionId: Int? = args.sessionId
    private val argBoulderId: Int? = args.boulderId

    // Session, in deren Kontext das Formular läuft. Add: das Nav-Argument (bzw. die aktive
    // Session). Edit: die Session des bearbeiteten Boulders. Steuert, welches Gradsystem das
    // Dropdown speist — asynchron aufgelöst, daher als eigener Flow (Startwert = arg).
    private val sessionIdFlow = MutableStateFlow(argSessionId)

    // Effektives Gradsystem: bevorzugt das der Session, sonst der globale Standard, sonst das
    // erste vorhandene System — so ist das Dropdown nie ohne Not leer.
    private val gradesFlow = combine(
        sessionIdFlow,
        sessionRepository.observeAll(),
        gradeRepository.observeAllSystems(),
        settingsRepository.selectedGradeSystemId,
    ) { sessionId, sessions, systems, selectedId ->
        fun valid(id: Int?): Int? = id?.takeIf { candidate -> systems.any { it.id == candidate } }
        val sessionSystem = valid(sessions.firstOrNull { it.id == sessionId }?.gradeSystemId)
        sessionSystem ?: valid(selectedId) ?: systems.firstOrNull()?.id
    }.distinctUntilChanged().flatMapLatest { systemId ->
        if (systemId == null) flowOf(emptyList())
        else gradeRepository.observeGradesBySystem(systemId)
    }

    // Startwerte: im Edit-Fall der bestehende Boulder, sonst die Add-Defaults.
    private val initialFlow = MutableStateFlow<RouteFormInitial?>(null)

    init {
        viewModelScope.launch {
            val existing = argBoulderId?.let { routeRepository.getById(it) }
            // Session-Kontext auflösen: Edit → Session des Boulders; Add ohne Argument → aktive.
            sessionIdFlow.value = when {
                existing != null -> existing.sessionId
                argSessionId != null -> argSessionId
                else -> sessionRepository.observeActive().first()?.id
            }
            initialFlow.value = existing
                ?.let {
                    RouteFormInitial(
                        gradeId = it.gradeId,
                        color = it.color ?: DefaultRouteColorKey,
                        sektor = it.sektor.orEmpty(),
                        name = it.name,
                        attempts = it.attempts,
                        status = it.status,
                        mediaUri = it.mediaUri,
                        notiz = it.notes.orEmpty(),
                    )
                }
                ?: RouteFormInitial()
        }
    }

    val uiState: StateFlow<RouteFormUiState> = combine(
        gradesFlow,
        initialFlow,
    ) { grades, initial ->
        RouteFormUiState(
            ready = initial != null,
            isEditing = argBoulderId != null,
            grades = grades.map { GradeOption(id = it.id, label = it.label) },
            initial = initial ?: RouteFormInitial(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RouteFormUiState(),
    )

    /**
     * Speichert den Boulder und ruft danach [onSaved].
     * Edit-Modus (boulderId gesetzt): aktualisiert die bestehende Route.
     * Add-Modus: legt zur Ziel-Session (arg oder aktive) eine neue Route an; fehlt eine
     * Session, wird nichts gespeichert (kein FK-Ziel) und nur zurücknavigiert.
     */
    fun save(input: RouteFormInput, onSaved: () -> Unit) {
        viewModelScope.launch {
            if (argBoulderId != null) {
                val existing = routeRepository.getById(argBoulderId)
                if (existing != null) {
                    routeRepository.update(
                        existing.copy(
                            gradeId = input.gradeId,
                            color = input.color,
                            name = input.name.trim(),
                            sektor = input.sektor.trim().ifBlank { null },
                            attempts = input.attempts,
                            status = input.status,
                            mediaUri = input.mediaUri,
                            notes = input.notiz.trim().ifBlank { null },
                        )
                    )
                }
            } else {
                val sessionId = argSessionId ?: sessionRepository.observeActive().first()?.id
                if (sessionId == null) {
                    onSaved()
                    return@launch
                }
                routeRepository.create(
                    RouteEntity(
                        sessionId = sessionId,
                        gradeId = input.gradeId,
                        color = input.color,
                        name = input.name.trim(),
                        sektor = input.sektor.trim().ifBlank { null },
                        attempts = input.attempts,
                        status = input.status,
                        mediaUri = input.mediaUri,
                        notes = input.notiz.trim().ifBlank { null },
                    )
                )
            }
            onSaved()
        }
    }
}
