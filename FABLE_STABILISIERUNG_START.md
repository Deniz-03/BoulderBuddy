# 🏁 Fable-5 START — Ghost Climber Skelett-Stabilisierung (7.5b)

> **Lies zuerst diese drei Dokumente, dann leg los — du musst NICHT explorieren:**
> 1. **[`FABLE_GHOSTCLIMBER_STABILISIERUNG.md`](FABLE_GHOSTCLIMBER_STABILISIERUNG.md)** — Diagnose +
>    priorisierter Umbauplan (Stufen 0–4). **Das ist deine Hauptvorlage.**
> 2. [`FABLE_GHOSTCLIMBER_START.md`](FABLE_GHOSTCLIMBER_START.md) — Tech-Stack, Repo-Fakten,
>    Build/Arbeitsweise (gilt unverändert).
> 3. Anhang A in [`PHASE7_PLAN.md`](PHASE7_PLAN.md) — algorithmische Pipeline-Spezifikation.

---

## Auftrag

Die Pose-/Skelett-Erkennung im Ghost Climber ist funktional, sieht aber **buggy** aus (Zittern,
blinkende/springende Knochen, vertauschte Glieder) und ist so **nicht analysetauglich**. Ursache ist
**nicht** das Overlay-Mapping, sondern **Pose-Qualität + fehlende zeitliche Stabilisierung**. Details +
Root Causes stehen im Diagnose-Doc (Abschnitt 1).

## Scope dieses Auftrags (verbindlich)

| Stufe | Inhalt | Status |
|---|---|---|
| **0** | Messbarkeit: Debug-Overlay (roh vs. gefiltert), Dropout/Confidence-HUD, Fixtures, Kennzahlen | **Jetzt bauen** |
| **3** | **Wechsel ML Kit → MediaPipe Pose Landmarker** (entschieden, s.u.) | **Jetzt bauen — vor Stufe 1** |
| **1** ⭐ | Zeitliche Stabilität: 12–15 fps, One-Euro-Filter, `landmarksAt`-Blink-Fix, Hysterese über `visibility` | **Jetzt bauen** |
| **2** | ROI-Crop-Tracking, anatomische Plausibilität, Links/Rechts-Konsistenz | **Danach** |
| **4** | Orientierungs-Guard, Batch-Decode via MediaCodec | **Follow-up** |

**Bau-Reihenfolge: 0 → 3 → 1 → 2 → 4.** Der Modellwechsel (3) kommt **vor** den Filtern (1), weil er
die Confidence-Semantik ändert (echtes `visibility`/`presence` statt `inFrameLikelihood`) — die
Hysterese-Schwelle aus Stufe 1 baut darauf auf. So wird nichts doppelt gebaut. Stufe 0 muss zuerst
stehen, sonst ist der Fortschritt nicht messbar.

## Stufe 3 — MediaPipe-Migration (entschieden, verbindlich)

Ersetze ML Kit Pose durch den **MediaPipe Tasks „Pose Landmarker"** — dieselbe BlazePose-Familie, aber
mit den Bausteinen, die Stufe 1/2 brauchen:
- **Dependency:** `com.google.mediapipe:tasks-vision` (Katalog-Eintrag in `libs.versions.toml`
  ergänzen). ML-Kit-Pose-Dependencies danach entfernen.
- **Modell-Asset:** `pose_landmarker_full.task` (oder `_heavy` für Kletter-OOD-Posen) in
  `app/src/main/assets/` ablegen — anders als ML Kit lädt MediaPipe **nicht** selbst nach.
- **API:** `PoseLandmarker` mit **`RunningMode.VIDEO`** und `detectForVideo(mpImage, timestampMs)` —
  liefert die frames in Zeit-Reihenfolge mit **internem Tracking** (ersetzt das alte `STREAM_MODE`).
- **Pro Landmark jetzt `visibility` UND `presence`** → ersetzt den `inFrameLikelihood`-Missbrauch als
  Qualitätsmaß (Root Cause C). Zusätzlich verfügbar: **World-Landmarks** (3D) und **Segmentierungsmaske**.
- **Datenmodell:** `GhostLandmark` um `presence` erweitern (bestehendes `confidence`-Feld = `visibility`
  weiterführen); JSON-Artefakt-Format bleibt abwärts nicht nötig (destruktive DB-Migration, keine
  Bestandsdaten — s. `FABLE_GHOSTCLIMBER_START.md` §4).
- Entscheidung + Modellwahl (full vs. heavy) in `04 – Entwicklung/Code-Entscheidungen.md` dokumentieren.

## Konkrete erste Schritte

1. **Stufe 0 zuerst.** Baue in den Preview-/Player-Pfad einen Debug-Toggle, der rohe und gefilterte
   Keypoints gleichzeitig zeigt + ein Text-HUD mit Dropout-Quote und mittlerer Confidence/Sekunde.
   Lege die Baseline-Kennzahlen (Jitter, Dropout, Flip-Anteil) an den Fixtures fest und **notiere die
   Ausgangswerte** — daran misst du jede weitere Stufe. **Miss die Baseline noch mit ML Kit**, damit
   der MediaPipe-Effekt sichtbar wird.
2. **Dann Stufe 3** (Migration, s.o.): Dependency + Asset + `RunningMode.VIDEO` + `visibility`/`presence`
   ins Datenmodell. Ein grüner Build mit unveränderter Overlay-Optik = Migration steht.
3. **Dann Stufe 1** auf der MediaPipe-Ausgabe (jeder Punkt ein grüner Build + Commit):
   `POSE_SAMPLE_FPS` hoch → One-Euro-Filter (im Analyse-Frame-Raum, **vor** dem Cachen in
   `GhostPoseTrack`) → `landmarksAt`-Blink-Fix → Hysterese-Schwelle über `visibility`.

## Wichtige Datei-Anker (aus der Diagnose)
- Extraktion/Modus/Confidence: [`ghost/pose/VideoPoseExtractor.kt`](app/src/main/java/com/boulderbuddy/ghost/pose/VideoPoseExtractor.kt)
- Abtastrate/Schwellen: [`ghost/GhostTuning.kt`](app/src/main/java/com/boulderbuddy/ghost/GhostTuning.kt)
- Blink-/Interpolations-Bug: [`ghost/model/GhostPose.kt`](app/src/main/java/com/boulderbuddy/ghost/model/GhostPose.kt) (`landmarksAt`)
- Zeichen-/Schwellen-Filter: [`ui/components/SkeletonDraw.kt`](app/src/main/java/com/boulderbuddy/ui/components/SkeletonDraw.kt)
- Decode/Orientierung: [`ghost/video/ScaledFrames.kt`](app/src/main/java/com/boulderbuddy/ghost/video/ScaledFrames.kt)

## Was du NICHT anfasst
- Homographie, DTW, Zeitmapping, Modus-Vorschlag, Sturz-Erkennung, Persistenz (M2–M5). Ruhigere
  Keypoints verbessern diese Stufen automatisch mit.
- Die Overlay-Mapping-Mathematik in `drawSkeletonOverlay` (ist korrekt).

## Testmaterial / Fixtures
- Referenzvideos: **`Referenz.MOV`** + **`Vergleich.MOV`** — H.264, kodiert 1920×1080 mit
  `rotation=90°` → Anzeige Portrait 1080×1920, 30 fps, ~41 s, statische Kamera.
- **Nicht** die 73-MB-Originale in git committen. Lege je einen **~5-s-On-Wall-Ausschnitt** als
  kleines Test-Fixture ab und dokumentiere den Pfad.
- Pose-Qualität lässt sich nur **auf einem echten Android-Gerät** verifizieren (ML Kit braucht Play
  Services). Nutze das Debug-Overlay aus Stufe 0 zur Sichtprüfung.

## Definition of Done (an den Fixtures, s. Diagnose Abschnitt 3)
Skelett steht ruhig bei statischem Griff · kein Blinken einzelner Knochen · Arme/Beine in
Rücken-/Überkopfphasen nicht dauerhaft vertauscht · Overlay in jeder Orientierung deckungsgleich ·
Kennzahlen (Jitter/Dropout/Flip) messbar besser als die Stufe-0-Baseline.

## Arbeitsweise (wie im Projekt)
- **Build:** `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`, dann
  `& ".\gradlew.bat" :app:assembleDebug --console=plain`. AGP-9-Regeln gelten projektweit.
- **Eine Stufe/ein Teilschritt = ein grüner Build = ein Commit.**
- **Doku-Pflicht (Obsidian):** Architektur-Entscheidungen (v.a. One-Euro-Filter, evtl. MediaPipe) →
  `04 – Entwicklung/Code-Entscheidungen.md`; Stolpersteine → `04 – Entwicklung/Bugs & Fixes.md`.
