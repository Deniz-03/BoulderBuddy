package com.boulderbuddy.ghost.analysis

import com.boulderbuddy.ghost.GhostTuning
import com.boulderbuddy.ghost.model.GhostPoint
import kotlin.math.sqrt

// =============================================================================
// Ghost Climber — Sturz-/Abbrucherkennung (M5, P5)
// =============================================================================
//
// Ein Abbruch zeigt sich als Geschwindigkeits-Spike der Hüfte (deutlich über der
// eigenen Baseline) MIT anhaltender Abwärtsbewegung — die Mindestdauer entprellt
// Ausschütteln und Dynos (P5-Grenzfälle). Kamerabewegung wird NICHT herausgerechnet:
// feste Kamera (Stativ) ist Annahme des gesamten Features (A.7 Q6).
//
// Schwellen sind Kalibrier-Parameter (GhostTuning, A.6): ohne gelabelte echte
// Aufnahmen bleiben Fehlalarme möglich — die Werte sind plausible Startpunkte.
// Hinweis: auch der Absprung nach dem Top erfüllt das Muster; das resultierende
// Fade-out nach dem Top ist visuell unschädlich.

/**
 * Index des Sample-Frames, an dem ein Sturz/Abbruch beginnt — oder null, wenn die
 * (geglättete) Hüfttrajektorie keinen zeigt. Y wächst nach unten (Bildkoordinaten).
 */
fun detectAbortFrame(trajectory: List<GhostPoint>): Int? {
    if (trajectory.size < GhostTuning.FALL_MIN_DOWNWARD_FRAMES + 2) return null

    // Geschwindigkeit je Frame-Übergang.
    val dx = DoubleArray(trajectory.size - 1)
    val dy = DoubleArray(trajectory.size - 1)
    val speed = DoubleArray(trajectory.size - 1)
    for (i in 0 until trajectory.size - 1) {
        dx[i] = (trajectory[i + 1].x - trajectory[i].x).toDouble()
        dy[i] = (trajectory[i + 1].y - trajectory[i].y).toDouble()
        speed[i] = sqrt(dx[i] * dx[i] + dy[i] * dy[i])
    }

    // Baseline aus dem eigenen Versuch, ROBUST via Median + MAD: Mittelwert/σ würden
    // vom Sturz selbst aufgebläht (der Ausreißer soll die Schwelle nicht mitbestimmen).
    val median = median(speed)
    val mad = median(DoubleArray(speed.size) { kotlin.math.abs(speed[it] - median) })
    val robustSigma = 1.4826 * mad
    val threshold = maxOf(
        median + GhostTuning.FALL_SPEED_SIGMA_FACTOR * robustSigma,
        // MAD ≈ 0 bei sehr gleichförmiger Bewegung → Untergrenze relativ zum Median.
        median * GhostTuning.FALL_SPEED_MIN_MEDIAN_FACTOR,
    )

    for (i in speed.indices) {
        if (speed[i] <= threshold) continue
        // Spike gefunden — hält die Abwärtsbewegung an? (dy > 0 = fallend)
        val windowEnd = i + GhostTuning.FALL_MIN_DOWNWARD_FRAMES
        if (windowEnd > speed.size) return null
        val sustainedDown = (i until windowEnd).all { dy[it] > 0 }
        if (sustainedDown) return i
    }
    return null
}

private fun median(values: DoubleArray): Double {
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
}
