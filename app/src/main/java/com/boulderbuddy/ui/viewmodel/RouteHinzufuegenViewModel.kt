package com.boulderbuddy.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.boulderbuddy.data.db.entity.GradeEntity
import com.boulderbuddy.data.db.entity.GradeSystemEntity
import com.boulderbuddy.data.db.entity.RouteEntity
import com.boulderbuddy.data.model.RouteStatus
import com.boulderbuddy.data.repository.GradeRepository
import com.boulderbuddy.data.repository.RouteRepository
import com.boulderbuddy.data.repository.SessionRepository
import com.boulderbuddy.ui.navigation.RouteHinzufuegen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Eingabe des "Boulder hinzufügen"-Formulars (Phase 6.5). */
data class RouteFormInput(
    val grade: String,
    val sektor: String,
    val name: String,
    val colorHex: String,
    val attempts: Int,
    val status: RouteStatus,
    val mediaUri: String?,
    val notiz: String,
)

@HiltViewModel
class RouteHinzufuegenViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val routeRepository: RouteRepository,
    private val gradeRepository: GradeRepository,
) : ViewModel() {

    // Ziel-Session aus den Nav-Argumenten; null = "ohne aktive Session angelegt" (offene Frage).
    private val argSessionId: Int? = savedStateHandle.toRoute<RouteHinzufuegen>().sessionId

    /**
     * Speichert den Boulder zur Ziel-Session und ruft danach [onSaved].
     * Fehlt eine Ziel-Session (arg null) wird ersatzweise die aktive genommen; existiert auch
     * die nicht, wird nichts gespeichert (kein FK-Ziel) und nur zurücknavigiert.
     */
    fun save(input: RouteFormInput, onSaved: () -> Unit) {
        viewModelScope.launch {
            val sessionId = argSessionId ?: sessionRepository.observeActive().first()?.id
            if (sessionId == null) {
                onSaved()
                return@launch
            }
            routeRepository.create(
                RouteEntity(
                    sessionId = sessionId,
                    gradeId = resolveGradeId(sessionId, input.grade.trim(), input.colorHex),
                    name = input.name.trim(),
                    sektor = input.sektor.trim().ifBlank { null },
                    attempts = input.attempts,
                    status = input.status,
                    mediaUri = input.mediaUri,
                    notes = input.notiz.trim().ifBlank { null },
                )
            )
            onSaved()
        }
    }

    /**
     * Löst das getippte Grade-Label + die gewählte Farbe in eine `gradeId` auf.
     * Find-or-create innerhalb des Gradsystems der Session-Halle: gleiches Label (case-insensitive)
     * = bestehender Grad, sonst wird ein neuer mit der gewählten Farbe angelegt. So bleibt die
     * grade-zentrale Schema-Sicht erhalten und alle Anzeige-Pfade (gradeId → Label/Farbe) greifen.
     */
    private suspend fun resolveGradeId(sessionId: Int, label: String, colorHex: String): Int? {
        if (label.isBlank()) return null
        val session = sessionRepository.getById(sessionId) ?: return null
        val gymId = session.gymId

        val systems = gradeRepository.observeSystemsByGym(gymId).first()
        val systemId = systems.firstOrNull()?.id
            ?: gradeRepository.createSystem(GradeSystemEntity(gymId = gymId, name = "Standard"))

        val grades = gradeRepository.observeGradesBySystem(systemId).first()
        grades.firstOrNull { it.label.equals(label, ignoreCase = true) }?.let { return it.id }

        val nextOrder = (grades.maxOfOrNull { it.order } ?: -1) + 1
        return gradeRepository.createGrade(
            GradeEntity(systemId = systemId, label = label, color = colorHex, order = nextOrder)
        )
    }
}
