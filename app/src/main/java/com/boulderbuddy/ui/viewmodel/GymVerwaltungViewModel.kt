package com.boulderbuddy.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulderbuddy.data.repository.GymRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Eine Halle in der Verwaltungs-Liste (Gym-Näherungs-Push, M1). */
data class GymUi(
    val id: Int,
    val name: String,
    /** Freitext-Adresse; `null` = nicht gesetzt. */
    val location: String?,
    /** `true`, wenn Koordinaten hinterlegt sind (Gym wird geofenced). */
    val hasCoordinates: Boolean,
    val proximityAlertsEnabled: Boolean,
)

data class GymVerwaltungUiState(
    val gyms: List<GymUi> = emptyList(),
)

@HiltViewModel
class GymVerwaltungViewModel @Inject constructor(
    gymRepository: GymRepository,
) : ViewModel() {

    val uiState: StateFlow<GymVerwaltungUiState> = gymRepository.observeAll()
        .map { gyms ->
            GymVerwaltungUiState(
                gyms = gyms.map {
                    GymUi(
                        id = it.id,
                        name = it.name,
                        location = it.location,
                        hasCoordinates = it.latitude != null && it.longitude != null,
                        proximityAlertsEnabled = it.proximityAlertsEnabled,
                    )
                },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = GymVerwaltungUiState(),
        )
}
