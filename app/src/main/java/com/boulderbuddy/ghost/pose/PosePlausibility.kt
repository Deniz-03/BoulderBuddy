package com.boulderbuddy.ghost.pose

import com.boulderbuddy.ghost.GhostTuning
import com.boulderbuddy.ghost.model.GhostLandmark
import com.boulderbuddy.ghost.model.GhostLandmarkTypes
import com.boulderbuddy.ghost.model.GhostPoseFrame
import com.boulderbuddy.ghost.model.bodyScales
import com.boulderbuddy.ghost.model.coreCentroid
import com.boulderbuddy.ghost.model.distance
import com.boulderbuddy.ghost.model.smoothedPersonScales
import kotlin.math.hypot

// =============================================================================
// Stufe 2 — anatomische Plausibilität + Links/Rechts-Konsistenz (Diagnose §2)
// =============================================================================
//
// BlazePose ist auf aufrechte Fitness-Posen trainiert; Kletterposen (Rücken zur
// Kamera, Überkopf-Reaches) provozieren Links/Rechts-Vertauschungen und
// halluzinierte Glieder (Root Causes C/D).
//
// [cleanPoseFrames] ist der Einstieg und fasst mehrere Prüfungen zusammen, die über die
// Stufen dazugekommen sind: Skala- und Positions-Gate gegen das ganze Skelett, das
// Ruck-Gate über die Zentroid-Beschleunigung (S3), die Links/Rechts-Konsistenz und die
// anatomischen Klemmen. Was die Gates verwerfen, wird begrenzt interpoliert — zu lange
// Strecken werden geleert statt geraten ([PoseGateStats.dropped]).
//
// **Das alles läuft auf der ROHEN Spur**, als erste Stufe der Kette in
// [com.boulderbuddy.ghost.pose.extractPoseTrack]: vor der Lücken-Interpolation, vor
// One-Euro und vor der Hysterese. Die späteren Stufen sollen konsistente, physikalisch
// mögliche Eingaben bekommen — eine Glättung über eine vertauschte Pose glättet den
// Fehler mit ein, statt ihn zu entfernen.

/**
 * Wie viele Frames die einzelnen Pose-Gates ersetzt haben (S4d). Ohne diese Zahlen ist
 * nicht zu sehen, ob ein Gate gar nicht greift oder im Gegenteil so viel ersetzt, dass
 * die Spur überwiegend aus Interpolation besteht — beides sähe im Overlay ähnlich aus.
 */
data class PoseGateStats(
    val total: Int,
    val scaleInvalid: Int,
    val shiftInvalid: Int,
    val jerkInvalid: Int,
    /** Ungültige Strecken über [GhostTuning.MAX_POSE_INTERPOLATION_FRAMES] — geleert
     *  statt interpoliert. */
    val dropped: Int,
)

/** Reihenfolge der Verarbeitungsschritte: Pose-Konsistenz (Skala + Position) →
 *  L/R-Konsistenz → Plausibilität. Der Pose-Gate zuerst, damit L/R und Plausibilität
 *  nicht auf einer kollabierten oder verschobenen Pose aufsetzen. */
fun cleanPoseFrames(
    frames: List<GhostPoseFrame>,
    frameHeight: Int,
    onStats: (PoseGateStats) -> Unit = {},
): List<GhostPoseFrame> =
    applyAnatomicalPlausibility(
        enforceLeftRightConsistency(enforcePoseConsistency(frames, onStats)),
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
fun enforcePoseConsistency(
    frames: List<GhostPoseFrame>,
    onStats: (PoseGateStats) -> Unit = {},
): List<GhostPoseFrame> {
    if (frames.size < 3) return frames
    val scales = bodyScales(frames)
    val centroids = frames.map { coreCentroid(it.landmarks) }
    if (scales.count { it != null } < 3) return frames

    // Referenzgröße je Frame aus einem ROLLIERENDEN Fenster (S2b) statt aus der ganzen
    // Spur: über 30 s ändert sich die scheinbare Körpergröße legitim (der Kletterer
    // entfernt sich, die Perspektive dreht). Gegen einen globalen Median musste das
    // Toleranzfenster diese Drift mit abdecken und ließ echte Kollapse durch; lokal
    // darf es eng sein. Ein einzelner Ausreißer verschiebt den Median nicht.
    //
    // Dieselbe Funktion wie in Rekonstruktion und Kennzahlen: hier stand vorher eine
    // zweite, eigene Median-Implementierung mit eigener Fensterbreite. Genau solche
    // getrennten Referenzen waren zweimal die Ursache für Fehldiagnosen — dass Pass und
    // Messung dasselbe meinen, ist wichtiger als der exakte Fensterwert.
    val smoothed = smoothedPersonScales(scales)
    val localScale = DoubleArray(frames.size) { i -> smoothed[i] ?: -1.0 }

    // Phase 1: Skalen-Kollaps/-Explosion.
    val invalid = BooleanArray(frames.size) { i ->
        val s = scales[i]
        val reference = localScale[i]
        s != null && reference > 0.0 &&
            (s < reference * GhostTuning.POSE_SCALE_MIN_RATIO ||
                s > reference * GhostTuning.POSE_SCALE_MAX_RATIO)
    }
    // Skalen-Urteil einfrieren — Referenz fürs Positions-Gate (kein Kaskadieren).
    val scaleInvalid = invalid.copyOf()
    val scaleCount = invalid.count { it }
    var shiftCount = 0
    var jerkCount = 0

    // Phase 2: isolierter Positionssprung. Referenz ist der Fenster-Median der Zentroide
    // skalen-gültiger Frames — eine GLATTE schnelle Bewegung liegt nahe dem Median (wird
    // NICHT markiert), nur ein Ausreißer, der wegspringt und zurückkehrt, weicht stark ab.
    val half = GhostTuning.POSE_SHIFT_MEDIAN_WINDOW
    for (i in frames.indices) {
        if (invalid[i]) continue
        val c = centroids[i] ?: continue
        val reference = localScale[i]
        if (reference <= 0.0) continue
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
        val shiftLimit = reference * GhostTuning.POSE_SHIFT_MAX_RATIO
        if (hypot(c.first - mx, c.second - my) > shiftLimit) {
            invalid[i] = true
            shiftCount++
        }
    }

    // Phase 3: Ruck-Gate (S3a). Das Positions-Gate oben misst den WEG zum Fenster-Median
    // und vermischt damit zwei Fälle, die nichts miteinander zu tun haben: ein echter
    // schneller Zug (großer Weg, aber glatt) und ein Wegzucken (großer Weg, ruckartig).
    // Deshalb musste seine Schwelle so weit stehen, dass das Zucken durchfiel. Hier wird
    // stattdessen die BESCHLEUNIGUNG gemessen: Vorhersage aus den beiden Vorframes mit
    // konstanter Geschwindigkeit, Residuum an der Körpergröße normiert. Eine glatte
    // Bewegung sagt sich gut vorher, ein Ruck nicht.
    for (i in 2 until frames.size) {
        if (invalid[i] || invalid[i - 1] || invalid[i - 2]) continue
        val c = centroids[i] ?: continue
        // Nur bei lückenlosen Vorgängern — sonst stimmt die Zeitbasis der Extrapolation
        // nicht (die Sample-Zeiten sind äquidistant, ausgefallene Frames sind es nicht).
        val p1 = centroids[i - 1] ?: continue
        val p2 = centroids[i - 2] ?: continue
        val reference = localScale[i]
        if (reference <= 0.0) continue
        val predictedX = p1.first + (p1.first - p2.first)
        val predictedY = p1.second + (p1.second - p2.second)
        if (hypot(c.first - predictedX, c.second - predictedY) >
            reference * GhostTuning.POSE_JERK_MAX_RATIO
        ) {
            invalid[i] = true
            jerkCount++
        }
    }

    if (invalid.none { it }) {
        onStats(PoseGateStats(frames.size, 0, 0, 0, 0))
        return frames
    }
    var dropped = 0
    val result = frames.mapIndexed { i, frame ->
        if (!invalid[i]) return@mapIndexed frame
        var lo = i - 1
        while (lo >= 0 && invalid[lo]) lo--
        var hi = i + 1
        while (hi < frames.size && invalid[hi]) hi++
        // Interpolationslänge begrenzen (S2c): über eine lange ungültige Strecke ist die
        // erfundene Bewegung zwangsläufig linear, die echte aber nicht — sichtbar als
        // Skelett, das der Bewegung nachhinkt oder ihr vorauseilt. Lieber gar keins.
        if (hi - lo - 1 > GhostTuning.MAX_POSE_INTERPOLATION_FRAMES) {
            dropped++
            return@mapIndexed frame.copy(landmarks = emptyList())
        }
        val prev = if (lo >= 0) frames[lo] else null
        val next = if (hi < frames.size) frames[hi] else null
        frame.copy(landmarks = interpolatePose(prev, next, frame.timeMs))
    }
    onStats(PoseGateStats(frames.size, scaleCount, shiftCount, jerkCount, dropped))
    return result
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
 * **Geschwindigkeitslimit:** springt ein Gelenk schneller als
 * [GhostTuning.MAX_LANDMARK_SPEED_FRAME_HEIGHTS_PER_S], wird es entlang der
 * Bewegungsrichtung auf die maximal mögliche Distanz zur letzten akzeptierten Position
 * geklemmt (kein Teleport, aber das Gelenk bleibt am Körper).
 *
 * 7.5c: früher wurden Verstöße VERWORFEN — das ließ Glieder verschwinden und wieder
 * auftauchen (sichtbares "Morphen"). Klemmen hält die Kette geschlossen und dämpft
 * nur den unplausiblen Anteil; die nachgelagerte One-Euro-Glättung glättet den Rest.
 *
 * S2a (7.5e): die frühere Knochenlängen-Prüfung ist hier RAUS. Sie maß absolute
 * Pixellängen gegen einen Track-Median — die schwanken aber legitim mit Perspektive
 * und Abstand, weshalb ihre Toleranz auf 1,5 stehen musste und praktisch nichts mehr
 * fing (gemessen: Formfehler 21–23 %). Zuständig ist jetzt [enforceRigidSkeleton] mit
 * dem auf die Körpergröße normierten Verhältnis. Ein Mechanismus je Fehlerbild.
 */
fun applyAnatomicalPlausibility(
    frames: List<GhostPoseFrame>,
    frameHeight: Int,
): List<GhostPoseFrame> {
    if (frames.isEmpty()) return frames

    // Geschwindigkeit gegen die letzte AKZEPTIERTE Position des Gelenks (mit Zeitstempel).
    val lastAccepted = HashMap<Int, Pair<GhostLandmark, Long>>()
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
