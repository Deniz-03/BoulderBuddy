package com.boulderbuddy.ui.viewmodel

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulderbuddy.data.db.entity.GhostAnalysisEntity
import com.boulderbuddy.data.repository.GhostAnalysisRepository
import com.boulderbuddy.ghost.GhostArtifactStore
import com.boulderbuddy.ghost.GhostTuning
import com.boulderbuddy.ghost.analysis.GhostTimeMapping
import com.boulderbuddy.ghost.analysis.detectAbortFrame
import com.boulderbuddy.ghost.analysis.RoutePolyline
import com.boulderbuddy.ghost.analysis.buildTimeMapping
import com.boulderbuddy.ghost.analysis.dtw
import com.boulderbuddy.ghost.analysis.progressSignal
import com.boulderbuddy.ghost.analysis.smoothedHipTrajectory
import com.boulderbuddy.ghost.analysis.suggestRoutePath
import com.boulderbuddy.ghost.analysis.suggestViewMode
import com.boulderbuddy.ghost.model.GhostViewMode
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
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    // --- Darstellungsmodus (M4, P7) ---
    /** Von der Ähnlichkeitsmetrik vorgeschlagener Modus (Vorbelegung). */
    val suggestedMode: GhostViewMode = GhostViewMode.OVERLAY,
    /** Kurzbegründung des Vorschlags für die UI. */
    val suggestionReason: String = "",
    /** Aktiver Modus — vorbelegt mit [suggestedMode], vom Nutzer überstimmbar. */
    val viewMode: GhostViewMode = GhostViewMode.OVERLAY,
    // --- Abbruch/Sturz + Persistenz (M5) ---
    /** Homographie Vergleich→Referenz (für die Persistenz aufbewahrt). */
    val homographyCmp: Homography? = null,
    /** Erkannter Abbruch-/Sturzzeitpunkt (P5) je Versuch auf der eigenen Zeitachse;
     *  null = keiner erkannt. Steuert das Fade-out (P4c). */
    val refAbortTimeMs: Long? = null,
    val cmpAbortTimeMs: Long? = null,
    /** Gespeicherte Analysen (DB), neueste zuerst. */
    val savedAnalyses: List<SavedAnalysisUi> = emptyList(),
    /** true, nachdem die aktuelle Analyse gespeichert wurde (deaktiviert den Button). */
    val analysisSaved: Boolean = false,
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

/** Listeneintrag einer gespeicherten Analyse (Screens bleiben Entity-frei). */
data class SavedAnalysisUi(
    val id: Int,
    val createdAtText: String,
    val modeLabel: String,
)

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
    private val analysisRepository: GhostAnalysisRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GhostClimberUiState())
    val uiState: StateFlow<GhostClimberUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)

    init {
        // Gespeicherte Analysen live in den State spiegeln (Liste im Auswahl-Schritt).
        viewModelScope.launch {
            analysisRepository.observeAll().collect { analyses ->
                _uiState.update { state ->
                    state.copy(
                        savedAnalyses = analyses.map {
                            SavedAnalysisUi(
                                id = it.id,
                                createdAtText = dateFormat.format(Date(it.createdAt)),
                                modeLabel = if (it.suggestedMode == GhostViewMode.SIDE_BY_SIDE.name) {
                                    "Side-by-Side"
                                } else {
                                    "Overlay"
                                },
                            )
                        },
                    )
                }
            }
        }
    }

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
                data class AlignmentResult(
                    val homography: Homography,
                    val ghost: GhostPoseTrack,
                    val trajectory: List<GhostPoint>,
                    val suggestion: List<GhostPoint>,
                )

                val result = withContext(Dispatchers.Default) {
                    val homography = Homography.estimate(
                        src = state.comparison.anchors.map { it.toVec2() },
                        dst = state.reference.anchors.map { it.toVec2() },
                        ransacIterations = GhostTuning.RANSAC_ITERATIONS,
                        inlierThresholdPx = GhostTuning.RANSAC_INLIER_THRESHOLD_PX,
                    )
                    val trajectory = refTrack.smoothedHipTrajectory()
                        ?: throw IllegalStateException(
                            "Im Referenz-Video wurde keine Person sicher erkannt",
                        )
                    AlignmentResult(
                        homography = homography,
                        ghost = cmpTrack.transformedBy(homography, refTrack),
                        trajectory = trajectory,
                        suggestion = suggestRoutePath(refTrack).orEmpty(),
                    )
                }
                _uiState.update {
                    it.copy(
                        homographyCmp = result.homography,
                        ghostTrack = result.ghost,
                        hipTrajectory = result.trajectory,
                        suggestedPath = result.suggestion,
                        routePath = result.suggestion,
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
                val sync = withContext(Dispatchers.Default) {
                    computeSync(refTrack, ghostTrack, state.routePath)
                }
                _uiState.update { it.applySync(sync) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Synchronisation fehlgeschlagen")
                }
            }
        }
    }

    /** Ergebnis der Synchronisation (M3–M5): Zeitmapping, Modus-Vorschlag, Abbrüche. */
    private data class SyncResult(
        val mapping: GhostTimeMapping,
        val suggestedMode: GhostViewMode,
        val suggestionReason: String,
        val refAbortTimeMs: Long?,
        val cmpAbortTimeMs: Long?,
    )

    private fun GhostClimberUiState.applySync(sync: SyncResult): GhostClimberUiState = copy(
        timeMapping = sync.mapping,
        suggestedMode = sync.suggestedMode,
        suggestionReason = sync.suggestionReason,
        viewMode = sync.suggestedMode,
        refAbortTimeMs = sync.refAbortTimeMs,
        cmpAbortTimeMs = sync.cmpAbortTimeMs,
        analysisSaved = false,
        step = GhostStep.PREVIEW,
    )

    /**
     * Kern der Analyse (auf Dispatchers.Default aufrufen): Fortschrittssignale (P2) →
     * DTW (P1) → Zeitmapping; Modus-Vorschlag (P7); Sturz-/Abbrucherkennung (P5) auf
     * beiden Trajektorien — die Zeitachse des Geists ist die des Vergleichs-Videos.
     */
    private fun computeSync(
        refTrack: GhostPoseTrack,
        ghostTrack: GhostPoseTrack,
        routePath: List<GhostPoint>,
    ): SyncResult {
        val path = RoutePolyline(routePath)
        val refSignal = progressSignal(refTrack, path)
            ?: throw IllegalStateException("Referenz: keine verwertbare Pose-Spur")
        val cmpSignal = progressSignal(ghostTrack, path)
            ?: throw IllegalStateException("Vergleich: keine verwertbare Pose-Spur")
        val alignment = dtw(refSignal, cmpSignal, GhostTuning.DTW_BAND_FRACTION)
        val refTrajectory = refTrack.smoothedHipTrajectory().orEmpty()
        val cmpTrajectory = ghostTrack.smoothedHipTrajectory().orEmpty()
        val suggestion = suggestViewMode(
            refTrajectory = refTrajectory,
            cmpTrajectory = cmpTrajectory,
            path = path,
            dtwNormalizedDistance = alignment.normalizedDistance,
        )
        return SyncResult(
            mapping = buildTimeMapping(alignment.path, refTrack.frames, ghostTrack.frames),
            suggestedMode = suggestion.mode,
            suggestionReason = suggestion.reason,
            refAbortTimeMs = detectAbortFrame(refTrajectory)
                ?.let { refTrack.frames[it].timeMs },
            cmpAbortTimeMs = detectAbortFrame(cmpTrajectory)
                ?.let { ghostTrack.frames[it].timeMs },
        )
    }

    // --- Persistenz gespeicherter Analysen (M5, A.2) ---

    /** Speichert die aktuelle Analyse: Artefakt-Pfade + kompakte JSON-Ergebnisse in der DB. */
    fun saveAnalysis() {
        val state = _uiState.value
        val refUri = state.reference.uri ?: return
        val cmpUri = state.comparison.uri ?: return
        val homography = state.homographyCmp ?: return
        if (state.analysisSaved || state.routePath.size < 2) return
        viewModelScope.launch {
            try {
                analysisRepository.create(
                    GhostAnalysisEntity(
                        refMediaUri = refUri,
                        cmpMediaUri = cmpUri,
                        refKeypointsPath = artifactStore.poseTrackPath(refUri),
                        cmpKeypointsPath = artifactStore.poseTrackPath(cmpUri),
                        homographyCmpJson = json.encodeToString(homography.values()),
                        routePathJson = json.encodeToString(state.routePath),
                        suggestedMode = state.suggestedMode.name,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                _uiState.update { it.copy(analysisSaved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Speichern fehlgeschlagen") }
            }
        }
    }

    fun deleteAnalysis(id: Int) {
        viewModelScope.launch { analysisRepository.delete(id) }
    }

    /**
     * Stellt eine gespeicherte Analyse wieder her: Pose-Spuren aus den Artefakt-Dateien,
     * Homographie + Routenpfad aus der DB; Synchronisation wird neu gerechnet (schnell —
     * die teure Pose-Extraktion entfällt) und direkt die Vorschau geöffnet.
     */
    fun restoreAnalysis(id: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                val entity = analysisRepository.getById(id)
                    ?: throw IllegalStateException("Analyse nicht gefunden")
                val refTrack = artifactStore.loadPoseTrackFromPath(entity.refKeypointsPath)
                    ?: throw IllegalStateException("Analyse-Daten wurden gelöscht — bitte neu analysieren")
                val cmpTrack = artifactStore.loadPoseTrackFromPath(entity.cmpKeypointsPath)
                    ?: throw IllegalStateException("Analyse-Daten wurden gelöscht — bitte neu analysieren")
                val homography = Homography(json.decodeFromString<DoubleArray>(entity.homographyCmpJson))
                val routePath = json.decodeFromString<List<GhostPoint>>(entity.routePathJson)

                data class Restored(
                    val ghost: GhostPoseTrack,
                    val trajectory: List<GhostPoint>,
                    val sync: SyncResult,
                )

                val restored = withContext(Dispatchers.Default) {
                    val ghost = cmpTrack.transformedBy(homography, refTrack)
                    Restored(
                        ghost = ghost,
                        trajectory = refTrack.smoothedHipTrajectory().orEmpty(),
                        sync = computeSync(refTrack, ghost, routePath),
                    )
                }
                _uiState.update {
                    it.copy(
                        reference = GhostVideoSlot(uri = entity.refMediaUri, track = refTrack),
                        comparison = GhostVideoSlot(uri = entity.cmpMediaUri, track = cmpTrack),
                        homographyCmp = homography,
                        ghostTrack = restored.ghost,
                        hipTrajectory = restored.trajectory,
                        suggestedPath = routePath,
                        routePath = routePath,
                        error = null,
                    ).applySync(restored.sync)
                        // Wiederhergestellt = bereits gespeichert.
                        .copy(analysisSaved = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Analyse konnte nicht geladen werden")
                }
            }
        }
    }

    /** Nutzer überstimmt den Modus-Vorschlag (P7). */
    fun setViewMode(mode: GhostViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
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
