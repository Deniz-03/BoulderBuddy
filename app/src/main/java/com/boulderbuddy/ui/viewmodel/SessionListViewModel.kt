package com.boulderbuddy.ui.viewmodel

import androidx.compose.ui.graphics.Color
import com.boulderbuddy.R
import com.boulderbuddy.ui.Texte
import androidx.lifecycle.ViewModel
import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.boulderbuddy.data.db.entity.hallenName
import com.boulderbuddy.data.repository.GradeRepository
import com.boulderbuddy.data.repository.GymRepository
import com.boulderbuddy.data.repository.RouteRepository
import com.boulderbuddy.data.repository.SessionRepository
import com.boulderbuddy.ui.model.formatRelativeDay
import com.boulderbuddy.ui.model.formatSessionTag
import com.boulderbuddy.ui.model.istGetoppt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** Eine Zeile der Session-Übersicht. */
data class SessionListItemUi(
    val id: Int,
    val gym: String,
    val date: String,
    val accentColor: Color,
    val badges: List<String>,
    val isActive: Boolean,
)

/** Sortier-Kriterium der Session-Übersicht. */
enum class SessionSortMode(@param:StringRes val label: Int) {
    DATUM(R.string.sortierung_datum),
    HALLE(R.string.sortierung_halle),
    BOULDER(R.string.sortierung_boulder),
}

data class SessionListUiState(
    val sessions: List<SessionListItemUi> = emptyList(),
    val sortMode: SessionSortMode = SessionSortMode.DATUM,
    /** true = absteigend (neueste / meiste zuerst), false = aufsteigend. */
    val sortDescending: Boolean = true,
)

@HiltViewModel
class SessionListViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    routeRepository: RouteRepository,
    gymRepository: GymRepository,
    gradeRepository: GradeRepository,
    // Loest die Anzeigetexte aus strings.xml auf (siehe ui/Texte.kt: als Schnittstelle,
    // damit dieses ViewModel ohne Android testbar bleibt).
    private val texte: Texte,
) : ViewModel() {

    // Sortierung ist reiner Anzeige-Zustand (nicht persistiert) und lebt daher im ViewModel,
    // nicht im DAO: die Liste ist klein und wird ohnehin komplett im Speicher aufgebaut.
    private val sortState = MutableStateFlow(SessionSortMode.DATUM to true)

    /**
     * Wählt das Sortier-Kriterium. Erneutes Wählen des bereits aktiven Kriteriums dreht die
     * Richtung um — so ist die Zeile ein einziger Bedienpunkt für Kriterium *und* Richtung.
     */
    fun setSortMode(mode: SessionSortMode) {
        sortState.update { (current, descending) ->
            if (current == mode) mode to !descending else mode to true
        }
    }

    val uiState: StateFlow<SessionListUiState> = combine(
        sessionRepository.observeAll(),
        routeRepository.observeAll(),
        gymRepository.observeAll(),
        gradeRepository.observeAllGrades(),
        sortState,
    ) { sessions, routes, gyms, _, (sortMode, descending) ->
        val gymsById = gyms.associateBy { it.id }
        val routesBySession = routes.groupBy { it.sessionId }

        val bySortKey = when (sortMode) {
            SessionSortMode.DATUM -> sessions.sortedBy { it.date }
            SessionSortMode.HALLE -> sessions.sortedBy {
                // Auch nach „Halle" sortiert eine Session mit gelöschter Halle noch sinnvoll:
                // ihr Schnappschuss-Name steht dort, wo der Hallenname stünde.
                it.hallenName { id -> gymsById[id]?.name }.orEmpty().lowercase()
            }
            SessionSortMode.BOULDER -> sessions.sortedBy { routesBySession[it.id].orEmpty().size }
        }
        val directed = if (descending) bySortKey.reversed() else bySortKey

        // Aktive Session (endedAt == null) bleibt in jeder Sortierung oben — sie ist der
        // Einstiegspunkt, nicht ein Listeneintrag wie jeder andere. sortedByDescending ist
        // stabil, die Sortierung innerhalb der beiden Gruppen bleibt also erhalten.
        val ordered = directed.sortedByDescending { it.endedAt == null }

        val items = ordered.map { session ->
            val sessionRoutes = routesBySession[session.id].orEmpty()
            val isActive = session.endedAt == null
            val topCount = sessionRoutes.count { it.status.istGetoppt }
            val badges = buildList {
                add(
                    texte.mehrzahl(
                        R.plurals.anzahl_boulder, sessionRoutes.size, sessionRoutes.size,
                    )
                )
                if (!isActive && topCount > 0) {
                    add(
                        texte.mehrzahl(
                            R.plurals.anzahl_tops, topCount, topCount,
                        )
                    )
                }
            }
            SessionListItemUi(
                id = session.id,
                gym = session.hallenName { gymsById[it]?.name }
                    ?: texte.hole(R.string.session_halle_unbekannt),
                date = formatSessionTag(session.date, laeuftNoch = isActive),
                accentColor = accentColorFor(sessionRoutes),
                badges = badges,
                isActive = isActive,
            )
        }
        SessionListUiState(
            sessions = items,
            sortMode = sortMode,
            sortDescending = descending,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SessionListUiState(),
    )
}
