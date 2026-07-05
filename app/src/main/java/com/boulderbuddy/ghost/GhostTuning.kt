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

    // --- Fortschrittssignal / Routenpfad (M3) ---------------------------------

    /** Gauss-Sigma (in Sample-Frames) für die Glättung von Trajektorie und Signal (P8). */
    const val SMOOTHING_SIGMA_FRAMES: Double = 2.0

    /** Stützpunkte, auf die der Pfad-Vorschlag (geglättete Hüfttrajektorie) reduziert wird. */
    const val PATH_SUGGESTION_POINTS: Int = 12

    // --- DTW (M3) --------------------------------------------------------------

    /**
     * Sakoe-Chiba-Band als Anteil der Signallänge: wie weit das Alignment von der
     * Diagonalen abweichen darf. Begrenzt Laufzeit UND verhindert absurde Warps.
     */
    const val DTW_BAND_FRACTION: Double = 0.25

    // --- Modus-Vorschlag Overlay vs. Side-by-Side (M4, P7) ---------------------

    /** Max. normalisierte DTW-Distanz (Anteil der Pfadlänge) für einen Overlay-Vorschlag. */
    const val MODE_DTW_DISTANCE_MAX_FRACTION: Double = 0.06

    /** Max. Differenz der lateralen Streuung (Anteil der Pfadlänge) für Overlay. */
    const val MODE_LATERAL_STD_DIFF_MAX_FRACTION: Double = 0.04

    /** Max. Winkel zwischen den PCA-Hauptrichtungen beider Trajektorien für Overlay. */
    const val MODE_MAIN_DIRECTION_MAX_DEG: Double = 25.0

    // --- Sturz-/Abbrucherkennung (M5, P5) --------------------------------------

    /** Geschwindigkeits-Spike-Schwelle: Median + n·σ_robust (1,4826·MAD) der Hüftgeschwindigkeit.
     *  Median/MAD statt Mittelwert/σ, damit der Sturz selbst die Baseline nicht verfälscht. */
    const val FALL_SPEED_SIGMA_FACTOR: Double = 3.0

    /** Untergrenze der Spike-Schwelle als Vielfaches des Medians — fängt den entarteten
     *  Fall MAD ≈ 0 (sehr gleichförmige Bewegung) ab. */
    const val FALL_SPEED_MIN_MEDIAN_FACTOR: Double = 2.0

    /** So viele Sample-Frames muss die Abwärtsbewegung anhalten (~0,7 s bei 6 fps) —
     *  entprellt Ausschütteln und dynamische Züge (P5-Grenzfälle). */
    const val FALL_MIN_DOWNWARD_FRAMES: Int = 4

    /** Fade-out-Dauer des Skeletts am Abbruchpunkt (P4c: Abbruch als Feature). */
    const val ABORT_FADE_MS: Long = 800L
}
