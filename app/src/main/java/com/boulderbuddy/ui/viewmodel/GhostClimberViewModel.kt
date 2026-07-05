package com.boulderbuddy.ui.viewmodel

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulderbuddy.ghost.GhostArtifactStore
import com.boulderbuddy.ghost.GhostTuning
import com.boulderbuddy.ghost.analysis.GhostTimeMapping
import com.boulderbuddy.ghost.analysis.RoutePolyline
import com.boulderbuddy.ghost.analysis.buildTimeMapping
import com.boulderbuddy.ghost.analysis.dtw
import com.boulderbuddy.ghost.analysis.progressSignal
import com.boulderbuddy.ghost.analysis.smoothedHipTrajectory
import com.boulderbuddy.ghost.analysis.suggestRoutePath
import com.boulderbuddy.ghost.geometry.Homography
import com.boulderbuddy.ghost.geometry.toVec2
import com.boulderbuddy.ghost.geometry.transformedBy
import com.boulderbuddy.ghost.model.GhostPoint
import com.boulderbuddy.ghost.model.GhostPoseTrack
import com.boulderbuddy.ghost.pose.VideoPoseExtractor
import com.boulderbuddy.ghost.video.GhostFrameDecoder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Die beiden Versuche eines Vergleichs. Referenz definiert den Wand-Referenzraum (P0). */
enum class GhostRole { REFERENCE, COMPARISON }

/** Schritte des geführten Flows (A.4): jeder Schritt eine eigene Ansicht. */
enum class GhostStep { SELECTION, ANCHORS, PATH, PREVIEW }

/** Zustand eines Video-Slots (Referenz bzw. Vergleich) durch alle Pipeline-Schritte. */
data class GhostVideoSlot(
    val uri: String? = null,
    /** Fortschritt der Pose-Extraktion in Frames (done/total); 0/0 = nicht gestartet. */
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    val track: GhostPoseTrack? = null,
    // --- Anker-Erfassung (M2) ---
    val anchorFrameTimeMs: Long = 0L,
    val anchorFrame: ImageBitmap? = null,
    val anchors: List<GhostPoint> = emptyList(),
)

data class GhostClimberUiState(
    val step: GhostStep = GhostStep.SELECTION,
    val reference: GhostVideoSlot = GhostVideoSlot(),
    val comparison: GhostVideoSlot = GhostVideoSlot(),
    val analyzing: Boolean = false,
    val error: String? = null,
    /** Vergleichs-Spur, per Homographie in den Referenzraum transformiert (M2-Ergebnis). */
    val ghostTrack: GhostPoseTrack? = null,
    // --- Routenpfad + Alignment (M3) ---
    /** Geglättete Hüfttrajektorie der Referenz (Kontext-Anzeige im Pfad-Editor). */
    val hipTrajectory: List<GhostPoint> = emptyList(),
    /** Pfad-Vorschlag (P3) — Basis für "Vorschlag wiederherstellen". */
    val suggestedPath: List<GhostPoint> = emptyList(),
    /** Der aktuell editierte Routenpfad (Polylinie im Referenzraum). */
    val routePath: List<GhostPoint> = emptyList(),
    /** DTW-Zeitmapping Referenz→Vergleich (M3-Ergebnis); steuert den Geist im Player. */
    val timeMapping: GhostTimeMapping? = null,
) {
    fun slot(role: GhostRole): GhostVideoSlot =
        if (role == GhostRole.REFERENCE) reference else comparison

    val canAnalyze: Boolean
        get() = reference.uri != null && comparison.uri != null && !analyzing

    /** ≥4 Anker pro Video und gleiche Anzahl (Korrespondenzen sind Index-gepaart). */
    val anchorsComplete: Boolean
        get() = reference.anchors.size >= GhostTuning.MIN_ANCHORS &&
            reference.anchors.size == comparison.anchors.size
}

/**
 * Ghost Climber (Phase 7.5): geführter Flow über die Pipeline-Schritte.
 * M1: Videos wählen + Posen extrahieren (ML Kit, offline, gecacht).
 * M2: Anker antippen → eigene Kotlin-Homographie → Vergleichs-Posen im Referenzraum.
 * Schwere Arbeit läuft auf Dispatchers.Default/IO, nie im UI-Thread.
 */
@HiltViewModel
class GhostClimberViewModel @Inject constructor(
    private val poseExtractor: VideoPoseExtractor,
    private val artifactStore: GhostArtifactStore,
    private val frameDecoder: GhostFrameDecoder,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GhostClimberUiState())
    val uiState: StateFlow<GhostClimberUiState> = _uiState.asStateFlow()

    /** Neues Video gewählt: Slot zurücksetzen — Spur/Anker gehören zur alten URI. */
    fun onVideoSelected(role: GhostRole, uri: String) {
        updateSlot(role) { GhostVideoSlot(uri = uri) }
        _uiState.update { it.copy(ghostTrack = null, error = null) }
    }

    /** Posen beider Videos extrahieren (sequenziell — EIN ML-Kit-Detector), dann zu den Ankern. */
    fun analyze() {
        val state = _uiState.value
        if (!state.canAnalyze) return
        viewModelScope.launch {
            _uiState.update { it.copy(analyzing = true, error = null) }
            try {
                for (role in GhostRole.entries) {
                    val uri = _uiState.value.slot(role).uri ?: continue
                    if (_uiState.value.slot(role).track != null) continue
                    val track = artifactStore.loadPoseTrack(uri)
                        ?: poseExtractor.extract(uri) { done, total ->
                            updateSlot(role) { it.copy(progressDone = done, progressTotal = total) }
                        }.also { artifactStore.savePoseTrack(it) }
                    updateSlot(role) { it.copy(track = track) }
                }
                _uiState.update { it.copy(analyzing = false, step = GhostStep.ANCHORS) }
                // Standbilder für die Anker-Erfassung vorladen.
                GhostRole.entries.forEach { loadAnchorFrame(it, 0L) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(analyzing = false, error = e.message ?: "Analyse fehlgeschlagen")
                }
            }
        }
    }

    /** Standbild-Zeitpunkt fürs Anker-Tippen gewählt (Slider losgelassen). */
    fun loadAnchorFrame(role: GhostRole, timeMs: Long) {
        val uri = _uiState.value.slot(role).uri ?: return
        updateSlot(role) { it.copy(anchorFrameTimeMs = timeMs) }
        viewModelScope.launch {
            val bitmap = frameDecoder.frameAt(uri, timeMs)
            // Nur übernehmen, wenn der Zeitpunkt noch aktuell ist (Slider schneller als Decode).
            if (_uiState.value.slot(role).anchorFrameTimeMs == timeMs) {
                updateSlot(role) { it.copy(anchorFrame = bitmap?.asImageBitmap()) }
            }
        }
    }

    fun addAnchor(role: GhostRole, point: GhostPoint) {
        updateSlot(role) { it.copy(anchors = it.anchors + point) }
    }

    fun removeLastAnchor(role: GhostRole) {
        updateSlot(role) { it.copy(anchors = it.anchors.dropLast(1)) }
    }

    /**
     * M2-Kern: Homographie Vergleich→Referenz aus den Index-gepaarten Ankern schätzen
     * (normalisierte DLT + RANSAC) und die Vergleichs-Posen in den Referenzraum
     * transformieren (P0). Die Referenz selbst bleibt unverändert (Identität).
     * Danach weiter zum Pfad-Schritt mit der Referenz-Trajektorie als Vorschlag (P3).
     */
    fun computeAlignment() {
        val state = _uiState.value
        if (!state.anchorsComplete) return
        val refTrack = state.reference.track ?: return
        val cmpTrack = state.comparison.track ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                val (ghost, trajectory, suggestion) = withContext(Dispatchers.Default) {
                    val homography = Homography.estimate(
                        src = state.comparison.anchors.map { it.toVec2() },
                        dst = state.reference.anchors.map { it.toVec2() },
                        ransacIterations = GhostTuning.RANSAC_ITERATIONS,
                        inlierThresholdPx = GhostTuning.RANSAC_INLIER_THRESHOLD_PX,
                    )
                    val ghost = cmpTrack.transformedBy(homography, refTrack)
                    val trajectory = refTrack.smoothedHipTrajectory()
                        ?: throw IllegalStateException(
                            "Im Referenz-Video wurde keine Person sicher erkannt",
                        )
                    Triple(ghost, trajectory, suggestRoutePath(refTrack).orEmpty())
                }
                _uiState.update {
                    it.copy(
                        ghostTrack = ghost,
                        hipTrajectory = trajectory,
                        suggestedPath = suggestion,
                        routePath = suggestion,
                        step = GhostStep.PATH,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Homographie fehlgeschlagen — Anker prüfen")
                }
            }
        }
    }

    // --- Routenpfad-Korrektur (M3, P3) ---

    fun addPathPoint(point: GhostPoint) {
        _uiState.update { it.copy(routePath = it.routePath + point) }
    }

    fun removeLastPathPoint() {
        _uiState.update { it.copy(routePath = it.routePath.dropLast(1)) }
    }

    fun resetPathToSuggestion() {
        _uiState.update { it.copy(routePath = it.suggestedPath) }
    }

    /**
     * M3-Kern: Fortschrittssignale beider Versuche (Bogenlänge der Pfad-Projektion, P2)
     * berechnen und per DTW alignieren (P1) → Zeitmapping für den Geist im Player.
     * Beide Spuren liegen bereits im Referenzraum und teilen die Abtastrate (P8).
     */
    fun confirmPath() {
        val state = _uiState.value
        if (state.routePath.size < 2) return
        val refTrack = state.reference.track ?: return
        val ghostTrack = state.ghostTrack ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                val mapping = withContext(Dispatchers.Default) {
                    val path = RoutePolyline(state.routePath)
                    val refSignal = progressSignal(refTrack, path)
                        ?: throw IllegalStateException("Referenz: keine verwertbare Pose-Spur")
                    val cmpSignal = progressSignal(ghostTrack, path)
                        ?: throw IllegalStateException("Vergleich: keine verwertbare Pose-Spur")
                    val alignment = dtw(refSignal, cmpSignal, GhostTuning.DTW_BAND_FRACTION)
                    buildTimeMapping(alignment.path, refTrack.frames, ghostTrack.frames)
                }
                _uiState.update { it.copy(timeMapping = mapping, step = GhostStep.PREVIEW) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Synchronisation fehlgeschlagen")
                }
            }
        }
    }

    fun backToSelection() {
        _uiState.update { it.copy(step = GhostStep.SELECTION, error = null) }
    }

    fun backToAnchors() {
        _uiState.update { it.copy(step = GhostStep.ANCHORS, error = null) }
    }

    fun backToPath() {
        _uiState.update { it.copy(step = GhostStep.PATH, error = null) }
    }

    private fun updateSlot(role: GhostRole, transform: (GhostVideoSlot) -> GhostVideoSlot) {
        _uiState.update { state ->
            when (role) {
                GhostRole.REFERENCE -> state.copy(reference = transform(state.reference))
                GhostRole.COMPARISON -> state.copy(comparison = transform(state.comparison))
            }
        }
    }
}
