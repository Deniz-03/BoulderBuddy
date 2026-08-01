# 🏁 Fable-5 START-KONTEXT — Ghost Climber (7.5)

> **Lies NUR dieses Dokument + Anhang A in [`PHASE7_PLAN.md`](PHASE7_PLAN.md).** Es fasst alle
> Repo-Fakten und finalen Entscheidungen (Stand 2026-07-04) zusammen, damit du **nicht explorieren
> musst**. Wo dieses Dokument Anhang A widerspricht, **gilt dieses Dokument** (die Tech-Stack-
> Entscheidungen wurden nach A.1/A.2 revidiert).

---

## 1. Finale Entscheidungen (A.7 — geklärt, verbindlich)

| # | Frage | Entscheidung |
|---|-------|-------------|
| 1 | Routen-Identität | **Option 1: zwei frei gewählte Videos.** Keine `ClimbEntity`, keine geräteübergreifende Routen-Identität. Nutzer wählt Referenz + Vergleich selbst aus beliebigen `RouteEntity.mediaUri` (oder frisch importiert). |
| 2 | Videoquelle | **Nur Import.** Kein CameraX. Wiederverwenden: bestehender `PhotoPicker` (`PickVisualMedia.ImageAndVideo`, schon aus 7.3c) für die Auswahl. |
| 3 | Anker-Erfassung | **Hybrid: automatische Griff-/Feature-Detektion mit manueller Korrektur per Antippen.** Manuelles Tippen ist der **garantierte Baseline-Pfad** (muss immer funktionieren); Auto-Detektion legt Anker vor, Nutzer korrigiert/ergänzt durch Tippen. |
| 4 | CV-Bibliothek | **Kein OpenCV.** Pose via **ML Kit Pose Detection** (native Google-Lib); **Homographie selbst in Kotlin** implementieren (normalisierte DLT + optional RANSAC); `perspectiveTransform` = einfache Matrixmultiplikation, selbst schreiben. **DTW** ohnehin selbst in Kotlin. „Falls möglich" = wenn ein Teil ohne native Lib nicht sinnvoll machbar ist, dokumentiere es und schlage die schlankste Lösung vor, bevor du eine schwere native Lib ziehst. |

**Defaults für die nicht explizit gefragten A.7-Punkte:**
- **Kalibrierdaten (A.7 Q4 / A.6):** „Best effort für Demo/Abgabe". Sturz-/Modus-Schwellen als
  benannte, zentral gehaltene Default-Konstanten mit Kommentar „empirisch zu kalibrieren". Echte
  Kalibrierung nur, falls Videomaterial bereitsteht.
- **Kamera-Setup (A.7 Q6):** **feste Kamera (Stativ) angenommen.** Bewegte Kamera / Homographie pro
  Frame ist **nicht** Ziel.

---

## 2. Tech-Stack — was gilt statt Anhang A.1

| Baustein | Anhang A (alt) | **Jetzt gültig** |
|----------|----------------|------------------|
| Pose | MediaPipe `tasks-vision` | **ML Kit Pose Detection** — Dependency ist **schon eingebunden & verifiziert** (s. §3). 33 → ML Kit liefert 33 `PoseLandmark`s inkl. `InFrameLikelihood`. Pro Frame auf `InputImage`. |
| Homographie | OpenCV `findHomography` | **Selbst in Kotlin** (normalisierte DLT aus ≥4 Korrespondenzen, optional RANSAC gegen Ausreißer). |
| Punkt-Transform | OpenCV `perspectiveTransform` | **Selbst** (3×3-Matrix × homogener Punkt). |
| Auto-Anker | (n/a) | **Best effort ohne OpenCV.** Manuelles Tippen ist Pflicht-Baseline; Auto-Detektion ist additive Kür — wenn ohne native Lib zu fragil, liefere nur die manuelle Variante + dokumentiere. |
| DTW | selbst in Kotlin | unverändert selbst in Kotlin (klassisches DP, optional Sakoe-Chiba-Band). |
| Video-Frames | `MediaMetadataRetriever` / `MediaExtractor` | unverändert — **Plattform-API, keine Dependency**. |
| Wiedergabe/Overlay | Media3 + Compose `Canvas` | unverändert; **Media3 ist schon im Projekt** (7.3c). `VideoPlayer`-Composable existiert (s. §4). |

**Alles bleibt Android Native** (Kotlin + Jetpack + Google-ML-Kit). Kein Cross-Platform, keine
„Problem-löst-alles"-Lib — die Pipeline (Homographie/Fortschritt/DTW/Alignment) ist Eigencode; die
Libs liefern nur Bausteine (Skelett-Keypoints, Video-I/O). Das ist die vom Projekt geforderte Linie.

---

## 3. Dependency — bereits vorbereitet & verifiziert ✅

In [`gradle/libs.versions.toml`](gradle/libs.versions.toml) + [`app/build.gradle.kts`](app/build.gradle.kts)
ist **schon eingetragen und baut grün**:

```kotlin
implementation(libs.mlkit.pose.detection.accurate)  // com.google.mlkit:pose-detection-accurate:18.0.0-beta5
```

- „accurate"-Modell (genauer, Offline-Analyse — Latenz unkritisch). Schlankere Alternative
  `libs.mlkit.pose.detection` (base) ist im Katalog ebenfalls vorhanden, falls du wechseln willst.
- **Kein weiteres native Dependency nötig** für den Kern (kein OpenCV, kein MediaPipe).
- ML Kit lädt sein Modell selbst nach; **kein `.task`/Asset** manuell ablegen (anders als in A.1
  beschrieben — das galt für MediaPipe).

---

## 4. Repo-Fakten, die du sonst suchen müsstest

**Datenbank (WICHTIG — Anhang A.2 ist hier veraltet):**
- Aktuelle Version ist **v4**, nicht v2. Datei: [`data/db/BoulderBuddyDatabase.kt`](app/src/main/java/com/boulderbuddy/data/db/BoulderBuddyDatabase.kt).
- Provider nutzt **`.fallbackToDestructiveMigration(dropAllTables = true)`** in
  [`di/DatabaseModule.kt`](app/src/main/java/com/boulderbuddy/di/DatabaseModule.kt).
- **→ Für `GhostAnalysisEntity` KEINE handgeschriebene `Migration` nötig.** Einfach: Entity in
  `entities = [...]` ergänzen, `version = 4` → `5` erhöhen, DAO-Getter ergänzen. Die destruktive
  Migration wischt beim Schemawechsel (kein Bestandsnutzer). `exportSchema = true` ist an → das neue
  Schema landet in `app/schemas/`.
- Muster für neue Persistenz: Entity in `data/db/entity/`, DAO in `data/db/dao/`, DAO-Getter in
  `BoulderBuddyDatabase` + Provider in `DatabaseModule`, Repository-Interface+Impl in
  `data/repository/`, `@Binds` in [`di/RepositoryModule.kt`](app/src/main/java/com/boulderbuddy/di/RepositoryModule.kt).
- **Keypoints/Homographie/DTW-Pfade NICHT als BLOB-Spalten** — als JSON-Datei im App-Storage
  ablegen, nur Pfad in der DB (wie in A.2 empfohlen). `kotlinx-serialization-json` ist schon im
  Projekt (type-safe Nav) → für die JSON-Artefakte nutzbar.

**Bestehende Video-Schicht (aus 7.3c — wiederverwenden, nicht neu bauen):**
- [`ui/components/VideoPlayer.kt`](app/src/main/java/com/boulderbuddy/ui/components/VideoPlayer.kt) —
  View-basierte `PlayerView` via `AndroidView`, lifecycle-aware (pausiert im Hintergrund, `release()`
  beim Dispose). Basis für die Wiedergabe; das Skelett-Overlay legst du als Compose-`Canvas` darüber.
- [`util/MediaType.kt`](app/src/main/java/com/boulderbuddy/util/MediaType.kt) — `mediaTypeOf(uri)`
  (Foto vs. Video via `contentResolver.getType`). Für „ist das ausgewählte Medium ein Video".
- [`ui/components/PhotoPicker.kt`](app/src/main/java/com/boulderbuddy/ui/components/PhotoPicker.kt) —
  `PickVisualMedia.ImageAndVideo`. Für die Referenz-/Vergleichs-Auswahl.

**Navigation & Einstiegspunkt:**
- Type-safe Routen in [`ui/navigation/Destinations.kt`](app/src/main/java/com/boulderbuddy/ui/navigation/Destinations.kt)
  (`@Serializable object`/`data class`), verdrahtet in
  [`ui/navigation/AppNavigation.kt`](app/src/main/java/com/boulderbuddy/ui/navigation/AppNavigation.kt)
  (`composable<Route> { ... }`, Args via `toRoute()`).
- **`BottomNavTab.GhostClimber` existiert schon** (Enum-Wert + Icon `DirectionsRun` in
  [`ui/components/BottomNav.kt`](app/src/main/java/com/boulderbuddy/ui/components/BottomNav.kt)),
  ist aber **absichtlich NICHT** in `topLevelDestinations` (Destinations.kt) → kein Screen/Route.
- Laut A.4: Einstiegspunkt **klar als „Experimental / Ghost Climber" kennzeichnen, nicht im
  MVP-Kernfluss.** Empfehlung: eigenes **Push-Ziel** (z.B. neuer `SettingsRow` in
  [`ui/screens/EinstellungenScreen.kt`](app/src/main/java/com/boulderbuddy/ui/screens/EinstellungenScreen.kt)
  oder Eintrag im Boulder-Detail), **nicht** als 5. Bottom-Tab (hält die Abgabe stabil). Der
  GhostClimber-Enum-Wert kann als Icon dienen, wenn du doch einen Tab willst.

**Theme/Komponenten (Function-first UI mit bestehenden Tokens):**
- Farben/Abstände: `BoulderBuddy.colors.*`, `Dimens.*`, `MaterialTheme.colorScheme.*`
  (`ui/theme/`). Dark Mode ist seit 7.4a aktiv — nutze **semantische Tokens**, keine hartkodierten
  Farben, sonst bricht Dark Mode.
- Wiederverwendbare Bausteine in `ui/components/` (`PrimaryButton`, `TextField`, `SelectableChip`,
  `BoulderBuddyScaffold`, `TopBar`, …).
- Schwere Arbeit (Pose je Frame, DTW) **nicht im UI-Thread** — Coroutine/`WorkManager`
  (WorkManager ist noch nicht im Projekt; falls du es nimmst, Katalog-Eintrag ergänzen. Für eine
  einmalige Offline-Analyse reicht auch ein `viewModelScope` + `Dispatchers.Default`).

---

## 5. Arbeitsweise (wie im restlichen Projekt)

- **Build:** `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`, dann
  `& ".\gradlew.bat" :app:assembleDebug --console=plain`. **AGP-9-Fallen** (Hilt ≥ 2.60,
  `android.disallowKotlinSourceSets=false`) gelten projektweit — nichts extra nötig.
- **Ein Meilenstein = ein grüner Build = ein Commit.** Meilensteine M1–M5 stehen in Anhang A.5.
- **Doku-Pflicht (Obsidian-Vault):** jede Architektur-Entscheidung (v.a. „ML Kit statt MediaPipe",
  „eigene Homographie statt OpenCV", DB v5) → `04 – Entwicklung/Code-Entscheidungen.md`;
  Stolpersteine → `04 – Entwicklung/Bugs & Fixes.md`.
- **Pipeline-Spezifikation:** Anhang A.3 (P0–P8) bleibt deine algorithmische Vorlage — nur die
  konkreten Libs sind wie oben ersetzt.

---

## 6. Haupt-Risiko, das du früh adressieren solltest

**Auto-Griff-Detektion ohne OpenCV** ist der wackeligste Punkt (kein nativer Feature-Detector im
Android-SDK, ML Kit erkennt keine Kletter-Griffe). Reihenfolge daher:
1. **M1/M2 zuerst mit MANUELLEM Antippen** der Anker bauen — das ist der garantierte Pfad und macht
   die ganze Pipeline (Homographie → Transform → DTW → Overlay) demonstrierbar.
2. Auto-Detektion **danach** als additive Kür oben drauf. Wenn sie ohne schwere native Lib zu
   unzuverlässig ist: dokumentieren, manuelle Korrektur bleibt der ehrliche Default — **kein**
   Abgabe-Blocker.
