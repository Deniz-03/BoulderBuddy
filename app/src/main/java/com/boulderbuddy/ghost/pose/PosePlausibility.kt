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

/** Reihenfolge der Verarbeitungsschritte: L/R-Konsistenz → Plausibilität. */
fun cleanPoseFrames(frames: List<GhostPoseFrame>, frameHeight: Int): List<GhostPoseFrame> =
    applyAnatomicalPlausibility(enforceLeftRightConsistency(frames), frameHeight)

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
 * Verwirft physisch unmögliche Landmarks (zwei Pässe über die Offline-Spur):
 *
 * 1. **Knochenlängen-Konstanz:** Median jeder Knochenlänge über den ganzen Track
 *    (robust gegen die Ausreißer selbst); Frames, deren Länge um mehr als
 *    [GhostTuning.BONE_LENGTH_TOLERANCE_FACTOR] abweicht, verlieren das distale Gelenk.
 * 2. **Geschwindigkeitslimit:** springt ein Gelenk schneller als
 *    [GhostTuning.MAX_LANDMARK_SPEED_FRAME_HEIGHTS_PER_S], ist es ein Fehlgriff des
 *    Modells (Teleport), nicht Bewegung — verworfen.
 *
 * Verworfene Landmarks fängt downstream der Blink-Fix/die Hysterese ab (kurz
 * halten, bei anhaltender Lücke ausblenden).
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

    // Pass 2: Verstöße verwerfen. Geschwindigkeit gegen die letzte AKZEPTIERTE
    // Position des Gelenks (mit ihrem Zeitstempel) prüfen.
    val lastAccepted = HashMap<Int, Pair<GhostLandmark, Long>>()
    val tolerance = GhostTuning.BONE_LENGTH_TOLERANCE_FACTOR
    return frames.map { frame ->
        val kept = HashMap<Int, GhostLandmark>(frame.landmarks.size)
        frame.landmarks.forEach { lm -> kept[lm.type] = lm }

        // Geschwindigkeitslimit.
        frame.landmarks.forEach { lm ->
            val (prev, prevTimeMs) = lastAccepted[lm.type] ?: return@forEach
            val dtS = (frame.timeMs - prevTimeMs) / 1000.0
            if (dtS <= 0) return@forEach
            val speedLimit = GhostTuning.MAX_LANDMARK_SPEED_FRAME_HEIGHTS_PER_S *
                frameHeight * dtS
            if (dist(prev, lm) > speedLimit) kept.remove(lm.type)
        }

        // Knochenlängen-Konstanz (proximal → distal).
        RIGID_BONES.forEach { bone ->
            val median = medianLength[bone] ?: return@forEach
            val from = kept[bone.first] ?: return@forEach
            val to = kept[bone.second] ?: return@forEach
            val length = dist(from, to)
            if (length > median * tolerance || length < median / tolerance) {
                kept.remove(bone.second)
            }
        }

        kept.values.forEach { lm ->
            if (lm.confidence >= GhostTuning.MIN_LANDMARK_CONFIDENCE) {
                lastAccepted[lm.type] = lm to frame.timeMs
            }
        }
        frame.copy(landmarks = frame.landmarks.filter { kept.containsKey(it.type) })
    }
}

private fun GhostPoseFrame.confident(type: Int): GhostLandmark? =
    landmarks.firstOrNull {
        it.type == type && it.confidence >= GhostTuning.MIN_LANDMARK_CONFIDENCE
    }

private fun dist(a: GhostLandmark, b: GhostLandmark): Double =
    hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
