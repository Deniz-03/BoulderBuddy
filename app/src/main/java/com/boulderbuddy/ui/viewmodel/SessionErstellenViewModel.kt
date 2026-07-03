package com.boulderbuddy.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulderbuddy.data.db.entity.GymEntity
import com.boulderbuddy.data.db.entity.SessionEntity
import com.boulderbuddy.data.repository.GymRepository
import com.boulderbuddy.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionErstellenViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val gymRepository: GymRepository,
) : ViewModel() {

    /**
     * Legt eine neue, aktive Session an (`endedAt = null`) und meldet die neue ID zurück.
     *
     * Halle: Die [SessionEntity] referenziert nur eine `gymId` (kein Gradsystem-FK), daher wird
     * die getippte Halle "find-or-create" behandelt — gleicher Name (case-insensitive) = bestehende
     * Halle, sonst wird eine neue angelegt. Die Grading-Auswahl im Screen bleibt vorerst reine UI
     * (die Session speichert kein Gradsystem); sie steuert erst beim Route-Anlegen die Grade.
     */
    fun createSession(ort: String, notiz: String, onCreated: (Int) -> Unit) {
        viewModelScope.launch {
            val name = ort.trim().ifBlank { "Meine Halle" }
            val existing = gymRepository.observeAll().first()
                .firstOrNull { it.name.equals(name, ignoreCase = true) }
            val gymId = existing?.id ?: gymRepository.create(GymEntity(name = name))

            val newId = sessionRepository.create(
                SessionEntity(
                    gymId = gymId,
                    date = System.currentTimeMillis(),
                    notes = notiz.trim().ifBlank { null },
                    endedAt = null,
                )
            )
            onCreated(newId)
        }
    }
}
