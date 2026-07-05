package com.boulderbuddy.ghost.model

import com.google.mlkit.vision.pose.PoseLandmark

/**
 * Skelett-Topologie fürs Overlay: welche Landmark-Paare als "Knochen" verbunden werden.
 * Gesicht + Finger sind bewusst ausgelassen — für die Kletteranalyse zählen Rumpf und
 * Extremitäten, weniger Punkte = ruhigeres Bild.
 */
object GhostSkeleton {

    val BONES: List<Pair<Int, Int>> = listOf(
        // Rumpf
        PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
        PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
        PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
        PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
        // Arme
        PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW,
        PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
        PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
        PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
        // Beine
        PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
        PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
        PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE,
        PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE,
        // Füße
        PoseLandmark.LEFT_ANKLE to PoseLandmark.LEFT_HEEL,
        PoseLandmark.LEFT_HEEL to PoseLandmark.LEFT_FOOT_INDEX,
        PoseLandmark.RIGHT_ANKLE to PoseLandmark.RIGHT_HEEL,
        PoseLandmark.RIGHT_HEEL to PoseLandmark.RIGHT_FOOT_INDEX,
    )

    /** Nur diese Landmark-Typen werden als Gelenkpunkte gezeichnet. */
    val JOINT_TYPES: Set<Int> = BONES.flatMapTo(mutableSetOf()) { listOf(it.first, it.second) }
}
