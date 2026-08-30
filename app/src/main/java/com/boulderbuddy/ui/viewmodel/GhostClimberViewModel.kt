package com.boulderbuddy.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.boulderbuddy.ui.Fehlerkanal
import com.boulderbuddy.ui.schreibe
import com.boulderbuddy.ui.Texte
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.boulderbuddy.data.camera.EigeneAufnahmen
import com.boulderbuddy.data.db.entity.GhostAnalysisEntity
import com.boulderbuddy.R
import com.boulderbuddy.data.repository.GhostAnalysisRepository
import com.boulderbuddy.ghost.Fortschritt
import com.boulderbuddy.ghost.GhostAnalyseRunner
import com.boulderbuddy.ghost.GhostAnalyseStand
import com.boulderbuddy.ghost.GhostArtifactStore
import com.boulderbuddy.ghost.GhostTuning
import com.boulderbuddy.ghost.service.GhostAnalyseService
import com.boulderbuddy.ghost.analysis.GhostTimeMapping
import com.boulderbuddy.ghost.analysis.detectAbortFrame
import com.boulderbuddy.ghost.analysis.qualityMetrics
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
import com.boulderbuddy.ghost.pose.transformedBy
import com.boulderbuddy.ghost.model.GhostPoint
import com.boulderbuddy.ghost.model.GhostPoseTrack
import com.boulderbuddy.ghost.video.GhostFrameDecoder
import com.boulderbuddy.ui.navigation.GhostClimber
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
) {
    /**
     * Der Slot, wie er während eines laufenden Hintergrund-Laufs aussieht. Gehört die
     * gemeldete URI nicht zu diesem Slot, ist er von einem Lauf, den dieser Bildschirm nicht
     * kennt — dann zählt der Lauf, nicht der leere Slot.
     */
    fun imLauf(uri: String, fortschritt: Fortschritt): GhostVideoSlot =
        if (this.uri == uri) {
            copy(progressDone = fortschritt.fertig, progressTotal = fortschritt.gesamt)
        } else {
            GhostVideoSlot(
                uri = uri,
                progressDone = fortschritt.fertig,
                progressTotal = fortschritt.gesamt,
            )
        }

    fun ohneFortschritt(): GhostVideoSlot = copy(progressDone = 0, progressTotal = 0)
}

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
    /** Normalisierte DTW-Restdistanz als Anteil der Routenlänge — dieselbe Zahl, die
     *  über Overlay vs. Side-by-Side entscheidet (P7). Nur fürs Debug-HUD (S0). */
    val dtwDistanceFraction: Double? = null,
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
    /** App-interne Videos als dritte Quelle neben Kamera und Galerie; neueste zuerst. */
    val eigeneAufnahmen: List<EigeneAufnahmeUi> = emptyList(),
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
 * Listeneintrag einer app-internen Aufnahme. Angezeigt wird der Zeitpunkt und nicht der
 * Dateiname: `BB_1756458012345.mp4` sagt niemandem etwas, und die übernommenen Dateien
 * heißen nach ihrem Hash — beide Namen sind zum Wiedererkennen wertlos.
 */
data class EigeneAufnahmeUi(
    val uri: String,
    val zeitText: String,
    val groesseText: String,
)

/**
 * Ghost Climber (Phase 7.5): geführter Flow über die Pipeline-Schritte.
 * M1: Videos wählen + Posen extrahieren (MediaPipe, offline, gecacht).
 * M2: Anker antippen → eigene Kotlin-Homographie → Vergleichs-Posen im Referenzraum.
 * Schwere Arbeit läuft auf Dispatchers.Default/IO, nie im UI-Thread.
 *
 * Die Pose-Extraktion läuft seit 7.5h **nicht mehr hier**, sondern im
 * [com.boulderbuddy.ghost.service.GhostAnalyseService] auf dem [GhostAnalyseRunner]. Dieses
 * ViewModel stirbt mit dem Bildschirm; die sieben Minuten Rechnung dürfen das nicht. Es
 * startet den Dienst, liest den Stand mit und übernimmt das Ergebnis — auch dann, wenn es
 * zwischendurch neu aufgebaut wurde und vom Start des Laufs gar nichts mitbekommen hat.
 */
@HiltViewModel
class GhostClimberViewModel @Inject constructor(
    application: Application,
    private val runner: GhostAnalyseRunner,
    private val artifactStore: GhostArtifactStore,
    private val frameDecoder: GhostFrameDecoder,
    private val analysisRepository: GhostAnalysisRepository,
    private val eigeneAufnahmen: EigeneAufnahmen,
    // Loest die Anzeigetexte aus strings.xml auf (siehe ui/Texte.kt).
    private val fehlerkanal: Fehlerkanal,
    private val texte: Texte,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    /**
     * Session, an die eine hier gespeicherte Analyse gehängt wird; `null` = eigenständig.
     *
     * Kommt aus dem Nav-Argument und nicht aus `sessionRepository.observeActive()`. Der
     * Unterschied ist der Fall, den die naheliegende Variante falsch macht: eine Analyse
     * entsteht oft Stunden nach dem Klettern zu Hause, und läuft dann zufällig noch eine
     * vergessene Session, hinge sie plötzlich in dieser. Wer aus einer Session heraus öffnet,
     * hat die Zuordnung dagegen ausgesprochen.
     */
    private val sessionId: Int? = savedStateHandle.toRoute<GhostClimber>().sessionId

    /**
     * Die wiederhergestellte Analyse, solange sie noch dieselbe ist — dann schreibt
     * [saveAnalysis] auf diese Zeile zurück, statt eine zweite anzulegen.
     *
     * Ohne das ist „Analyse öffnen → Pfad anpassen → speichern" ein Neuanlegen: neben der
     * alten Analyse stünde eine zweite, und die trüge die Session der aufrufenden Route —
     * also **keine**, wenn man aus dem Session-Block heraus geöffnet hat. Wer den Routenpfad
     * einer Session-Analyse korrigierte, fand das Ergebnis anschließend nicht mehr in seiner
     * Session.
     *
     * Die Kennung fällt weg, sobald ein Video gewechselt wird ([onVideoSelected]). Dann ist
     * es eine andere Analyse, und eine andere Analyse ist eine neue Zeile.
     */
    private var bearbeiteteAnalyse: Int? = null

    /**
     * Läuft gerade eine Wiederherstellung? Solange sie läuft, darf der Hintergrund-Lauf den
     * Bildschirm nicht anfassen.
     *
     * Gesetzt wird das **synchron**, noch vor der Coroutine — und genau darin liegt der Sinn.
     * Beim Aufbau starten zwei Dinge nebeneinander: das Herstellen der gespeicherten Analyse
     * (Datenbank + zwei Spur-Dateien) und der Sammler auf [GhostAnalyseRunner.stand], der
     * sofort den aktuellen Wert bekommt. Wer zuerst fertig ist, entscheidet sonst der Zufall
     * des Dateisystems. Ein Flag, das erst in der Coroutine gesetzt wird, käme regelmäßig zu
     * spät.
     */
    private var stellteAnalyseHer = false

    private val _uiState = MutableStateFlow(GhostClimberUiState())
    val uiState: StateFlow<GhostClimberUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
    // Aufnahmen mit Sekunde: zwei Versuche derselben Route entstehen oft direkt
    // hintereinander, und auf die Minute genau hießen dann beide gleich — ausgerechnet
    // die beiden, zwischen denen man in dieser Liste unterscheiden muss.
    private val aufnahmeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY)

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
                                    texte.hole(R.string.ghost_modus_side_by_side)
                                } else {
                                    texte.hole(R.string.ghost_modus_overlay)
                                },
                            )
                        },
                    )
                }
            }
        }
        ladeEigeneAufnahmen()
        // Direkt auf eine gespeicherte Analyse gesprungen (Tap im Session-Block): sie wird
        // gleich beim Aufbau hergestellt, damit man nicht auf einer leeren Auswahl landet.
        savedStateHandle.toRoute<GhostClimber>().analyseId?.let(::restoreAnalysis)
        // Den Stand der Hintergrund-Analyse mitlesen. Das ist zugleich die Wiederaufnahme:
        // ein frisch aufgebautes ViewModel bekommt beim ersten Sammeln den aktuellen Wert
        // und findet damit von selbst in einen Lauf zurück, den es nie gestartet hat.
        viewModelScope.launch {
            runner.stand.collect { uebernimm(it) }
        }
    }

    /** Neues Video gewählt: Slot zurücksetzen — Spur/Anker gehören zur alten URI. */
    fun onVideoSelected(role: GhostRole, uri: String) {
        updateSlot(role) { GhostVideoSlot(uri = uri) }
        _uiState.update { it.copy(ghostTrack = null, error = null) }
        // Anderes Video, andere Analyse — ein Speichern legt jetzt wieder neu an.
        bearbeiteteAnalyse = null
        // Kam das Video gerade aus der Kamera, liegt es jetzt im Aufnahme-Ordner und gehört
        // in die Liste — sonst fehlte ausgerechnet die frischeste Aufnahme darin.
        ladeEigeneAufnahmen()
    }

    /**
     * Liest den Aufnahme-Ordner. Läuft bei jedem Aufbau dieses ViewModels: genau dann kommt
     * jemand zurück, der seine Aufnahme sonst nicht mehr fände.
     */
    private fun ladeEigeneAufnahmen() {
        viewModelScope.launch {
            val aufnahmen = eigeneAufnahmen.videos().map {
                EigeneAufnahmeUi(
                    uri = it.uri,
                    zeitText = aufnahmeFormat.format(Date(it.aufgenommenAm)),
                    groesseText = String.format(
                        Locale.GERMANY,
                        "%.1f MB",
                        it.groesseBytes / (1024.0 * 1024.0),
                    ),
                )
            }
            _uiState.update { it.copy(eigeneAufnahmen = aufnahmen) }
        }
    }

    /**
     * Posen beider Videos extrahieren (sequenziell — EIN MediaPipe-Landmarker) und danach zu
     * den Ankern. Die Arbeit macht der Dienst; hier wird nur angestoßen.
     */
    fun analyze() {
        val state = _uiState.value
        if (!state.canAnalyze) return
        GhostAnalyseService.starte(
            context = getApplication(),
            refUri = state.reference.uri ?: return,
            cmpUri = state.comparison.uri ?: return,
        )
    }

    /**
     * Bricht die laufende Analyse ab — der Knopf dazu ist Pflicht, seit der Bildschirm sie
     * nicht mehr beendet. Der Dienst räumt den Lauf in seinem `onDestroy` mit ab.
     */
    fun brichAnalyseAb() {
        GhostAnalyseService.stoppe(getApplication())
    }

    /**
     * Spiegelt den Stand des Runners in den Bildschirm-Zustand.
     *
     * Die URIs kommen aus dem Runner zurück und nicht aus dem eigenen Zustand: nach einem
     * Neuaufbau des Bildschirms sind die Slots leer, und ohne sie stünde die Fortschrittszeile
     * unter zwei namenlosen Kacheln.
     */
    private fun uebernimm(stand: GhostAnalyseStand) {
        // Der Hintergrund-Lauf spricht nur in die Auswahl hinein. Weiter hinten im Flow
        // arbeitet der Nutzer an EINEM konkreten Videopaar, und ein Lauf, den er längst
        // hinter sich gelassen hat, darf ihm das nicht wegziehen.
        //
        // Im gewöhnlichen Ablauf kostet das nichts: angestoßen wird aus der Auswahl heraus,
        // und die Übernahme des Ergebnisses ist gerade der Schritt, der zu den Ankern führt.
        // Ohne den Wächter aber trifft ein Lauf auf eine wiederhergestellte Analyse — und
        // ersetzt beide Slots durch fremde Videos, während `bearbeiteteAnalyse` weiter auf
        // die alte Zeile zeigt. Wer danach speichert, schreibt Homographie und Routenpfad
        // des fremden Paars in die gespeicherte Analyse. Sie behält ihre Videos und wird
        // damit unbrauchbar, ohne dass irgendwo eine Meldung erscheint.
        if (stellteAnalyseHer || _uiState.value.step != GhostStep.SELECTION) return
        when (stand) {
            is GhostAnalyseStand.Untaetig -> _uiState.update {
                it.copy(
                    analyzing = false,
                    reference = it.reference.ohneFortschritt(),
                    comparison = it.comparison.ohneFortschritt(),
                )
            }

            is GhostAnalyseStand.Laeuft -> _uiState.update {
                it.copy(
                    analyzing = true,
                    error = null,
                    reference = it.reference.imLauf(stand.refUri, stand.ref),
                    comparison = it.comparison.imLauf(stand.cmpUri, stand.cmp),
                )
            }

            is GhostAnalyseStand.Fertig -> {
                _uiState.update {
                    it.copy(
                        analyzing = false,
                        error = null,
                        reference = GhostVideoSlot(uri = stand.refUri, track = stand.refTrack),
                        comparison = GhostVideoSlot(uri = stand.cmpUri, track = stand.cmpTrack),
                        step = GhostStep.ANCHORS,
                    )
                }
                // Frisch gerechnete Spuren sind eine andere Analyse — aus demselben Grund
                // wie in [onVideoSelected]. Wer aus einer wiederhergestellten Analyse zurück
                // in die Auswahl geht und dort einen Lauf abholt, legt beim Speichern neu an,
                // statt die alte Zeile zu überschreiben.
                bearbeiteteAnalyse = null
                runner.quittiere()
                // Standbilder für die Anker-Erfassung vorladen.
                GhostRole.entries.forEach { loadAnchorFrame(it, 0L) }
            }

            is GhostAnalyseStand.Fehler -> {
                _uiState.update { it.copy(analyzing = false, error = stand.meldung) }
                runner.quittiere()
            }
        }
    }

    /** Standbild-Zeitpunkt fürs Anker-Tippen gewählt (Slider losgelassen). */
    fun loadAnchorFrame(role: GhostRole, timeMs: Long) {
        val uri = _uiState.value.slot(role).uri ?: return
        updateSlot(role) { it.copy(anchorFrameTimeMs = timeMs) }
        viewModelScope.launch {
            // Der Decoder wirft, sobald die URI nicht mehr erreichbar ist — eine
            // Galerie-Freigabe überlebt einen Neustart nur, wenn sie dauerhaft genommen
            // wurde, und ein Video kann inzwischen gelöscht sein. Ungefangen reißt das die
            // App mit: eine Ausnahme aus einem `launch` läuft in den Standard-Handler, und
            // der beendet den Prozess. Ein fehlendes Standbild ist dagegen bloß ein leeres
            // Feld — der Slot beginnt ohnehin ohne Bild.
            val bitmap = try {
                frameDecoder.frameAt(uri, timeMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("GhostClimber", "Standbild nicht lesbar: $uri", e)
                null
            }
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
                            texte.hole(R.string.ghost_fehler_keine_person),
                        )
                    val ghost = cmpTrack.transformedBy(homography, refTrack)
                    // Die Homographie ist die einzige Pipeline-Stufe ohne eigene
                    // Messung — dabei ist sie aus WAND-Ankern geschätzt und wird auf
                    // den KÖRPER angewendet, verzieht die Pose also zwangsläufig. Hier
                    // steht schwarz auf weiß, was sie kostet: dieselben Kennzahlen vor
                    // und nach dem Raumwechsel, beide skalen-normiert und damit direkt
                    // vergleichbar.
                    logHomographyCost(cmpTrack, ghost)
                    AlignmentResult(
                        homography = homography,
                        ghost = ghost,
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
                    it.copy(error = e.message ?: texte.hole(R.string.ghost_fehler_homographie))
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
                    it.copy(error = e.message ?: texte.hole(R.string.ghost_fehler_synchronisation))
                }
            }
        }
    }

    /**
     * Formkennzahlen des Vergleichs-Versuchs vor und nach dem Wechsel in den
     * Referenzraum (Logcat-Tag "GhostPoseMetrics"). Steigt "Morph" hier deutlich, ist
     * die Unruhe des Geists eine Folge der Homographie und nicht der Pose-Erkennung —
     * das ließ sich bisher an keiner Stelle unterscheiden.
     */
    private fun logHomographyCost(before: GhostPoseTrack, after: GhostPoseTrack) {
        fun line(track: GhostPoseTrack): String {
            val m = track.qualityMetrics()
            return String.format(
                Locale.GERMANY,
                "Puls %.1f× · Unruhe %.2f%% · Morph %.2f%% · Verkürzung %.1f%% · Kollaps %.1f%%",
                m.centroidPulse,
                m.centroidWobble * 100,
                m.boneLengthWobble * 100,
                m.boneLengthCv * 100,
                m.scaleCv * 100,
            )
        }
        Log.d("GhostPoseMetrics", "Geist vor Homographie:  ${line(before)}")
        Log.d("GhostPoseMetrics", "Geist nach Homographie: ${line(after)}")
    }

    /** Ergebnis der Synchronisation (M3–M5): Zeitmapping, Modus-Vorschlag, Abbrüche. */
    private data class SyncResult(
        val mapping: GhostTimeMapping,
        val suggestedMode: GhostViewMode,
        val suggestionReason: String,
        val refAbortTimeMs: Long?,
        val cmpAbortTimeMs: Long?,
        val dtwDistanceFraction: Double,
    )

    private fun GhostClimberUiState.applySync(sync: SyncResult): GhostClimberUiState = copy(
        timeMapping = sync.mapping,
        dtwDistanceFraction = sync.dtwDistanceFraction,
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
            ?: throw IllegalStateException(texte.hole(R.string.ghost_fehler_spur_referenz))
        val cmpSignal = progressSignal(ghostTrack, path)
            ?: throw IllegalStateException(texte.hole(R.string.ghost_fehler_spur_vergleich))
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
            dtwDistanceFraction = if (path.totalLength > 0.0) {
                alignment.normalizedDistance / path.totalLength
            } else {
                0.0
            },
        )
    }

    // --- Persistenz gespeicherter Analysen (M5, A.2) ---

    /**
     * Sichert die aktuelle Auswertung — und zwar auf zwei Arten, je nachdem, was vorliegt.
     *
     * **Korrektur**, wenn eine wiederhergestellte Analyse nachbearbeitet wurde
     * ([bearbeiteteAnalyse]): dann wandern nur Homographie, Routenpfad und Modus zurück in
     * dieselbe Zeile. Session und Erstellzeitpunkt bleiben — wer den Routenpfad nachbessert,
     * verschiebt seine Analyse nicht in eine andere Session und legt keine zweite an.
     *
     * **Neuanlage** sonst, mit der Session der aufrufenden Route (`null` beim Einstieg aus
     * den Einstellungen). Die neue Zeile gilt danach als die bearbeitete: ein zweites
     * Nachbessern derselben Analyse korrigiert sie wieder, statt eine dritte anzulegen.
     */
    fun saveAnalysis() {
        val state = _uiState.value
        val refUri = state.reference.uri ?: return
        val cmpUri = state.comparison.uri ?: return
        val homography = state.homographyCmp ?: return
        if (state.analysisSaved || state.routePath.size < 2) return
        // Erst die alte Meldung weg. Sonst stünde neben einem frischen Versuch noch der
        // Fehler des vorigen — und der Bildschirm könnte nicht unterscheiden, ob DIESER
        // Versuch gescheitert ist (siehe den Verlassen-nach-Speichern-Weg in
        // GhostClimberScreen, der genau das auswerten muss).
        _uiState.update { it.copy(error = null) }
        viewModelScope.launch {
            try {
                val bearbeitet = bearbeiteteAnalyse
                if (bearbeitet != null) {
                    // Korrektur einer vorhandenen Analyse: nur die Auswertung wandert zurück.
                    // Session und Erstellzeitpunkt bleiben, wo sie waren — eine Nachbesserung
                    // verschiebt die Analyse nicht und macht sie nicht neu.
                    analysisRepository.update(
                        id = bearbeitet,
                        homographieJson = json.encodeToString(homography.values()),
                        routenpfadJson = json.encodeToString(state.routePath),
                        modus = state.suggestedMode.name,
                    )
                    _uiState.update { it.copy(analysisSaved = true) }
                    return@launch
                }
                analysisRepository.create(
                    GhostAnalysisEntity(
                        sessionId = sessionId,
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
                    .also { neueId -> bearbeiteteAnalyse = neueId }
                _uiState.update { it.copy(analysisSaved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: texte.hole(R.string.ghost_fehler_speichern)) }
            }
        }
    }

    fun deleteAnalysis(id: Int) {
        viewModelScope.launch {
            fehlerkanal.schreibe(
                R.string.ghost_fehler_loeschen,
                protokollMarke = "Ghost-Analyse loeschen",
            ) {
                analysisRepository.delete(id)
            }
        }
    }

    /**
     * Stellt eine gespeicherte Analyse wieder her: Pose-Spuren aus den Artefakt-Dateien,
     * Homographie + Routenpfad aus der DB; Synchronisation wird neu gerechnet (schnell —
     * die teure Pose-Extraktion entfällt) und direkt die Vorschau geöffnet.
     */
    fun restoreAnalysis(id: Int) {
        stellteAnalyseHer = true
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                val entity = analysisRepository.getById(id)
                    ?: throw IllegalStateException(texte.hole(R.string.ghost_fehler_nicht_gefunden))
                val refTrack = artifactStore.loadPoseTrackFromPath(entity.refKeypointsPath)
                    ?: throw IllegalStateException(texte.hole(R.string.ghost_fehler_daten_geloescht))
                val cmpTrack = artifactStore.loadPoseTrackFromPath(entity.cmpKeypointsPath)
                    ?: throw IllegalStateException(texte.hole(R.string.ghost_fehler_daten_geloescht))
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
                // Ab hier ist jedes Speichern eine Korrektur dieser Zeile, kein Neuanlegen.
                bearbeiteteAnalyse = id
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: texte.hole(R.string.ghost_fehler_laden))
                }
            } finally {
                // Auch nach einem Fehlschlag: sonst bliebe der Bildschirm für den Rest seines
                // Lebens taub für den Hintergrund-Lauf.
                stellteAnalyseHer = false
            }
        }
    }

    /** Nutzer überstimmt den Modus-Vorschlag (P7). */
    fun setViewMode(mode: GhostViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    fun backToSelection() {
        _uiState.update { it.copy(step = GhostStep.SELECTION, error = null) }
        ladeEigeneAufnahmen()
        // Ein Stand, den der Wächter oben abgewiesen hat, wartet noch im Runner — ein
        // StateFlow meldet sich von selbst erst wieder, wenn sich der Wert ändert. Hier ist
        // der Bildschirm zurück in der Auswahl und damit aufnahmebereit; ohne diese Zeile
        // bliebe ein fertiger Lauf bis zum nächsten Aufbau des Bildschirms unsichtbar.
        uebernimm(runner.stand.value)
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
