package com.boulderbuddy.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulderbuddy.data.db.entity.GradeEntity
import com.boulderbuddy.data.db.entity.GradeSystemEntity
import com.boulderbuddy.data.db.entity.GymEntity
import com.boulderbuddy.data.repository.GradeRepository
import com.boulderbuddy.data.repository.GymRepository
import com.boulderbuddy.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Ein Gradsystem in der Verwaltungs-Liste (Name + Anzahl seiner Grade). */
data class GradeSystemUi(
    val id: Int,
    val name: String,
    val gradeCount: Int,
    /** `true`, wenn löschbar. Globale Standards (V-Scale/Französisch, `gymId == null`) nicht. */
    val deletable: Boolean = false,
)

data class EinstellungenUiState(
    val systems: List<GradeSystemUi> = emptyList(),
    /** ID des als "Standard-Grading" gewählten Systems; steuert das Grade-Dropdown. */
    val selectedGradeSystemId: Int? = null,
)

@HiltViewModel
class EinstellungenViewModel @Inject constructor(
    private val gymRepository: GymRepository,
    private val gradeRepository: GradeRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<EinstellungenUiState> = combine(
        gradeRepository.observeAllSystems(),
        gradeRepository.observeAllGrades(),
        settingsRepository.selectedGradeSystemId,
    ) { systems, grades, selectedId ->
        val countBySystem = grades.groupingBy { it.systemId }.eachCount()
        EinstellungenUiState(
            systems = systems.map {
                GradeSystemUi(
                    id = it.id,
                    name = it.name,
                    gradeCount = countBySystem[it.id] ?: 0,
                    // Globale Standards (gymId == null) sind geschützt, alles andere löschbar.
                    deletable = it.gymId != null,
                )
            },
            selectedGradeSystemId = selectedId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EinstellungenUiState(),
    )

    /** Merkt das gewählte Standard-Grading-System (persistent via DataStore). */
    fun selectGradeSystem(systemId: Int) {
        viewModelScope.launch { settingsRepository.setSelectedGradeSystem(systemId) }
    }

    /**
     * Löscht ein Custom-Grading-System. Globale Standards (V-Scale/Französisch) sind geschützt
     * und werden hier ignoriert. Boulder dieses Systems behalten ihre Farbe, verlieren aber die
     * Grad-Zuordnung (FK SET_NULL).
     */
    fun deleteGradeSystem(systemId: Int) {
        viewModelScope.launch { gradeRepository.deleteSystem(systemId) }
    }

    /**
     * Legt ein neues Custom-Grading-System an. Jedes Label wird zu einem Grad (reine
     * Schwierigkeit, Reihenfolge = Eingabe-Index). Leere Labels werden ignoriert. Fehlt eine
     * Halle, wird eine Standard-Halle angelegt, damit das System einen FK-Anker hat.
     */
    fun createGradeSystem(name: String, labels: List<String>) {
        val cleanName = name.trim()
        val cleanLabels = labels.map { it.trim() }.filter { it.isNotBlank() }
        if (cleanName.isBlank() || cleanLabels.isEmpty()) return
        viewModelScope.launch {
            val gymId = gymRepository.observeAll().first().firstOrNull()?.id
                ?: gymRepository.create(GymEntity(name = "Meine Halle"))
            val systemId = gradeRepository.createSystem(
                GradeSystemEntity(gymId = gymId, name = cleanName)
            )
            gradeRepository.createGrades(
                cleanLabels.mapIndexed { index, label ->
                    GradeEntity(systemId = systemId, label = label, order = index)
                }
            )
        }
    }
}
