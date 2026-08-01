package com.boulderbuddy.ghost.model

import kotlinx.serialization.Serializable

// =============================================================================
// Ghost Climber — Datenmodell der Pose-Extraktion (Phase 7.5, M1)
// =============================================================================
//
// Die Modelle sind @Serializable, weil Keypoints laut Plan (A.2) als JSON-Datei im
// App-Storage landen (Pfad in der DB, keine BLOB-Spalten) — siehe GhostArtifactStore.
// Koordinaten leben im PIXELRAUM des skalierten Analyse-Frames (GhostTuning
// .POSE_INPUT_LONG_SIDE_PX), nicht im Original-Video: entscheidend ist nur, dass
// Posen UND Homographie-Anker eines Videos denselben Raum teilen.

/** Einfacher 2D-Punkt im Analyse-Frame-Raum — für Anker (M2) und Routenpfad (M3). */
@Serializable
data class GhostPoint(
    val x: Float,
    val y: Float,
)

/**
 * Ein Pose-Landmark: `type` = BlazePose-Index ([GhostLandmarkTypes], 0–32).
 * Seit Stufe 3 (MediaPipe): `confidence` = **visibility** (Position vertrauenswürdig,
 * ersetzt den früheren InFrameLikelihood-Missbrauch, Root Cause C) und `presence` =
 * Wahrscheinlichkeit, dass der Punkt überhaupt im Bild ist.
 */
@Serializable
data class GhostLandmark(
    val type: Int,
    val x: Float,
    val y: Float,
    val confidence: Float,
    val presence: Float = 1f,
)

/** Alle Landmarks eines abgetasteten Video-Frames. Leere Liste = keine Pose erkannt. */
@Serializable
data class GhostPoseFrame(
    val timeMs: Long,
    val landmarks: List<GhostLandmark>,
)

/** Komplette Pose-Spur eines Videos (Ergebnis von VideoPoseExtractor, gecacht als JSON). */
@Serializable
data class GhostPoseTrack(
    val videoUri: String,
    /** Maße des skalierten Analyse-Frames — Referenz fürs Overlay-Mapping. */
    val frameWidth: Int,
    val frameHeight: Int,
    val durationMs: Long,
    val sampleFps: Double,
    val frames: List<GhostPoseFrame>,
    /**
     * Ungefilterte Roh-Landmarks fürs Debug-Overlay (Stufe 0: roh vs. gefiltert
     * vergleichbar machen). null, solange kein Filter aktiv ist ([frames] = roh).
     */
    val rawFrames: List<GhostPoseFrame>? = null,
)

/**
 * Landmarks zur Wiedergabezeit [timeMs], linear zwischen den beiden umliegenden
 * Sample-Frames interpoliert (die Extraktion tastet mit ~12 fps ab, das Video
 * läuft mit 30+ — ohne Interpolation springt das Skelett sichtbar).
 *
 * Blink-Fix (Stufe 1, Root Cause B): fehlt ein Landmark in genau EINEM der beiden
 * Nachbar-Frames (einzelner Aussetzer), wird es aus dem anderen gehalten statt für
 * das ganze Sample-Intervall zu verschwinden. Erst wenn es in beiden Nachbarn fehlt
 * (echte Lücke), blendet das Overlay es aus.
 */
fun GhostPoseTrack.landmarksAt(timeMs: Long): List<GhostLandmark> {
    if (frames.isEmpty()) return emptyList()
    val insertion = frames.binarySearchBy(timeMs) { it.timeMs }
    if (insertion >= 0) return frames[insertion].landmarks
    val after = -insertion - 1
    if (after <= 0) return frames.first().landmarks
    if (after >= frames.size) return frames.last().landmarks
    val a = frames[after - 1]
    val b = frames[after]
    val t = (timeMs - a.timeMs).toFloat() / (b.timeMs - a.timeMs).toFloat()
    val byTypeA = a.landmarks.associateBy { it.type }
    val byTypeB = b.landmarks.associateBy { it.type }
    // Nachbarn für die weiche Interpolation (S4c); fehlen sie (Spuranfang/-ende),
    // fällt der jeweilige Punkt unten auf lineare Interpolation zurück.
    val byTypePrev = frames.getOrNull(after - 2)?.landmarks?.associateBy { it.type }
    val byTypeNext = frames.getOrNull(after + 1)?.landmarks?.associateBy { it.type }
    return (byTypeA.keys + byTypeB.keys).mapNotNull { type ->
        val la = byTypeA[type]
        val lb = byTypeB[type]
        when {
            la != null && lb != null -> GhostLandmark(
                type = type,
                x = catmullRom(byTypePrev?.get(type)?.x, la.x, lb.x, byTypeNext?.get(type)?.x, t),
                y = catmullRom(byTypePrev?.get(type)?.y, la.y, lb.y, byTypeNext?.get(type)?.y, t),
                confidence = minOf(la.confidence, lb.confidence),
                presence = minOf(la.presence, lb.presence),
            )
            // Einseitiger Aussetzer: kurz halten (max. ein Sample-Intervall).
            la != null -> la
            else -> lb
        }
    }
}

/**
 * Catmull-Rom-Interpolation zwischen [p1] und [p2] mit den Nachbarn [p0]/[p3] (S4c).
 *
 * Lineare Interpolation trifft jeden Stützpunkt mit einem Knick: die Bewegungsrichtung
 * springt alle 83 ms (12 fps), was auch bei kleinen Amplituden als Unruhe sichtbar ist.
 * Catmull-Rom läuft stetig differenzierbar durch die Stützpunkte — es glättet die
 * DARSTELLUNG, ohne die Daten zu verändern: bei t=0 und t=1 liegt es exakt auf den
 * gemessenen Werten.
 *
 * Fehlt ein Nachbar (Spuranfang/-ende), wird er GESPIEGELT statt dupliziert. Den
 * Randpunkt zu duplizieren ergäbe dort eine Tangente von null, also ein sichtbares
 * Einschwingen im ersten und letzten Sample-Intervall; die Spiegelung setzt die
 * vorhandene Steigung fort und ist für gleichförmige Bewegung exakt linear.
 */
private fun catmullRom(p0: Float?, p1: Float, p2: Float, p3: Float?, t: Float): Float {
    val a = p0 ?: (2f * p1 - p2)
    val d = p3 ?: (2f * p2 - p1)
    val t2 = t * t
    val t3 = t2 * t
    return 0.5f * (
        2f * p1 +
            (-a + p2) * t +
            (2f * a - 5f * p1 + 4f * p2 - d) * t2 +
            (-a + 3f * p1 - 3f * p2 + d) * t3
        )
}
