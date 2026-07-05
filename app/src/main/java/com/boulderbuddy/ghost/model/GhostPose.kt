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

/** Ein ML-Kit-Landmark: `type` = PoseLandmark-Konstante (0–32), `confidence` = InFrameLikelihood. */
@Serializable
data class GhostLandmark(
    val type: Int,
    val x: Float,
    val y: Float,
    val confidence: Float,
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
)

/**
 * Landmarks zur Wiedergabezeit [timeMs], linear zwischen den beiden umliegenden
 * Sample-Frames interpoliert (die Extraktion tastet nur mit ~6 fps ab, das Video
 * läuft mit 30+ — ohne Interpolation springt das Skelett sichtbar).
 *
 * Fehlt ein Landmark in einem der beiden Nachbar-Frames (Pose verloren), fällt es
 * weg statt zu raten — das Overlay blendet es dann aus.
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
    val byType = b.landmarks.associateBy { it.type }
    return a.landmarks.mapNotNull { la ->
        val lb = byType[la.type] ?: return@mapNotNull null
        GhostLandmark(
            type = la.type,
            x = la.x + (lb.x - la.x) * t,
            y = la.y + (lb.y - la.y) * t,
            confidence = minOf(la.confidence, lb.confidence),
        )
    }
}
