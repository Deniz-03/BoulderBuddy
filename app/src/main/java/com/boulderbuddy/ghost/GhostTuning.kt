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
     * Analysedauer und zeitlicher Auflösung des Fortschrittssignals fürs DTW. Beide
     * Videos werden mit derselben Rate abgetastet — das ersetzt das Resampling aus P8.
     * Stufe 1: 6 → 12 fps — bei 30-fps-Material verwarf 6 fps vier von fünf Frames,
     * schnelle Züge fielen zwischen die Samples (Aliasing/"Teleportieren").
     */
    const val POSE_SAMPLE_FPS: Double = 12.0

    /**
     * Lange Kante der Analyse-Frames in Pixeln. Frames werden vor der Pose-Erkennung
     * herunterskaliert (schnellerer Decode + schnellere Inferenz); alle Keypoint- und
     * Anker-Koordinaten eines Videos leben in DIESEM skalierten Frame-Raum.
     */
    const val POSE_INPUT_LONG_SIDE_PX: Int = 720

    /** Visibility-Schwelle, unter der ein Landmark als "verloren" gilt (P8, seit
     *  Stufe 3 echtes MediaPipe-visibility statt InFrameLikelihood). */
    const val MIN_LANDMARK_CONFIDENCE: Float = 0.5f

    // --- Zeitliche Stabilisierung (Stufe 1, 7.5b) ------------------------------

    /** One-Euro: Grund-Cutoff in Hz — je niedriger, desto ruhiger steht das Skelett
     *  bei statischem Griff. */
    const val ONE_EURO_MIN_CUTOFF_HZ: Double = 1.0

    /** One-Euro: Geschwindigkeitsanteil des Cutoffs — je höher, desto weniger Lag
     *  bei schnellen Zügen (dafür weniger Glättung in Bewegung). */
    const val ONE_EURO_BETA: Double = 0.02

    /** One-Euro: Cutoff der Ableitungs-Glättung in Hz (Standardwert des Papers). */
    const val ONE_EURO_DERIV_CUTOFF_HZ: Double = 1.0

    /** Fehlt ein Landmark länger als so viele Sample-Frames, startet sein Filter neu,
     *  statt die Position vom veralteten Zustand "nachzuziehen". */
    const val FILTER_RESET_GAP_FRAMES: Int = 3

    /** Hysterese-Ausblendschwelle: ein SICHTBARES Landmark bleibt sichtbar, solange
     *  visibility ≥ diesem Wert — erst darunter beginnt der Ausblend-Zähler.
     *  (Einblenden verlangt weiterhin ≥ [MIN_LANDMARK_CONFIDENCE].) */
    const val VISIBILITY_HIDE_THRESHOLD: Float = 0.3f

    /** So viele Sample-Frames in Folge muss visibility unter der Ausblendschwelle
     *  liegen, bevor das Landmark verschwindet (~250 ms bei 12 fps) — verhindert
     *  das Ein-/Ausflackern am Schwellwert (Root Cause B). */
    const val VISIBILITY_HIDE_FRAMES: Int = 3

    // --- Detektionsqualität (Stufe 2, 7.5b) ------------------------------------

    /** Toleranzfaktor der Knochenlängen-Konstanz: weicht eine Knochenlänge um mehr
     *  als [1/f, f] vom Track-Median ab, wird das distale Gelenk verworfen
     *  (halluzinierte Position, Root Cause C/D). */
    const val BONE_LENGTH_TOLERANCE_FACTOR: Float = 1.5f

    /** Geschwindigkeitslimit pro Gelenk in Frame-Höhen pro Sekunde — schnellere
     *  "Sprünge" sind physisch unmöglich (Wandausschnitt ≈ 3–4 m) und werden
     *  verworfen. Empirisch zu kalibrieren. */
    const val MAX_LANDMARK_SPEED_FRAME_HEIGHTS_PER_S: Float = 2.5f

    /** L/R-Konsistenz: getauscht wird nur, wenn die überkreuzte Zuordnung klar
     *  besser ist (crossed < margin · straight) — verhindert Daueroszillation
     *  bei nahezu symmetrischen Posen. */
    const val LR_SWAP_MARGIN: Float = 0.8f

    /** ROI-Crop: Erweiterung der letzten Personen-Bounding-Box je Seite. */
    const val ROI_EXPAND_FRACTION: Float = 0.2f

    /** ROI-Crop: so viele sichere Landmarks braucht die Box, sonst nächster Frame
     *  wieder als Vollbild (Person verloren → neu suchen). */
    const val ROI_MIN_CONFIDENT_LANDMARKS: Int = 6

    /** ROI-Crop: Glättung der Box über die Zeit (0 = einfrieren, 1 = sofort springen) —
     *  ruhige Box hilft dem internen MediaPipe-Tracking auf dem Crop-Strom. */
    const val ROI_BOX_SMOOTHING: Float = 0.5f

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

    /** Gauss-Sigma (in Sample-Frames) für die Glättung von Trajektorie und Signal (P8).
     *  4 Frames ≈ 333 ms bei 12 fps — mit der Abtastrate skaliert, damit die Glättung
     *  in Echtzeit dieselbe bleibt wie vor Stufe 1 (2 Frames bei 6 fps). */
    const val SMOOTHING_SIGMA_FRAMES: Double = 4.0

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

    /** So viele Sample-Frames muss die Abwärtsbewegung anhalten (~0,7 s bei 12 fps) —
     *  entprellt Ausschütteln und dynamische Züge (P5-Grenzfälle). */
    const val FALL_MIN_DOWNWARD_FRAMES: Int = 8

    /** Fade-out-Dauer des Skeletts am Abbruchpunkt (P4c: Abbruch als Feature). */
    const val ABORT_FADE_MS: Long = 800L
}
