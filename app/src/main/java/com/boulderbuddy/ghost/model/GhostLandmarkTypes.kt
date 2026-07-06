package com.boulderbuddy.ghost.model

/**
 * Landmark-Indizes der BlazePose-Topologie (33 Punkte) als eigene Konstanten.
 * Die Werte sind in ML Kit (`PoseLandmark.*`) und MediaPipe Pose Landmarker identisch —
 * eigene Konstanten machen das Datenmodell unabhängig von der Pose-Bibliothek
 * (Stufe 3: Migration ML Kit → MediaPipe, FABLE_GHOSTCLIMBER_STABILISIERUNG.md).
 */
object GhostLandmarkTypes {
    const val NOSE = 0
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val LEFT_KNEE = 25
    const val RIGHT_KNEE = 26
    const val LEFT_ANKLE = 27
    const val RIGHT_ANKLE = 28
    const val LEFT_HEEL = 29
    const val RIGHT_HEEL = 30
    const val LEFT_FOOT_INDEX = 31
    const val RIGHT_FOOT_INDEX = 32

    /** Gesamtzahl der BlazePose-Landmarks — Bezugsgröße für Dropout-Quoten (Stufe 0). */
    const val COUNT = 33

    /** Links/Rechts-Paare der Extremitäten — Basis der Flip-Kennzahl (Stufe 0). */
    val LEFT_RIGHT_PAIRS: List<Pair<Int, Int>> = listOf(
        LEFT_SHOULDER to RIGHT_SHOULDER,
        LEFT_ELBOW to RIGHT_ELBOW,
        LEFT_WRIST to RIGHT_WRIST,
        LEFT_HIP to RIGHT_HIP,
        LEFT_KNEE to RIGHT_KNEE,
        LEFT_ANKLE to RIGHT_ANKLE,
    )
}
