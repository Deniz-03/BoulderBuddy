package com.boulderbuddy.ghost.pose

import com.boulderbuddy.ghost.GhostTuning
import com.boulderbuddy.ghost.model.GhostLandmark
import com.boulderbuddy.ghost.model.GhostLandmarkTypes
import com.boulderbuddy.ghost.model.GhostPoseFrame
import com.boulderbuddy.ghost.model.RIGID_BONES
import com.boulderbuddy.ghost.model.bodyScale
import com.boulderbuddy.ghost.model.coreCentroid
import com.boulderbuddy.ghost.model.distance
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

/** Reihenfolge der Verarbeitungsschritte: Pose-Konsistenz (Skala + Position) →
 *  L/R-Konsistenz → Plausibilität. Der Pose-Gate zuerst, damit L/R und Plausibilität
 *  nicht auf einer kollabierten oder verschobenen Pose aufsetzen. */
fun cleanPoseFrames(frames: List<GhostPoseFrame>, frameHeight: Int): List<GhostPoseFrame> =
    applyAnatomicalPlausibility(
        enforceLeftRightConsistency(enforcePoseConsistency(frames)),
        frameHeight,
    )

// --- Pose-Konsistenz (7.5c): Ganzkörper-Kollaps + verschobenes Skelett abfangen -------
//
// Die Per-Landmark-/Per-Knochen-Filter fangen zwei Fehlerbilder der GANZEN Pose nicht:
// (1) gleichmäßiger Skalenkollaps ("Schrumpfen") — der Rumpf steckt in keinem RIGID_BONE
// und eine proportional kleine Pose ist in sich konsistent; (2) ein isolierter Ganzkörper-
// Sprung — Form/Größe stimmen, aber die Pose liegt neben dem Körper. Beides prüft dieser
// POSE-EBENEN-Pass an einer bei fixer Kamera stabilen Körpergröße und ersetzt Ausreißer
// durch zeitliche Interpolation der nächsten gültigen Frames (Offline-Vorteil: ganze Spur).

/**
 * Ersetzt Frames mit unplausibler Körpergröße (Kollaps/Aufblähung) ODER isoliertem
 * Ganzkörper-Positionssprung durch die zeitliche Interpolation der nächsten gültigen
 * Nachbar-Frames. Braucht ≥3 messbare Größen für einen robusten Median, sonst unverändert.
 */
fun enforcePoseConsistency(frames: List<GhostPoseFrame>): List<GhostPoseFrame> {
    if (frames.size < 3) return frames
    val scales = frames.map { bodyScale(it.landmarks) }
    val centroids = frames.map { coreCentroid(it.landmarks) }
    val known = scales.filterNotNull().sorted()
    if (known.size < 3) return frames
    val median = known[known.size / 2]
    val minScale = median * GhostTuning.POSE_SCALE_MIN_RATIO
    val maxScale = median * GhostTuning.POSE_SCALE_MAX_RATIO

    // Phase 1: Skalen-Kollaps/-Explosion.
    val invalid = BooleanArray(frames.size) { i ->
        val s = scales[i]
        s != null && (s < minScale || s > maxScale)
    }
    // Skalen-Urteil einfrieren — Referenz fürs Positions-Gate (kein Kaskadieren).
    val scaleInvalid = invalid.copyOf()

    // Phase 2: isolierter Positionssprung. Referenz ist der Fenster-Median der Zentroide
    // skalen-gültiger Frames — eine GLATTE schnelle Bewegung liegt nahe dem Median (wird
    // NICHT markiert), nur ein Ausreißer, der wegspringt und zurückkehrt, weicht stark ab.
    val shiftLimit = median * GhostTuning.POSE_SHIFT_MAX_RATIO
    val half = GhostTuning.POSE_SHIFT_MEDIAN_WINDOW
    for (i in frames.indices) {
        if (invalid[i]) continue
        val c = centroids[i] ?: continue
        val winX = ArrayList<Double>(2 * half + 1)
        val winY = ArrayList<Double>(2 * half + 1)
        for (j in (i - half)..(i + half)) {
            if (j !in frames.indices || scaleInvalid[j]) continue
            val cj = centroids[j] ?: continue
            winX += cj.first
            winY += cj.second
        }
        if (winX.size < 3) continue
        winX.sort()
        winY.sort()
        val mx = winX[winX.size / 2]
        val my = winY[winY.size / 2]
        if (hypot(c.first - mx, c.second - my) > shiftLimit) invalid[i] = true
    }

    if (invalid.none { it }) return frames
    return frames.mapIndexed { i, frame ->
        if (!invalid[i]) return@mapIndexed frame
        var lo = i - 1
        while (lo >= 0 && invalid[lo]) lo--
        var hi = i + 1
        while (hi < frames.size && invalid[hi]) hi++
        val prev = if (lo >= 0) frames[lo] else null
        val next = if (hi < frames.size) frames[hi] else null
        frame.copy(landmarks = interpolatePose(prev, next, frame.timeMs))
    }
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
                straight += distance(pl, cl) + distance(pr, cr)
                crossed += distance(pl, cr) + distance(pr, cl)
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
            distance(from, to)
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
            val moved = distance(prev, lm)
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
            val length = distance(from, to)
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
