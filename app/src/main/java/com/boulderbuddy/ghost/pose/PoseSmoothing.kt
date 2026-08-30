package com.boulderbuddy.ghost.pose

import com.boulderbuddy.ghost.GhostTuning
import com.boulderbuddy.ghost.model.GhostLandmark
import com.boulderbuddy.ghost.model.GhostPoseFrame
import kotlin.math.PI
import kotlin.math.abs

// =============================================================================
// Stufe 1 — One-Euro-Filter pro Landmark
// =============================================================================
//
// Der One-Euro-Filter (Casiez et al., CHI 2012) ist der Standard für Echtzeit-
// Pose-Glättung: ein Tiefpass, dessen Cutoff mit der Signalgeschwindigkeit wächst —
// starkes Glätten bei ruhigem Griff (das Zittern verschwindet), wenig Lag bei
// schnellen Zügen (das Skelett bleibt dran). Angewandt im Analyse-Frame-Raum,
// VOR dem Cachen in den GhostPoseTrack (Diagnose Root Cause A).

/** One-Euro-Filter für EINEN Skalar (eine Koordinaten-Achse eines Landmarks). */
internal class OneEuroFilter(
    private val minCutoffHz: Double = GhostTuning.ONE_EURO_MIN_CUTOFF_HZ,
    private val beta: Double = GhostTuning.ONE_EURO_BETA,
    private val derivCutoffHz: Double = GhostTuning.ONE_EURO_DERIV_CUTOFF_HZ,
) {
    private var prevValue = 0.0
    private var prevDeriv = 0.0
    private var prevTimeS = 0.0
    private var initialized = false

    fun filter(value: Float, timeS: Double): Float {
        if (!initialized) {
            initialized = true
            prevValue = value.toDouble()
            prevTimeS = timeS
            return value
        }
        val dt = (timeS - prevTimeS).coerceAtLeast(1e-6)
        val rawDeriv = (value - prevValue) / dt
        val deriv = lowpass(rawDeriv, prevDeriv, alpha(derivCutoffHz, dt))
        val cutoff = minCutoffHz + beta * abs(deriv)
        val filtered = lowpass(value.toDouble(), prevValue, alpha(cutoff, dt))
        prevValue = filtered
        prevDeriv = deriv
        prevTimeS = timeS
        return filtered.toFloat()
    }

    private fun alpha(cutoffHz: Double, dt: Double): Double {
        val tau = 1.0 / (2.0 * PI * cutoffHz)
        return 1.0 / (1.0 + tau / dt)
    }

    private fun lowpass(value: Double, prev: Double, alpha: Double): Double =
        alpha * value + (1.0 - alpha) * prev
}

/**
 * Glättet die Landmark-Positionen einer Pose-Spur zeitlich (ein Filterpaar je
 * Landmark-Typ, x/y getrennt). Confidence/Presence bleiben unverändert.
 *
 * Fehlt ein Landmark länger als [GhostTuning.FILTER_RESET_GAP_FRAMES] Frames,
 * startet sein Filter neu, statt vom veralteten Zustand "nachgezogen" zu werden
 * (sichtbares Hinterherschleifen nach Verdeckungen).
 *
 * [minCutoffHz] und [beta] stehen als Parameter da, obwohl in der App immer die Werte aus
 * [GhostTuning] gelten: eine Filtereinstellung ist nur so gut wie die Kennzahl, an der man
 * sie misst, und dafür muss dieselbe Spur mit verschiedenen Werten durchlaufen können.
 * Am Gerät wäre jeder Versuch ein Sieben-Minuten-Lauf; auf der Roh-Spur sind es
 * Millisekunden (siehe `OneEuroSweepTest`).
 */
fun smoothPoseFrames(
    frames: List<GhostPoseFrame>,
    minCutoffHz: Double = GhostTuning.ONE_EURO_MIN_CUTOFF_HZ,
    beta: Double = GhostTuning.ONE_EURO_BETA,
): List<GhostPoseFrame> {
    class LandmarkState {
        val x = OneEuroFilter(minCutoffHz = minCutoffHz, beta = beta)
        val y = OneEuroFilter(minCutoffHz = minCutoffHz, beta = beta)
        var lastFrameIndex = -1
    }

    val states = HashMap<Int, LandmarkState>()
    return frames.mapIndexed { index, frame ->
        val timeS = frame.timeMs / 1000.0
        frame.copy(
            landmarks = frame.landmarks.map { lm ->
                val state = states[lm.type]
                    ?.takeIf { index - it.lastFrameIndex <= GhostTuning.FILTER_RESET_GAP_FRAMES }
                    ?: LandmarkState().also { states[lm.type] = it }
                state.lastFrameIndex = index
                lm.copy(
                    x = state.x.filter(lm.x, timeS),
                    y = state.y.filter(lm.y, timeS),
                )
            },
        )
    }
}

/**
 * Füllt kurze zeitliche Lücken EINZELNER Landmarks durch lineare Interpolation
 * zwischen den umgebenden sicheren Vorkommen (offline, ganze Spur bekannt). Behebt
 * (a) in Einzelframes fehlende Gliedmaßen und (b) ganz fehlende Skelette über kurze
 * Strecken — solange die Lücke ≤ [GhostTuning.MAX_GAP_FILL_FRAMES] Sample-Frames ist
 * und von sicheren Vorkommen (≥ [GhostTuning.VISIBILITY_SHOW_THRESHOLD]) eingerahmt
 * wird. Gefüllte Landmarks bekommen Confidence [GhostTuning.MIN_LANDMARK_CONFIDENCE],
 * damit sie gezeichnet werden; sie ersetzen ein evtl. vorhandenes unsicheres Vorkommen.
 *
 * Läuft VOR [smoothPoseFrames]: die gefüllten Frames schließen die Lücke, sodass der
 * One-Euro-Filter durchgehend glättet statt nach der Lücke neu zu starten.
 */
fun fillLandmarkGaps(frames: List<GhostPoseFrame>): List<GhostPoseFrame> {
    if (frames.size < 3) return frames

    // Pro Typ: Frame-Indizes mit sicherem Vorkommen (Anker).
    val anchorsByType = HashMap<Int, MutableList<Int>>()
    frames.forEachIndexed { i, frame ->
        frame.landmarks.forEach { lm ->
            if (lm.confidence >= GhostTuning.VISIBILITY_SHOW_THRESHOLD) {
                anchorsByType.getOrPut(lm.type) { ArrayList() }.add(i)
            }
        }
    }

    // Zu füllende Landmarks je Frame-Index sammeln.
    val fills = HashMap<Int, MutableList<GhostLandmark>>()
    anchorsByType.forEach { (type, indices) ->
        for (k in 0 until indices.size - 1) {
            val prevIdx = indices[k]
            val nextIdx = indices[k + 1]
            val gap = nextIdx - prevIdx - 1
            if (gap <= 0 || gap > GhostTuning.MAX_GAP_FILL_FRAMES) continue
            val prevLm = frames[prevIdx].landmarks.first { it.type == type }
            val nextLm = frames[nextIdx].landmarks.first { it.type == type }
            val prevTime = frames[prevIdx].timeMs
            val span = (frames[nextIdx].timeMs - prevTime).toFloat()
            for (i in (prevIdx + 1) until nextIdx) {
                // Nicht füllen, wenn der Typ hier ohnehin schon sicher ist.
                if (frames[i].landmarks.any {
                        it.type == type && it.confidence >= GhostTuning.VISIBILITY_SHOW_THRESHOLD
                    }
                ) {
                    continue
                }
                val t = if (span <= 0f) 0f else ((frames[i].timeMs - prevTime) / span).coerceIn(0f, 1f)
                fills.getOrPut(i) { ArrayList() }.add(
                    GhostLandmark(
                        type = type,
                        x = prevLm.x + (nextLm.x - prevLm.x) * t,
                        y = prevLm.y + (nextLm.y - prevLm.y) * t,
                        confidence = GhostTuning.MIN_LANDMARK_CONFIDENCE,
                    ),
                )
            }
        }
    }
    if (fills.isEmpty()) return frames

    return frames.mapIndexed { i, frame ->
        val add = fills[i] ?: return@mapIndexed frame
        val addedTypes = add.mapTo(HashSet()) { it.type }
        // Vorhandenes (unsicheres) Vorkommen desselben Typs durch die Füllung ersetzen.
        frame.copy(landmarks = frame.landmarks.filter { it.type !in addedTypes } + add)
    }
}

/**
 * Hysterese über visibility (Stufe 1, Root Cause B): die harte 0.5-Schwelle beim
 * Zeichnen ließ Landmarks am Schwellwert ein-/ausflackern. Stattdessen:
 *
 * - **Einblenden** sofort, sobald visibility ≥ [GhostTuning.MIN_LANDMARK_CONFIDENCE].
 * - **Sichtbar bleiben**, solange visibility ≥ [GhostTuning.VISIBILITY_HIDE_THRESHOLD];
 *   die gemeldete Confidence wird dabei auf die Zeichen-Schwelle angehoben, damit
 *   nachgelagerte Konsumenten (Overlay, Hüftsignal) das gehaltene Landmark behalten.
 * - **Ausblenden** erst nach [GhostTuning.VISIBILITY_HIDE_FRAMES] Frames in Folge
 *   unter der Ausblendschwelle (echte Verdeckung statt Rausch-Dip).
 *
 * Läuft NACH [smoothPoseFrames] auf der geglätteten Spur; die Roh-Spur bleibt
 * unangetastet (Debug-Vergleich).
 *
 * **S2c (7.5e):** die Entprellung gilt nur noch für Landmarks, die im Frame VORHANDEN,
 * aber schwach sind — deren Position ist echt, nur die Confidence wackelt. Das frühere
 * Überbrücken GANZ fehlender Landmarks mit ihrer letzten Position ist entfernt: es hielt
 * ein Glied bis zu 250 ms an einer veralteten Stelle fest, während der Körper weiterlief
 * (sichtbar als „das Skelett braucht zu lange, um zu folgen"). Kurze echte Lücken füllt
 * ohnehin [fillLandmarkGaps] sauber per Interpolation zwischen zwei Ankern — eine
 * Position zu halten war der dritte, schlechteste von drei Überbrückungs-Mechanismen.
 */
fun applyVisibilityHysteresis(frames: List<GhostPoseFrame>): List<GhostPoseFrame> {
    class LandmarkState {
        var visible = false
        var framesBelowHide = 0
    }

    val states = HashMap<Int, LandmarkState>()
    return frames.map { frame ->
        val kept = ArrayList<GhostLandmark>(frame.landmarks.size)
        frame.landmarks.forEach { lm ->
            val state = states.getOrPut(lm.type) { LandmarkState() }
            val emitted: GhostLandmark? = when {
                !state.visible ->
                    if (lm.confidence >= GhostTuning.VISIBILITY_SHOW_THRESHOLD) {
                        state.visible = true
                        state.framesBelowHide = 0
                        // Auf die Zeichen-Confidence anheben, damit SkeletonDraw das
                        // (evtl. schwach erkannte) Landmark auch zeichnet.
                        lm.copy(confidence = maxOf(lm.confidence, GhostTuning.MIN_LANDMARK_CONFIDENCE))
                    } else {
                        null
                    }
                lm.confidence >= GhostTuning.VISIBILITY_HIDE_THRESHOLD -> {
                    state.framesBelowHide = 0
                    lm.copy(confidence = maxOf(lm.confidence, GhostTuning.MIN_LANDMARK_CONFIDENCE))
                }
                ++state.framesBelowHide >= GhostTuning.VISIBILITY_HIDE_FRAMES -> {
                    state.visible = false
                    null
                }
                // Noch im Entprell-Fenster: halten, Position kommt vom Filter.
                else -> lm.copy(confidence = GhostTuning.MIN_LANDMARK_CONFIDENCE)
            }
            if (emitted != null) kept += emitted
        }
        frame.copy(landmarks = kept)
    }
}
