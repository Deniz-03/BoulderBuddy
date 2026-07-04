# 🧗 BoulderBuddy — Phase 7: Konkreter Umsetzungsplan

> **Zweck:** Feingliedrige Roadmap für Phase 7 (Loose Enden / Post-MVP). Ergänzt
> [`IMPLEMENTIERUNGSPLAN.md`](IMPLEMENTIERUNGSPLAN.md) — dort steht der Gesamtplan, hier die
> Detail-Schritte, Stolpersteine und offenen Fragen für Phase 7.
>
> Erstellt: 2026-07-04 · Branch: `Phase-7` · Abgabe-Ziel: ~2026-08-01 (≈ 4 Wochen)
>
> **Arbeitsweise wie gehabt:** eine Teilaufgabe = ein Commit-Block, nach jedem Schritt
> `./gradlew assembleDebug` grün, Häkchen hier pflegen, Architektur-Entscheidungen in
> `04 – Entwicklung/Code-Entscheidungen.md` (Obsidian) nachtragen.
>
> **Build-Reminder:** `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` setzen,
> dann `& ".\gradlew.bat" assembleDebug --console=plain`. AGP-9-Besonderheiten (Hilt ≥ 2.60,
> `android.disallowKotlinSourceSets=false`) gelten auch für ein neues Wear-Modul.

---

## 0. Ausgangslage (Stand 2026-07-04)

Phase 0–6 sind **abgeschlossen**: lauffähiges Phone-MVP, Room-Datenschicht, Hilt-DI, alle
ViewModels an echte Daten angebunden, Daten überleben Neustart. Single-Modul-Projekt (`:app`),
type-safe Navigation, gemeinsames Scaffold mit Bottom-Nav.

**Wichtig für die Priorisierung:** Laut [`Requirements.md`] sind **„Responsive Layouts: Phone,
Tablet, Smartwatch" ein Must-Have**. Damit sind 7.1 (Tablet) und 7.2 (Wear OS) formal
MVP-Pflicht und haben Vorrang vor den Should-/Nice-to-Haves — auch wenn sie im
Implementierungsplan unter „Phase 7 / Post-MVP" stehen.

**Was der Code schon vorbereitet (relevant für die Detailplanung):**
- `HangboardTemplateEntity` + `HangboardTemplateDao` + `HangboardRepository` existieren, sind
  aber **noch nirgends im UI verdrahtet** → Basis für 7.3 „Timer-Voreinstellungen".
- Timer-Config läuft aktuell über `SettingsRepository`/`TimerConfig` (DataStore, genau *eine*
  gemerkte Einstellung), nicht über die Templates.
- `RouteEntity.mediaUri` ist eine *einzelne* URI, im UI via Coil `AsyncImage` (nur Bild) →
  Basis + Lücke für 7.3 „Video-Support".
- `Theme.kt` ist **reines `lightColorScheme`** (kein `darkColorScheme`, kein
  `isSystemInDarkTheme()`); die Custom-`BoulderBuddyColors` sind fest helle Werte → 7.4 Dark Mode
  ist echte Arbeit, kein Ein-Zeiler.

---

## 1. Empfohlene Reihenfolge & Zeitbudget (≈ 4 Wochen)

Priorisierung nach *Requirements-Pflicht → Aufwand/Nutzen → Doku-Wert*:

| Prio | Teil | Warum | grobe Schätzung |
|------|------|-------|-----------------|
| **1** | **7.1 Tablet-Layout** | Must-Have, klein, hoher Doku-Wert (Screenshots) | 2–3 Tage |
| **2** | **7.6 Tests (Kern)** | Prozessdoku verlangt Testbarkeit; früh = stabiles Fundament | 2 Tage |
| **3** | **7.2 Wear-OS-Modul** | Must-Have, aber größter Brocken → Scope eng halten | 5–8 Tage |
| **4** | **7.3 Should-Haves** | Timer-Presets (Infra da) > Export > Video | 3–4 Tage |
| **5** | **7.4 Nice-to-Haves** | Dark Mode zuerst (gut demonstrierbar) | 1–3 Tage |
| **6** | **7.5 Ghost Climber (Voll)** | Homographie + DTW + Overlay/Side-by-Side — **Fable 5** (s. Anhang A) | best effort |

> **Entscheidung (2026-07-04):** Reihenfolge = **Must-Have zuerst** (7.1/7.2/7.6), aber **alles**
> soll abgearbeitet werden.
>
> **Zwei Advanced Features werden von Fable 5 umgesetzt** (self-contained Briefings am Ende):
> - **Ghost Climber, Vollausbau** (nicht mehr nur PoC): Wand-Homographie, Dynamic Time Warping zur
>   Skelett-Synchronisierung, umschaltbar zwischen **Overlay-** und **Side-by-Side-Ansicht**.
>   → **[Anhang A](#anhang-a--fable-5-auftrag-ghost-climber-vollausbau)**.
> - **Automatische Hangboard-Erkennung auf der Uhr** (Advanced-Ausbau von 7.2): Sensorik erkennt
>   Hängen/Loslassen, misst Satz- und (variable) Pausenlängen automatisch, bis der Nutzer die
>   Session auf der Uhr beendet. → **[Anhang B](#anhang-b--fable-5-auftrag-automatische-hangboard-erkennung-uhr)**.
>
> UI folgt in beiden Fällen der Funktion (Function-first).

---

## 7.1 — Tablet-Layout (`WindowSizeClass` + `ListDetailPaneScaffold`)

**Ziel:** Auf ≥ 600 dp die Sessions-Liste + Session-Detail nebeneinander; Statistik-Dashboard
breiter. Referenz: `03 – Architektur & Tech/Multi-Device-Strategie.md` (Breakpoints Compact/Medium/
Expanded).

### Konkrete Schritte
- [x] **7.1.1** Dependencies ergänzt (`libs.versions.toml` + `app/build.gradle.kts`):
  - `androidx.compose.material3.adaptive:adaptive` (Version `1.1.0`, eigene Linie, nicht vom BOM)
  - `androidx.compose.material3.adaptive:adaptive-layout`
  - `androidx.compose.material3.adaptive:adaptive-navigation`
  - `androidx.compose.material3:material3-window-size-class` (aus BOM, ohne Version). ⚠️ Alias darf
    nicht `...size.class` heißen — `class` ist ein reservierter Gradle-Katalog-Alias → als
    `androidx-compose-material3-windowsizeclass` eingetragen.
- [x] **7.1.2** `WindowSizeClass` in `MainActivity` via `currentWindowAdaptiveInfo().windowSizeClass`
  bestimmt und als Parameter an `AppNavigation` gereicht; dort `isWideLayout` (≥ 600 dp) abgeleitet.
- [x] **7.1.3** Sessions-Ziel adaptiv: bei Medium/Expanded neues `SessionsListDetail`
  (`ListDetailPaneScaffold`, List = `SessionUebersichtScreen`, Detail = `SessionRoute`);
  Compact unverändert (Push). `SessionViewModel` auf **Assisted Injection** umgestellt, damit die
  `sessionId` im Detail-Pane ohne Nav-Argumente ankommt.
- [x] **7.1.4** Statistik-Screen: Sektionen extrahiert; bei `wide=true` Grade-Verteilung + Aktivität
  nebeneinander (`Row`), sonst untereinander (unverändert).
- [ ] **7.1.5** Verifizieren im Tablet-Emulator (Portrait 600–840 dp, Landscape > 840 dp) + Phone
  (Regression: Compact darf sich nicht ändern). — **offen (Emulator-Sichtprüfung).** `assembleDebug`
  ist grün.

### Zu beachten
- Die bestehende Bottom-Nav (aus Phase 1.3) muss auf Tablet weiterhin funktionieren — ggf. auf
  breiten Layouts `NavigationRail` statt Bottom-Nav erwägen (optional, Nice-to-have).
- `ListDetailPaneScaffold` hält eigenen Pane-State; das muss mit dem bestehenden `NavController`
  koexistieren, ohne doppelte Detail-Navigation. Sauberste Variante: adaptive Navigation nur
  *innerhalb* des Sessions-Tabs, restliche Ziele bleiben klassisch.

### Offene Fragen
- Nur Sessions adaptiv, oder auch Boulder-Übersicht → Boulder-Detail? (Vorschlag: erst Sessions,
  Boulder analog nur wenn Zeit bleibt.)

---

## 7.2 — Wear-OS-Modul (`wear/`)

**Ziel:** Eigenständiges Compose-for-Wear-Modul mit Hangboard-Timer als Kernfeature. Referenz:
`Multi-Device-Strategie.md`.

### Entschieden (2026-07-04): Companion, Minimal-Scope
- **Companion**, kein Standalone → keine eigene Datenschicht auf der Uhr. Die Uhr ist primär ein
  Hangboard-Timer mit Vibration.
- **Sync-Umfang:** Uhr → Phone „Hangboard-Durchlauf fertig". Der fertige Durchlauf wird — **falls
  eine aktive Session auf dem Phone existiert** — in genau diese Session getrackt (sonst nur lokaler
  Timer, kein Logging). Das entspricht dem bestehenden `recordWorkout()` im
  `HangboardTimerViewModel` (schreibt eine `HangboardSessionEntity` in die aktive Session).

### Konkrete Schritte (Minimal-Scope, Timer-first)
- [x] **7.2.1** Neues Modul `:wear` angelegt (`settings.gradle.kts` `include(":wear")`, eigenes
  `wear/build.gradle.kts`, eigenes `AndroidManifest.xml` mit `<uses-feature android:name="android.hardware.type.watch">`,
  `standalone=false` = Companion). minSdk 30 (Wear OS 3+). **`applicationId = "com.boulderbuddy"`
  gleich wie `:app`** (Data-Layer-Kopplung). Kein Hilt auf der Uhr (bewusst, kleines Modul).
  AGP-9-Fallen (Built-in-Kotlin, `disallowKotlinSourceSets=false`) gelten projektweit — bauen grün.
- [x] **7.2.2** Wear-Dependencies im Katalog + `wear/build.gradle.kts`: `androidx.wear.compose:compose-material`
  (`1.4.1`), `compose-foundation`, `compose-navigation`, `androidx.wear:wear` (`1.3.0`),
  `play-services-wearable` (`19.0.0`, Data Layer). Compose-BOM geteilt mit `:app`.
- [x] **7.2.3** Wear-`MainActivity` (`presentation/`) + Timer-Screen (Compose for Wear OS,
  `ScalingLazyColumn` + `CircularProgressIndicator`): Start/Pause/Reset, HÄNGEN/PAUSE-Anzeige,
  Satz-Zähler, im Ausgangszustand Stepper für Sätze/Hang/Pause. **Vibrations-Feedback** bei
  Phasenwechsel (150 ms) und Ende (500 ms) via `VibratorManager`/`Vibrator`. Eigenes
  `TimerViewModel` (`AndroidViewModel`) — **gleiche Zustandsmaschine** wie das Phone-VM
  (HANG→REST→DONE), aber eigenständig (getrennte Module).
- [x] **7.2.4** Data-Layer-Anbindung: Uhr sendet bei DONE via `MessageClient` an alle verbundenen
  Nodes (`PhoneConnector` + `WearSyncContract`, Pfad `/boulderbuddy/hangboard_completed`,
  Text-Payload). Auf dem Phone `HangboardWearListenerService` (`@AndroidEntryPoint`,
  im Manifest registriert) → schreibt den Durchlauf **nur bei aktiver Session** in genau diese
  (analog `recordWorkout()`). ⚠️ Hilt-`ServiceComponent` zog `error_prone_annotations` als neue
  `:app`-Dependency nach sich.
- [ ] **7.2.5** „Session starten / aktive Route als geschafft markieren" auf der Uhr — **Ausblick**
  (Minimal-Scope: bewusst weggelassen, Data-Layer-Gerüst steht als Basis dafür bereit).
- [ ] **7.2.6** Verifizieren im Wear-OS-Emulator (+ gepaartem Phone-Emulator für Data Layer) —
  **offen (Emulator-Sichtprüfung).** `:wear:assembleDebug` + `:app:assembleDebug` sind grün.

> **Advanced-Ausbau (Fable 5): automatische Hangboard-Erkennung.** Aufbauend auf dem hier
> gebauten Companion-Timer erkennt die Uhr per Sensorik selbst, wann gehängt wird, misst Satz-
> und Pausenlängen automatisch und automatisiert so die ganze Hangboard-Session. Kompletter
> Auftrag: **[Anhang B](#anhang-b--fable-5-auftrag-automatische-hangboard-erkennung-uhr)**. Das
> `:wear`-Modul (7.2.1) + Data-Layer-Anbindung (7.2.4) sind dafür **Voraussetzung**.

### Zu beachten
- Data Layer braucht **gleiche `applicationId`** (oder passende Capability-Deklaration) auf beiden
  Modulen, sonst finden sich Phone & Watch nicht.
- Der Wear-Timer sollte auch ohne aktive Phone-Session laufen (reiner Timer) — Session-Tracking ist
  additiv.
- Hilt auf Wear ist optional; für ein kleines Timer-Modul evtl. bewusst ohne DI (Aufwand/Nutzen).

---

## 7.3 — Should-Haves

Reihenfolge nach Aufwand/Nutzen: **Timer-Voreinstellungen → Session-Export → Video-Support.**

### 7.3a Hangboard-Timer-Voreinstellungen
> Infrastruktur (`HangboardTemplateEntity`/`-Dao`/`HangboardRepository`) existiert, ist aber
> ungenutzt. Aufgabe = anbinden, nicht neu bauen.
- [x] **7.3a.1** `HangboardRepository` um `delete` ergänzt (`observeAll`/`create`/`update` waren da),
  `HangboardTemplateDao.delete` (`@Delete`) hinzugefügt und Repository in `HangboardTimerViewModel`
  injiziert (Presets als beobachteter Room-Flow im UI-State).
- [x] **7.3a.2** UI im `HangboardTimerScreen`: Preset-Chips im Einstell-Dialog (`InputChip` +
  `FlowRow`) → Tap lädt Werte in die Stepper; „Als Preset speichern" (Name-Dialog); „Presets
  bearbeiten"-Modus zum Löschen (Chip zeigt X).
- [x] **7.3a.3** SeedData um 3 Standard-Presets erweitert („Repeater 7/3", „Max Hangs 10/60",
  „Warmup 5/10").
- [x] **7.3a.4** Entschieden: **Templates = benannte Presets** (Room), **DataStore bleibt für
  „zuletzt genutzt"**. Ein Preset-Tap befüllt nur die Stepper; „Übernehmen" persistiert die Config
  wie bisher via DataStore. `repRestSec` wird vom Timer noch nicht genutzt → = `restSec`.

### 7.3b Session-Export
- [x] **7.3b.1** Format entschieden (2026-07-04): **CSV-Gesamtexport** aller Sessions (eine Zeile je
  Route, Sessions ohne Routen behalten eine Zeile). IDs gegen Namen aufgelöst (Halle, Gradsystem,
  Grad-Label) → CSV ohne DB lesbar. UTF-8 mit BOM (Excel-Umlaute), RFC-4180-Escaping.
- [x] **7.3b.2** `SessionExporter` (`data/export/`, `@Inject`, DAOs + `@ApplicationContext`) baut das
  CSV und schreibt in die Ziel-URI; `EinstellungenViewModel.exportSessions(uri)` ruft ihn auf und
  liefert eine Toast-Rückmeldung (`exportMessage`).
- [x] **7.3b.3** Storage Access Framework (`ActivityResultContracts.CreateDocument("text/csv")`);
  Einstiegspunkt „Sessions exportieren (CSV)" in der App-Gruppe der Einstellungen.

### 7.3c Video-Support für Routen — **zurückgestellt (2026-07-04)**
> Entscheidung: erst nach 7.6 Tests. Zieht Media3-Dependency + echte Schema-Migration (v4→v5) nach
> und ist im Plan als Letztes der Should-Haves eingeordnet. Offene Frage 7.3c.1 (mediaType-Feld vs.
> MIME) bleibt bis dahin offen.
- [ ] **7.3c.1** Schema-Frage klären: reicht `RouteEntity.mediaUri` + MIME-Erkennung (Bild vs.
  Video), oder braucht es ein explizites `mediaType`-Feld? → **Schema-Migration v4→v5** nötig, wenn
  ein Feld dazukommt (echte Migration, da inzwischen ggf. Bestandsdaten).
- [ ] **7.3c.2** PhotoPicker auf `PickVisualMedia` mit Bild+Video umstellen (unterstützt schon beides).
- [ ] **7.3c.3** Video-Wiedergabe: Media3/ExoPlayer als neue Dependency; im Boulder-Detail statt
  `AsyncImage` einen `PlayerView`/Compose-Wrapper, wenn die URI ein Video ist.

### Zu beachten
- Video zieht die größte neue Dependency (Media3) + Migration nach → nur angehen, wenn 7.1/7.2/7.6
  stehen.

---

## 7.4 — Nice-to-Haves

Reihenfolge: **Dark Mode → Speech-to-Text-Notizen → Homescreen-Widget.**

### 7.4a Dark Mode
- [ ] **7.4a.1** `darkColorScheme` in `Theme.kt` + **dunkle Varianten der `BoulderBuddyColors`**
  (die Custom-Farben sind aktuell fest hell → zweiter Farbsatz nötig).
- [ ] **7.4a.2** `BoulderBuddyTheme` um `isSystemInDarkTheme()` (und optional expliziten Toggle in
  Einstellungen via DataStore) erweitern; passenden Farbsatz per `CompositionLocalProvider` liefern.
- [ ] **7.4a.3** Alle Screens im Dark Mode durchsehen (Kontraste, Route-Farben, Charts).

### 7.4b Speech-to-Text-Notizen
- [ ] **7.4b.1** `RecognizerIntent`/`SpeechRecognizer` an den Notiz-Feldern (Route + Session);
  Mikrofon-Permission.

### 7.4c Homescreen-Widget
- [ ] **7.4c.1** Glance-Widget (z. B. „aktive Session / letzte Tops / Schnellstart Timer").
  Eigene Glance-Dependency.

---

## 7.5 — Ghost Climber (Vollausbau, Fable 5)

**Entscheidung (2026-07-04): Vollausbau, umgesetzt von Fable 5.** Wand-Homographie + Dynamic Time
Warping + umschaltbare Overlay-/Side-by-Side-Ansicht. Kein reduzierter PoC mehr. Konzept:
`Feature – Ghostclimber.md`; Pipeline-Analyse (P0–P8): `Ghostclimber – Synchronisation &
Alignment.md`.

**Voraussetzung:** 7.3c Video-Support (Aufnahme/Wiedergabe) sollte stehen.

→ Vollständiges, self-contained Briefing für Fable 5:
**[Anhang A](#anhang-a--fable-5-auftrag-ghost-climber-vollausbau)**.

---

## 7.6 — Tests

**Ziel:** Prozessdoku-tauglicher Test-Nachweis, kein Vollabdeckungs-Anspruch.
- [x] **7.6.1** Test-Dependencies ergänzt (Katalog + `app/build.gradle.kts`): `androidx.room:room-testing`,
  `kotlinx-coroutines-test` (1.10.2), `androidx.arch.core:core-testing`, `turbine` (1.2.0, Flow-Tests),
  `google.truth` (1.4.4). Aufgeteilt: JVM-Tests (`testImplementation`: coroutines-test/turbine/truth/
  arch-core) vs. instrumentiert (`androidTestImplementation`: room-testing/coroutines-test/truth).
- [x] **7.6.2** DAO-Tests mit Room **in-memory** (`androidTest`, `createInMemoryDatabase()`-Helper):
  `SessionDaoTest` (Aktiv-Marker `endedAt IS NULL`, Row-ID, `endSession`, neueste aktive),
  `RouteDaoTest` (`observeBySession` filtert + Reihenfolge), `GradeDaoTest` (`observeBySystem`
  nach `sortOrder`, Filter). **Auf Emulator ausgeführt: grün.**
- [x] **7.6.3** Repository-Test `SessionRepositoryTest` (echte In-Memory-Room): `create` gibt ID
  zurück + Session wird aktiv, `endSession` setzt `endedAt` → keine aktive Session mehr.
  **Auf Emulator ausgeführt: grün.**
- [x] **7.6.4** ViewModel-Tests (JVM, `MainDispatcherRule` + `runTest`): `HangboardTimerViewModelTest`
  (Zustandsmaschine HANG→REST→DONE mit virtueller Zeit, Tracking in aktive Session, Reset, Preset)
  + `StatistikViewModelTest` (Flash-Rate/Tops/Hangboard-Summen/Grade-Verteilung, Turbine). Fakes in
  `test/.../fake/`. **`testDebugUnitTest` grün.**
- [x] **7.6.5** Compose-UI-Test `HangboardTimerScreenTest` (`androidTest`): rendert UI-State,
  Start-Tap löst `onPlayPause` aus. **Auf Emulator ausgeführt: grün.**

### Zu beachten
- ViewModel-/Flow-Tests brauchen `TestDispatcher`; der Timer nutzt `delay` → `runTest` +
  `advanceTimeBy` verwenden. **Umgesetzt:** `MainDispatcherRule` (ersetzt `Dispatchers.Main` durch
  `StandardTestDispatcher`); `advanceTimeBy(n)` + `runCurrent()`, da ein bei genau `n` fälliger
  `delay` sonst nicht ausgeführt wird.
- **Instrumentierte Tests (7.6.2/3/5)** via `:app:connectedDebugAndroidTest` — bei mehreren AVDs das
  Ziel per `ANDROID_SERIAL` selektieren. **Auf `Pixel_6a(AVD)` ausgeführt: 12/12 grün.**

---

## Offene Fragen — vor Umsetzung zu klären

**Geklärt (2026-07-04):**
- ✅ **Scope/Deadline:** Must-Have zuerst (7.1/7.2/7.6), danach *alles* abarbeiten inkl. Ghost
  Climber als PoC am Ende.
- ✅ **Wear:** Companion, Minimal-Scope; fertiger Timer-Durchlauf wird in die aktive Phone-Session
  getrackt, falls eine existiert.
- ✅ **Ghost Climber:** Vollausbau (Homographie + DTW + Overlay/Side-by-Side), umgesetzt von Fable 5
  → [Anhang A](#anhang-a--fable-5-auftrag-ghost-climber-vollausbau).
- ✅ **Uhr Advanced:** automatische Hang-Erkennung, umgesetzt von Fable 5
  → [Anhang B](#anhang-b--fable-5-auftrag-automatische-hangboard-erkennung-uhr).

**Noch offen (allgemein):**
1. **Video-Support** (7.3c): eigenes `mediaType`-Feld + Migration, oder MIME-Erkennung auf
   bestehender `mediaUri`? (Wird von 7.5 Ghost Climber vorausgesetzt.)
2. **User-Profil** (Altlast aus IMPLEMENTIERUNGSPLAN): „Hallo, Deniz" — fest lassen oder simple
   Einstellung? (klein, evtl. mit 7.4 mitnehmen)
3. **Tablet-Umfang** (7.1): nur Sessions adaptiv oder auch Boulder?

> Die **feature-spezifischen** offenen Fragen für die beiden Fable-Aufträge stehen jeweils am Ende
> von Anhang A bzw. Anhang B („MUSS vor Start geklärt werden").

---

## Doku-Pflichten (Obsidian-Vault, nicht vergessen)

- Jede Architektur-Entscheidung (Tablet-Adaptive-Ansatz, Wear-Modul-Setup, Dark-Mode-Farbmodell,
  ggf. Schema-Migration v2→v3) → `04 – Entwicklung/Code-Entscheidungen.md` (Vorlage: Datum/
  Entscheidung/Alternativen/Begründung/Konsequenzen).
- Build-/Setup-Stolpersteine (v. a. Wear-Modul unter AGP 9) → `04 – Entwicklung/Bugs & Fixes.md`.
- Fortschritt pro Woche → `04 – Entwicklung/Sprint-Log.md`.
- `TODO.md` im Vault: „Wear OS Modul hinzufügen" / „Tablet-Layout" abhaken, wenn erledigt.
- Requirements-Häkchen in `Requirements.md` setzen (Tablet/Wear = Must-Have).
- Screenshots/Recordings pro Gerät (Phone/Tablet/Wear) für die Prozessdoku sammeln.

---

*Fortschritt bitte direkt hier pflegen (Häkchen). Gesamtplan: [`IMPLEMENTIERUNGSPLAN.md`](IMPLEMENTIERUNGSPLAN.md).*

---
---

# Anhang A — Fable-5-Auftrag: Ghost Climber (Vollausbau)

> **An Fable 5:** Dies ist dein vollständiger Arbeitsauftrag für das Ghost-Climber-Feature. Lies
> vorher die zwei Konzept-Notizen im Obsidian-Vault: `01 – Planung & Konzept/Feature –
> Ghostclimber.md` (Vision) und `03 – Architektur & Tech/Ghostclimber – Synchronisation &
> Alignment.md` (die Pipeline-Analyse P0–P8 — sie ist deine algorithmische Spezifikation). Trage
> jede getroffene Entscheidung in `04 – Entwicklung/Code-Entscheidungen.md` nach. Baue nach jedem
> Meilenstein grün (`assembleDebug`) und committe.

## A.0 Ziel & Nicht-Ziele

**Ziel:** Zwei aufgenommene Versuche *derselben Route* vergleichbar machen: Wand per Homographie in
einen gemeinsamen Referenzraum bringen, Kletter-Posen (Skelette) extrahieren und mit **Dynamic Time
Warping** zeitlich synchronisieren, dann **umschaltbar** als **Overlay** (Skelette/Videos
übereinander) oder **Side-by-Side** darstellen. Die App schlägt anhand einer Ähnlichkeitsmetrik
einen Modus vor; der Nutzer kann überstimmen.

**Nicht-Ziele (bewusst ausgeklammert):**
- Keine Griff-/Route-Klassifizierung. Griffe dienen **nur** als Homographie-Anker (≥ 4 Punkte).
- Keine Live-/Echtzeit-Analyse während des Kletterns. **Offline-Batch** nach der Aufnahme.
- Keine biomechanisch exakte Schwerpunktberechnung — 2D-Näherung genügt (Hüftmitte).
- Kein Anspruch auf robuste Sturzerkennung ohne Kalibrierung (P5 ist „best effort").

## A.1 Tech-Stack (konkrete Dependencies, in Katalog eintragen)

- **Pose-Estimation:** MediaPipe Tasks Vision — `com.google.mediapipe:tasks-vision`
  (`PoseLandmarker`, 33 Landmarks, normalisierte + Welt-Koordinaten, Video-Modus). Modell-`.task`
  in `assets/` ablegen. (Alternative nur falls MediaPipe scheitert: ML Kit Pose Detection.)
- **Computer Vision (Homographie):** OpenCV for Android — `org.opencv:opencv:4.11.0` (Maven
  Central, kein manueller SDK-Import mehr nötig). Genutzt: `Calib3d.findHomography`,
  `Core.perspectiveTransform`, optional `Imgproc.warpPerspective` fürs Video-Warping.
- **Video:** Media3 — `androidx.media3:media3-exoplayer` + `media3-ui` (Wiedergabe);
  Frame-Extraktion für die Offline-Analyse via `MediaMetadataRetriever.getFrameAtTime` **oder**
  `MediaExtractor`/`MediaCodec` (schneller bei vielen Frames). Aufnahme: **CameraX**
  (`androidx.camera:camera-video`) — kann mit 7.3c geteilt werden.
- **DTW:** selbst in Kotlin implementieren (klassisches DP über die 1D-Fortschrittssignale,
  ~50 Zeilen; keine Library nötig). Optional Sakoe-Chiba-Band zur Beschleunigung.
- **Charts/Overlay-Rendering:** Compose `Canvas` über dem `PlayerView` (Skelett zeichnen) bzw.
  zwei `PlayerView` nebeneinander für Side-by-Side.

> ⚠️ **AGP-9-Falle:** neue Module/Deps erben Hilt ≥ 2.60 + `disallowKotlinSourceSets=false`
> (siehe Haupt-Plan-Header). OpenCV zieht native `.so` → APK wächst; ggf. ABI-Filter setzen.

## A.2 Datenmodell — WICHTIG: erst die Route-Identität lösen

**Problem (blockierend):** Das aktuelle Schema hat **keine geräteübergreifende Routen-Identität**.
`RouteEntity` hängt an *einer* Session (`sessionId` FK). Ghost Climber vergleicht aber zwei
Versuche *derselben* Route — die typischerweise in *verschiedenen* Sessions geloggt wurden. Es gibt
heute nichts, was „das ist zweimal dieselbe Route" ausdrückt.

**→ Kläre zuerst die gewählte Option (A.7 Frage 1). Empfehlung: Option 1.**

- **Option 1 (empfohlen, minimalinvasiv):** Ghost Climber arbeitet auf **zwei frei gewählten
  Videos** (aus `RouteEntity.mediaUri` von zwei beliebigen Routen *oder* frisch aufgenommen). Keine
  formale Routen-Identität nötig; der Nutzer wählt „Referenz" + „Vergleich" selbst. Für die Abgabe
  völlig ausreichend und demonstrierbar.
- **Option 2 (sauberer, teurer):** Neue Entity `ClimbEntity` (persistente „Problem/Route"-Identität
  über Sessions), `RouteEntity` bekommt optionale `climbId`. Erlaubt „alle Versuche dieser Route".
  Mehr Schema-Arbeit + Migration + UI zum Verknüpfen. Nur wenn Zeit bleibt.

**Neue Persistenz für Analyse-Artefakte (bei beiden Optionen):**
- Rohe Keypoints pro Frame sind klein → als **JSON/Protobuf-Datei** im App-Storage ablegen
  (Pfad in DB referenzieren), **nicht** als BLOB-Spalten. Videos bleiben groß → nur URI speichern,
  Video nicht kopieren.
- Neue Entity `GhostAnalysisEntity` (Vorschlag): `id`, `refMediaUri`, `cmpMediaUri`,
  `refKeypointsPath`, `cmpKeypointsPath`, `homographyRefJson`, `homographyCmpJson`,
  `routePathJson` (Polylinie), `dtwPathJson`, `suggestedMode` (OVERLAY/SIDE_BY_SIDE), `createdAt`.
  → **Schema-Migration** (aktuell v2; neue Version + echte `Migration`, da ggf. Bestandsdaten).

## A.3 Pipeline-Implementierung (folgt P0–P8 des Alignment-Docs)

Baue in dieser Reihenfolge (= „Implementierungsreihenfolge" im Alignment-Doc). Jeder Meilenstein
ist einzeln demonstrierbar:

- [ ] **A.3.1 Fundament (P0):** Für jedes Video: Frames extrahieren → `PoseLandmarker` je Frame →
  Landmarks speichern. **Beide** Posen-Sätze durch die jeweilige Homographie transformieren
  (`perspectiveTransform`) → gemeinsamer Wand-Referenzraum. **Ohne diesen Schritt ist nichts
  vergleichbar** (P0). Hüftmitte als robustesten Punkt führen; Gaussian-Smoothing aufs Rohsignal.
- [ ] **A.3.2 Homographie (Anker-Erfassung):** ≥ 4 korrespondierende Wandpunkte bestimmen.
  **Empfehlung: manuelles Tippen** (Nutzer markiert dieselben 4+ markanten Griffe/Volumes in beiden
  Videos) — deutlich robuster als automatische Feature-Detektion (ORB/AKAZE) und für die Abgabe
  ehrlicher. Automatik optional als Ausbau. `findHomography` → Referenzraum.
- [ ] **A.3.3 Fortschrittssignal (P2/P3):** Routenpfad als Polylinie bestimmen —
  **Nutzer-Korrektur-Ansatz (empfohlen, P3):** geglättete Hüfttrajektorie des Referenzversuchs als
  Vorschlag anzeigen, Nutzer korrigiert/verlängert bis Top. Pfad pro Analyse cachen. Fortschritt je
  Versuch = **Bogenlänge** der auf den Pfad projizierten Hüftposition (nicht reine Y-Koordinate!).
- [ ] **A.3.4 Alignment (P1):** **DTW** auf den beiden 1D-Fortschrittssignalen (vorher auf gleiche
  Framerate resampeln, P8). Ergebnis = Frame-Mapping ref↔cmp. Behandelt unterschiedliches Tempo
  und Pausen automatisch.
- [ ] **A.3.5 Robustheit (P4/P5):** Abbruch/Sturz erkennen (Geschwindigkeits-Spike > Baseline+n·σ,
  anhaltende Abwärtsbewegung über Mindestdauer; Kamerabewegung als gemeinsame Keypoint-Bewegung
  herausrechnen). **Abbruch als Feature (P4c):** der abgebrochene Versuch faded am Abbruchpunkt aus,
  der andere klettert weiter. Schwellwerte sind **Kalibrier-Parameter** (A.6), keine Konstanten.
- [ ] **A.3.6 Modus-Vorschlag (P7):** Ähnlichkeit der normalisierten Trajektorien messen
  (DTW-Distanz + laterale Varianz-Differenz + Hauptrichtung via PCA) → `suggestedMode`. Bei
  fundamental unterschiedlicher Beta (P6) → **Side-by-Side** vorschlagen (ehrlicher als
  irreführendes Overlay).

## A.4 UI (folgt der Funktion — Function-first)

Kein Screen-Design vorab; die UI bildet die Pipeline-Schritte als geführten Flow ab. Baue mit den
bestehenden `ui/theme`-Tokens/Komponenten. Eigener, klar als **„Experimental / Ghost Climber"**
gekennzeichneter Einstiegspunkt (z. B. aus dem Boulder-Detail oder einem eigenen Menüpunkt), **nicht
im MVP-Kernfluss**, damit die Abgabe stabil bleibt.

Flow (jeder Schritt = ein Compose-Screen/Sheet):
1. **Auswahl:** Referenz-Video + Vergleichs-Video wählen/aufnehmen.
2. **Anker setzen:** In beiden Videos 4+ korrespondierende Wandpunkte antippen (A.3.2).
3. **Pfad bestätigen:** vorgeschlagene Trajektorie zeigen, Nutzer korrigiert (A.3.3).
4. **Verarbeiten:** Fortschrittsanzeige während der Offline-Analyse (Pose+DTW laufen im
   Background/`WorkManager` oder Coroutine, nicht im UI-Thread).
5. **Vergleichsansicht:** Video-Player mit Skelett-Overlay **plus Umschalter Overlay ⇄
   Side-by-Side** (vorbelegt mit `suggestedMode`); Scrubber/Play über die DTW-gemappte Zeitachse;
   optional Schwerpunktverlauf einblendbar.

## A.5 Reihenfolge / Meilensteine (jeder einzeln lauffähig)

M1: Video-Auswahl + Pose-Extraktion + Skelett über *einem* Video zeichnen (kein Vergleich).
M2: Homographie (manuelle Anker) + Posen-Transformation in Referenzraum.
M3: Fortschrittssignal + DTW + Overlay zweier synchronisierter Skelette.
M4: Side-by-Side-Modus + Umschalter + `suggestedMode`-Metrik.
M5: Abbruch-/Sturz-Handling + Persistenz (`GhostAnalysisEntity`) + Kalibrierung.

## A.6 Empirisch zu kalibrieren (aus dem Alignment-Doc)

Sturz-Geschwindigkeitsschwelle (n·σ), Mindestdauer Abwärtsbewegung, Konfidenz-Schwelle „Pose
verloren", DTW-Distanz-Grenze (Overlay↔Side-by-Side), Glättungs-Sigma. **Vorgehen:** ~10 echte
Versuchspaare aufnehmen & labeln, Histogramme bilden, Trennlinien setzen. Werte als benannte
Konstanten/Config zentral halten, nicht verstreut hartkodieren.

## A.7 MUSS vor Start geklärt werden (an Deniz/Peer)

1. **Routen-Identität:** Option 1 (zwei frei gewählte Videos, empfohlen) oder Option 2 (`ClimbEntity`
   für echte Routen-Verknüpfung)? — bestimmt Datenmodell & Migration (A.2).
2. **Videoquelle:** In-App-Aufnahme (CameraX) *und* Import, oder reicht Import bestehender Videos?
3. **Anker-Erfassung:** manuelles Antippen (empfohlen, robust) akzeptiert, oder wird
   automatische Griff-Detektion erwartet? (Automatik = deutlich mehr Risiko/Aufwand.)
4. **Kalibrierdaten:** Können ~10 gelabelte Versuchspaare (echte Wand, feste Kamera) bereitgestellt
   werden? Ohne sie bleiben Sturzerkennung & Modus-Schwellen ungenau (A.6).
5. **OpenCV akzeptiert?** Native Lib vergrößert die APK — ok, oder Homographie-Alternative gewünscht?
6. **Kamera-Setup:** feste Kamera (Stativ) als Annahme ok? Bewegte Kamera macht die Homographie
   pro Frame nötig (großer Mehraufwand).

---
---

# Anhang B — Fable-5-Auftrag: Automatische Hangboard-Erkennung (Uhr)

> **An Fable 5:** Advanced-Ausbau des Wear-Companion-Timers aus 7.2. Baut auf dem `:wear`-Modul
> (7.2.1) und der Data-Layer-Anbindung (7.2.4) auf — **stelle sicher, dass die stehen, bevor du
> beginnst.** Lies `03 – Architektur & Tech/Multi-Device-Strategie.md`. Entscheidungen →
> `04 – Entwicklung/Code-Entscheidungen.md`, Sensor-/Kalibrier-Erkenntnisse zusätzlich in
> `04 – Entwicklung/Bugs & Fixes.md`.

## B.0 Ziel & Nicht-Ziele

**Ziel:** Die **Hangboard-Session auf der Uhr automatisieren**. Statt fester Sätze/Pausen erkennt
die Uhr per Sensorik selbst:
- **Hängen erkannt → Satz-Timer startet** (misst die Hängedauer).
- **Loslassen erkannt → Satz stoppt**, die **Pausenlänge wird gemessen** (variabel, *keine* feste
  Vorgabe).
- **Erneutes Hängen → nächster Satz startet** automatisch.
- … bis der Nutzer die **Hangboard-Session auf der Uhr manuell beendet**.
Ergebnis = eine automatisch protokollierte Session aus N Sätzen mit je gemessener Hänge-/Pausenzeit,
die — falls auf dem Phone eine aktive Kletter-Session läuft — dorthin getrackt wird.

**Nicht-Ziele:**
- Keine Griff-/Übungs-Erkennung (nur „hängt / hängt nicht").
- Keine medizinische/Kraft-Auswertung.
- Der bestehende **manuelle** Companion-Timer (7.2) bleibt als Fallback erhalten — die Automatik ist
  ein zusätzlicher Modus.

## B.1 Tech-Stack (Wear)

- **Sensorik:** `SensorManager` mit `TYPE_ACCELEROMETER` + `TYPE_GRAVITY` (Orientierung) und/oder
  `TYPE_LINEAR_ACCELERATION` (Bewegung ohne Gravitation); optional `TYPE_GYROSCOPE`. Sampling
  `SENSOR_DELAY_GAME` (~50 Hz) reicht.
- **Dauerbetrieb:** Erfassung während der Session in einem **Foreground Service** auf der Uhr
  (sonst schläft die CPU) + Wakelock so kurz wie möglich. Akku beachten.
- **Haptik:** `Vibrator`/`VibratorManager` — kurzes Feedback bei erkanntem Satz-Start/-Ende.
- **Sync:** `play-services-wearable` Data Layer (schon in 7.2.2 eingebunden) → fertige Auto-Session
  ans Phone.
- **Optional:** `androidx.health:health-services-client` (Herzfrequenz als *zusätzliches* Signal),
  nur wenn Rohsensorik allein zu unzuverlässig ist.

## B.2 Detektionslogik — Zustandsmaschine

Kern ist eine kleine, **gut testbare** State-Machine, die aus dem Sensorstrom Ereignisse ableitet.
Halte sie **frei von Android-Abhängigkeiten** (reine Kotlin-Funktion `f(sensorSample) -> stateEvent`),
damit sie mit aufgezeichneten Logs unit-testbar ist.

Zustände: `IDLE → HANGING ⇄ RESTING → (ENDED)`.

Signal-Heuristik (Startpunkt, **muss kalibriert werden**, B.5):
- **Hängen (Dead Hang):** Arm über Kopf **und** geringe Bewegung. → Gravitationsvektor zeigt entlang
  einer bestimmten Achse (Arm gehoben) **und** Varianz der linearen Beschleunigung über ein
  gleitendes Fenster (z. B. 1 s) **unter** Schwelle `σ_still`.
- **Übergang HANGING→RESTING (Loslassen):** Bewegungs-Spike (Varianz über `σ_move`) **oder**
  Orientierungswechsel (Arm sinkt) über eine Mindestdauer → entprellen, um Zappeln/Ausschütteln
  nicht als Satzende zu werten.
- **RESTING→HANGING:** erneut „Hängen"-Bedingung stabil über `t_min_hang` (z. B. 2 s), damit kurzes
  Antippen keinen Satz auslöst.
- **Mindestdauern** gegen Fehlauslösung: `t_min_hang`, `t_min_rest`, Debounce an jedem Übergang.

Jeder abgeschlossene Zyklus erzeugt ein Segment `{ hangMs, restMsDanach }`.

## B.3 Datenmodell — variable Segmente

Die bestehende `HangboardSessionEntity` hat **feste** `hangSec/restSec/totalSets` → passt **nicht**
für variable Auto-Sessions. Zwei Optionen (kläre B.6 Frage 3):
- **Option 1 (empfohlen):** neue Entity `AutoHangboardSessionEntity` (`id`, `sessionId` FK nullable,
  `startedAt`, `endedAt`, `mode = AUTO`) + `HangboardSegmentEntity` (`id`, `parentId` FK, `index`,
  `hangMs`, `restMs`). Sauber abfragbar für Statistik.
- **Option 2 (schnell):** Segmentliste als JSON-Feld an einer erweiterten `HangboardSessionEntity`.
  Weniger Schema-Arbeit, schlechter auswertbar.
Beides erfordert **Schema-Migration** (aktuell v2 → v3, echte `Migration`).

## B.4 Sync zur Phone-Session (Data Layer)

- Uhr sammelt die Segmente **lokal** während der Session (Companion, aber Detektion läuft
  eigenständig auf der Uhr).
- Bei **„Session beenden"** auf der Uhr: Gesamtergebnis (Segmentliste + Summen) via `DataClient`/
  `MessageClient` ans Phone senden.
- Auf dem Phone nimmt ein `WearableListenerService` das entgegen und schreibt es — **falls
  `sessionRepository.observeActive()` eine aktive Session liefert** — in genau diese Session
  (analog zum bestehenden `recordWorkout()` im `HangboardTimerViewModel`, aber mit den echten
  Auto-Segmenten). Keine aktive Session → nur lokal auf der Uhr protokollieren / verwerfen (kläre
  B.6 Frage 4).

## B.5 Kalibrierung (essentiell — nicht raten)

Die Schwellen (`σ_still`, `σ_move`, Orientierungsachse/-toleranz, `t_min_hang`, `t_min_rest`,
Debounce) **müssen an echten Aufnahmen** bestimmt werden. Vorgehen:
- [ ] **B.5.1** Debug-Modus in die Wear-App bauen, der den **rohen Sensorstrom mit Labels** („jetzt
  Hängen / jetzt Pause") aufzeichnet und exportiert (z. B. via Data Layer ans Phone oder in eine
  Datei).
- [ ] **B.5.2** Ein paar echte Hangboard-Durchgänge aufnehmen (verschiedene Griffe/Intensitäten).
- [ ] **B.5.3** Offline Schwellen bestimmen (Histogramme, Trennlinien), als zentrale Config ablegen.
- [ ] **B.5.4** State-Machine gegen die aufgezeichneten Logs **unit-testen** (B.2 ist dafür
  Android-frei gebaut).

## B.6 UI (folgt der Funktion — Compose for Wear OS)

- **Modus-Wahl** beim Öffnen des Timers: „Manuell" (7.2) vs. „Auto".
- **Auto-Screen:** großer Status (`IDLE/HÄNGT: 00:07 / PAUSE: 00:12`), live hochzählend; aktueller
  Satz-Index; deutliches **Vibrations-Feedback** bei Satz-Start/-Ende; ein einziger prominenter
  **„Session beenden"**-Button (Bestätigung, damit nicht versehentlich).
- Nach dem Beenden: Kurz-Zusammenfassung (N Sätze, Gesamt-Hängezeit) + Hinweis, ob ans Phone
  übertragen.
- Wear-typisch: wenige, große Touch-Ziele; funktioniert am Handgelenk mit gestrecktem Arm ablesbar.

## B.7 Reihenfolge / Meilensteine

M1: Foreground Service + Sensor-Logging + Debug-Export (B.5.1).
M2: State-Machine (B.2) offline gegen Logs getestet.
M3: Live-Auto-Screen auf der Uhr mit Haptik (B.6), lokale Segment-Erfassung (B.3).
M4: Data-Layer-Übertragung ans Phone + Eintrag in aktive Session (B.4).
M5: Kalibrierung final + Akku-Check.

## B.8 MUSS vor Start geklärt werden (an Deniz/Peer)

1. **Kalibrierdaten/Hardware:** Gibt es Zugang zu **Hangboard + Wear-Gerät** (kein Emulator — der
   liefert keine echten Sensordaten), um Sensor-Logs aufzunehmen? Ohne echte Daten ist die
   Detektion nicht kalibrierbar. **Wichtigste Frage.**
2. **Trage-Arm:** An welchem Arm sitzt die Uhr relativ zum Hängen? Beide Arme hängen — reicht das
   Signal des Uhr-Arms, oder braucht es Annahmen? (beeinflusst Orientierungsachse in B.2)
3. **Datenmodell:** Option 1 (`AutoHangboardSessionEntity` + Segmente, empfohlen) oder Option 2
   (JSON-Segmentliste)? (B.3)
4. **Keine aktive Phone-Session:** Auto-Session dann nur lokal auf der Uhr behalten, verwerfen, oder
   als „lose" Hangboard-Session ohne Kletter-Session-Bezug auf dem Phone speichern? (B.4)
5. **Genauigkeitsanspruch:** Reicht „gut genug für Demo/Doku" (Abgabe-Kontext) oder wird
   zuverlässige Alltagstauglichkeit erwartet? (steckt den Kalibrieraufwand ab)
