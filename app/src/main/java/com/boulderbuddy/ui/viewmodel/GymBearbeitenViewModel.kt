package com.boulderbuddy.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.boulderbuddy.data.db.entity.GymEntity
import com.boulderbuddy.data.repository.GymRepository
import com.boulderbuddy.proximity.GeofenceManager
import com.boulderbuddy.proximity.GymLocationClient
import com.boulderbuddy.ui.navigation.GymBearbeiten
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Editierbarer Zustand des Gym-Editors (Gym-Näherungs-Push, M1). */
data class GymBearbeitenUiState(
    /** `false`, solange das Gym noch geladen wird. */
    val ready: Boolean = false,
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
) {
    val hasCoordinates: Boolean get() = latitude != null && longitude != null
}

@HiltViewModel
class GymBearbeitenViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gymRepository: GymRepository,
    private val locationClient: GymLocationClient,
    private val geofenceManager: GeofenceManager,
) : ViewModel() {

    private val gymId = savedStateHandle.toRoute<GymBearbeiten>().gymId

    private val _uiState = MutableStateFlow(GymBearbeitenUiState())
    val uiState: StateFlow<GymBearbeitenUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
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
                    )
                }
            }
        }
    }

    fun setName(name: String) = _uiState.update { it.copy(name = name) }

    fun setLocation(location: String) = _uiState.update { it.copy(location = location) }

    fun setRadius(meters: Int) = _uiState.update { it.copy(radiusMeters = meters) }

    fun setAlertsEnabled(enabled: Boolean) = _uiState.update { it.copy(alertsEnabled = enabled) }

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
                            locationError = "Kein Standort verfügbar — sind die Standortdienste an?",
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        capturingLocation = false,
                        locationError = "Standort konnte nicht ermittelt werden: ${e.message}",
                    )
                }
            }
        }
    }

    /** Bestätigt, dass die Standort-Fehlermeldung angezeigt wurde (leert sie). */
    fun consumeLocationError() = _uiState.update { it.copy(locationError = null) }

    /** Persistiert den Editor-Zustand und meldet Erfolg zurück (Screen navigiert dann zurück). */
    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        if (!state.ready || state.name.isBlank()) return
        viewModelScope.launch {
            val existing = gymRepository.getById(gymId) ?: return@launch
            gymRepository.update(
                existing.copy(
                    name = state.name.trim(),
                    location = state.location.trim().ifBlank { null },
                    latitude = state.latitude,
                    longitude = state.longitude,
                    geofenceRadiusMeters = state.radiusMeters,
                    proximityAlertsEnabled = state.alertsEnabled,
                )
            )
            // Koordinaten/Radius/Toggle können sich geändert haben → Geofences neu
            // registrieren (M2). Idempotent, daher bedenkenlos bei jedem Save.
            geofenceManager.refreshGeofences()
            onSaved()
        }
    }
}
