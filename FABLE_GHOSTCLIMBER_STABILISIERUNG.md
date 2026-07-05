# 🩺 Fable-5 — Ghost Climber: Skelett-Erkennung stabilisieren (7.5b)

> **Auftrag:** Die Pose-/Skelett-Erkennung im Ghost Climber ist funktional, aber **visuell buggy
> und für eine echte Analyse unbrauchbar** (Zittern, springende Knochen, verdrehte/vertauschte
> Glieder, Blinken). Dieses Dokument diagnostiziert die Ursachen an **echtem Videomaterial** und gibt
> einen priorisierten Umbauplan. Es ergänzt [`FABLE_GHOSTCLIMBER_START.md`](FABLE_GHOSTCLIMBER_START.md)
> (Tech-Stack, Repo-Fakten, Arbeitsweise gelten unverändert).

---

## 0. Wie diese Diagnose entstand (Vertrauensbasis)

Die On-Device-Pipeline (ML Kit + Play Services) ließ sich **nicht in der Entwicklungsumgebung
ausführen** — dafür braucht es ein Android-Gerät. Stattdessen wurde die stärkste mögliche
Ersatzanalyse gemacht:

1. **Container beider Referenzvideos exakt geparst** (MOV-Atome) — liefert die tatsächlichen
   Eingabeparameter der Pipeline.
2. **Echte Frames aus beiden Videos extrahiert und visuell inspiziert** (OpenCV-Decode, gleiche
   Display-Orientierung wie ExoPlayer/MediaMetadataRetriever).
3. **Kompletter Code-Pfad durchgelesen**: `VideoPoseExtractor` → `scaledFrameAt` → `GhostPoseTrack`
   → `landmarksAt` → `drawSkeletonOverlay` und die Player-Composables.

ML Kit „accurate" ist intern **BlazePose (Full/Heavy)** — dieselbe Modellfamilie wie MediaPipe. Die
aus den Frames abgeleiteten Schwächen gelten also 1:1 für die App.

### Fakten zu den Referenzvideos (`Referenz.MOV`, `Vergleich.MOV`)

| Eigenschaft | Wert | Konsequenz für die Pose-Pipeline |
|---|---|---|
| Codec | **H.264 (avc1)** | Gut dekodierbar, kein HEVC-Problem. |
| Kodierte Auflösung | **1920×1080 quer**, `rotation = 90°` | Anzeige = **Portrait 1080×1920**. `getScaledFrameAtTime` **muss** rotieren (Risiko, s. Stufe 4). |
| Framerate | **30 fps** | Aktuelle Abtastung mit **6 fps** verwirft 4 von 5 Frames → Aliasing bei dynamischen Zügen. |
| Dauer / Frames | ~41 s / ~1230 Frames | Bei 6 fps ≈ 247 Inferenzen pro Video. |
| Kamera | **statisch/Stativ**, identische Perspektive in beiden Videos | Gut für Homographie & Side-by-Side. |
| Subjekt-Größe | Kletterer-Box ~300 px breit im 1080-Frame → **bei 720-px-Analyse (≈405 px breit) nur ~112 px** | An der Grenze von BlazePose, oben an der Wand noch kleiner. |
| Pose-Charakter | Rücken/seitlich zur Kamera, **Überkopf-Reaches**, Füße hoch, Körper an Wand gepresst; **graues Shirt vor grauer Wand** (niedriger Kontrast) | **Out-of-Distribution** für BlazePose → Links/Rechts-Vertauschungen, halluzinierte verdeckte Glieder. |

Die Alignment-/Rotations-Mathematik im Overlay ist **im Grundsatz korrekt** (Analyse-Frame und
PlayerView haben dasselbe Seitenverhältnis 9:16). Das „Buggy"-Problem ist **kein Mapping-Fehler**,
sondern **Pose-Qualität + fehlende zeitliche Stabilisierung**.

---

## 1. Root-Cause-Analyse (nach Hebelwirkung sortiert)

### A. Keine zeitliche Glättung/Filterung der Landmarks ⭐ Hauptursache des „Zitterns"
- [`VideoPoseExtractor.kt:49`](app/src/main/java/com/boulderbuddy/ghost/pose/VideoPoseExtractor.kt) nutzt
  **`SINGLE_IMAGE_MODE`** → jede Pose wird unabhängig geschätzt, **null zeitliche Konsistenz**.
  BlazePose wackelt pro Frame um mehrere Pixel.
- **Nirgends** ein One-Euro-/Kalman-Filter. Der rohe Jitter wird gecacht und geht direkt aufs Overlay.

### B. 6-fps-Abtastung + fragile Interpolation → Blinken & „Teleportieren"
- `POSE_SAMPLE_FPS = 6` bei 30-fps-Material: schnelle Züge liegen zwischen zwei Samples → lineare
  Interpolation schneidet Ecken ab.
- **Blink-Bug** in [`GhostPose.kt:69-77`](app/src/main/java/com/boulderbuddy/ghost/model/GhostPose.kt):
  `landmarksAt` **verwirft ein Landmark komplett**, sobald es in **einem** der beiden Nachbar-Frames
  fehlt (`byType[la.type] ?: return@mapNotNull null`). Ein einzelner Aussetzer löscht den Knochen für
  das ganze 166-ms-Intervall.
- **Confidence-Kollaps**: interpolierte Confidence = `min(a, b)`. Dippt ein Nachbar unter `0.5`,
  verschwindet das Landmark für das ganze Intervall (harte Schwelle in
  [`SkeletonDraw.kt:32`](app/src/main/java/com/boulderbuddy/ui/components/SkeletonDraw.kt)). → sichtbares Flackern.

### C. `inFrameLikelihood` ist **keine** Positionsgenauigkeit
- Gespeichert als `confidence` ([`VideoPoseExtractor.kt:85`](app/src/main/java/com/boulderbuddy/ghost/pose/VideoPoseExtractor.kt)),
  überall als Qualitätsmaß behandelt. `inFrameLikelihood` sagt nur *„Landmark ist im Bild"*, **nicht**
  *„Position stimmt"*. Verdeckte Glieder (an der Wand, gekreuzt) bekommen hohe Likelihood **an
  halluzinierter Position** → Knochen springen ins Leere, obwohl der Filter sie durchlässt.
- ML-Kit-Base liefert **kein** echtes per-Punkt-`visibility`. Das ist eine Bibliotheks-Grenze (s. Stufe 3).

### D. Kletterposen sind Out-of-Distribution → Links/Rechts-Flips
- In den echten Frames (50–90 % Fortschritt): Kletterer **rückseitig/seitlich**, Arme über Kopf,
  Körper an Wand. BlazePose ist auf aufrechte Fitness-Posen trainiert → **vertauscht Links/Rechts**
  und **flippt** Detektionen. Das erklärt die „verdrehten/verhedderten" Skelette.
- Keinerlei zeitliche Links/Rechts-Konsistenz oder anatomische Plausibilitätsprüfung vorhanden.

### E. Kleines Subjekt bei 720-px-Ganzbild-Analyse
- Ganzes Bild wird auf 720 px lange Kante skaliert; der Kletterer ist darin klein (~112 px breit,
  oben weniger). Dünne Glieder → ungenaue Extremitäten-Landmarks. **Kein ROI-Crop** auf die Person.

### F. Decode-Robustheit & Orientierung (latentes Risiko)
- `getScaledFrameAtTime(..., OPTION_CLOSEST, ...)` ([`ScaledFrames.kt:21`](app/src/main/java/com/boulderbuddy/ghost/video/ScaledFrames.kt))
  ist **pro Frame teuer** (~247×) und **wendet auf manchen Geräten die Rotation der skalierten
  Variante nicht an** → dann liegt der Analyse-Frame quer, das Skelett erscheint 90° verdreht/gestaucht.
  Aktuell (beide Videos rot=90) läuft es offenbar, ist aber ungeschützt.
- Nicht dekodierbare Frames werden zu `landmarks = emptyList()` → zusätzliche Lücken/Blinken.

### G. Mehrpersonen/Spotter
- ML Kit Single-Pose greift die **prominenteste** Person. Läuft ein Spotter/Bystander ins Bild,
  springt das Skelett auf die falsche Person. In diesen beiden Clips kein Spotter sichtbar, generell
  aber abzusichern (ROI-Tracking bindet an den Kletterer).

---

## 2. Umbauplan für Fable (priorisiert, jede Stufe = grüner Build + Commit)

> Reihenfolge nach **Hebel/Aufwand**. Stufe 0 zuerst, Stufe 1 bringt den größten sichtbaren Sprung
> bei kleinstem Risiko. Stufe 3 ist optional (größter Qualitätssprung, aber Modellwechsel).

### Stufe 0 — Messbarkeit herstellen (Pflicht-Vorstufe)
Ohne Baseline ist „besser" nicht verifizierbar.
- **Debug-Overlay/-Toggle** im Preview-Player: rohe vs. gefilterte Keypoints, plus Text-HUD mit
  Dropout-Rate und mittlerer Confidence pro Sekunde.
- **Regressions-Fixtures:** die beiden Referenzvideos als feste Testvideos verankern. **Nicht** die
  73-MB-MOVs in git — stattdessen je einen **~5-s-Ausschnitt (On-Wall-Teil)** als kleines Fixture
  ablegen und den Pfad dokumentieren. (Originale liegen unter `…/Downloads/Videos/Referenz.MOV`,
  `Vergleich.MOV`.)
- **Kennzahlen** definieren, an denen jede weitere Stufe gemessen wird:
  Jitter (mittlere Frame-zu-Frame-Verschiebung stabiler Gelenke bei ruhigem Griff),
  Dropout-Quote pro Landmark, Anteil Frames mit Links/Rechts-Flip.

### Stufe 1 — Zeitliche Stabilität ⭐ (größter Effekt, kleinster Aufwand)
1. **Abtastrate hoch** auf **12–15 fps** (`GhostTuning.POSE_SAMPLE_FPS`). Analyse-Zeit steigt linear
   — durch Stufe 4 (schnellerer Decode) kompensierbar; ggf. Base-Modell testen.
2. **`STREAM_MODE`** statt `SINGLE_IMAGE_MODE` (Frames werden ohnehin in aufsteigender Zeit
   sequenziell verarbeitet → ML Kit kann intern tracken/glätten).
3. **One-Euro-Filter pro Landmark** im Analyse-Frame-Raum, angewandt **vor dem Cachen** in den
   `GhostPoseTrack`. (One-Euro ist der Standard für Echtzeit-Pose-Glättung: wenig Lag bei schneller
   Bewegung, starke Glättung bei Ruhe. ~40 Zeilen Kotlin, keine Dependency.)
4. **`landmarksAt`-Blink-Fix** ([`GhostPose.kt`](app/src/main/java/com/boulderbuddy/ghost/model/GhostPose.kt)):
   ein in genau einem Nachbar fehlendes Landmark **nicht verwerfen**, sondern kurz halten/extrapolieren;
   nur bei längeren Lücken ausblenden.
5. **Hysterese statt harter 0.5-Schwelle**: Landmark erst nach *N* Frames unter Schwelle ausblenden,
   sofort wieder einblenden — verhindert das Ein-/Ausflackern am Schwellwert.

### Stufe 2 — Detektionsqualität
6. **ROI-Crop-Tracking:** letzte bekannte Personen-Bounding-Box (aus den Landmarks) um ~20 % erweitern,
   nächsten Frame **auf diesen Crop** croppen und Pose darauf rechnen → effektiv höhere Subjekt-
   Auflösung; Ergebnis in den Vollbild-Frame-Raum zurückmappen. Bindet zugleich an den Kletterer (löst G).
7. **Anatomische Plausibilität** als Post-Filter: Knochenlängen-Konstanz (Ober-/Unterarm-Länge sollte
   über die Zeit stabil bleiben) und Geschwindigkeitslimit pro Gelenk → verwirft physisch unmögliche
   Sprünge (löst C/D teilweise ohne Modellwechsel).
8. **Links/Rechts-Konsistenz** über die Zeit erzwingen (Zuordnung, die Sprünge minimiert), gegen die
   BlazePose-Flips.

### Stufe 3 — Modellwechsel MediaPipe ✅ ENTSCHIEDEN (wird gebaut, VOR Stufe 1)
9. **Migration ML Kit → MediaPipe Tasks „Pose Landmarker"** — vom Nutzer entschieden (2026-07-05).
   Gleiches BlazePose, aber: echtes **`visibility` + `presence` pro Landmark** (ersetzt den
   `inFrameLikelihood`-Missbrauch, Root Cause C), **World-Landmarks** (3D), **Segmentierungsmaske**
   (bessere Verdeckungs-Behandlung), **wählbares Modell** (Lite/Full/Heavy).
   - Dependency `com.google.mediapipe:tasks-vision`; Modell-Asset `pose_landmarker_full.task` (oder
     `_heavy`) in `app/src/main/assets/` (MediaPipe lädt **nicht** selbst nach).
   - `PoseLandmarker` mit **`RunningMode.VIDEO`** + `detectForVideo(img, timestampMs)` → internes
     Tracking, ersetzt das ML-Kit-`STREAM_MODE`.
   - `GhostLandmark` um `presence` erweitern, `confidence` = `visibility` weiterführen.
   - **Reihenfolge:** kommt **vor** Stufe 1, weil die Confidence-Semantik sich ändert und die
     Hysterese-Schwelle darauf aufbaut. Modellwahl (full/heavy) in `Code-Entscheidungen.md` festhalten.
   *(Anhang A.1 sah MediaPipe ursprünglich sogar vor — der Wechsel ist eine Rückkehr zum Ursprungsplan.)*

### Stufe 4 — Decode-Robustheit & Performance
10. **Orientierungs-Guard** in `scaledFrameAt`: Bitmap-Seitenverhältnis gegen
    `METADATA_KEY_VIDEO_ROTATION` + `…_WIDTH/HEIGHT` prüfen und bei Mismatch selbst rotieren
    (schützt gegen das 90°-Geräte-Problem, F).
11. **Batch-Decode über `MediaCodec`/`MediaExtractor` auf eine Surface** statt ~247× einzelnem
    `getScaledFrameAtTime(OPTION_CLOSEST)` → deutlich schneller, macht die höhere Abtastrate aus
    Stufe 1 bezahlbar; robusteres Null-Frame-Handling.

---

## 3. Akzeptanzkriterien (an den Referenzvideos)
- Skelett **steht ruhig** bei statischem Griff (kein sichtbares Zittern der Gelenke).
- **Kein Blinken** einzelner Knochen zwischen zwei Samples.
- Arme/Beine **nicht dauerhaft vertauscht** in den Überkopf-/Rückenphasen.
- Overlay bleibt bei allen getesteten Geräteorientierungen **deckungsgleich** mit dem Video.
- Analyse eines 40-s-Clips bleibt in vertretbarer Zeit (Zielwert in Stufe 0 festlegen).

## 4. Was **nicht** angefasst werden muss
- Homographie, DTW, Zeitmapping, Modus-Vorschlag, Sturz-Erkennung, Persistenz (M2–M5) — die sind
  **nicht** die Ursache des „Buggy"-Eindrucks. Bessere/ruhigere Keypoints verbessern diese Stufen
  automatisch mit (sauberere Trajektorie → besseres Signal → besseres DTW).
- Die Overlay-Mapping-Mathematik in `drawSkeletonOverlay` ist korrekt.
