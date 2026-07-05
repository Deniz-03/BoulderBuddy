package com.boulderbuddy.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulderbuddy.ghost.GhostArtifactStore
import com.boulderbuddy.ghost.model.GhostPoseTrack
import com.boulderbuddy.ghost.pose.VideoPoseExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GhostClimberUiState(
    /** Gewähltes Referenz-Video (M1: erst mal nur eins; M2+ kommt das Vergleichs-Video dazu). */
    val videoUri: String? = null,
    val analyzing: Boolean = false,
    /** Fortschritt der Pose-Extraktion in Frames (done/total); 0/0 = noch nicht gestartet. */
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    /** Fertige Pose-Spur — schaltet den Skelett-Player frei. */
    val poseTrack: GhostPoseTrack? = null,
    val error: String? = null,
)

/**
 * Ghost Climber (Phase 7.5, M1): Video wählen → Pose-Spur extrahieren (ML Kit, offline)
 * → Skelett-Overlay über der Wiedergabe. Die schwere Arbeit läuft im Extractor auf
 * Dispatchers.Default; Ergebnisse werden über den GhostArtifactStore als JSON gecacht.
 */
@HiltViewModel
class GhostClimberViewModel @Inject constructor(
    private val poseExtractor: VideoPoseExtractor,
    private val artifactStore: GhostArtifactStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GhostClimberUiState())
    val uiState: StateFlow<GhostClimberUiState> = _uiState.asStateFlow()

    /** Neues Video gewählt: alte Analyse verwerfen (die Spur gehört zur alten URI). */
    fun onVideoSelected(uri: String) {
        _uiState.update {
            it.copy(videoUri = uri, poseTrack = null, error = null, progressDone = 0, progressTotal = 0)
        }
    }

    fun analyze() {
        val uri = _uiState.value.videoUri ?: return
        if (_uiState.value.analyzing) return
        viewModelScope.launch {
            _uiState.update { it.copy(analyzing = true, error = null, poseTrack = null) }
            try {
                val track = artifactStore.loadPoseTrack(uri)
                    ?: poseExtractor.extract(uri) { done, total ->
                        _uiState.update { it.copy(progressDone = done, progressTotal = total) }
                    }.also { artifactStore.savePoseTrack(it) }
                _uiState.update { it.copy(analyzing = false, poseTrack = track) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(analyzing = false, error = e.message ?: "Analyse fehlgeschlagen")
                }
            }
        }
    }
}
