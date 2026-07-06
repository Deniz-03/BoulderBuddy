package com.boulderbuddy.ghost.model

import com.boulderbuddy.ghost.model.GhostLandmarkTypes as T

/**
 * Skelett-Topologie fürs Overlay: welche Landmark-Paare als "Knochen" verbunden werden.
 * Gesicht + Finger sind bewusst ausgelassen — für die Kletteranalyse zählen Rumpf und
 * Extremitäten, weniger Punkte = ruhigeres Bild.
 */
object GhostSkeleton {

    val BONES: List<Pair<Int, Int>> = listOf(
        // Rumpf
        T.LEFT_SHOULDER to T.RIGHT_SHOULDER,
        T.LEFT_SHOULDER to T.LEFT_HIP,
        T.RIGHT_SHOULDER to T.RIGHT_HIP,
        T.LEFT_HIP to T.RIGHT_HIP,
        // Arme
        T.LEFT_SHOULDER to T.LEFT_ELBOW,
        T.LEFT_ELBOW to T.LEFT_WRIST,
        T.RIGHT_SHOULDER to T.RIGHT_ELBOW,
        T.RIGHT_ELBOW to T.RIGHT_WRIST,
        // Beine
        T.LEFT_HIP to T.LEFT_KNEE,
        T.LEFT_KNEE to T.LEFT_ANKLE,
        T.RIGHT_HIP to T.RIGHT_KNEE,
        T.RIGHT_KNEE to T.RIGHT_ANKLE,
        // Füße
        T.LEFT_ANKLE to T.LEFT_HEEL,
        T.LEFT_HEEL to T.LEFT_FOOT_INDEX,
        T.RIGHT_ANKLE to T.RIGHT_HEEL,
        T.RIGHT_HEEL to T.RIGHT_FOOT_INDEX,
    )

    /** Nur diese Landmark-Typen werden als Gelenkpunkte gezeichnet. */
    val JOINT_TYPES: Set<Int> = BONES.flatMapTo(mutableSetOf()) { listOf(it.first, it.second) }
}
