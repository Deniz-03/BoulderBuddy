package com.boulderbuddy.ghost.pose

import com.boulderbuddy.ghost.GhostTuning
import com.boulderbuddy.ghost.model.GhostLandmark
import com.boulderbuddy.ghost.model.GhostLandmarkTypes
import com.boulderbuddy.ghost.model.GhostPoseFrame
import kotlin.math.hypot

// =============================================================================
// Stufe 2 — anatomische Plausibilität + Links/Rechts-Konsistenz (Diagnose §2)
// =============================================================================
//
// BlazePose ist auf aufrechte Fitness-Posen trainiert; Kletterposen (Rücken zur
// Kamera, Überkopf-Reaches) provozieren Links/Rechts-Vertauschungen und
// halluzinierte Glieder (Root Causes C/D). Beide Filter laufen auf der ROHEN
// Spur, VOR One-Euro-Glättung und Hysterese — die Filter sollen konsistente,
// physikalisch mögliche Eingaben bekommen.

/** Reihenfolge der Verarbeitungsschritte: Skalen-Konsistenz → L/R-Konsistenz →
 *  Plausibilität. Der Skalen-Gate zuerst, damit L/R und Plausibilität nicht auf einer
 *  kollabierten Pose aufsetzen. */
fun cleanPoseFrames(frames: List<GhostPoseFrame>, frameHeight: Int): List<GhostPoseFrame> =
    applyAnatomicalPlausibility(
        enforceLeftRightConsistency(enforcePoseScaleConsistency(frames)),
        frameHeight,
    )

// --- Pose-Skalen-Konsistenz (7.5c): Ganzkörper-Kollaps ("Schrumpfen") abfangen -------
//
// Die Per-Landmark-/Per-Knochen-Filter fangen einen gleichmäßigen Skalenkollaps der
// GANZEN Pose nicht: der Rumpf steckt in keinem RIGID_BONE, und eine proportional kleine
// Pose ist in sich konsistent. Deshalb ein Check auf POSE-EBENE: die Rumpfgröße (Schulter-
// Mitte ↔ Hüft-Mitte) ist bei fixer Kamera weitgehend konstant. Frames, deren Rumpf
// unplausibel von der Median-Rumpfgröße des Videos abweicht, werden durch zeitliche
// Interpolation zwischen den nächsten gültigen Frames ersetzt (Offline-Vorteil: die
// ganze Spur ist bekannt, daher echte Interpolation statt bloßem Einfrieren).

/**
 * Ersetzt Frames mit kollabierter/aufgeblähter Rumpfgröße durch die zeitliche
 * Interpolation der nächsten gültigen Nachbar-Frames. Braucht ≥3 messbare Rumpfgrößen
 * für einen robusten Median, sonst unverändert.
 */
fun enforcePoseScaleConsistency(frames: List<GhostPoseFrame>): List<GhostPoseFrame> {
    val scales = frames.map { torsoScale(it) }
    val known = scales.filterNotNull().sorted()
    if (known.size < 3) return frames
    val median = known[known.size / 2]
    val minScale = median * GhostTuning.POSE_SCALE_MIN_RATIO
    val maxScale = median * GhostTuning.POSE_SCALE_MAX_RATIO

    val collapsed = BooleanArray(frames.size) { i ->
        val s = scales[i]
        s != null && (s < minScale || s > maxScale)
    }
    if (collapsed.none { it }) return frames

    return frames.mapIndexed { i, frame ->
        if (!collapsed[i]) return@mapIndexed frame
        var lo = i - 1
        while (lo >= 0 && collapsed[lo]) lo--
        var hi = i + 1
        while (hi < frames.size && collapsed[hi]) hi++
        val prev = if (lo >= 0) frames[lo] else null
        val next = if (hi < frames.size) frames[hi] else null
        frame.copy(landmarks = interpolatePose(prev, next, frame.timeMs))
    }
}

/** Rumpfgröße (Schulter-Mitte ↔ Hüft-Mitte) in Pixeln, oder null wenn nicht alle vier
 *  Rumpfpunkte sicher sind. */
private fun torsoScale(frame: GhostPoseFrame): Double? {
    val ls = frame.confident(GhostLandmarkTypes.LEFT_SHOULDER) ?: return null
    val rs = frame.confident(GhostLandmarkTypes.RIGHT_SHOULDER) ?: return null
    val lh = frame.confident(GhostLandmarkTypes.LEFT_HIP) ?: return null
    val rh = frame.confident(GhostLandmarkTypes.RIGHT_HIP) ?: return null
    return hypot(
        ((ls.x + rs.x) - (lh.x + rh.x)).toDouble() / 2.0,
        ((ls.y + rs.y) - (lh.y + rh.y)).toDouble() / 2.0,
    )
}

/** Zeitlich interpolierte Pose zwischen [prev] und [next] (nur gemeinsame Landmark-
 *  Typen); fehlt ein Nachbar, wird der vorhandene gehalten. */
private fun interpolatePose(
    prev: GhostPoseFrame?,
    next: GhostPoseFrame?,
    timeMs: Long,
): List<GhostLandmark> {
    if (prev == null && next == null) return emptyList()
    if (prev == null) return next!!.landmarks
    if (next == null) return prev.landmarks
    val span = (next.timeMs - prev.timeMs).toFloat()
    val t = if (span <= 0f) 0f else ((timeMs - prev.timeMs) / span).coerceIn(0f, 1f)
    val nextByType = next.landmarks.associateBy { it.type }
    return prev.landmarks.mapNotNull { a ->
        val b = nextByType[a.type] ?: return@mapNotNull null
        a.copy(
            x = a.x + (b.x - a.x) * t,
            y = a.y + (b.y - a.y) * t,
            confidence = minOf(a.confidence, b.confidence),
        )
    }
}

// --- Links/Rechts-Konsistenz (Punkt 8) ---------------------------------------

private val LR_TYPE_SWAP: Map<Int, Int> = buildMap {
    // BlazePose: linke Körperseite 11,13,15,…,31 ↔ rechte 12,14,16,…,32 sowie
    // Gesichts-Paare 1–3↔4–6 (Augen), 7↔8 (Ohren), 9↔10 (Mundwinkel).
    (11..31 step 2).forEach { left -> put(left, left + 1); put(left + 1, left) }
    (1..3).forEach { left -> put(left, left + 3); put(left + 3, left) }
    put(7, 8); put(8, 7)
    put(9, 10); put(10, 9)
}

/**
 * Erzwingt zeitliche Links/Rechts-Konsistenz: passt die aktuelle Pose besser auf
 * den (bereits korrigierten) Vorgänger-Frame, wenn man ALLE Seiten tauscht, war es
 * ein BlazePose-Ganzköper-Flip → tauschen. Ganzer Körper statt einzelner Paare,
 * weil die Flips als Ganzes auftreten und Einzeltausch anatomisch inkonsistente
 * Skelette erzeugen würde.
 */
fun enforceLeftRightConsistency(frames: List<GhostPoseFrame>): List<GhostPoseFrame> {
    var prev: Map<Int, GhostLandmark>? = null
    return frames.map { frame ->
        val confident = frame.landmarks
            .filter { it.confidence >= GhostTuning.MIN_LANDMARK_CONFIDENCE }
            .associateBy { it.type }
        val reference = prev
        val result = if (reference == null) {
            frame
        } else {
            var straight = 0.0
            var crossed = 0.0
            var comparable = 0
            GhostLandmarkTypes.LEFT_RIGHT_PAIRS.forEach { (leftType, rightType) ->
                val pl = reference[leftType] ?: return@forEach
                val pr = reference[rightType] ?: return@forEach
                val cl = confident[leftType] ?: return@forEach
                val cr = confident[rightType] ?: return@forEach
                straight += dist(pl, cl) + dist(pr, cr)
                crossed += dist(pl, cr) + dist(pr, cl)
                comparable++
            }
            if (comparable > 0 && crossed < straight * GhostTuning.LR_SWAP_MARGIN) {
                frame.copy(
                    landmarks = frame.landmarks.map { lm ->
                        lm.copy(type = LR_TYPE_SWAP[lm.type] ?: lm.type)
                    },
                )
            } else {
                frame
            }
        }
        // Korrigierten Frame als Referenz führen — sonst oszilliert der Vergleich.
        result.landmarks
            .filter { it.confidence >= GhostTuning.MIN_LANDMARK_CONFIDENCE }
            .associateBy { it.type }
            .takeIf { it.isNotEmpty() }
            ?.let { prev = it }
        result
    }
}

// --- Anatomische Plausibilität (Punkt 7) --------------------------------------

/** Knochen (proximal → distal), deren Länge über die Zeit konstant bleiben muss.
 *  Bei Verletzung wird das DISTALE Gelenk verworfen (Extremitäten halluzinieren
 *  eher als Rumpfpunkte). Reihenfolge proximal zuerst: ein verworfener Ellbogen
 *  nimmt den abhängigen Unterarm-Check gleich mit. */
private val RIGID_BONES: List<Pair<Int, Int>> = listOf(
    GhostLandmarkTypes.LEFT_SHOULDER to GhostLandmarkTypes.LEFT_ELBOW,
    GhostLandmarkTypes.LEFT_ELBOW to GhostLandmarkTypes.LEFT_WRIST,
    GhostLandmarkTypes.RIGHT_SHOULDER to GhostLandmarkTypes.RIGHT_ELBOW,
    GhostLandmarkTypes.RIGHT_ELBOW to GhostLandmarkTypes.RIGHT_WRIST,
    GhostLandmarkTypes.LEFT_HIP to GhostLandmarkTypes.LEFT_KNEE,
    GhostLandmarkTypes.LEFT_KNEE to GhostLandmarkTypes.LEFT_ANKLE,
    GhostLandmarkTypes.RIGHT_HIP to GhostLandmarkTypes.RIGHT_KNEE,
    GhostLandmarkTypes.RIGHT_KNEE to GhostLandmarkTypes.RIGHT_ANKLE,
)

/**
 * Zieht physisch unmögliche Landmarks auf die plausible Grenze zurück, statt sie zu
 * verwerfen (zwei Pässe über die Offline-Spur):
 *
 * 1. **Geschwindigkeitslimit:** springt ein Gelenk schneller als
 *    [GhostTuning.MAX_LANDMARK_SPEED_FRAME_HEIGHTS_PER_S], wird es entlang der
 *    Bewegungsrichtung auf die maximal mögliche Distanz zur letzten akzeptierten
 *    Position geklemmt (kein Teleport, aber das Gelenk bleibt am Körper).
 * 2. **Knochenlängen-Konstanz:** Median jeder Knochenlänge über den ganzen Track
 *    (robust gegen die Ausreißer selbst); weicht die Länge um mehr als
 *    [GhostTuning.BONE_LENGTH_TOLERANCE_FACTOR] ab, wird das DISTALE Gelenk entlang
 *    der Knochenrichtung auf die Toleranzgrenze der Median-Länge gezogen.
 *
 * 7.5c: früher wurden Verstöße VERWORFEN — das ließ Glieder verschwinden und wieder
 * auftauchen (sichtbares "Morphen"). Klemmen hält die Kette geschlossen und dämpft
 * nur den unplausiblen Anteil; die nachgelagerte One-Euro-Glättung glättet den Rest.
 */
fun applyAnatomicalPlausibility(
    frames: List<GhostPoseFrame>,
    frameHeight: Int,
): List<GhostPoseFrame> {
    if (frames.isEmpty()) return frames

    // Pass 1: Median-Länge je Knochen über den Track (nur sichere Endpunkte).
    val medianLength = RIGID_BONES.associateWith { (fromType, toType) ->
        val lengths = frames.mapNotNull { frame ->
            val from = frame.confident(fromType) ?: return@mapNotNull null
            val to = frame.confident(toType) ?: return@mapNotNull null
            dist(from, to)
        }.sorted()
        if (lengths.isEmpty()) null else lengths[lengths.size / 2]
    }

    // Pass 2: Verstöße klemmen. Geschwindigkeit gegen die letzte AKZEPTIERTE
    // Position des Gelenks (mit ihrem Zeitstempel) prüfen.
    val lastAccepted = HashMap<Int, Pair<GhostLandmark, Long>>()
    val tolerance = GhostTuning.BONE_LENGTH_TOLERANCE_FACTOR
    return frames.map { frame ->
        val kept = HashMap<Int, GhostLandmark>(frame.landmarks.size)
        frame.landmarks.forEach { lm -> kept[lm.type] = lm }

        // Geschwindigkeitslimit: auf die maximal mögliche Distanz zum Vorframe klemmen.
        frame.landmarks.forEach { lm ->
            val (prev, prevTimeMs) = lastAccepted[lm.type] ?: return@forEach
            val dtS = (frame.timeMs - prevTimeMs) / 1000.0
            if (dtS <= 0) return@forEach
            val speedLimit = GhostTuning.MAX_LANDMARK_SPEED_FRAME_HEIGHTS_PER_S *
                frameHeight * dtS
            val moved = dist(prev, lm)
            if (moved > speedLimit && moved > 0.0) {
                val f = (speedLimit / moved).toFloat()
                kept[lm.type] = lm.copy(
                    x = prev.x + (lm.x - prev.x) * f,
                    y = prev.y + (lm.y - prev.y) * f,
                )
            }
        }

        // Knochenlängen-Konstanz (proximal → distal): distales Gelenk auf die
        // Toleranzgrenze der Median-Länge ziehen, Richtung beibehalten.
        RIGID_BONES.forEach { bone ->
            val median = medianLength[bone] ?: return@forEach
            val from = kept[bone.first] ?: return@forEach
            val to = kept[bone.second] ?: return@forEach
            val length = dist(from, to)
            val limit = when {
                length > median * tolerance -> median * tolerance
                length < median / tolerance -> median / tolerance
                else -> return@forEach
            }
            if (length > 0.0) {
                val f = (limit / length).toFloat()
                kept[bone.second] = to.copy(
                    x = from.x + (to.x - from.x) * f,
                    y = from.y + (to.y - from.y) * f,
                )
            }
        }

        kept.values.forEach { lm ->
            if (lm.confidence >= GhostTuning.MIN_LANDMARK_CONFIDENCE) {
                lastAccepted[lm.type] = lm to frame.timeMs
            }
        }
        // Geklemmte Koordinaten aus kept übernehmen; Reihenfolge der Original-Spur halten.
        frame.copy(landmarks = frame.landmarks.mapNotNull { kept[it.type] })
    }
}

private fun GhostPoseFrame.confident(type: Int): GhostLandmark? =
    landmarks.firstOrNull {
        it.type == type && it.confidence >= GhostTuning.MIN_LANDMARK_CONFIDENCE
    }

private fun dist(a: GhostLandmark, b: GhostLandmark): Double =
    hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
