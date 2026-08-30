# BoulderBuddy

Trainingstagebuch fürs Bouldern: Sessions in einer Halle protokollieren, einzelne Boulder mit
Grad, Farbe, Sektor und Versuchen festhalten, und den Verlauf über die Zeit ansehen.

Dazu kommt, was beim Training tatsächlich in der Hand liegt: ein Hangboard-Timer (am Telefon
und an der Uhr, dort mit automatischer Satz-Erkennung über die Bewegungssensoren), Foto- und
Videoaufnahme direkt zum Boulder, ein Videovergleich zweier Begehungen mit überlagertem Skelett
(„Ghost Climber"), ein Homescreen-Widget für die laufende Session, eine Erinnerung beim
Betreten einer gespeicherten Halle, und ein Abgleich zwischen zwei Geräten ohne Konto und
ohne fremden Dienst.

Die App ist auf Deutsch, läuft ab Android 8 (`minSdk 26`) und speichert alles lokal — es gibt
keinen Server und kein Login.

## Aufbau

Zwei Gradle-Module:

| Modul | Inhalt |
|---|---|
| `:app` | Die Telefon- und Tablet-App. Single-Activity, Jetpack Compose, Room-Datenbank. |
| `:wear` | Die Wear-OS-App: Hangboard-Timer, automatische Satz-Erkennung, Sensor-Log. |

Innerhalb von `:app` (`com.boulderbuddy`):

```
data/       Room (entity, dao, Migrations), Repositories, Kamera, Sprache, CSV-Export
di/         Hilt-Module
ghost/      Ghost Climber: Pose-Erkennung (MediaPipe), Geometrie, Analyse
proximity/  Geofences, Näherungs-Benachrichtigung, Besuchsstatistik
sync/       Geräte-Abgleich (Nearby Connections + Datei-Weg)
ui/         Screens, ViewModels, Navigation, Theme, Fehlerkanal + Texte
util/       Kleinkram ohne eigene Heimat (MediaType)
wearsync/   Gegenstelle zur Uhr (Data Layer)
widget/     Homescreen-Widget (Glance)
```

Die Datenbank ist bei **v12**; die Wahrheit über das Schema sind die exportierten JSONs in
`app/schemas/`, nicht die Entities.

## Tech-Stack

Kotlin 2.2 · Jetpack Compose (Material 3) · Navigation Compose mit typsicheren Routen ·
Room · Hilt · DataStore · Coroutines/Flow.

Für einzelne Bereiche: CameraX (Aufnahme), Media3/ExoPlayer (Wiedergabe), MediaPipe Tasks
Vision (Pose), Glance (Widget), Material3 Adaptive (Tablet-Layouts), Play Services für
Location/Geofencing, Nearby Connections und den Wear Data Layer.

Gebaut mit AGP 9.2 (Built-in Kotlin) und dem Gradle-Version-Catalog in
`gradle/libs.versions.toml` — Versionen stehen dort, nicht in den `build.gradle.kts`.

## Bauen

Die Shell hier hat kein `JAVA_HOME`; die JBR aus Android Studio tut es:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
```

Unter PowerShell entsprechend `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`.
Danach:

```bash
./gradlew assembleDebug --console=plain
```

**Zwei Besonderheiten von AGP 9**, ohne die der Build nicht durchläuft — beide sind im Repo
schon gesetzt, hier steht nur, warum:

- **Hilt ≥ 2.60.** Ältere Versionen kommen mit dem Built-in-Kotlin-Aufbau nicht zurecht.
- **`android.disallowKotlinSourceSets=false`** in `gradle.properties`. KSP meldet seine
  generierten Quellen über die `kotlin.sourceSets`-DSL an; ohne das Flag bricht die
  Code-Generierung von Room und Hilt.

Ein `@AndroidEntryPoint`-Service braucht außerdem `error_prone_annotations` als explizite
Abhängigkeit — der generierte Code referenziert sie, transitiv ist sie nur `compileOnly`.

## Tests

Drei Ebenen, absteigend nach Geschwindigkeit:

```bash
./gradlew :app:testDebugUnitTest :wear:testDebugUnitTest --console=plain
```

JVM-Tests, keine Emulation, Sekunden bis Minuten. Hier liegt der Großteil: die
Ghost-Climber-Geometrie, die Statistik-Aggregation, der CSV-Export, der Abgleich, die
Näherungs-Regeln, die Hang-Erkennung der Uhr.

```bash
python tools/pruefe_migrationen.py
```

Fährt jede Room-Migration gegen echtes SQLite und vergleicht das Ergebnis mit den exportierten
Schemas — jeden Zwischenschritt und jeden Startpunkt, dazu die Datenprüfungen der Migrationen,
die Daten umziehen. Läuft in Sekunden ohne Emulator und ist das, was man beim Schreiben einer
Migration tatsächlich startet.

```bash
./gradlew :app:connectedDebugAndroidTest --console=plain
```

Instrumented-Tests, brauchen Gerät oder Emulator. Nur das, was ohne Android nicht geht: Rooms
eigene Migrations-Validierung und die Compose-Layout-Tests.

## Dokumentation

Der Projektbericht für die Abgabe liegt in [`doku/`](doku/) — dort auch die Prüfung seiner
Aussagen gegen den Quelltext.

## Planungsdokumente

Die Dokumente im Wurzelverzeichnis sind Arbeitsgrundlage, nicht Nachdokumentation — sie wurden
vor dem jeweiligen Bauabschnitt geschrieben.

**Roadmaps**

- [`IMPLEMENTIERUNGSPLAN.md`](IMPLEMENTIERUNGSPLAN.md) — Gesamtplan vom UI-Prototyp zur MVP.
- [`PHASE7_PLAN.md`](PHASE7_PLAN.md) — Phase 7 (lose Enden, Post-MVP) im Detail, inklusive der
  Anhänge A–C, die als Auftrag an ein zweites Modell dienten.

**Pläne einzelner Bauabschnitte**

- [`TABLET_PLAN.md`](TABLET_PLAN.md) — Tablet-Layouts (List-Detail, Breitenregeln).
- [`SYNC_PLAN.md`](SYNC_PLAN.md) — Geräte-Abgleich, achte Fassung; Anschluss an den Tablet-Plan.
- [`PUSHNOT_TESTEN.md`](PUSHNOT_TESTEN.md) — wie sich der Näherungs-Push ohne Fahrt zur Halle
  prüfen lässt.

**Durchsichten**

- [`KOMMENTARPFLEGE.md`](KOMMENTARPFLEGE.md) — ein Durchgang durch jede Code-Datei mit einer
  einzigen Frage: stimmt noch, was dort behauptet wird? Mit den drei Befunden, die dabei
  herauskamen — und einem in eigener Sache.

**Testpläne und ihre Ergebnisse** (jeweils Paare — Plan nennt Vorbedingung und Erwartung,
das Ergebnis hält fest, was wirklich passierte)

- [`PIXEL_TESTPLAN.md`](PIXEL_TESTPLAN.md) ·
  [`PIXEL_TESTLAUF_ERGEBNIS.md`](PIXEL_TESTLAUF_ERGEBNIS.md) — am Pixel 6a.
- [`EMULATOR_TESTPLAN.md`](EMULATOR_TESTPLAN.md) ·
  [`EMULATOR_TESTLAUF_ERGEBNIS.md`](EMULATOR_TESTLAUF_ERGEBNIS.md) — Wear OS und Tablet.

**Übergaben an Fable 5** — Startkontexte für die Blöcke, die ein zweites Modell gebaut hat.
Sie sind so geschrieben, dass ohne Exploration losgelegt werden kann, und dokumentieren
nebenbei den Repo-Stand zum jeweiligen Zeitpunkt.

- [`FABLE_GHOSTCLIMBER_START.md`](FABLE_GHOSTCLIMBER_START.md) — Ghost Climber (7.5).
- [`FABLE_GHOSTCLIMBER_STABILISIERUNG.md`](FABLE_GHOSTCLIMBER_STABILISIERUNG.md) ·
  [`FABLE_STABILISIERUNG_START.md`](FABLE_STABILISIERUNG_START.md) — Diagnose und Auftrag zur
  Skelett-Stabilisierung.
- [`FABLE_HANGBOARD_START.md`](FABLE_HANGBOARD_START.md) — automatische Hangboard-Erkennung
  an der Uhr.
- [`FABLE_GYMPUSH_START.md`](FABLE_GYMPUSH_START.md) — Gym-Näherungs-Push.

## Was Hardware braucht

Ohne die passenden Geräte nicht prüfbar, und deshalb bewusst als offen geführt: der Funkweg des
Geräte-Abgleichs (zwei Telefone), die Kopplung mit der Uhr (Companion-App), die Kalibrierung
der automatischen Satz-Erkennung (Emulatoren liefern stehende Sensorwerte) und das
Doze-Verhalten des Näherungs-Pushes über echte Stunden.
