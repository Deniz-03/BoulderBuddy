package com.boulderbuddy.ui.viewmodel

import com.boulderbuddy.R
import com.boulderbuddy.ui.Texte
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulderbuddy.data.db.entity.HangboardWorkoutMode
import com.boulderbuddy.data.db.entity.hallenName
import com.boulderbuddy.data.repository.GymRepository
import com.boulderbuddy.data.repository.HangboardWorkoutRepository
import com.boulderbuddy.data.repository.SessionRepository
import com.boulderbuddy.ui.model.formatRelativeDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Ein Eintrag der Hangboard-Historie (fertig formatiert, Farben entscheidet der Screen). */
data class HangboardHistorieEntryUi(
    val id: Int,
    /** Verknüpfte Halle ("Halle Nord") oder "Eigenständig" bei Workouts ohne Session. */
    val title: String,
    /** z.B. "Heute · Manuell" bzw. "12. Juni · Auto". */
    val subtitle: String,
    /** z.B. ["6 Sätze", "00:42 Hängezeit"]. */
    val badges: List<String>,
    /** true = ohne Kletter-Session (eigenständiges Training). */
    val standalone: Boolean,
    /** true = automatisch erkannt (AUTO), false = manueller Timer. */
    val auto: Boolean,
)

data class HangboardHistorieUiState(
    val loading: Boolean = true,
    /** Alle Workouts (Phone+Uhr, manuell+auto, mit und ohne Session), neueste zuerst. */
    val entries: List<HangboardHistorieEntryUi> = emptyList(),
)

/**
 * Speist die Hangboard-Historie (§0 Säule 5): die Liste ALLER Workouts über das vereinte
 * Modell — erst dadurch werden eigenständige Trainings (ohne Session) sichtbar, statt nur
 * anonym in den Statistik-Summen aufzugehen.
 */
@HiltViewModel
class HangboardHistorieViewModel @Inject constructor(
    hangboardWorkoutRepository: HangboardWorkoutRepository,
    sessionRepository: SessionRepository,
    gymRepository: GymRepository,
    // Loest die Anzeigetexte aus strings.xml auf (siehe ui/Texte.kt: als Schnittstelle,
    // damit dieses ViewModel ohne Android testbar bleibt).
    private val texte: Texte,
) : ViewModel() {

    val uiState: StateFlow<HangboardHistorieUiState> = combine(
        hangboardWorkoutRepository.observeAll(),
        sessionRepository.observeAll(),
        gymRepository.observeAll(),
    ) { workouts, sessions, gyms ->
        val gymNameBySessionId = sessions.associate { session ->
            session.id to session.hallenName { id -> gyms.firstOrNull { it.id == id }?.name }
        }
        HangboardHistorieUiState(
            loading = false,
            entries = workouts.map { workout ->
                val w = workout.workout
                val gymName = w.sessionId?.let { gymNameBySessionId[it] }
                val hangSeconds = workout.totalHangMs / 1000
                HangboardHistorieEntryUi(
                    id = w.id,
                    title = if (w.sessionId == null) {
                        texte.hole(R.string.historie_eigenstaendig)
                    } else {
                        gymName ?: texte.hole(R.string.historie_session)
                    },
                    subtitle = listOf(
                        formatRelativeDay(w.endedAt),
                        texte.hole(
                            if (w.mode == HangboardWorkoutMode.AUTO) {
                                R.string.historie_auto
                            } else {
                                R.string.historie_manuell
                            },
                        ),
                    ).joinToString(" · "),
                    badges = listOf(
                        texte.mehrzahl(
                            R.plurals.historie_saetze,
                            workout.segments.size,
                            workout.segments.size,
                        ),
                        texte.hole(
                            R.string.historie_haengezeit,
                            "%02d:%02d".format(hangSeconds / 60, hangSeconds % 60),
                        ),
                    ),
                    standalone = w.sessionId == null,
                    auto = w.mode == HangboardWorkoutMode.AUTO,
                )
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HangboardHistorieUiState(),
    )
}
