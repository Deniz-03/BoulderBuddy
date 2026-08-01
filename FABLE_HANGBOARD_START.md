# 🏁 Fable-5 START-KONTEXT — Automatische Hangboard-Erkennung (Uhr, Anhang B)

> **Lies NUR dieses Dokument + [Anhang B in `PHASE7_PLAN.md`](PHASE7_PLAN.md#anhang-b--fable-5-auftrag-automatische-hangboard-erkennung-uhr).**
> Es fasst alle Repo-Fakten und finalen Entscheidungen (Stand 2026-07-06) zusammen, damit du
> **nicht explorieren musst**. Wo dieses Dokument Anhang B widerspricht, **gilt dieses Dokument**.
>
> **An Fable 5:** Dies ist dein vollständiger Arbeitsauftrag für die automatische
> Hangboard-Erkennung auf der Uhr — der Advanced-Ausbau des Wear-Companion-Timers (7.2). **Wichtig:**
> Dieser Auftrag ist an ein **übergeordnetes, einheitliches UX-Konzept für den gesamten
> Hangboard-Timer gebunden (§0)** — du baust nicht nur die Auto-Erkennung dazu, sondern ziehst den
> **bestehenden manuellen Pfad auf dasselbe vereinte Modell** um. Das `:wear`-Modul und die
> einseitige Data-Layer-Anbindung (Uhr → Phone) **existieren bereits & bauen grün** (s. §3). Lies
> vorab `03 – Architektur & Tech/Multi-Device-Strategie.md` im Obsidian-Vault. Trage jede
> Architektur-Entscheidung in `04 – Entwicklung/Code-Entscheidungen.md` nach, Sensor-/Kalibrier-
> Erkenntnisse zusätzlich in `04 – Entwicklung/Bugs & Fixes.md`. Baue nach jedem Meilenstein grün
> (`:wear:assembleDebug` + `:app:assembleDebug`) und committe.

---

## 0. Übergeordnetes UX-Konzept: „Hangboard-Workout" als *ein* Ding (2026-07-06, verbindlich)

Der bisherige Zustand war **keine konsistente UX**: Config-Persistenz unterschied sich je Gerät, Presets
gab es nur auf dem Phone, das Ergebnis wurde ohne aktive Session lautlos verworfen, und „keine Session"
sollte je nach Modus (manuell vs. auto) etwas Gegensätzliches bedeuten. Dahinter lagen **zwei parallele
Datenmodelle**. Dieses Konzept vereint alles.

**Leitgedanke:** Ein Hangboard-Workout ist ein Hangboard-Workout — **egal ob Phone oder Uhr, egal ob
manuell oder auto.** Immer gleich gespeichert, gleich angezeigt, gleich mit einer Session verknüpft.

**Säule 1 — Ein vereintes Datenmodell (ersetzt `HangboardSessionEntity` UND das in Anhang B geplante
`AutoHangboardSessionEntity`):**
```
HangboardWorkoutEntity(
  id,
  sessionId: Int?,                 // null = eigenständig, gesetzt = an Kletter-Session gehängt
  mode: MANUAL | AUTO,
  origin: PHONE | WATCH,
  startedAt, endedAt,
  plannedSets, plannedHangSec, plannedRestSec   // Snapshot der Vorgabe; bei AUTO nullable/leer
)
HangboardSegmentEntity(id, workoutId FK CASCADE, index, hangMs, restMs)
```
Manueller Durchlauf = Segmente aus der Vorgabe (identische Dauern); Auto-Durchlauf = gemessene Segmente.
**Eine** Abfrage für die gesamte Hangboard-Statistik über Phone+Uhr, manuell+auto.

**Säule 2 — „Immer speichern, verknüpfen wenn möglich" (die verbindliche Antwort auf ‚keine Session'):**
Jedes beendete Workout wird gespeichert. Läuft eine Kletter-Session → wird es **angehängt** (`sessionId`
gesetzt, erscheint im Session-Detail). Läuft keine → **eigenständiges** Hangboard-Training
(`sessionId = null`), sichtbar in der Hangboard-Historie/Statistik. **Nie verworfen, nie blockiert** —
identisch für manuell/auto/Phone/Uhr. **Die Verknüpfungs-Entscheidung fällt an EINER Stelle:** dort, wo
das fertige Workout persistiert wird (Phone-VM bzw. Phone-Listener), per
`sessionRepository.observeActive().first()?.id`.

> ⚠️ **Das kehrt die frühere B.8-Q4-Antwort (‚Auto an aktive Session binden / sonst blockieren') um.**
> Bewusste Entscheidung (2026-07-06) zugunsten von Konsistenz & Null-Datenverlust. Ein **Gate/eine
> bidirektionale Zustandsabfrage der Uhr entfällt damit** (vereinfacht §2/§8).

**Säule 3 — Klares Feedback statt Stille:** Nach jedem DONE eine Kurz-Zusammenfassung (N Sätze,
Gesamt-Hängezeit) **plus wohin gespeichert wurde**: „In Session *Halle X* gespeichert" bzw. „Als
eigenständiges Hangboard-Training gespeichert". Auf dem Phone ist diese Angabe **definitiv**; auf der Uhr
best-effort („an Phone übertragen"), da die Verknüpfung auf dem Phone entschieden wird (optionaler
Ack-Rückkanal Phone→Uhr = Kür).

**Säule 4 — Config & Presets überall gleich:** Presets werden die *eine* Quelle gespeicherter
Timer-Einstellungen — **auch auf der Uhr**. Das Phone publiziert Preset-Liste + „zuletzt genutzt" via
Data Layer (`DataClient`/DataItem, Phone→Uhr); die Uhr liest sie und **persistiert die zuletzt genutzte
Config lokal** (kleiner DataStore), statt bei jedem Öffnen auf 6/7/3 zurückzuspringen.

**Konsequenz — du fasst auch den bestehenden Code an (nicht nur neu bauen):**
- Phone [`HangboardTimerViewModel.recordWorkout()`](app/src/main/java/com/boulderbuddy/ui/viewmodel/HangboardTimerViewModel.kt#L199)
  → schreibt künftig `HangboardWorkoutEntity` (+ Segmente), **immer** (Standalone bei fehlender Session)
  statt bei fehlender Session `return`.
- Phone [`HangboardWearListenerService`](app/src/main/java/com/boulderbuddy/wearsync/HangboardWearListenerService.kt)
  → dito, plus Auto-Payload (Segmente).
- Statistik (`StatistikViewModel` + zugehörige Queries) liest künftig `hangboard_workout`/`_segment`
  statt `hangboard_session`.
- Session-Detail-UI (Hangboard-Block) liest das neue Modell.
- Neue Oberfläche für **eigenständige** Hangboard-Trainings (Historie/Statistik) — minimal, aber
  vorhanden, damit Standalone-Workouts sichtbar sind (sonst wäre „immer speichern" wieder unsichtbar).

**Säule 5 — Sichtbarkeit: wo ein Workout auftaucht (3-Sichten-Modell, EIN Datenmodell):**

| Sicht | Was sie zeigt | Query | Status heute |
|-------|---------------|-------|--------------|
| **Session-Detail** (Hangboard-Block) | Workouts *dieser* Session | `sessionId = X` | existiert (`observeBySession`) → auf neues Modell umziehen |
| **Hangboard-Historie** *(neu)* | *alle* Workouts, je Eintrag Datum · Modus (Manuell/Auto) · Sätze · Hängezeit + Label **„eigenständig"** bzw. **„· Halle X"** | alle, `date DESC` | **fehlt komplett → bauen** |
| **Statistik** (Aggregat) | Summen über *alle* Workouts (Anzahl, Sätze, Gesamt-Hängezeit) | alle | existiert, **session-unabhängig** |

- **Die Aggregat-Statistik ist gratis konsistent.** `StatistikViewModel` aggregiert bereits `observeAll()`
  (DAO = `SELECT * FROM … ORDER BY date DESC`, **ohne Session-Filter**). Sobald `sessionId` nullable ist,
  fließen Standalone-Workouts **ohne Query-Änderung** in dieselben Summen.
- **Der einzige echte UI-Neubau ist die Hangboard-Historie.** Ohne sie wäre ein sessionloses Workout nur
  anonym in den Summen, nirgends als Eintrag — „immer speichern" bliebe unsichtbar. Natürlicher Einstieg:
  den bestehenden Hangboard-Block im Statistik-Screen **antippbar** machen → Liste; alternativ Einstieg vom
  Timer. Function-first, bestehende `ui/components`-Bausteine + Theme-Tokens.
- **Hängezeit wird genauer (nicht nur konsistent):** heute rechnet die Statistik `completedSets * hangSec`
  (Plan-Wert). Mit den Segmenten wird sie zur **Summe der gemessenen `hangMs`** — Auto-Workouts werden
  dadurch korrekt, manuelle bleiben es. Query/Aggregation entsprechend auf Segmente umstellen.
- **Heatmap-Nuance (bewusst entscheiden):** Die Aktivitäts-Heatmap zählt heute **nur Routen** über das
  **Session**-Datum. Ein eigenständiges Hangboard-Training an einem kletterfreien Tag lässt die Heatmap
  **nicht** aufleuchten. Wenn „Training = Aktivität" gelten soll (empfohlen für Konsistenz), die Heatmap 
  zusätzlich aus Workout-Daten speisen — sonst dokumentieren, dass sie bewusst nur Kletter-Aktivität zeigt.

---

## 1. Finale Entscheidungen (B.8 + UX-Konzept — geklärt, verbindlich)

| # | Frage | Entscheidung |
|---|-------|-------------|
| 1 / 5 | Kalibrierdaten / Genauigkeit | **Echte Hardware verfügbar.** Zugang zu Hangboard + echter Wear-Uhr → **echte Kalibrierung**. Debug-Logging (B.5.1) als erster Meilenstein, echte Durchgänge aufnehmen, Schwellen offline bestimmen. Emulator liefert **keine** echten Beschleunigungsdaten → Detektions-Verifikation auf dem Gerät. |
| 2 | Trage-Arm | **Uhr-Arm hängt mit.** Uhr sitzt an einem der hängenden Arme → Orientierungsachse „Arm über Kopf" als Startpunkt der Heuristik (B.2), per Kalibrierung verfeinert. |
| 3 | Datenmodell | **Vereintes Modell (§0 Säule 1):** `HangboardWorkoutEntity` + `HangboardSegmentEntity` — **ersetzt** `HangboardSessionEntity`. **DB v5 → v6, destruktiv** (keine Migration — s. §4). Gilt für manuell UND auto. |
| 4 | Keine aktive Phone-Session | **„Immer speichern, verknüpfen wenn möglich" (§0 Säule 2).** Kein Gate, kein Verwerfen: aktive Session → anhängen, sonst eigenständiges Hangboard-Training. **Kehrt die alte Q4-Antwort um.** |

---

## 2. Data Layer — was jetzt (nach Wegfall des Gates) über die Uhr↔Phone-Kopplung läuft

Weil §0 das Gate streicht, muss die Uhr den Phone-Session-Zustand **nicht** blockierend abfragen — das
vereinfacht die Kopplung. Es bleiben zwei **je einseitige** Publish-Kanäle:

1. **Ergebnis Uhr → Phone (bestehend, erweitern):** fertiges Workout (manuell: wie bisher; auto:
   Segmentliste) via `MessageClient`/`DataClient`. Die Verknüpfung mit einer Session entscheidet das
   **Phone** beim Empfang (`observeActive()` → anhängen oder Standalone). Basis:
   `PhoneConnector` + `WearSyncContract` + `HangboardWearListenerService` (§3).
2. **Presets/Config Phone → Uhr (neu, §0 Säule 4):** Phone publiziert Preset-Liste + „zuletzt genutzt"
   als `DataItem` (`DataClient`) unter z. B. `/boulderbuddy/hangboard_presets`; die Uhr liest/beobachtet
   sie. `DataItem` wird vom System synchronisiert & gecacht (überlebt kurze Trennung) → ideal für „letzter
   bekannter Stand". Contract-Pfad wie üblich **in beiden Modulen gespiegelt** (getrennte Module).

> Für viele Segmente ist eine kompaktere Payload als der bestehende Text-Encoder sinnvoll (z. B.
> `DataItem` mit Byte-Array oder `kotlinx-serialization`-JSON — ist im Projekt vorhanden).

---

## 3. `:wear`-Modul & Data Layer — bereits vorhanden (wiederverwenden, nicht neu bauen)

Das Companion-Timer-Fundament aus **7.2 steht und baut grün.** Deine Basis:

**Wear-Modul (`:wear`, `namespace = com.boulderbuddy.wear`, `applicationId = "com.boulderbuddy"`
— gleich wie `:app`, Pflicht für Data Layer):**
- [`presentation/MainActivity.kt`](wear/src/main/java/com/boulderbuddy/wear/presentation/MainActivity.kt)
  — `ComponentActivity`, kein Hilt auf der Uhr (bewusst). → [`WearApp.kt`](wear/src/main/java/com/boulderbuddy/wear/presentation/WearApp.kt)
  (Compose-Root, Wear-`MaterialTheme`, **aktuell nur ein Screen, noch keine Wear-Navigation** — für die
  Modus-Wahl manuell/auto ergänzt du eine kleine Navigation, `wear.compose.navigation` ist schon drin).
- [`presentation/TimerScreen.kt`](wear/src/main/java/com/boulderbuddy/wear/presentation/TimerScreen.kt)
  + [`presentation/TimerViewModel.kt`](wear/src/main/java/com/boulderbuddy/wear/presentation/TimerViewModel.kt)
  — der **manuelle** Timer (Zustandsmaschine `HANG → REST → DONE`, `AndroidViewModel`,
  Vibrations-Feedback via `VibratorManager`/`Vibrator`, `viewModelScope` + `delay`). **Muster für dein
  Auto-ViewModel** (Haptik-Helfer `vibrate()`/`resolveVibrator()` übernehmen). ⚠️ Dieser manuelle Timer
  bekommt gemäß §0 die lokale Config-Persistenz + Preset-Anzeige.
- [`data/PhoneConnector.kt`](wear/src/main/java/com/boulderbuddy/wear/data/PhoneConnector.kt)
  — `object`, sendet bei DONE via `MessageClient`. **Erweitern** um das Senden des Auto-Ergebnisses
  (Segmente), analog `sendHangboardCompleted(...)`.
- [`data/WearSyncContract.kt`](wear/src/main/java/com/boulderbuddy/wear/data/WearSyncContract.kt)
  — Pfad `/boulderbuddy/hangboard_completed` + Text-`encode(...)`. **Neuen Pfad + Encoder** für die
  Auto-Segmente ergänzen; **neuen Lese-Pfad** für Presets (§2 Kanal 2).

**Phone-Seite (`:app`):**
- [`wearsync/HangboardWearListenerService.kt`](app/src/main/java/com/boulderbuddy/wearsync/HangboardWearListenerService.kt)
  — `@AndroidEntryPoint`-`WearableListenerService`, im [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml)
  registriert (Intent-Filter `MESSAGE_RECEIVED`, `pathPrefix="/boulderbuddy/hangboard_completed"`).
  Injiziert `SessionRepository` + Repository, schreibt das Workout. **Gemäß §0 umbauen:** schreibt jetzt
  `HangboardWorkoutEntity`(+Segmente) **immer** (Standalone bei fehlender Session), plus neuer
  Pfad/Parser für Auto-Segmente. Neue Pfade im Manifest-`pathPrefix` ergänzen.
- ⚠️ Hilt-`@AndroidEntryPoint`-Service zog historisch `error_prone_annotations` als `:app`-Dependency
  nach — ist bereits drin, nur zur Info.

**Wear-Dependencies** ([`wear/build.gradle.kts`](wear/build.gradle.kts)): Compose-for-Wear
(`wear.compose.material/foundation/navigation`), `androidx.wear`, **`play-services-wearable` (Data Layer,
schon drin)**, Compose-BOM geteilt mit `:app`. **Manifest** hat bereits `VIBRATE`, `<uses-feature ...watch>`,
`standalone=false`.

**Was du NEU brauchst:**
- Auf der Uhr: **Foreground-Service-Berechtigungen** (`FOREGROUND_SERVICE` + passender FGS-Typ für die
  Sensor-Erfassung), ggf. `WAKE_LOCK`, `BODY_SENSORS` nur falls Health Services / Herzfrequenz (optional,
  B.1). `SensorManager` selbst braucht **keine** Permission. Für die Config-Persistenz auf der Uhr einen
  kleinen `DataStore` (Preferences) — Dependency im Wear-Katalog ergänzen.
- Kein neues native Dependency.

---

## 4. Datenbank — DB steht auf v5, destruktiv (kein Handschreiben), vereintes Modell

- Aktuelle Version ist **v5**. Datei:
  [`data/db/BoulderBuddyDatabase.kt`](app/src/main/java/com/boulderbuddy/data/db/BoulderBuddyDatabase.kt).
  Provider nutzt **`.fallbackToDestructiveMigration(dropAllTables = true)`** in
  [`di/DatabaseModule.kt`](app/src/main/java/com/boulderbuddy/di/DatabaseModule.kt) (pre-Release, keine
  Bestandsnutzer).
- **→ KEINE handgeschriebene `Migration`.** Vorgehen (identisch zu Ghost Climber v4→v5):
  1. `HangboardWorkoutEntity` + `HangboardSegmentEntity` in `data/db/entity/` anlegen (Muster:
     [`entity/HangboardSessionEntity.kt`](app/src/main/java/com/boulderbuddy/data/db/entity/HangboardSessionEntity.kt)
     — `@Entity`, FK auf `SessionEntity` mit `onDelete = CASCADE`, `@Index`; Segment-FK auf Workout mit
     CASCADE). **`sessionId` bewusst `nullable`** (Standalone-Workouts, §0 Säule 2).
  2. **Alte `HangboardSessionEntity` + `HangboardSessionDao` + `HangboardSessionRepository` entfernen/
     ersetzen** durch das vereinte Modell (destruktiv → kein Datenverlust-Risiko).
  3. Beide neuen Entities in `entities = [...]` von `BoulderBuddyDatabase`, DAO-Getter ergänzen,
     `version = 5` → `6`, Kommentarblock (v6) ergänzen.
  4. DAO-Provider in `DatabaseModule`; Repository-Interface+Impl in `data/repository/` (Muster:
     [`repository/HangboardSessionRepository.kt`](app/src/main/java/com/boulderbuddy/data/repository/HangboardSessionRepository.kt)),
     `@Binds` in [`di/RepositoryModule.kt`](app/src/main/java/com/boulderbuddy/di/RepositoryModule.kt).
  5. **Aufrufer nachziehen:** Phone-`HangboardTimerViewModel`, `HangboardWearListenerService`,
     `StatistikViewModel` (+ Queries), Session-Detail-UI, Seed-/Testdaten, betroffene Tests (7.6).
- `exportSchema = true` ist an → neues Schema in `app/schemas/` mitcommitten.

---

## 5. Tech-Stack (Wear) — Auto-Detektion

- **Sensorik:** `SensorManager` mit `TYPE_GRAVITY` (Arm-Orientierung „über Kopf") + `TYPE_LINEAR_ACCELERATION`
  (Bewegung ohne Gravitation → Stille/Bewegungs-Varianz). Optional `TYPE_ACCELEROMETER` roh /
  `TYPE_GYROSCOPE`. Sampling `SENSOR_DELAY_GAME` (~50 Hz) reicht.
- **Dauerbetrieb:** **Foreground Service** auf der Uhr für die Sensor-Erfassung während der Session
  (sonst schläft die CPU), kurzer Wakelock. Akku im Blick (M5 = Akku-Check).
- **Detektions-State-Machine (B.2) Android-frei halten:** reine Kotlin-Funktion
  `f(sensorSample, state) -> stateEvent`, Zustände `IDLE → HANGING ⇄ RESTING → ENDED`, damit sie gegen
  aufgezeichnete Logs **unit-testbar** ist (M2). Schwellen (`σ_still`, `σ_move`, Orientierungsachse/
  -toleranz, `t_min_hang`, `t_min_rest`, Debounce) als **zentrale, benannte Config**. Jeder Zyklus erzeugt
  ein Segment `{ hangMs, restMsDanach }` → passt 1:1 auf `HangboardSegmentEntity`.
- **Haptik:** `vibrate()`/`resolveVibrator()` aus dem bestehenden `TimerViewModel` übernehmen.
- **Sync:** Ergebnis (Segmente) ans Phone (§2 Kanal 1); Presets von dort (§2 Kanal 2).

---

## 6. UI (Function-first, Compose for Wear OS)

- **Modus-Wahl beim Öffnen:** „Manuell" (bestehender `TimerScreen`) vs. „Auto". Dafür kleine
  Wear-Navigation (bisher zeigt `WearApp()` direkt `TimerScreen()`). `wear.compose.navigation` ist schon
  drin.
- **Kein Start-Gate mehr** (§0): Der Timer startet immer, unabhängig davon, ob das Phone eine Session hat.
- **Manueller Screen:** wie bisher, aber Config **persistiert lokal** (kein Reset auf 6/7/3) und zeigt die
  **vom Phone synchronisierten Presets** (§2 Kanal 2) zur Auswahl.
- **Auto-Screen:** großer Live-Status (`IDLE` / `HÄNGT 00:07` / `PAUSE 00:12`), hochzählend; aktueller
  Satz-Index; deutliches Vibrations-Feedback bei Satz-Start/-Ende; **ein** prominenter „Session beenden"-
  Button **mit Bestätigung**.
- **Nach Beenden (beide Modi, §0 Säule 3):** Kurz-Zusammenfassung (N Sätze, Gesamt-Hängezeit) + „an Phone
  übertragen" (Uhr) bzw. definitiver Speicherort auf dem Phone.
- **Phone-Seite:** dieselbe Zusammenfassung + **klarer Speicherort-Hinweis** („In Session *Halle X*"
  bzw. „Eigenständiges Hangboard-Training"). Neue, schlanke **Standalone-Hangboard-Historie** (Liste/
  Statistik), damit sessionlose Workouts sichtbar sind.
- Wear-typisch: wenige, große Touch-Ziele, am gestreckten Arm ablesbar; bestehende Wear-`MaterialTheme`-
  Tokens; auf dem Phone semantische Theme-Tokens (Dark Mode aktiv seit 7.4a).

---

## 7. Kalibrierung (echte Hardware vorhanden → nicht raten)

Weil Hardware verfügbar ist (Q1), ist Kalibrierung **Pflicht**:
- [ ] **B.5.1 Debug-Logging-Modus** in die Wear-App: roher Sensorstrom **mit Labels** („jetzt Hängen /
  jetzt Pause") aufzeichnen und exportieren (Data Layer → Phone-Datei oder direkt auf der Uhr). **= M1.**
- [ ] **B.5.2** Echte Hangboard-Durchgänge aufnehmen (verschiedene Griffe/Intensitäten, Uhr am hängenden
  Arm gem. Q2).
- [ ] **B.5.3** Schwellen offline bestimmen (Histogramme, Trennlinien) → zentrale Config.
- [ ] **B.5.4** State-Machine (B.2) gegen die aufgezeichneten Logs **unit-testen** (JVM, Android-frei).

---

## 8. Meilensteine (jeder einzeln lauffähig, ein grüner Build = ein Commit)

| M | Inhalt | Demo-fähig |
|---|--------|-----------|
| **M0** | **Vereintes Datenmodell (§0/§4)** + bestehenden manuellen Pfad (Phone-VM, Wear-Listener, Statistik, Session-Detail) darauf umziehen, **„immer speichern"** inkl. Standalone. Tests grün. | Manueller Timer speichert konsistent, auch ohne Session |
| **M1** | Foreground Service + Sensor-Logging + Debug-Export (B.5.1). | Sensorstrom aufnehmbar |
| **M2** | State-Machine (B.2), offline gegen Logs unit-getestet (B.5.4). | grüne Unit-Tests |
| **M3** | Live-Auto-Screen + Haptik + lokale Segment-Erfassung + **Modus-Wahl** manuell/auto (§6). | Uhr erkennt Sätze live |
| **M4** | Auto-Ergebnis Uhr → Phone, Eintrag ins vereinte Modell (anhängen/Standalone, §2/§4) + **Preset-Sync Phone → Uhr** (§0 Säule 4). | Ende-zu-Ende + Presets auf der Uhr |
| **M5** | Kalibrierung final (B.5.3) + Akku-Check. | belastbare Schwellen |

> **M0 zuerst** — das vereinte Modell ist das Fundament; ohne es baust du die Auto-Erkennung wieder in eine
> inkonsistente Struktur. Danach folgt Anhang B.7 (M1 = Logging als Enabler für M2/M5).

---

## 9. Arbeitsweise (wie im restlichen Projekt)

- **Build:** `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`, dann
  `& ".\gradlew.bat" :wear:assembleDebug :app:assembleDebug --console=plain`. **AGP-9-Fallen** (Hilt ≥ 2.60,
  `android.disallowKotlinSourceSets=false`) gelten projektweit.
- **Ein Meilenstein = ein grüner Build = ein Commit.** State-Machine-Unit-Tests auf der JVM; Sensor-/
  Detektions-Verifikation **auf dem echten Gerät** (Emulator liefert keine Sensordaten).
- **Doku-Pflicht (Obsidian-Vault):** jede Architektur-Entscheidung (v. a. „vereintes Hangboard-Workout-
  Modell v6", „immer speichern / Standalone statt Gate", „Preset-Sync Phone→Uhr", State-Machine
  Android-frei) → `04 – Entwicklung/Code-Entscheidungen.md`; Sensor-/Kalibrier-Erkenntnisse +
  Stolpersteine → `04 – Entwicklung/Bugs & Fixes.md`; Fortschritt → `Sprint-Log.md`.
- **Algorithmische Spezifikation:** Anhang B.2 (State-Machine) + B.5 (Kalibrierung) bleiben Vorlage; §0
  liefert das UX-Konzept, dieses Dokument die Repo-Anker und die verbindlichen Entscheidungen.

---

## 10. Haupt-Risiken, die du früh adressieren solltest

1. **M0 nicht überspringen.** Baust du die Auto-Erkennung vor dem vereinten Modell, zementierst du die
   Inkonsistenz, die dieses Konzept gerade behebt.
2. **Ohne echte Logs kein sinnvoller Algorithmus.** Deshalb M1 (Logging) vor der State-Machine.
3. **Fehlauslösungen** (Zappeln/Ausschütteln als Satzende): Debounce + Mindestdauern (`t_min_hang`,
   `t_min_rest`) sind Pflicht (B.2).
4. **Foreground Service + Akku** auf der Uhr: FGS-Typ korrekt deklarieren, Wakelock minimal.
5. **Session endet während des Hangboards:** Die Verknüpfung wird erst **beim Persistieren** (Ende)
   entschieden — endet die Session vorher, wird das Workout eben Standalone. `sessionId` `nullable`, beim
   Schreiben erneut `observeActive()` prüfen; nicht crashen.
