package com.boulderbuddy.ghost.analysis

import com.boulderbuddy.ghost.GhostTuning
import com.boulderbuddy.ghost.model.GhostLandmark
import com.boulderbuddy.ghost.model.GhostLandmarkTypes
import com.boulderbuddy.ghost.model.GhostPoseFrame
import com.boulderbuddy.ghost.model.GhostPoseTrack
import com.boulderbuddy.ghost.model.RIGID_BONES
import com.boulderbuddy.ghost.model.bodyScale
import com.boulderbuddy.ghost.model.coreCentroid
import com.boulderbuddy.ghost.model.distance
import com.boulderbuddy.ghost.model.personScales
import kotlin.math.hypot
import kotlin.math.sqrt

// =============================================================================
// Stufe 0 — Qualitäts-Kennzahlen der Pose-Erkennung (Diagnose-Doc §2, Stufe 0)
// =============================================================================
//
// An diesen drei Zahlen wird jede weitere Stabilisierungs-Stufe gemessen (Baseline
// zuerst mit ML Kit erheben, dann MediaPipe/Filter dagegen vergleichen):
//  - Jitter: MEDIAN der Frame-zu-Frame-Verschiebung sicher erkannter Landmarks in px.
//    Der Median statt Mittelwert approximiert "ruhiger Griff": echte Züge sind selten
//    und landen in den oberen Quantilen, das Grundzittern dominiert die Mitte.
//  - Dropout: Anteil fehlender/unsicherer Landmark-Slots (33 je Frame erwartet).
//  - Flip: Anteil der Frame-Übergänge, bei denen ein Links/Rechts-Paar überkreuzt
//    besser zum Vorgänger passt als gerade — das BlazePose-Vertauschungs-Symptom.

/** Kennzahlen einer kompletten Pose-Spur. Alle Quoten in [0,1]. */
data class PoseQualityMetrics(
    val jitterPx: Double,
    val dropoutRate: Double,
    val flipRate: Double,
    val meanConfidence: Double,
    /** **Morph-Metrik** (A7): mittlerer Variationskoeffizient der auf die Körpergröße
     *  normierten Knochenlängen. Anatomisch ist dieses Verhältnis konstant — jede
     *  Streuung darin IST das sichtbare "Morphen". 0 = starre Proportionen. */
    val boneLengthCv: Double,
    /** **Kollaps-Metrik** (A7): Variationskoeffizient der Körpergröße über die Spur.
     *  Bei fixer Kamera schwankt sie nur perspektivisch (langsam); ein Ausschlag ist
     *  das "Schrumpfen" der ganzen Pose. */
    val scaleCv: Double,
    /** **Halluzinations-Metrik** (S2a): Anteil der Knochen-Messungen, deren Länge den
     *  Median um mehr als [GhostTuning.RIGID_MAX_FACTOR] übersteigt. Anders als
     *  [boneLengthCv] ist das ein EINSEITIGES Maß, und genau deshalb aussagekräftiger:
     *  eine Projektion kann durch Verkürzung nur kürzer werden, nie länger — jede
     *  Überlänge ist erfunden. Das ist der Anteil, den die rigide Rekonstruktion
     *  entfernt; die legitime Verkürzung bleibt in [boneLengthCv] stehen. */
    val boneOverExtensionRate: Double,
    /**
     * **Unruhe-Metrik** (S6b): hochfrequenter Versatz des Rumpfzentrums gegenüber
     * seiner eigenen geglätteten Bahn, als Anteil der Körpergröße.
     *
     * Die Lücke, die alle bisherigen Kennzahlen offen ließen: [boneLengthCv] misst
     * Längenverhältnisse, [scaleCv] die Körpergröße, [jitterPx] wird von echter
     * Bewegung dominiert — gegen ein Skelett, das als GANZES pro Frame ein Stück neben
     * dem Körper landet, sind alle drei blind. Genau das ist aber das sichtbare
     * "wackelt hin und her und bleibt nicht sauber über dem Körper".
     *
     * Gemessen wird gegen einen kurzen Fenster-Median: eine echte Bewegung ist über
     * fünf Frames nahezu geradlinig und hinterlässt kaum Rest, ein Zucken schon.
     */
    val centroidWobble: Double,
)

/** Dropout + mittlere Confidence innerhalb einer Wiedergabe-Sekunde (Debug-HUD). */
data class PoseSecondStats(
    val dropoutRate: Double,
    val meanConfidence: Double,
)

/** Kennzahlen über die gesamte Spur — einmal pro Track berechnen (remember/cache). */
fun GhostPoseTrack.qualityMetrics(): PoseQualityMetrics = frames.qualityMetrics()

/** Dieselben Kennzahlen für eine beliebige Frame-Liste — erlaubt den direkten
 *  Vergleich gefilterte Spur vs. [GhostPoseTrack.rawFrames] im Debug-HUD. */
fun List<GhostPoseFrame>.qualityMetrics(): PoseQualityMetrics {
    val frames = this
    var slots = 0L
    var confident = 0L
    var confidenceSum = 0.0
    var confidenceCount = 0L
    val displacements = ArrayList<Double>(frames.size * GhostLandmarkTypes.COUNT)
    var flipTransitions = 0
    var flipHits = 0

    frames.forEachIndexed { index, frame ->
        slots += GhostLandmarkTypes.COUNT
        frame.landmarks.forEach { lm ->
            confidenceSum += lm.confidence
            confidenceCount++
            if (lm.confidence >= GhostTuning.MIN_LANDMARK_CONFIDENCE) confident++
        }
        if (index == 0) return@forEachIndexed
        val prev = frames[index - 1].landmarks
            .filter { it.confidence >= GhostTuning.MIN_LANDMARK_CONFIDENCE }
            .associateBy { it.type }
        val curr = frame.landmarks
            .filter { it.confidence >= GhostTuning.MIN_LANDMARK_CONFIDENCE }
            .associateBy { it.type }
        curr.forEach { (type, lm) ->
            val p = prev[type] ?: return@forEach
            displacements += hypot((lm.x - p.x).toDouble(), (lm.y - p.y).toDouble())
        }
        GhostLandmarkTypes.LEFT_RIGHT_PAIRS.forEach { (leftType, rightType) ->
            val pl = prev[leftType] ?: return@forEach
            val pr = prev[rightType] ?: return@forEach
            val cl = curr[leftType] ?: return@forEach
            val cr = curr[rightType] ?: return@forEach
            fun dist(ax: Float, ay: Float, bx: Float, by: Float) =
                hypot((ax - bx).toDouble(), (ay - by).toDouble())
            val straight = dist(pl.x, pl.y, cl.x, cl.y) + dist(pr.x, pr.y, cr.x, cr.y)
            val crossed = dist(pl.x, pl.y, cr.x, cr.y) + dist(pr.x, pr.y, cl.x, cl.y)
            flipTransitions++
            if (crossed < straight) flipHits++
        }
    }

    displacements.sort()
    val boneStats = frames.boneStats()
    return PoseQualityMetrics(
        jitterPx = if (displacements.isEmpty()) 0.0 else displacements[displacements.size / 2],
        dropoutRate = if (slots == 0L) 0.0 else 1.0 - confident.toDouble() / slots,
        flipRate = if (flipTransitions == 0) 0.0 else flipHits.toDouble() / flipTransitions,
        meanConfidence = if (confidenceCount == 0L) 0.0 else confidenceSum / confidenceCount,
        boneLengthCv = boneStats.cv,
        scaleCv = coefficientOfVariation(frames.mapNotNull { bodyScale(it.landmarks) }),
        boneOverExtensionRate = boneStats.overExtensionRate,
        centroidWobble = frames.centroidWobble(),
    )
}

/** Halbe Fensterbreite des Median, gegen den die Unruhe gemessen wird. 2 → 5 Frames
 *  (~0,4 s): kurz genug, dass echte Bewegung darin geradlinig ist und keinen Rest
 *  hinterlässt, lang genug, um ein einzelnes Zucken sichtbar zu machen. */
private const val WOBBLE_MEDIAN_WINDOW = 2

/**
 * Unruhe-Metrik (S6b): Median des Abstands zwischen Rumpfzentrum und dem gleitenden
 * MITTEL seiner Nachbarschaft, normiert auf die Körpergröße.
 *
 * Bewusst ein Mittelwert und kein Median als Referenz: ein Fenster-Median folgt einer
 * Frame-zu-Frame-Alternation perfekt (im Fenster gewinnt die Mehrheit, also der eigene
 * Wert) und sähe damit ausgerechnet das Hin-und-Her nicht, um das es hier geht. Ein
 * gleitendes Mittel liegt dagegen zwischen den beiden Auslenkungen. Die Robustheit
 * gegen einen einzelnen groben Aussetzer kommt vom Median ÜBER die Frames: der
 * beschreibt die DAUERHAFTE Unruhe, Einzelfälle sind Sache der Gates.
 */
private fun List<GhostPoseFrame>.centroidWobble(): Double {
    if (size < 2 * WOBBLE_MEDIAN_WINDOW + 1) return 0.0
    val centroids = map { coreCentroid(it.landmarks) }
    val scales = personScales(this)
    val residuals = ArrayList<Double>(size)
    for (i in indices) {
        val c = centroids[i] ?: continue
        val scale = scales[i] ?: continue
        if (scale <= 0.0) continue
        var sumX = 0.0
        var sumY = 0.0
        var count = 0
        for (j in (i - WOBBLE_MEDIAN_WINDOW)..(i + WOBBLE_MEDIAN_WINDOW)) {
            if (j !in indices) continue
            centroids[j]?.let { sumX += it.first; sumY += it.second; count++ }
        }
        if (count < 3) continue
        residuals += hypot(c.first - sumX / count, c.second - sumY / count) / scale
    }
    if (residuals.isEmpty()) return 0.0
    residuals.sort()
    return residuals[residuals.size / 2]
}

private class BoneStats(val cv: Double, val overExtensionRate: Double)

/** Relative Messtoleranz der Überlängen-Quote (0,1 % — weit über Float-Rundung, weit
 *  unter jeder anatomisch bedeutsamen Abweichung). */
private const val OVER_EXTENSION_TOLERANCE = 1.001

/**
 * Form-Kennzahlen (A7/S2a): je starrem Knochen die auf die Körpergröße normierte Länge
 * sammeln. Die Normierung ist der Punkt — die absolute Pixellänge schwankt legitim mit
 * Perspektive und Abstand, das Verhältnis zur Körpergröße nicht. Daraus zwei Zahlen:
 * der Variationskoeffizient (Gesamt-Formfehler) und der Anteil der ÜBERLÄNGEN gegenüber
 * dem Median (rein erfundener Anteil).
 *
 * S5b: normiert wird gegen [personScales], dieselbe Referenz wie im Rekonstruktions-Pass.
 * Vorher stand hier die ROHE Rumpfmessung pro Frame — die schrumpft aber schon, wenn
 * sich der Kletterer nur dreht, und zählte damit legitime Drehung als Morphen. Dass Pass
 * und Kennzahl gegen verschiedene Referenzen liefen, hat mich zweimal in die Irre geführt.
 */
private fun List<GhostPoseFrame>.boneStats(): BoneStats {
    val cvPerBone = ArrayList<Double>(RIGID_BONES.size)
    var measurements = 0
    var overExtended = 0
    val scales = personScales(this)
    RIGID_BONES.forEach { (fromType, toType) ->
        val ratios = mapIndexedNotNull { i, frame ->
            val scale = scales[i] ?: return@mapIndexedNotNull null
            if (scale <= 0.0) return@mapIndexedNotNull null
            val from = frame.shown(fromType) ?: return@mapIndexedNotNull null
            val to = frame.shown(toType) ?: return@mapIndexedNotNull null
            distance(from, to) / scale
        }
        // Unter drei Messungen sind beide Kennzahlen nicht aussagekräftig.
        if (ratios.size < 3) return@forEach
        cvPerBone += coefficientOfVariation(ratios)
        val median = ratios.sorted()[ratios.size / 2]
        // Messtoleranz: ein geklemmter Knochen landet EXAKT auf der Grenze, und die
        // Hälfte davon rundet in Float minimal darüber (gemessen: Faktor 1,00000025).
        // Ohne diese Toleranz zählt die Kennzahl Rundungsrauschen als Halluzination —
        // sie meldete deshalb 26,5 %, obwohl die Rekonstruktion sauber gearbeitet hatte.
        val limit = median * GhostTuning.RIGID_MAX_FACTOR * OVER_EXTENSION_TOLERANCE
        measurements += ratios.size
        overExtended += ratios.count { it > limit }
    }
    return BoneStats(
        cv = if (cvPerBone.isEmpty()) 0.0 else cvPerBone.average(),
        overExtensionRate = if (measurements == 0) 0.0 else {
            overExtended.toDouble() / measurements
        },
    )
}

/** σ/μ — dimensionslos, dadurch über verschiedene Videos und Zoomstufen vergleichbar. */
private fun coefficientOfVariation(values: List<Double>): Double {
    if (values.size < 3) return 0.0
    val mean = values.average()
    if (mean <= 0.0) return 0.0
    val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
    return sqrt(variance) / mean
}

private fun GhostPoseFrame.shown(type: Int): GhostLandmark? =
    landmarks.firstOrNull {
        it.type == type && it.confidence >= GhostTuning.VISIBILITY_SHOW_THRESHOLD
    }

/** Kennzahlen je Wiedergabe-Sekunde, Schlüssel = Sekunde (timeMs/1000). */
fun GhostPoseTrack.perSecondStats(): Map<Long, PoseSecondStats> =
    frames.groupBy { it.timeMs / 1000 }.mapValues { (_, frames) ->
        val slots = frames.size * GhostLandmarkTypes.COUNT
        val landmarks = frames.flatMap { it.landmarks }
        val confident = landmarks.count { it.confidence >= GhostTuning.MIN_LANDMARK_CONFIDENCE }
        PoseSecondStats(
            dropoutRate = if (slots == 0) 0.0 else 1.0 - confident.toDouble() / slots,
            meanConfidence = if (landmarks.isEmpty()) 0.0 else
                landmarks.sumOf { it.confidence.toDouble() } / landmarks.size,
        )
    }
