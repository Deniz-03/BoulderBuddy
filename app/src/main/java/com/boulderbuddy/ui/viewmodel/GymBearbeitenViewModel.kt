package com.boulderbuddy.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.boulderbuddy.R
import com.boulderbuddy.ui.Fehlerkanal
import com.boulderbuddy.ui.Texte
import com.boulderbuddy.ui.schreibe
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.boulderbuddy.data.db.entity.GymEntity
import com.boulderbuddy.data.repository.GradeRepository
import com.boulderbuddy.data.repository.GymRepository
import com.boulderbuddy.data.repository.GymVisitRepository
import com.boulderbuddy.proximity.GeofenceManager
import com.boulderbuddy.proximity.GymLocationClient
import com.boulderbuddy.ui.navigation.GymBearbeiten
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

/** Editierbarer Zustand des Gym-Editors (Gym-Näherungs-Push, M1). */
data class GymBearbeitenUiState(
    /** `false`, solange das Gym noch geladen wird. */
    val ready: Boolean = false,
    /** `true`, wenn gerade eine neue Halle angelegt wird (Titel + Button-Text hängen daran). */
    val neu: Boolean = false,
    /** Wählbare Gradsysteme für den Hallen-Standard. */
    val systems: List<GradeSystemUi> = emptyList(),
    /** Standard-Gradsystem der Halle; `null` = keins gesetzt. */
    val defaultGradeSystemId: Int? = null,
    val name: String = "",
    /** Freitext-Adresse (bleibt bewusst von den Koordinaten getrennt). */
    val location: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Int = GymEntity.DEFAULT_GEOFENCE_RADIUS_METERS,
    val alertsEnabled: Boolean = true,
    /** `true`, während der Standort-Fix läuft (Button zeigt Fortschritt). */
    val capturingLocation: Boolean = false,
    /** Einmalige Fehlermeldung der Standort-Erfassung; vom UI als Toast gezeigt. */
    val locationError: String? = null,
    /** Gelerntes Besuchsmuster, z.B. "8 Besuche · meist dienstags" (M3); `null` = keine Besuche. */
    val visitSummary: String? = null,
    /** Sessions an dieser Halle — die Rückfrage vor dem Löschen nennt die Zahl. */
    val sessionCount: Int = 0,
) {
    val hasCoordinates: Boolean get() = latitude != null && longitude != null
}

@HiltViewModel
class GymBearbeitenViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gymRepository: GymRepository,
    private val gymVisitRepository: GymVisitRepository,
    gradeRepository: GradeRepository,
    private val locationClient: GymLocationClient,
    private val geofenceManager: GeofenceManager,
    // Loest die Anzeigetexte aus strings.xml auf (siehe ui/Texte.kt: als Schnittstelle,
    // damit dieses ViewModel ohne Android testbar bleibt).
    private val fehlerkanal: Fehlerkanal,
    private val texte: Texte,
) : ViewModel() {

    /** `null` = neue Halle. Die ID der frisch angelegten steht danach in [angelegteGymId]. */
    private val gymId: Int? = savedStateHandle.toRoute<GymBearbeiten>().gymId

    private val _uiState = MutableStateFlow(GymBearbeitenUiState())
    val uiState: StateFlow<GymBearbeitenUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            if (gymId == null) {
                // Neue Halle: leeres Formular, aber sofort bedienbar.
                _uiState.update { it.copy(ready = true, neu = true) }
            } else {
                gymRepository.getById(gymId)?.let { gym ->
                    _uiState.update {
                        it.copy(
                            ready = true,
                            name = gym.name,
                            location = gym.location.orEmpty(),
                            latitude = gym.latitude,
                            longitude = gym.longitude,
                            radiusMeters = gym.geofenceRadiusMeters,
                            alertsEnabled = gym.proximityAlertsEnabled,
                            defaultGradeSystemId = gym.defaultGradeSystemId,
                            sessionCount = gymRepository.countSessions(gymId),
                        )
                    }
                }
            }
        }
        // Wählbare Gradsysteme für den Hallen-Standard (Standards + Custom).
        viewModelScope.launch {
            combine(
                gradeRepository.observeAllSystems(),
                gradeRepository.observeAllGrades(),
            ) { systems, grades ->
                val countBySystem = grades.groupingBy { it.systemId }.eachCount()
                systems.map {
                    GradeSystemUi(id = it.id, name = it.name, gradeCount = countBySystem[it.id] ?: 0)
                }
            }.collect { systems -> _uiState.update { it.copy(systems = systems) } }
        }
        // Gelerntes Besuchsmuster live anzeigen (M3): "8 Besuche · meist dienstags".
        // Eine noch nicht angelegte Halle hat keine Besuche — dann gibt es nichts zu beobachten.
        if (gymId != null) viewModelScope.launch {
            gymVisitRepository.observeStats(gymId).collect { stats ->
                val summary = if (stats.totalVisits == 0) {
                    null
                } else {
                    val topDay = stats.visitsByDayOfWeek.maxByOrNull { it.value }?.key
                    val besuche = texte.mehrzahl(
                        R.plurals.halle_besuche, stats.totalVisits, stats.totalVisits,
                    )
                    if (topDay == null) {
                        besuche
                    } else {
                        // "Dienstag" → "meist dienstags" (deutsche Wochentage + Suffix "s").
                        val dayLabel = topDay.getDisplayName(TextStyle.FULL, Locale.GERMAN)
                            .lowercase(Locale.GERMAN)
                        texte.hole(R.string.halle_besuche_tag, besuche, dayLabel)
                    }
                }
                _uiState.update { it.copy(visitSummary = summary) }
            }
        }
    }

    fun setName(name: String) = _uiState.update { it.copy(name = name) }

    fun setLocation(location: String) = _uiState.update { it.copy(location = location) }

    fun setRadius(meters: Int) = _uiState.update { it.copy(radiusMeters = meters) }

    fun setAlertsEnabled(enabled: Boolean) = _uiState.update { it.copy(alertsEnabled = enabled) }

    /**
     * Setzt das Standard-Gradsystem der Halle; nochmal auf dasselbe getippt hebt es wieder auf.
     * Kein Standard ist ein gültiger Zustand — nicht jede Halle hat ein festes System.
     */
    fun setDefaultGradeSystem(systemId: Int) = _uiState.update {
        it.copy(defaultGradeSystemId = if (it.defaultGradeSystemId == systemId) null else systemId)
    }

    /** Manuelle Koordinaten-Eingabe (Notnagel, wenn der Standort-Button nicht nutzbar ist). */
    fun setCoordinates(latitude: Double, longitude: Double) =
        _uiState.update { it.copy(latitude = latitude, longitude = longitude) }

    fun clearCoordinates() = _uiState.update { it.copy(latitude = null, longitude = null) }

    /**
     * Übernimmt den aktuellen Gerätestandort in die Editor-Felder. Der Screen stellt vorher
     * sicher, dass die Foreground-Location-Permission erteilt ist.
     */
    fun captureCurrentLocation() {
        if (_uiState.value.capturingLocation) return
        viewModelScope.launch {
            _uiState.update { it.copy(capturingLocation = true) }
            try {
                val location = locationClient.currentLocation()
                if (location != null) {
                    _uiState.update {
                        it.copy(
                            capturingLocation = false,
                            latitude = location.latitude,
                            longitude = location.longitude,
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            capturingLocation = false,
                            locationError = texte.hole(R.string.halle_standort_nicht_ermittelbar),
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        capturingLocation = false,
                        locationError = texte.hole(
                            R.string.halle_standort_fehler,
                            e.message.orEmpty(),
                        ),
                    )
                }
            }
        }
    }

    /** Bestätigt, dass die Standort-Fehlermeldung angezeigt wurde (leert sie). */
    fun consumeLocationError() = _uiState.update { it.copy(locationError = null) }

    /**
     * Persistiert den Editor-Zustand und meldet die ID der Halle zurück — beim Bearbeiten die
     * bestehende, beim Anlegen die neu vergebene. Der Aufrufer (Session-Formular) wählt die
     * Halle damit direkt aus, statt sie in der Liste suchen zu müssen.
     *
     * Ohne Namen passiert nichts: eine Halle ohne Namen wäre in der Auswahl nicht wiederzufinden.
     */
    fun save(onSaved: (gymId: Int) -> Unit) {
        val state = _uiState.value
        if (!state.ready || state.name.isBlank()) return
        viewModelScope.launch {
            var gespeicherteId: Int? = null
            val geschrieben = fehlerkanal.schreibe(
                R.string.fehler_halle_speichern,
                protokollMarke = "Halle speichern",
            ) {
                // Beim Bearbeiten auf der geladenen Zeile aufsetzen, damit Spalten erhalten
                // bleiben, die der Editor gar nicht anfasst. Ist sie zwischenzeitlich gelöscht
                // worden, gibt es nichts zu speichern — und keine leere Halle als Ersatz.
                val basis = if (gymId == null) {
                    GymEntity(name = "")
                } else {
                    gymRepository.getById(gymId) ?: return@schreibe
                }
                val entwurf = basis.copy(
                    name = state.name.trim(),
                    location = state.location.trim().ifBlank { null },
                    latitude = state.latitude,
                    longitude = state.longitude,
                    geofenceRadiusMeters = state.radiusMeters,
                    proximityAlertsEnabled = state.alertsEnabled,
                    defaultGradeSystemId = state.defaultGradeSystemId,
                )
                gespeicherteId = if (gymId == null) {
                    gymRepository.create(entwurf)
                } else {
                    gymRepository.update(entwurf)
                    gymId
                }
                // Koordinaten/Radius/Toggle können sich geändert haben → Geofences neu
                // registrieren (M2). Idempotent, daher bedenkenlos bei jedem Save.
                geofenceManager.refreshGeofences()
            }
            // Nur bei Erfolg schliessen: `onSaved` navigiert zurueck, und mit ihm waere die
            // Eingabe weg — bei einer Halle mit von Hand gesetzten Koordinaten die
            // aergerlichste Sorte Verlust.
            gespeicherteId?.takeIf { geschrieben }?.let(onSaved)
        }
    }

    /**
     * Löscht die Halle und meldet zurück, dass der Screen schließen kann.
     *
     * Was dabei *nicht* passiert, ist der Punkt: Sessions und ihre Boulder bleiben, sie tragen
     * den Hallennamen seit v10 selbst. Ein hallenspezifisches Gradsystem wird global statt
     * gelöscht, seine Grade bleiben also gültig. Es verschwindet nur, was ohne die Halle keine
     * Bedeutung mehr hat: ihr Besuchs-Log und ihr Geofence.
     */
    fun delete(onDeleted: () -> Unit) {
        val id = gymId ?: return
        viewModelScope.launch {
            val geloescht = fehlerkanal.schreibe(
                R.string.fehler_halle_loeschen,
                protokollMarke = "Halle loeschen",
            ) {
                gymRepository.delete(id)
                // Der Geofence dieser Halle muss weg — refreshGeofences meldet ohnehin alle
                // neu an.
                geofenceManager.refreshGeofences()
            }
            // Nur schliessen, wenn wirklich geloescht wurde. Sonst stuende der Nutzer vor
            // einer Liste, in der die Halle weiterhin steht, ohne zu wissen warum.
            if (geloescht) onDeleted()
        }
    }
}
