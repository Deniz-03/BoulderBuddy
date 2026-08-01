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
import kotlin.math.abs
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
    /**
     * **Verkürzungs-Streuung** (A7): mittlerer Variationskoeffizient der auf die
     * Körpergröße normierten Knochenlängen.
     *
     * S7a — Namens- und Deutungskorrektur: das hieß bis hierher "Morph-Metrik", und das
     * war falsch. Gemessen liegt das 10.-Perzentil der Längenverhältnisse bei 0,37–0,87
     * des Medians; die Breite dieser Verteilung stammt also ganz überwiegend aus
     * LEGITIMER perspektivischer Verkürzung, nicht aus Halluzination. Eine Verteilungs-
     * breite kann Morphen auch prinzipiell nicht messen: dieselben Längen in anderer
     * zeitlicher Reihenfolge ergeben denselben Wert, und Morphen IST eine Aussage über
     * die zeitliche Reihenfolge. Dafür ist [boneLengthWobble] zuständig.
     *
     * Der Wert bleibt erhalten, weil er eine echte Frage beantwortet — wie stark arbeitet
     * dieser Kletterer in die Tiefe — nur eben nicht die nach der Bildruhe.
     */
    val boneLengthCv: Double,
    /**
     * **Morph-Metrik** (S7a): zeitliche Abweichung der normierten Knochenlänge vom Mittel
     * ihrer beiden Nachbarframes, als Anteil der Soll-Länge.
     *
     * Das ist die Größe, die "das Glied wird länger und wieder kürzer" tatsächlich misst:
     * eine echte Verkürzung läuft über mehrere Frames und ist zwischen zwei Nachbarn
     * nahezu linear (hinterlässt also fast keinen Rest), ein Aufpumpen für einen Frame
     * nicht. Gemessen 0,55 % gefiltert gegen 2,3 % roh — die Filterkette arbeitet hier
     * also weit besser, als [boneLengthCv] mit 19,7 % gegen 25,5 % vermuten ließ.
     */
    val boneLengthWobble: Double,
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
     * Referenz sind die NACHBARN, der eigene Frame zählt nicht mit (S7a). Vorher lief er
     * im Fenster-Mittel mit und dämpfte seine eigene Abweichung um ein Fünftel — die
     * Kennzahl maß die Störung also gegen sich selbst.
     *
     * Der MEDIAN beschreibt die dauerhafte Unruhe; für seltene, dafür heftige Ereignisse
     * ist er blind (ein Ausschlag in jedem zwölften Frame verschiebt ihn nicht um einen
     * Zähler). Dafür ist [centroidPulse] da — beide Zahlen gehören zusammen gelesen.
     */
    val centroidWobble: Double,
    /**
     * **Puls-Metrik** (S7a): wie stark die Unruhe von einer PERIODE abhängt —
     * schlechteste Phase geteilt durch die typische Phase, gemessen über
     * [GhostTuning.ROI_BOX_CHECK_INTERVAL_FRAMES] Frames.
     *
     * Diese Zahl existiert, weil ein Median die eigentliche Ursache verschluckt hat: die
     * Extraktion behandelt jeden n-ten Frame anders als die übrigen, und wenn dieser eine
     * Frame systematisch daneben liegt, ergibt das eine periodische Störung. Für das Auge
     * ist die viel auffälliger als gleich starkes zufälliges Rauschen — ein regelmäßiges
     * Zucken liest man als Fehler, ein unregelmäßiges als Bildrauschen. Für jede
     * Kennzahl, die über die ganze Spur mittelt, ist sie dagegen unsichtbar.
     *
     * 1,0 = keine Periodik. Gemessen vor S7b: 4,9 bzw. 5,3.
     *
     * Bewusst phasen-AGNOSTISCH (schlechteste Phase, nicht "Phase 0"): so schlägt die
     * Kennzahl auch an, wenn eine periodische Störung an anderer Stelle entsteht, und sie
     * bleibt gültig, falls sich die Prüf-Logik einmal verschiebt.
     */
    val centroidPulse: Double,
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
    val wobble = frames.centroidWobble()
    return PoseQualityMetrics(
        jitterPx = if (displacements.isEmpty()) 0.0 else displacements[displacements.size / 2],
        dropoutRate = if (slots == 0L) 0.0 else 1.0 - confident.toDouble() / slots,
        flipRate = if (flipTransitions == 0) 0.0 else flipHits.toDouble() / flipTransitions,
        meanConfidence = if (confidenceCount == 0L) 0.0 else confidenceSum / confidenceCount,
        boneLengthCv = boneStats.cv,
        scaleCv = coefficientOfVariation(frames.mapNotNull { bodyScale(it.landmarks) }),
        boneOverExtensionRate = boneStats.overExtensionRate,
        centroidWobble = wobble.median,
        centroidPulse = wobble.pulse,
        boneLengthWobble = boneStats.wobble,
    )
}

/** Halbe Fensterbreite der Nachbarschaft, gegen die die Unruhe gemessen wird. 2 → die
 *  je zwei Nachbarn links und rechts (~0,4 s): kurz genug, dass echte Bewegung darin
 *  geradlinig ist und keinen Rest hinterlässt, lang genug, um ein einzelnes Zucken
 *  sichtbar zu machen. */
private const val WOBBLE_MEDIAN_WINDOW = 2

/** Unruhe-Median über die Spur plus die Periodizität derselben Residuen. */
private class WobbleStats(val median: Double, val pulse: Double)

/**
 * Unruhe-Metrik (S6b): Abstand zwischen Rumpfzentrum und dem Mittel seiner NACHBARN,
 * normiert auf die Körpergröße — Median über die Spur, dazu der Puls (S7a).
 *
 * Der eigene Frame gehört nicht in die Referenz (S7a). Vorher lief er im Fenster mit,
 * womit ein Ausschlag zu einem Fünftel in seine eigene Referenz einging und sich selbst
 * um denselben Anteil kleinrechnete. Gegen die Nachbarn gemessen bleibt die Frage sauber:
 * wie weit liegt dieser Frame neben dem, was seine Umgebung erwarten lässt.
 *
 * Bewusst ein Mittelwert und kein Median als Referenz: ein Fenster-Median folgt einer
 * Frame-zu-Frame-Alternation perfekt (im Fenster gewinnt die Mehrheit) und sähe damit
 * ausgerechnet das Hin-und-Her nicht, um das es hier geht. Ein Mittel liegt dagegen
 * zwischen den beiden Auslenkungen. Die Robustheit gegen einen einzelnen groben
 * Aussetzer kommt vom Median ÜBER die Frames.
 */
private fun List<GhostPoseFrame>.centroidWobble(): WobbleStats {
    if (size < 2 * WOBBLE_MEDIAN_WINDOW + 1) return WobbleStats(0.0, 1.0)
    val centroids = map { coreCentroid(it.landmarks) }
    val scales = personScales(this)
    val residuals = ArrayList<Double>(size)
    // Residuen zusätzlich nach ihrer Phase abgelegt — dieselben Zahlen, nur anders
    // sortiert; daraus entsteht der Puls, ohne die Spur ein zweites Mal durchzugehen.
    val byPhase = Array(GhostTuning.ROI_BOX_CHECK_INTERVAL_FRAMES) { ArrayList<Double>() }
    for (i in indices) {
        val c = centroids[i] ?: continue
        val scale = scales[i] ?: continue
        if (scale <= 0.0) continue
        var sumX = 0.0
        var sumY = 0.0
        var count = 0
        for (j in (i - WOBBLE_MEDIAN_WINDOW)..(i + WOBBLE_MEDIAN_WINDOW)) {
            if (j == i || j !in indices) continue
            centroids[j]?.let { sumX += it.first; sumY += it.second; count++ }
        }
        if (count < 2) continue
        val residual = hypot(c.first - sumX / count, c.second - sumY / count) / scale
        residuals += residual
        byPhase[i % byPhase.size] += residual
    }
    if (residuals.isEmpty()) return WobbleStats(0.0, 1.0)
    residuals.sort()
    return WobbleStats(residuals[residuals.size / 2], byPhase.pulse())
}

/**
 * Puls (S7a): Median der schlechtesten Phase geteilt durch den Median der Phasen-Mediane.
 *
 * Der Nenner ist bewusst der Median ÜBER die Phasen und nicht der Gesamt-Median: hätte
 * die Störung mehrere Phasen erfasst, zöge sie den Gesamt-Median mit hoch und würde sich
 * dadurch selbst kaschieren. Der Median der Phasen-Mediane beschreibt dagegen die
 * typische, unauffällige Phase, auch wenn zwei oder drei aus der Reihe fallen.
 */
private fun Array<ArrayList<Double>>.pulse(): Double {
    // Zu dünn besetzte Phasen (sehr kurze Spuren) tragen keinen belastbaren Median.
    val phaseMedians = mapNotNull { phase ->
        if (phase.size < 3) null else phase.sorted()[phase.size / 2]
    }
    if (phaseMedians.size < 2) return 1.0
    val typical = phaseMedians.sorted()[phaseMedians.size / 2]
    // Der Nenner braucht eine Untergrenze, und die darf NICHT einfach 1,0 zurückgeben:
    // eine makellose Grundlinie mit einer einzigen ausschlagenden Phase ist der stärkste
    // denkbare Puls und würde damit als „alles ruhig" gemeldet — genau verkehrt herum.
    // Stattdessen wird der Nenner auf eine Größe gedeckelt, die unter jeder messbaren
    // Wirkung liegt (ein Zehntausendstel der Körpergröße ist weit im Subpixelbereich).
    // Auf echten Spuren liegt die typische Phase um Größenordnungen darüber, dort ändert
    // die Grenze nichts.
    return phaseMedians.max() / typical.coerceAtLeast(PULSE_BASELINE_FLOOR)
}

/** Untergrenze der typischen Phase im [pulse]-Nenner — siehe dort. */
private const val PULSE_BASELINE_FLOOR = 1e-4

private class BoneStats(
    val cv: Double,
    val overExtensionRate: Double,
    val wobble: Double,
)

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
    val wobbles = ArrayList<Double>(size * RIGID_BONES.size)
    val scales = personScales(this)
    RIGID_BONES.forEach { (fromType, toType) ->
        // Index-treu (null = in diesem Frame nicht messbar): das zeitliche Maß unten
        // braucht echte Nachbarschaft. Würde man die Lücken einfach herausfallen lassen,
        // stünden plötzlich Frames nebeneinander, die eine Sekunde auseinanderliegen.
        val perFrame = mapIndexed { i, frame ->
            val scale = scales[i] ?: return@mapIndexed null
            if (scale <= 0.0) return@mapIndexed null
            val from = frame.shown(fromType) ?: return@mapIndexed null
            val to = frame.shown(toType) ?: return@mapIndexed null
            distance(from, to) / scale
        }
        val ratios = perFrame.filterNotNull()
        // Unter drei Messungen ist keine der Kennzahlen aussagekräftig.
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

        // Zeitlicher Morph (S7a): Rest gegen das Mittel der beiden Nachbarn. Eine echte
        // Verkürzung läuft über mehrere Frames und ist lokal nahezu linear — sie fällt
        // hier heraus. Übrig bleibt, was ein einzelner Frame aus der Reihe tanzt.
        if (median <= 0.0) return@forEach
        for (i in 1 until perFrame.size - 1) {
            val previous = perFrame[i - 1] ?: continue
            val current = perFrame[i] ?: continue
            val next = perFrame[i + 1] ?: continue
            wobbles += abs(current - (previous + next) / 2.0) / median
        }
    }
    wobbles.sort()
    return BoneStats(
        cv = if (cvPerBone.isEmpty()) 0.0 else cvPerBone.average(),
        // Achtung bei der Deutung: NACH der rigiden Rekonstruktion ist dieser Wert
        // zwangsläufig ~0 — der Pass klemmt gegen denselben Median, gegen den hier
        // gemessen wird. Aussagekräftig ist er nur auf der ROHEN Spur, wo er beziffert,
        // wie viel erfundene Länge der Pass überhaupt zu entfernen hat.
        overExtensionRate = if (measurements == 0) 0.0 else {
            overExtended.toDouble() / measurements
        },
        wobble = if (wobbles.isEmpty()) 0.0 else wobbles[wobbles.size / 2],
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
