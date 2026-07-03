package com.boulderbuddy.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulderbuddy.data.db.entity.GradeEntity
import com.boulderbuddy.data.db.entity.GradeSystemEntity
import com.boulderbuddy.data.db.entity.GymEntity
import com.boulderbuddy.data.repository.GradeRepository
import com.boulderbuddy.data.repository.GymRepository
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
)

data class EinstellungenUiState(
    val systems: List<GradeSystemUi> = emptyList(),
)

@HiltViewModel
class EinstellungenViewModel @Inject constructor(
    private val gymRepository: GymRepository,
    private val gradeRepository: GradeRepository,
) : ViewModel() {

    val uiState: StateFlow<EinstellungenUiState> = combine(
        gradeRepository.observeAllSystems(),
        gradeRepository.observeAllGrades(),
    ) { systems, grades ->
        val countBySystem = grades.groupingBy { it.systemId }.eachCount()
        EinstellungenUiState(
            systems = systems.map {
                GradeSystemUi(id = it.id, name = it.name, gradeCount = countBySystem[it.id] ?: 0)
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EinstellungenUiState(),
    )

    /**
     * Legt ein neues Custom-Farbsystem an (MVP-Must-Have). Jede gewählte Farbe wird zu einem
     * Grad (Label = Farbname, Farbe = Hex, Reihenfolge = Auswahlindex). Fehlt eine Halle,
     * wird eine Standard-Halle angelegt, damit das System einen FK-Anker hat.
     */
    fun createColorSystem(name: String, colorGrades: List<Pair<String, String>>) {
        if (name.isBlank() || colorGrades.isEmpty()) return
        viewModelScope.launch {
            val gymId = gymRepository.observeAll().first().firstOrNull()?.id
                ?: gymRepository.create(GymEntity(name = "Meine Halle"))
            val systemId = gradeRepository.createSystem(
                GradeSystemEntity(gymId = gymId, name = name.trim())
            )
            gradeRepository.createGrades(
                colorGrades.mapIndexed { index, (label, hex) ->
                    GradeEntity(systemId = systemId, label = label, color = hex, order = index)
                }
            )
        }
    }
}
