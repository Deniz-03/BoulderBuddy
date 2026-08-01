package com.boulderbuddy.ghost.model

import com.boulderbuddy.ghost.GhostTuning
import kotlin.math.hypot

// =============================================================================
// Gemeinsame Pose-Geometrie (7.5e)
// =============================================================================
//
// Körpergröße, Rumpf-Zentrum und die Liste der starren Knochen werden inzwischen an
// drei Stellen gebraucht: von den Plausibilitäts-Filtern (ghost.pose), von der ROI-Box
// der Extraktion und von den Qualitäts-Kennzahlen (ghost.analysis). Bewusst hier im
// model-Paket, das beide anderen ohnehin importieren — statt dreimal dieselbe Messung.

/**
 * Knochen (proximal → distal), deren Länge im Verhältnis zur Körpergröße anatomisch
 * konstant ist. Reihenfolge proximal zuerst: wer den Ellbogen korrigiert, korrigiert
 * den davon abhängigen Unterarm gleich mit.
 *
 * S8a: die FÜSSE gehören dazu, und dass sie gefehlt haben, war eine reine Lücke — die
 * Liste endete am Knöchel, [com.boulderbuddy.ghost.model.GhostSkeleton.BONES] zeichnet
 * aber bis zur Fußspitze weiter. Damit war ausgerechnet der sichtbare Teil des Skeletts
 * der einzige völlig unbeschränkte: gemessen morphten die Fußknochen mit 1,4–1,8 %
 * gegen 0,43 % bei allen beschränkten Knochen, also drei- bis viermal so stark.
 *
 * Fußlänge und Fersenabstand sind anatomisch genauso konstant wie ein Unterarm; dass die
 * Landmarks dort unsicherer sind, ist ein Argument FÜR die Beschränkung, nicht dagegen.
 */
val RIGID_BONES: List<Pair<Int, Int>> = listOf(
    GhostLandmarkTypes.LEFT_SHOULDER to GhostLandmarkTypes.LEFT_ELBOW,
    GhostLandmarkTypes.LEFT_ELBOW to GhostLandmarkTypes.LEFT_WRIST,
    GhostLandmarkTypes.RIGHT_SHOULDER to GhostLandmarkTypes.RIGHT_ELBOW,
    GhostLandmarkTypes.RIGHT_ELBOW to GhostLandmarkTypes.RIGHT_WRIST,
    GhostLandmarkTypes.LEFT_HIP to GhostLandmarkTypes.LEFT_KNEE,
    GhostLandmarkTypes.LEFT_KNEE to GhostLandmarkTypes.LEFT_ANKLE,
    GhostLandmarkTypes.RIGHT_HIP to GhostLandmarkTypes.RIGHT_KNEE,
    GhostLandmarkTypes.RIGHT_KNEE to GhostLandmarkTypes.RIGHT_ANKLE,
    // Füße ans Ende der jeweiligen Kette — der Knöchel muss korrigiert sein, bevor die
    // Ferse an ihm hängt, und die Ferse, bevor die Fußspitze an ihr hängt.
    GhostLandmarkTypes.LEFT_ANKLE to GhostLandmarkTypes.LEFT_HEEL,
    GhostLandmarkTypes.LEFT_HEEL to GhostLandmarkTypes.LEFT_FOOT_INDEX,
    GhostLandmarkTypes.RIGHT_ANKLE to GhostLandmarkTypes.RIGHT_HEEL,
    GhostLandmarkTypes.RIGHT_HEEL to GhostLandmarkTypes.RIGHT_FOOT_INDEX,
)

/**
 * Kanten des Rumpf-Vierecks. Anatomisch ebenso konstant wie die Gliedmaßen-Knochen —
 * und weil die Körpergröße genau aus ihnen gemessen wird, ist der Rumpf die REFERENZ
 * für alle übrigen Längen. Bleibt er unbeschränkt, zappelt die Referenz, und die
 * Gliedmaßen erben das Zappeln entweder in der Zeit (Zittern) oder in der Form
 * (Morphen) — je nachdem, ob roh oder geglättet gemessen wird. Deshalb gehört er in
 * dieselbe Rekonstruktion.
 *
 * Vier Kanten ohne Diagonalen: das Viereck bleibt bewusst scherbar, damit sich der
 * Rumpf weiter drehen und neigen kann.
 */
val TORSO_EDGES: List<Pair<Int, Int>> = listOf(
    GhostLandmarkTypes.LEFT_SHOULDER to GhostLandmarkTypes.RIGHT_SHOULDER,
    GhostLandmarkTypes.LEFT_HIP to GhostLandmarkTypes.RIGHT_HIP,
    GhostLandmarkTypes.LEFT_SHOULDER to GhostLandmarkTypes.LEFT_HIP,
    GhostLandmarkTypes.RIGHT_SHOULDER to GhostLandmarkTypes.RIGHT_HIP,
)

/** Die vier Rumpfpunkte — die stabilsten Landmarks einer Kletterpose. */
private val CORE_TYPES = listOf(
    GhostLandmarkTypes.LEFT_SHOULDER,
    GhostLandmarkTypes.RIGHT_SHOULDER,
    GhostLandmarkTypes.LEFT_HIP,
    GhostLandmarkTypes.RIGHT_HIP,
)

/**
 * Robuste Körpergröße in Pixeln: Median mehrerer struktureller Maße (Schulter-/Hüft-
 * breite + Rumpfseiten). Robust gegen einzelne Fehlpunkte UND gegen perspektivische
 * Rumpfverkürzung — die Breiten bleiben dabei stabil.
 *
 * [minPresence] > 0 verlangt zusätzlich echte Präsenz: MediaPipe meldet für erfundene
 * Positionen durchaus hohe visibility, presence unterscheidet "verdeckt" von
 * "halluziniert". Wird für die ROI-Box gebraucht, wo eine Fehlmessung teuer ist.
 */
fun bodyScale(
    landmarks: List<GhostLandmark>,
    minConfidence: Float = GhostTuning.VISIBILITY_SHOW_THRESHOLD,
    minPresence: Float = 0f,
): Double? {
    fun at(type: Int) = landmarks.firstOrNull {
        it.type == type && it.confidence >= minConfidence && it.presence >= minPresence
    }
    val ls = at(GhostLandmarkTypes.LEFT_SHOULDER)
    val rs = at(GhostLandmarkTypes.RIGHT_SHOULDER)
    val lh = at(GhostLandmarkTypes.LEFT_HIP)
    val rh = at(GhostLandmarkTypes.RIGHT_HIP)
    val cues = ArrayList<Double>(4)
    if (ls != null && rs != null) cues += distance(ls, rs)
    if (lh != null && rh != null) cues += distance(lh, rh)
    if (ls != null && lh != null) cues += distance(ls, lh)
    if (rs != null && rh != null) cues += distance(rs, rh)
    if (cues.isEmpty()) return null
    cues.sort()
    return cues[cues.size / 2]
}

/**
 * **Körpergröße der Person** je Frame (S5b) — die EINE Referenz, gegen die alle
 * Längenprüfungen normieren, im Rekonstruktions-Pass wie in den Kennzahlen.
 *
 * [bodyScale] allein taugt dafür nicht: es ist der Median von vier Rumpfkanten, und die
 * verkürzen sich projiziert alle, wenn sich der Kletterer bloß DREHT. Eine Größe, die
 * sich ändert, wenn sich die Person nur dreht, misst nicht ihre Größe. Wer Knochen
 * dagegen normiert, prüft „Knochenlänge relativ dazu, wie breit die Schultern gerade
 * zufällig projizieren" — kein anatomischer Invariant.
 *
 * Die tatsächliche Größe ändert sich nur langsam (Abstand zur Kamera). Genau diese
 * langsame Komponente greift der rollierende Median ab, während er Drehungen (typisch
 * unter einer Sekunde) und Messrauschen verwirft.
 *
 * Wichtig ist weniger der genaue Fensterwert als dass **alle** Stellen dieselbe
 * Referenz benutzen: Pass und Kennzahl gegeneinander laufen zu lassen war die Ursache
 * dafür, dass Korrekturen an einer Stelle als Verschlechterung an der anderen auftauchten.
 */
fun personScales(
    frames: List<GhostPoseFrame>,
    window: Int = GhostTuning.PERSON_SCALE_WINDOW,
): List<Double?> = smoothedPersonScales(bodyScales(frames), window)

/** Rohe Rumpfmessung je Frame. Getrennt abrufbar, weil die Pose-Gates BEIDES brauchen:
 *  den rohen Wert als Prüfgröße und [smoothedPersonScales] als Referenz. */
fun bodyScales(frames: List<GhostPoseFrame>): List<Double?> =
    frames.map { bodyScale(it.landmarks) }

/** Rollierender Median über [window] Frames je Seite — siehe [personScales] für das Warum. */
fun smoothedPersonScales(
    rawScales: List<Double?>,
    window: Int = GhostTuning.PERSON_SCALE_WINDOW,
): List<Double?> {
    if (window <= 0) return rawScales
    return rawScales.indices.map { i ->
        val values = ArrayList<Double>(2 * window + 1)
        for (j in (i - window)..(i + window)) {
            if (j in rawScales.indices) rawScales[j]?.let { values += it }
        }
        if (values.isEmpty()) return@map null
        values.sort()
        values[values.size / 2]
    }
}

/** Zentrum der Rumpfpunkte, oder null bei weniger als drei brauchbaren Punkten. */
fun coreCentroid(
    landmarks: List<GhostLandmark>,
    minConfidence: Float = GhostTuning.VISIBILITY_SHOW_THRESHOLD,
    minPresence: Float = 0f,
): Pair<Double, Double>? {
    val core = CORE_TYPES.mapNotNull { type ->
        landmarks.firstOrNull {
            it.type == type && it.confidence >= minConfidence && it.presence >= minPresence
        }
    }
    if (core.size < 3) return null
    return core.sumOf { it.x.toDouble() } / core.size to
        core.sumOf { it.y.toDouble() } / core.size
}

fun distance(a: GhostLandmark, b: GhostLandmark): Double =
    hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
