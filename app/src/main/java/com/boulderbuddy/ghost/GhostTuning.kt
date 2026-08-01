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

    /** MediaPipe-Personen-Detektionsschwelle. Default 0.5 belassen — tiefer lädt
     *  Zuschauer/Fehldetektionen ein (der ROI-Crop hält die Analyse ohnehin an der
     *  Person). */
    const val MP_MIN_DETECTION_CONFIDENCE: Float = 0.5f

    /** MediaPipe-Präsenz-Schwelle je Landmark. 7.5c: 0.3 (unter Default 0.5), damit
     *  Glieder auch in unsicheren Kletterposen ausgegeben statt unterdrückt werden —
     *  die nachgelagerten Filter (Plausibilität/Hysterese) sortieren Unplausibles. */
    const val MP_MIN_PRESENCE_CONFIDENCE: Float = 0.3f

    /** MediaPipe-Tracking-Schwelle (VIDEO-Modus): ab welcher Tracking-Confidence der
     *  interne Tracker die Person als verloren betrachtet und neu detektiert. 7.5c:
     *  0.3 (unter Default 0.5), damit der Tracker bei kurzen Unsicherheiten dranbleibt
     *  statt ganz abzureißen (Ganzkörper-Blinken). */
    const val MP_MIN_TRACKING_CONFIDENCE: Float = 0.3f

    // --- Zeitliche Stabilisierung (Stufe 1, 7.5b) ------------------------------

    /** One-Euro: Grund-Cutoff in Hz — je niedriger, desto ruhiger steht das Skelett
     *  bei statischem Griff (aber träger). 7.5c-Experiment: 1.5 — höherer Cutoff als
     *  der ursprüngliche 1.0, damit das Skelett reaktiver bleibt; der zuvor getestete
     *  0.5 machte die Bewegung unnatürlich träge. Mit den übrigen Fixes ausprobieren. */
    const val ONE_EURO_MIN_CUTOFF_HZ: Double = 1.5

    /** One-Euro: Geschwindigkeitsanteil des Cutoffs — je höher, desto weniger Lag
     *  bei schnellen Zügen (dafür weniger Glättung in Bewegung). 7.5c-Experiment:
     *  0.015 — leicht unter dem ursprünglichen 0.02, die Reaktivität trägt schon der
     *  höhere Grund-Cutoff. */
    const val ONE_EURO_BETA: Double = 0.015

    /** One-Euro: Cutoff der Ableitungs-Glättung in Hz (Standardwert des Papers). */
    const val ONE_EURO_DERIV_CUTOFF_HZ: Double = 1.0

    /** Fehlt ein Landmark länger als so viele Sample-Frames, startet sein Filter neu,
     *  statt die Position vom veralteten Zustand "nachzuziehen". */
    const val FILTER_RESET_GAP_FRAMES: Int = 3

    /** Zeichen-/Einblendschwelle (7.5c): ab dieser visibility wird ein Landmark
     *  GEZEICHNET — bewusst unter [MIN_LANDMARK_CONFIDENCE] (0.5), weil BlazePose für
     *  sichtbare Kletter-Gliedmaßen oft nur ~0.3–0.45 meldet und diese sonst fehlen.
     *  Getrennt von der GEOMETRISCHEN Vertrauensschwelle (0.5, für Skala/Homographie/
     *  Plausibilität), die höher bleibt. */
    const val VISIBILITY_SHOW_THRESHOLD: Float = 0.3f

    /** Hysterese-Ausblendschwelle: ein SICHTBARES Landmark bleibt sichtbar, solange
     *  visibility ≥ diesem Wert — erst darunter beginnt der Ausblend-Zähler. 7.5c:
     *  0.3 → 0.2, damit schwach erkannte Glieder nicht vorzeitig verschwinden. */
    const val VISIBILITY_HIDE_THRESHOLD: Float = 0.2f

    /** So viele Sample-Frames in Folge muss visibility unter der Ausblendschwelle
     *  liegen, bevor das Landmark verschwindet (~250 ms bei 12 fps) — verhindert
     *  das Ein-/Ausflackern am Schwellwert (Root Cause B). */
    const val VISIBILITY_HIDE_FRAMES: Int = 3

    /** Maximale Länge (in Sample-Frames) einer zeitlichen Landmark-Lücke, die offline
     *  per Interpolation zwischen den umgebenden sicheren Vorkommen gefüllt wird.
     *  S2c: 8 → 4 (~0,33 s). Über eine längere Lücke ist die erfundene Bewegung
     *  zwangsläufig linear, die echte aber nicht — sichtbar als Glied, das der
     *  Bewegung vorauseilt oder nachhinkt. Längere Ausfälle bleiben leer. */
    const val MAX_GAP_FILL_FRAMES: Int = 4

    // --- Detektionsqualität (Stufe 2, 7.5b) ------------------------------------

    // --- Rigide Rekonstruktion (S2a, 7.5e) -------------------------------------
    //
    // Gemessen: Morph (Streuung der auf die Körpergröße normierten Knochenlängen) lag
    // bei 21–23 %, die alte Filterkette senkte sie um ganze 13 %. Ursache: der alte
    // Knochen-Check maß ABSOLUTE Pixellängen gegen einen Track-Median — die schwanken
    // aber legitim mit Perspektive und Abstand, weshalb die Toleranz auf 1,5 stehen
    // musste und praktisch nichts mehr fing.
    //
    // Die Grenzen sind bewusst ASYMMETRISCH, und das ist der geometrische Kern: die
    // Projektion eines Knochens kann durch Verkürzung nur KÜRZER werden als der echte
    // Knochen, niemals länger. Ein zu langer Knochen ist deshalb immer Halluzination
    // (hart klemmen), ein zu kurzer meist echte Verkürzung (großzügig lassen).

    /** Obergrenze der Knochenlänge als Vielfaches der Soll-Länge (Soll = Median-
     *  Verhältnis zur Körpergröße · Körpergröße dieses Frames). Eng, weil eine
     *  Überlänge geometrisch nicht vorkommen kann. */
    const val RIGID_MAX_FACTOR: Float = 1.1f

    /** Untergrenze — weit, damit ein zur Kamera zeigendes Glied natürlich verkürzt
     *  bleiben darf statt auf volle Länge herausgezogen zu werden. */
    const val RIGID_MIN_FACTOR: Float = 0.35f

    /** Halbe Fensterbreite (in Sample-Frames) des rollierenden Medians, der aus der
     *  rohen Rumpfmessung die KÖRPERGRÖSSE macht (S5b, [com.boulderbuddy.ghost.model.personScales]).
     *
     *  12 → Fenster von 25 Frames (~2 s). Breit genug, dass eine Drehung des Kletterers
     *  (typisch unter einer Sekunde, verkürzt alle Rumpfkanten projiziert) die Referenz
     *  nicht mitzieht; schmal genug, dass die echte, langsame Größenänderung durch
     *  Abstand zur Kamera weiter mitläuft.
     *
     *  Diese Konstante gilt für Rekonstruktion UND Kennzahlen — dass beide dieselbe
     *  Referenz benutzen, ist wichtiger als ihr genauer Wert.
     *
     *  Das Restrisiko ist einseitig, und zwar in die harmlose Richtung: ändert sich die
     *  Größe schneller, als das Fenster folgt, lag die Referenz. Bei einer SCHRUMPFENDEN
     *  Person liegt sie dann zu hoch, die Soll-Längen werden zu großzügig und es wird
     *  einfach nicht geklemmt. Nur eine schnell WACHSENDE Person (Annäherung an die
     *  Kamera) würde fälschlich gekürzt — und bei fester Kamera und einem Kletterer,
     *  der die Wand hochgeht, ist das der seltene Fall. */
    const val PERSON_SCALE_WINDOW: Int = 12

    /** Durchläufe der rigiden Rekonstruktion. Seit die Rumpfkanten mitkorrigiert werden
     *  (S5a) ist das ein echtes Fixpunkt-Verfahren und kein einmaliges Klemmen mehr:
     *  die vier Rumpfkanten teilen sich Endpunkte (eine Korrektur der Schulterbreite
     *  verändert beide Rumpfseiten), und die Körpergröße — der Maßstab ALLER Sollwerte —
     *  wird ihrerseits aus dem Rumpf gemessen. Jeder Durchlauf rechnet Sollwerte und
     *  Maßstab neu; das konvergiert, und offline kostet es nichts. */
    const val RIGID_ITERATIONS: Int = 3

    /** Toleranzfaktor der Knochenlängen-Konstanz (Alt-Wert, nur noch als Rückfall für
     *  Frames ohne messbare Körpergröße — dort fehlt der Bezug für [RIGID_MAX_FACTOR]). */
    const val BONE_LENGTH_TOLERANCE_FACTOR: Float = 1.5f

    /** Geschwindigkeitslimit pro Gelenk in Frame-Höhen pro Sekunde — schnellere
     *  "Sprünge" sind physisch unmöglich (Wandausschnitt ≈ 3–4 m) und werden
     *  verworfen. Empirisch zu kalibrieren. */
    const val MAX_LANDMARK_SPEED_FRAME_HEIGHTS_PER_S: Float = 2.5f

    /** L/R-Konsistenz: getauscht wird nur, wenn die überkreuzte Zuordnung klar
     *  besser ist (crossed < margin · straight) — verhindert Daueroszillation
     *  bei nahezu symmetrischen Posen. */
    const val LR_SWAP_MARGIN: Float = 0.8f

    // --- ROI-Crop (A1, 7.5e) ---------------------------------------------------
    //
    // Die Box für den nächsten Frame entsteht aus den Landmarks, die IM VORIGEN CROP
    // erkannt wurden — ein Regelkreis. Ohne Gegenmaßnahmen ist er positiv rückgekoppelt:
    // eine zu kleine Box schneidet Gliedmaßen ab, die nächste Box wird davon noch
    // kleiner, das Skelett kollabiert. Die Konstanten hier sind genau die Bremsen dagegen:
    // Mindestgröße an der KÖRPERGRÖSSE (nicht an der Box), Schrumpf-/Sprung-Limit und
    // ein periodischer Vollbild-Reset als Ausstieg aus einem eingelaufenen Fehler.

    /** ROI-Crop: Erweiterung je Seite als Anteil der Körpergröße (NICHT der Box —
     *  eine geschrumpfte Box würde sich sonst selbst immer weiter einschnüren). */
    const val ROI_EXPAND_BODY_FRACTION: Float = 0.6f

    /** ROI-Crop: die LANGE Boxseite misst mindestens so viele Körpergrößen. Die
     *  Körpergröße ist etwa eine Schulterbreite, eine ganze Person also grob 4 davon —
     *  5 deckt sie mit Reserve ab, selbst wenn nur der Rumpf erkannt wurde. Das ist
     *  die eigentliche Garantie gegen den Kollaps. */
    const val ROI_MIN_BODY_MULTIPLE: Float = 5.0f

    /** ROI-Crop: … und mindestens dieser Anteil der kurzen Frame-Seite — zweite,
     *  körpergrößen-unabhängige Untergrenze für den Fall einer Fehlmessung. */
    const val ROI_MIN_FRAME_FRACTION: Float = 0.3f

    /** ROI-Crop: minimale Präsenz eines Landmarks, damit es die Box aufspannen darf.
     *  visibility allein reicht nicht — MediaPipe meldet auch für erfundene Positionen
     *  hohe visibility, presence trennt "verdeckt" von "halluziniert". */
    const val ROI_MIN_PRESENCE: Float = 0.5f

    /** ROI-Crop: so viel darf die Boxfläche gegenüber dem Vorframe höchstens schrumpfen,
     *  sonst wird die neue Box verworfen und die alte behalten (Kollaps-Bremse). */
    const val ROI_MAX_SHRINK_PER_FRAME: Float = 0.75f

    /** ROI-Crop: maximaler Sprung des Box-Zentrums pro Frame als Vielfaches der
     *  Körpergröße — darüber ist die Box auf etwas anderes gesprungen. */
    const val ROI_MAX_CENTER_JUMP_BODY_FRACTION: Float = 1.5f

    /** ROI-Crop: so viele VERWORFENE Boxen in Folge, bevor die Box überprüft und neu
     *  verankert wird (S4b). Zuerst löste jede einzelne Verwerfung eine Neuverankerung
     *  aus — gemessen 74 davon auf 365 Frames. Eine einzelne Verwerfung ist normale
     *  Rauschabwehr; erst mehrere in Folge heißen, dass die Box wirklich festhängt. */
    const val ROI_REANCHOR_AFTER_REJECTS: Int = 3

    /** ROI-Crop: alle so vielen Sample-Frames wird die Box überprüft (~1 s bei 12 fps).
     *  Ohne diese Prüfung bliebe ein einmal eingelaufener Box-Fehler bis zum Videoende
     *  bestehen.
     *
     *  S7b: hieß bis hierher ROI_FULL_FRAME_INTERVAL_FRAMES, weil die Prüfung eine
     *  VOLLBILD-Detektion war, deren Landmarks in die Spur gingen. Genau das war die
     *  Hauptursache des sichtbaren Wackelns — siehe [ROI_CHECK_WIDEN_FACTOR]. Der Takt
     *  bleibt, seine Wirkung auf die Spur ist weg. */
    const val ROI_BOX_CHECK_INTERVAL_FRAMES: Int = 12

    /**
     * ROI-Crop: Vielfaches, auf das die Box für die periodische PRÜFUNG geweitet wird
     * (S7b). Der Prüf-Crop kommt weiterhin aus der VOLLEN Auflösung.
     *
     * Das ersetzt den früheren Vollbild-Reset, und der Grund ist gemessen: im 720er-
     * Vollbild ist die Schulterbreite des Kletterers ~57 px, im Crop füllt er das
     * Eingabebild. Zwei so verschiedene Arbeitspunkte liefern systematisch verschiedene
     * Landmarks — im Wechsel 11:1 ergab das eine Rechteckstörung bei exakt 1 Hz: das
     * Skelett sprang einmal pro Sekunde um 17 % der Körpergröße heraus und zurück (nach
     * der Filterkette noch 3,7 %; One-Euro lässt sie mit einem Grund-Cutoff von 1,5 Hz
     * ungehindert durch). Eine periodische Störung liest das Auge als Fehler, gleich
     * starkes zufälliges Rauschen dagegen als Bildrauschen.
     *
     * 2,0 ist der Kompromiss: weit genug, dass eine verrutschte Box die Person wieder
     * ganz enthält und der Fehler auffliegt; eng genug, dass der Maßstabsunterschied
     * klein bleibt. Die Prüf-Landmarks gehen ohnehin nicht in die Spur — die Weitung
     * muss die Person nur FINDEN, nicht genau vermessen.
     */
    const val ROI_CHECK_WIDEN_FACTOR: Float = 2.0f

    // --- Totband der Box-Prüfung (S8b) -----------------------------------------
    //
    // Die Prüfung hat ihre Box anfangs IMMER übernommen, auch wenn sie mit der laufenden
    // praktisch übereinstimmte. Damit blieb ein Rest der alten Störung übrig: die Box
    // sprang im Prüftakt auf einen leicht anderen Ausschnitt, und der FOLGEframe wurde
    // dort erkannt — gemessen als Faktor 1,8 gegenüber dem Grundrauschen, an genau
    // dieser Phase. Eine Prüfung, die immer eingreift, ist keine Prüfung mehr.
    //
    // Beide Grenzen beziehen sich auf die GEPRÜFTE Box, nicht auf die laufende: die
    // geprüfte stammt aus dem weiten Blick und ist hier die verlässlichere Referenz —
    // eine verrutschte Box würde sich sonst selbst zum Maßstab machen.

    /** Box-Prüfung: bis zu diesem Anteil der Boxbreite gilt ein Versatz des Zentrums als
     *  normales Rauschen und die laufende Box bleibt. Die Box ist mit
     *  [ROI_MIN_BODY_MULTIPLE] deutlich größer als die Person — 15 % davon sind noch
     *  weit davon entfernt, jemanden aus dem Bild zu schieben. */
    const val ROI_CHECK_MAX_CENTER_DRIFT: Float = 0.15f

    /** Box-Prüfung: so weit darf die laufende Boxbreite von der geprüften abweichen
     *  (nach oben wie nach unten), bevor neu verankert wird. */
    const val ROI_CHECK_MAX_SIZE_RATIO: Float = 1.25f

    /** ROI-Crop: so viele sichere Landmarks braucht die Box, sonst nächster Frame
     *  wieder als Vollbild (Person verloren → neu suchen). */
    const val ROI_MIN_CONFIDENT_LANDMARKS: Int = 6

    /** ROI-Crop: Glättung der Box über die Zeit (0 = einfrieren, 1 = sofort springen) —
     *  ruhige Box hilft dem internen MediaPipe-Tracking auf dem Crop-Strom. */
    const val ROI_BOX_SMOOTHING: Float = 0.5f

    // Die Referenzgröße der Pose-Gates ist [PERSON_SCALE_WINDOW] — dieselbe wie in
    // Rekonstruktion und Kennzahlen. Hier stand bis 7.5e eine zweite Fensterbreite (15)
    // mit eigener Median-Implementierung; getrennte Referenzen für dieselbe Größe waren
    // zweimal die Ursache für Fehldiagnosen.

    /** Pose-Skalen-Gate: untere Grenze der Körpergröße als Anteil des ROLLIERENDEN
     *  Median. 0.8 statt vorher 0.7 gegen den globalen Median — gemessen streute die
     *  Körpergröße um ~10 %, ein Fenster von −30 %/+70 % ließ praktisch alles durch. */
    const val POSE_SCALE_MIN_RATIO: Double = 0.8

    /** Pose-Skalen-Gate: obere Grenze der Körpergröße (aufgeblähte Fehl-Pose). */
    const val POSE_SCALE_MAX_RATIO: Double = 1.25

    /** Pose-Positions-Gate: max. Abweichung des Pose-Zentrums vom Fenster-Median als
     *  Anteil der Median-Körpergröße, bevor der Frame als „verschobenes Skelett" gilt.
     *  0.6 statt 1.0: eine ganze Körpergröße Versatz ist genau das sichtbare „Skelett
     *  zuckt vom Körper weg" — das darf nicht erst DANACH auffallen. Glatte schnelle
     *  Bewegungen bleiben verschont, weil die Referenz der Fenster-Median ist und eine
     *  glatte Bewegung nahe an ihm liegt. */
    const val POSE_SHIFT_MAX_RATIO: Double = 0.6

    /** Maximale Länge (in Sample-Frames) einer ungültigen Strecke, die noch per
     *  Interpolation überbrückt wird (S2c, ~0,5 s bei 12 fps). Vorher unbegrenzt: ein
     *  zwei Sekunden langer Aussetzer wurde zu einer zwei Sekunden langen linearen
     *  Rampe — sichtbar als Skelett, das der Bewegung hinterherhinkt oder ihr
     *  vorauseilt. Längere Strecken bleiben jetzt leer; ein fehlendes Skelett ist
     *  ehrlicher als ein erfundenes. */
    const val MAX_POSE_INTERPOLATION_FRAMES: Int = 6

    /** Halbe Fensterbreite (in Sample-Frames) des Zentrum-Medians fürs Positions-Gate.
     *  2 = Fenster von 5 Frames — robust gegen einen bis zwei Ausreißer. Bewusst KURZ:
     *  ein längeres Fenster würde eine legitime, anhaltende Aufwärtsbewegung als
     *  Abweichung werten. Anhaltende Versätze fängt stattdessen das Ruck-Gate.
     *
     *  Nicht zu verwechseln mit [PERSON_SCALE_WINDOW]: hier geht es um das Zentrum der
     *  Pose (bewegt sich ständig), dort um ihre Größe (ändert sich nur langsam). */
    const val POSE_SHIFT_MEDIAN_WINDOW: Int = 2

    /** Ruck-Gate (S3a, 7.5e): maximaler Vorhersagefehler des Pose-Zentrums als Anteil
     *  der Körpergröße. Vorhergesagt wird konstant-geschwindigkeit aus den zwei
     *  Vorframes, gemessen wird also BESCHLEUNIGUNG — und genau darin unterscheiden
     *  sich die beiden Fälle, die das Weg-basierte Positions-Gate nicht trennen konnte:
     *  ein echter schneller Zug ist schnell, aber GLATT (Vorhersagefehler ~0,1–0,15
     *  Körpergrößen), ein Wegzucken ist per Definition ein Beschleunigungs-Ausschlag
     *  (~0,5). 0.35 liegt dazwischen, mit Reserve nach beiden Seiten. */
    const val POSE_JERK_MAX_RATIO: Double = 0.35

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

    // --- Zeit-Mapping / Warp-Glättung (S1, 7.5d) -------------------------------
    //
    // Der DTW-Pfad ist eine Treppe: bei fast gleichem Tempo warpt das DTW nur noch
    // das Rauschen im Fortschrittssignal weg (Singularities — ein Ref-Frame auf viele
    // Cmp-Frames). Die daraus gebaute Warp-Funktion hat dann Plateaus (Geist steht)
    // und Sprünge (Geist rast) — genau der "laggy" Eindruck im Overlay. Die drei
    // Stellschrauben hier zähmen die Warp-Funktion NACH dem DTW; die Ursache im
    // Kostenmaß selbst behandelt S2 (Dead-Band + Warp-Penalty).

    /** Gauss-Sigma (in Referenz-Sample-Frames) für die Glättung der Warp-Funktion.
     *  4 Frames ≈ 333 ms bei 12 fps — dieselbe Zeitkonstante wie die übrige Glättung,
     *  genug um die Treppenstufen zu verschleifen, zu wenig um eine echte Pause
     *  (typisch mehrere Sekunden) zu verwischen. */
    const val WARP_SMOOTHING_SIGMA_FRAMES: Double = 4.0

    /** Anteil der LINEAREN Zeitachse im Mapping (0 = pur DTW, 1 = pur lineares
     *  Strecken). Konvexkombination zweier monotoner Funktionen — Monotonie bleibt
     *  automatisch erhalten. Der eigentliche "Weichheits-Regler": höher = ruhiger,
     *  aber ungenauer bei echten Tempo-Unterschieden. */
    const val WARP_LINEAR_BLEND: Double = 0.35

    /** Untergrenze der lokalen Warp-Steigung dCmp/dRef, als Vielfaches des GLOBALEN
     *  Tempo-Verhältnisses beider Versuche (relativ, damit unterschiedlich lange Videos
     *  dieselbe Grenze bekommen). > 0 heißt: der Geist bleibt NIE stehen, egal was das
     *  DTW behauptet. */
    const val WARP_MIN_SLOPE: Double = 0.4

    /** Obergrenze der lokalen Warp-Steigung — der Geist "beamt" nicht nach vorn.
     *  Beide Grenzen sind bewusst weit: sie sind ein Sicherheitsnetz gegen Ausreißer,
     *  die Grundruhe liefern Glättung + Blend. */
    const val WARP_MAX_SLOPE: Double = 2.0

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
