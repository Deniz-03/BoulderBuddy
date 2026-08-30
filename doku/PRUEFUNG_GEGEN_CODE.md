# Projektbericht gegen den Code geprüft

Geprüft am 30.08.2026 gegen `main` @ `c30ac55`. Grundlage ist `AppDokumentation.pdf`
(26 Seiten) in diesem Ordner.

Jede Aussage des Berichts, die eine Zahl, einen Klassennamen oder ein Android-Detail nennt,
wurde im Quelltext nachgesehen. Was unten unter **A** steht, ist belegt falsch; **B** ist
unvollständig; **C** ist geprüft und richtig — das steht hier, damit beim Nachbessern
niemand aus Versehen etwas anfasst, das stimmt.

---

## A — Sachlich falsch

### A1. Datenbank-Version: v11 → **v12**

Vier Stellen nennen v11. Der Code steht auf `version = 12`
(`data/db/BoulderBuddyDatabase.kt:90`); v12 kam mit `ghost_analysis.sessionId`.

| Stelle | Text |
|---|---|
| S. 6, Architekturdiagramm | Kasten „Room / SQLite **(v11)**" |
| S. 7, §3.1 Walk-through, Schritt 4 | „die Zeile in Room / SQLite **(v11)**" |
| S. 9, §3.2 Bibliothekstabelle, Zeile *Room* | „Lokale SQLite-DB **(v11)**" |
| S. 21, §5.1 Feature-Tabelle | „Room / SQLite **(DB V11)**" |

Das Diagramm muss dafür in Draw.io neu exportiert werden.

### A2. Adaptive Layouts: falsche Klasse genannt

§5.1 schreibt „Automatische Umschaltung Handy ↔ Tablet (**MainActivity**, WindowSizeClass)".

Ausgewertet wird die Breite in **`ui/theme/Breite.kt`** über
`currentWindowAdaptiveInfo().windowSizeClass`. `MainActivity` trägt sogar ausdrücklich den
Kommentar, dass die Fensterbreite dort *nicht mehr* durchgereicht wird.

→ „(`ui/theme/Breite.kt`, WindowSizeClass)".

### A3. CSV-Export: MediaStore und SAF vermischt

§5.1 führt „**MediaStore-Export** — Schreibt die Session-Daten als CSV in den
**Download-Ordner**".

Der Session-Export läuft über **SAF**: `ActivityResultContracts.CreateDocument("text/csv")`
(`ui/screens/EinstellungenScreen.kt:132`) — der Nutzer wählt den Ort selbst, es ist gerade
nicht fest der Download-Ordner. **MediaStore/Downloads** benutzt eine andere Stelle: der
Sensor-Log-Export der Uhr in `wearsync/HangboardWearListenerService.kt:157–165`.

→ Entweder den Eintrag auf „Storage Access Framework" umschreiben, oder beide als zwei
Einträge führen. Zwei getrennte Speicher-APIs sind eher ein Plus als ein Problem.

### A4. Photopicker: nicht in den Einstellungen

§5.1: „beim Anlegen einer Route, **in den Einstellungen** und im Ghost Climber".

Verwendet wird er in `RouteHinzufuegenScreen` und `GhostClimberScreen`. In
`EinstellungenScreen` gibt es keinen.

### A5. Klassenname falsch

§5.1 nennt `SystemSpeechRecognitionClient`. Die Klasse heißt
**`SpeechRecognitionClient`** (`data/speech/SpeechRecognitionClient.kt`).

### A6. „erstmals eine Laufzeit-Berechtigung (RECORD_AUDIO)"

§5.1, Eintrag *System-Spracheingabe*. `RECORD_AUDIO` ist nicht die erste
Laufzeit-Berechtigung der App: `ACCESS_FINE_LOCATION` kam mit dem Näherungs-Push
(Meilenstein 6.0, 07.07.), `CAMERA` im selben Meilenstein 9.1 wie die Spracheingabe.

→ „Dafür braucht die Spracheingabe die Laufzeit-Berechtigung RECORD_AUDIO."

### A7. Foreground Services: vier, nicht einer

§5.1 nennt unter *Foreground Service* nur die Sensor-Erfassung auf der Uhr. Tatsächlich:

| Dienst | Modul | Typ | Wozu |
|---|---|---|---|
| `SensorLoggingService` | `:wear` | `health` | Sensor-Aufzeichnung fürs Kalibrieren |
| `AutoHangService` | `:wear` | `health` | Live-Erkennung der Hänge-Sätze |
| `GhostAnalyseService` | `:app` | `dataSync` | Pose-Analyse, ~7 min je Videopaar |
| `AbgleichService` | `:app` | `dataSync` | Geräte-Abgleich, kann Gigabyte umfassen |

Die beiden auf dem Handy sind das stärkere Argument: sie laufen über Minuten und überleben
den Bildschirm. Beide sind bewusst `dataSync`, weil `mediaProcessing` erst ab API 35 gibt
und die App bei 26 beginnt — genau die Sorte Detail, die in dieser Kategorie zählt.

### A8. „die Bilddatei wird nicht kopiert, nur als URI referenziert"

§3.1 Walk-through, Schritt 4. Stimmt **im Moment des Speicherns**, aber nicht dauerhaft:
`sync/MedienUmzug.kt` + `sync/MedienSpeicher.kt` ziehen Galerie-Medien später
inhaltsadressiert in die App-Sandbox um (`filesDir/aufnahmen/<sha256>.<endung>`). Ohne das
wären die Verweise auf dem zweiten Gerät nach dem Abgleich tot.

→ Halbsatz ergänzen: „…nur als URI referenziert; vor dem ersten Geräte-Abgleich zieht die
App Galerie-Medien einmalig inhaltsadressiert in ihren eigenen Speicher um."

---

## B — Unvollständig

### B1. §7 „Future Work" ist leer

Auf S. 26 stehen nur die beiden Überschriften „Possible refactoring:" und „Possible
extensions:" — ohne einen einzigen Satz. Ein leerer Abschnitt in der Abgabe fällt sofort
auf. Aus dem Repo lässt er sich in zehn Minuten füllen; Vorschläge unten unter *Material*.

### B2. Sichtbares TODO im Tagebuch

Meilenstein 10 trägt in der Nummernspalte „(TODO: Datum wenn die Dok fertig ist)".

### B3. Keine Zahlen zum Endstand

Die Testzahlen im Tagebuch sind Momentaufnahmen je Meilenstein (8/8, 28, 105, 109, 162 …).
Der erreichte Stand steht nirgends. Das ist genau der Beleg, der in *Technical Quality*
zählt:

| Prüfung | Stand 30.08.2026 |
|---|---|
| JVM-Unit-Tests (`:app` + `:wear`) | **385 grün**, 1 übersprungen (braucht eine Gerätespur) |
| Instrumented-Tests auf dem Pixel 6a | **57/57 grün** |
| Room-Migrationen | **v1 → v12 vollständig geprüft**, gegen echtes SQLite |
| Android Lint | **0 Fehler**, 46 Warnungen (ausnahmslos „neuere Version verfügbar"), keine Baseline |
| Compiler | beide Module **ohne Warnung** |
| Release-Build | `:app` 72 MB, `:wear` 23 MB, beide grün inkl. `lintVitalRelease` |
| Umfang | 35.717 Zeilen Produktivcode, 10.094 Zeilen Testcode, 442 `@Test` in 71 Dateien |

### B4. Fehlerbehandlung kommt nicht vor

Seit den letzten Durchgängen liegt **jeder** Schreibvorgang in jedem ViewModel in einem
Schutz (`ui/Fehlerkanal.kt`): ein Fehler aus Room beendet nicht mehr die App, sondern
erscheint als Meldung — und wo im Erfolgsfall weiternavigiert wurde, bleibt die Eingabe bei
einem Fehlschlag stehen. Das Schema nennt „error handling" ausdrücklich. Ein kurzer
Abschnitt in §5.2 wäre gut investiert.

### B5. Die Einzige-Activity-Entscheidung wird nicht begründet

Das Bewertungsschema nennt unter *Android Features* wörtlich „Activities, **Fragments**".
Die App hat genau eine Activity und keine Fragments — eine bewusste, richtige Entscheidung,
die aber nirgends dasteht. Ein Satz in §3.1 genügt: Single-Activity + Compose-Navigation mit
typsicheren Routen ersetzt Fragments; der Zustand hängt am `NavBackStackEntry` statt an
einem FragmentManager.

### B6. „Rückgängig" im Geräte-Abgleich fehlt

Der Abgleich kann den letzten Durchgang zurücknehmen (`Abgleicher.machRueckgaengig()`,
Knopf im Abgleich-Screen). Das ist die heikelste Zusicherung des Features und kommt in der
Doku nicht vor — obwohl §3.1 den Abgleich sonst ausführlich begründet.

---

## C — Geprüft und richtig (nicht anfassen)

**§5.2 Implementation Details** ist durchgehend belegt. Nachgesehen und bestätigt:
`SENSOR_DELAY_GAME` (~50 Hz), `TYPE_GRAVITY` + `TYPE_LINEAR_ACCELERATION`,
`PARTIAL_WAKE_LOCK` mit Freigabe in `onDestroy`, FGS-Typ `health`,
`HIGH_SAMPLING_RATE_SENSORS` statt des dialogpflichtigen `BODY_SENSORS`,
`standalone = false`, die Aufteilung MessageClient (Uhr→Handy) / DataClient (Handy→Uhr),
„immer speichern, verknüpfen wenn möglich" mit `sessionId = null`, `@Relation`,
`error_prone_annotations` unter AGP 9, `Quality.HD`, `contentResolver.getType(uri)` statt
eines DB-Feldes, ExoPlayer über `AndroidView` mit `DisposableEffect` + `ON_STOP`/`release()`,
MediaPipe mit 33 Landmarks, `RunningMode.VIDEO` samt separater `RunningMode.IMAGE`-Instanz
für die Diagnose, ARGB_8888, Modell als mitgeliefertes Asset.

**§3.1 Architektur-Begründung** deckt sich mit dem Code, einschließlich des bewusst
weggelassenen `domain`-Layers. Eine Einschränkung: „Die Repositories sind dünne Fassaden
über den DAOs" gilt für die UI-Schicht lückenlos, aber `sync/Abgleicher.kt` und
`sync/MedienUmzug.kt` greifen an den Repositories vorbei direkt auf `SessionDao` bzw.
`MedienDao` zu. Für Massenoperationen vertretbar — wer ganz genau sein will, schreibt „…mit
Ausnahme des Abgleichs, der für Massenoperationen direkt auf den DAOs arbeitet".

**Weiter bestätigt:** 11 Tabellen; Ghost Climber wirklich aus den Einstellungen erreichbar
(`EinstellungenScreen` → `onOpenGhostClimber`); alle übrigen genannten Klassennamen
existieren; Mockups (S. 4–5) und Architekturdiagramm (S. 6) sind eingebettet.

**KI-Nutzung ist offengelegt** — §4.2 mit 23 Vorfällen plus die `KI-Nutzung.md` im Vault.
Das ist der einzige Posten, bei dem das Bewertungsschema Abzug *ankündigt*; er ist erfüllt.

---

## D — Was ich nicht beurteilen konnte

Ich komme an den Textlayer der PDF heran, kann die Seiten aber nicht rendern. Im
Textextrakt **verrutschen die Spalten** der Bibliothekstabelle (S. 8–9) und der
Issues-Spalte des Entwicklungstagebuchs deutlich — dort landet z. B. „Wear-OS-Basis-Support"
neben „Navigation Compose".

Das ist wahrscheinlich ein Extraktions-Artefakt unterschiedlich hoher Tabellenzeilen. Es
kann aber auch echt sein. **Bitte einmal selbst über die beiden Tabellen schauen**, ob Zweck
und Begründung wirklich neben der richtigen Bibliothek stehen.

---

## Material für §7 „Future Work"

Belegbar aus dem Repo, falls der Abschnitt gefüllt werden soll:

**Possible refactoring**

- **Eigene Domänen-Schicht.** Room-`Entity`-Typen reichen heute bis in die ViewModels. Für
  diese Größe pragmatisch, aber es ist die Stelle, an der „separation of concerns" zuerst
  hinsieht.
- **R8 für den Release.** 65 der 72 MB sind ungeschrumpfter Bytecode. Mit Keep-Regeln für
  MediaPipe wären unter 40 MB realistisch; vor einem Abgabetermin war es das Risiko nicht
  wert.
- **Typisierte Fehler im Ghost Climber.** Die Absagen reisen als `Exception`-Text bis in den
  UI-Zustand; ein typisierter Fehler wäre sauberer.
- **`AppNavigation` und `GhostClimberScreen`** sind mit 500+ bzw. 900+ Zeilen die beiden
  größten Dateien.

**Possible extensions**

- **Ablauf 36 fertig verdrahten.** Das Zurücknehmen einer Erstbegegnung soll laut
  `SYNC_PLAN.md` die Kopplung mit zurücksetzen; die Funktionen dafür stehen
  (`AbgleichDateien.verwirfKopplung`, `GeraeteIdentitaet.loeseKopplung`), gerufen werden sie
  noch nicht.
- **Vierte Zeitraum-Stufe „Tage"** für die Verlaufs-Diagramme (14-Tage-Fenster) — war gebaut
  und getestet, wurde zugunsten der Tages-Statistik verworfen.
- **Kalibrierung der automatischen Satz-Erkennung** an echter Wear-Hardware; die Schwellen
  stehen bislang auf Werten aus einer Garmin-Aufnahme.
- **Funkweg des Geräte-Abgleichs** mit zwei echten Geräten durchspielen — bislang ist nur
  der Datei-Weg am Gerät belegt.
