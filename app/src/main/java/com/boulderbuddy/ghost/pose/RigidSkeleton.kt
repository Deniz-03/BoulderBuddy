package com.boulderbuddy.ghost.pose

import com.boulderbuddy.ghost.GhostTuning
import com.boulderbuddy.ghost.model.GhostLandmark
import com.boulderbuddy.ghost.model.GhostPoseFrame
import com.boulderbuddy.ghost.model.RIGID_BONES
import com.boulderbuddy.ghost.model.TORSO_EDGES
import com.boulderbuddy.ghost.model.coreCentroid
import com.boulderbuddy.ghost.model.distance
import com.boulderbuddy.ghost.model.personScales

// =============================================================================
// S2a (7.5e) — Rigide Rekonstruktion der Gliedmaßenketten
// =============================================================================
//
// Gemessen lag der Formfehler (Streuung der auf die Körpergröße normierten Knochen-
// längen) bei 21–23 %, und die bisherige Filterkette senkte ihn um ganze 13 %. Das ist
// strukturell: alle bisherigen Filter arbeiten PRO LANDMARK — One-Euro glättet x und y
// jedes Punkts unabhängig, der alte Knochen-Check maß absolute Pixellängen gegen einen
// Track-Median. Ein Verfahren, das jeden Punkt einzeln behandelt, kann Form nicht
// erhalten; "Morphen" ist aber genau eine Form-Aussage.
//
// Dieser Pass erzwingt Form direkt: pro Kante das Median-VERHÄLTNIS zur Körpergröße
// (anatomisch konstant, anders als die Pixellänge), pro Frame daraus die Soll-Länge,
// und die Kette wird proximal→distal darauf gezogen. Richtung kommt vom Modell, Länge
// vom Soll.
//
// S5a: der RUMPF gehört mit dazu, und das war anfangs übersehen. Die Körpergröße wird
// aus den Rumpfkanten gemessen, sie ist also der Maßstab aller übrigen Sollwerte —
// blieb der Rumpf unbeschränkt, zappelte der Maßstab, und die Gliedmaßen erbten dieses
// Zappeln zwangsläufig: gegen die ROHE Körpergröße gemessen zeitlich (Zittern), gegen
// die GEGLÄTTETE als Widerspruch zum roh gezeichneten Rumpf im selben Frame (Morphen).
// Erst mit korrigiertem Rumpf ist beides gleichzeitig lösbar.
//
// Die Grenzen sind asymmetrisch, und das ist der geometrische Kern: die Projektion
// eines Knochens kann durch Verkürzung nur KÜRZER erscheinen als der echte Knochen,
// niemals länger. Eine Überlänge ist deshalb immer Halluzination und wird hart geklemmt;
// eine Unterlänge ist meist echte Verkürzung und bleibt großzügig erlaubt. Deshalb wird
// der Morph-Wert auch NICHT auf 0 sinken — der legitime Verkürzungsanteil bleibt drin.

/**
 * Zieht alle Gliedmaßenketten auf anatomisch konstante Proportionen (S2a).
 *
 * Läuft als LETZTER Schritt der Pipeline (S3b) — nach Glättung *und* Hysterese. Die
 * Glättung würde die Längen sonst wieder verziehen, und die Hysterese blendet Landmarks
 * wieder ein, deren Confidence sie anhebt: lief die Rekonstruktion davor, waren genau
 * diese (unsicheren, also besonders oft überlangen) Glieder nie geprüft. Da nur Längen
 * korrigiert und Richtungen übernommen werden, bleibt das Ergebnis so glatt wie die
 * Eingabe.
 *
 * [iterations] > 1 (S3c), weil ein Durchlauf seinen eigenen Sollwert nicht erreicht:
 * die Korrektur des Ellbogens ändert die Unterarmlänge, die Sollwerte stammen aber aus
 * dem Zustand davor. Jeder Durchlauf rechnet sie neu, das konvergiert zum Fixpunkt.
 *
 * Frames ohne messbare Körpergröße (Rumpf verdeckt) fallen auf den Median der ABSOLUTEN
 * Knochenlänge mit [GhostTuning.BONE_LENGTH_TOLERANCE_FACTOR] zurück — schwächer, aber
 * besser als gar keine Schranke.
 */
fun enforceRigidSkeleton(
    frames: List<GhostPoseFrame>,
    iterations: Int = GhostTuning.RIGID_ITERATIONS,
): List<GhostPoseFrame> {
    var current = frames
    repeat(iterations.coerceAtLeast(1)) { current = rigidPass(current) }
    return current
}

private fun rigidPass(frames: List<GhostPoseFrame>): List<GhostPoseFrame> {
    if (frames.isEmpty()) return frames
    // Die gemeinsame Körpergrößen-Referenz (S5b) — dieselbe Funktion, die auch die
    // Kennzahlen benutzen. Roh gemessen zappelt die Rumpfgröße mit den Landmarks UND
    // mit jeder Drehung des Kletterers; da sie die Soll-Länge jeder Kante skaliert,
    // trüge die Rekonstruktion das sonst in die ganze Pose.
    val scales = personScales(frames)

    // Pass 1: Soll-Proportionen über die ganze Spur (Offline-Vorteil). Median statt
    // Mittelwert — die Ausreißer, die wir korrigieren wollen, sollen ihn nicht ziehen.
    // Rumpfkanten und Gliedmaßen gleichermaßen (S5a): der Rumpf IST die Referenz, aus
    // der die Körpergröße stammt — bleibt er unbeschränkt, zappelt die Referenz.
    val targetRatio = HashMap<Pair<Int, Int>, Double>()
    val targetAbsolute = HashMap<Pair<Int, Int>, Double>()
    (TORSO_EDGES + RIGID_BONES).forEach { bone ->
        val ratios = ArrayList<Double>(frames.size)
        val absolutes = ArrayList<Double>(frames.size)
        frames.forEachIndexed { i, frame ->
            val from = frame.shown(bone.first) ?: return@forEachIndexed
            val to = frame.shown(bone.second) ?: return@forEachIndexed
            val length = distance(from, to)
            if (length <= 0.0) return@forEachIndexed
            absolutes += length
            val scale = scales[i]
            if (scale != null && scale > 0.0) ratios += length / scale
        }
        if (ratios.size >= 3) {
            ratios.sort()
            targetRatio[bone] = ratios[ratios.size / 2]
        }
        if (absolutes.size >= 3) {
            absolutes.sort()
            targetAbsolute[bone] = absolutes[absolutes.size / 2]
        }
    }

    // Pass 2: erst den Rumpf, dann die Ketten. Die Reihenfolge ist wesentlich — die
    // Gliedmaßen hängen an den korrigierten Schultern und Hüften, und innerhalb einer
    // Kette ist der korrigierte Ellbogen der Ansatzpunkt des Unterarms.
    return frames.mapIndexed { i, frame ->
        val scale = scales[i]
        val adjusted = HashMap<Int, GhostLandmark>(frame.landmarks.size)
        frame.landmarks.forEach { adjusted[it.type] = it }
        var changed = false

        /** Grenzen einer Kante; null, wenn kein Sollwert ermittelbar war. */
        fun limitsOf(edge: Pair<Int, Int>): Pair<Double, Double>? {
            val ratio = targetRatio[edge]
            return if (scale != null && scale > 0.0 && ratio != null) {
                val target = ratio * scale
                target * GhostTuning.RIGID_MIN_FACTOR to target * GhostTuning.RIGID_MAX_FACTOR
            } else {
                // Rückfall ohne messbare Körpergröße: symmetrische Alt-Toleranz.
                val target = targetAbsolute[edge] ?: return null
                val f = GhostTuning.BONE_LENGTH_TOLERANCE_FACTOR
                target / f to target * f
            }
        }

        // Rumpfkanten: SYMMETRISCH um ihre Mitte skalieren. Bei einer Rumpfkante gibt es
        // kein proximal/distal — einen der beiden Punkte zu bevorzugen würde den Rumpf
        // einseitig verziehen und sein Zentrum verschieben.
        TORSO_EDGES.forEach { edge ->
            val a = adjusted[edge.first]?.takeIf { it.isShown() } ?: return@forEach
            val b = adjusted[edge.second]?.takeIf { it.isShown() } ?: return@forEach
            val length = distance(a, b)
            if (length <= 0.0) return@forEach
            val (minLength, maxLength) = limitsOf(edge) ?: return@forEach
            val clamped = length.coerceIn(minLength, maxLength)
            if (clamped == length) return@forEach
            val f = (clamped / length).toFloat()
            val midX = (a.x + b.x) / 2f
            val midY = (a.y + b.y) / 2f
            adjusted[edge.first] = a.copy(
                x = midX + (a.x - midX) * f,
                y = midY + (a.y - midY) * f,
            )
            adjusted[edge.second] = b.copy(
                x = midX + (b.x - midX) * f,
                y = midY + (b.y - midY) * f,
            )
            changed = true
        }

        // Gliedmaßen: das DISTALE Gelenk entlang der Knochenrichtung nachziehen.
        RIGID_BONES.forEach { bone ->
            val from = adjusted[bone.first]?.takeIf { it.isShown() } ?: return@forEach
            val to = adjusted[bone.second]?.takeIf { it.isShown() } ?: return@forEach
            val length = distance(from, to)
            if (length <= 0.0) return@forEach
            val (minLength, maxLength) = limitsOf(bone) ?: return@forEach
            val clamped = length.coerceIn(minLength, maxLength)
            if (clamped == length) return@forEach
            val f = (clamped / length).toFloat()
            adjusted[bone.second] = to.copy(
                x = from.x + (to.x - from.x) * f,
                y = from.y + (to.y - from.y) * f,
            )
            changed = true
        }

        if (!changed) return@mapIndexed frame

        // Positions-Neutralität (S6a): die Rekonstruktion darf die FORM ändern, niemals
        // die LAGE. Die Rumpfkanten skalieren zwar symmetrisch um ihre jeweilige Mitte,
        // aber die beiden Seitenkanten verschieben Schultern UND Hüften — und damit das
        // Rumpfzentrum. Da dieser Pass als letzter läuft, wird diese Verschiebung von
        // keiner Glättung mehr aufgefangen: das ganze Skelett wandert pro Frame ein
        // Stück, sichtbar als "bleibt nicht sauber über dem Körper". Deshalb wird die
        // Pose am Ende um genau den entstandenen Versatz zurückgeschoben.
        val corrected = frame.landmarks.map { adjusted.getValue(it.type) }
        val before = coreCentroid(frame.landmarks)
        val after = coreCentroid(corrected)
        if (before == null || after == null) return@mapIndexed frame.copy(landmarks = corrected)
        val dx = (before.first - after.first).toFloat()
        val dy = (before.second - after.second).toFloat()
        if (dx == 0f && dy == 0f) return@mapIndexed frame.copy(landmarks = corrected)
        frame.copy(landmarks = corrected.map { it.copy(x = it.x + dx, y = it.y + dy) })
    }
}

private fun GhostLandmark.isShown(): Boolean =
    confidence >= GhostTuning.VISIBILITY_SHOW_THRESHOLD

private fun GhostPoseFrame.shown(type: Int): GhostLandmark? =
    landmarks.firstOrNull { it.type == type && it.isShown() }
