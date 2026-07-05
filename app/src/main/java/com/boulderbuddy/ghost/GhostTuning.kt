package com.boulderbuddy.ghost

/**
 * Zentrale Stellschrauben der Ghost-Climber-Pipeline (Phase 7.5).
 *
 * Alle Werte sind **empirisch zu kalibrieren** (Anhang A.6): Startwerte sind plausible
 * Defaults für Demo/Abgabe, keine gemessenen Schwellen. Bewusst EIN Ort statt verstreuter
 * Magic Numbers, damit eine spätere Kalibrierung (gelabelte Versuchspaare → Histogramme →
 * Trennlinien) nur diese Datei anfassen muss.
 */
object GhostTuning {

    // --- Pose-Extraktion (M1) ------------------------------------------------

    /**
     * Abtastrate der Offline-Pose-Extraktion in Frames/Sekunde. Kompromiss aus
     * Analysedauer (accurate-Modell braucht ~0,2–0,4 s/Frame) und zeitlicher Auflösung
     * des Fortschrittssignals fürs DTW. Beide Videos werden mit derselben Rate
     * abgetastet — das ersetzt das Resampling aus P8.
     */
    const val POSE_SAMPLE_FPS: Double = 6.0

    /**
     * Lange Kante der Analyse-Frames in Pixeln. Frames werden vor der Pose-Erkennung
     * herunterskaliert (schnellerer Decode + schnellere Inferenz); alle Keypoint- und
     * Anker-Koordinaten eines Videos leben in DIESEM skalierten Frame-Raum.
     */
    const val POSE_INPUT_LONG_SIDE_PX: Int = 720

    /** InFrameLikelihood-Schwelle, unter der ein Landmark als "verloren" gilt (P8). */
    const val MIN_LANDMARK_CONFIDENCE: Float = 0.5f

    /** Mindestanzahl abgetasteter Frames — kürzere Videos sind nicht analysierbar (P8). */
    const val MIN_POSE_FRAMES: Int = 10

    // --- Homographie / Anker (M2) --------------------------------------------

    /** Mindestanzahl korrespondierender Wandpunkte pro Video (A.3.2). */
    const val MIN_ANCHORS: Int = 4

    /** RANSAC-Iterationen bei >4 Ankern (Schutz gegen einzelne Fehl-Taps). */
    const val RANSAC_ITERATIONS: Int = 200

    /** Reprojektionsfehler in px (Analyse-Frame-Raum), bis zu dem ein Anker als Inlier gilt. */
    const val RANSAC_INLIER_THRESHOLD_PX: Double = 4.0
}
