package com.boulderbuddy.ghost.pose

import com.boulderbuddy.ghost.GhostTuning
import com.boulderbuddy.ghost.model.GhostPoseFrame
import kotlin.math.PI
import kotlin.math.abs

// =============================================================================
// Stufe 1 — One-Euro-Filter pro Landmark (FABLE_GHOSTCLIMBER_STABILISIERUNG.md)
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
 */
fun smoothPoseFrames(frames: List<GhostPoseFrame>): List<GhostPoseFrame> {
    class LandmarkState {
        val x = OneEuroFilter()
        val y = OneEuroFilter()
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
