# BoulderBuddy

Trainingstagebuch fürs Bouldern — mit einem Gradsystem pro Halle statt einer erzwungenen
Universalskala.

## Worum es geht

Kletterhallen bewerten ihre Boulder fast nie nach einem der bekannten Systeme (V-Scale,
Französisch) — stattdessen kommen eigene Nummerierungen oder Farbleitern zum Einsatz, die
zwischen Hallen nicht vergleichbar sind. Eine „8er“-Route in einer Halle hat mit einer
„8er“-Route in der nächsten oft nichts zu tun. Gängige Boulder-Apps ignorieren das und
zwingen eine feste Skala auf, die für keine der Hallen wirklich stimmt.

BoulderBuddy dreht das um: **jede Halle bekommt ihr eigenes, frei definierbares
Gradsystem.** Dazu eine sessionbasierte Dokumentation jeder Route (Foto/Video, Versuche,
Status, Notiz), ein hallenübergreifender Fortschrittsüberblick und ein Hangboard-Timer, der
die Behelfslösung „externe Timer-App nebenher“ überflüssig macht.

Die App läuft vollständig lokal — kein Server, kein Konto, keine Cloud. Was zwischen zwei
eigenen Geräten geteilt werden soll, tauschen die Geräte direkt miteinander aus.

## Features

- **Sessions & Routen** — Boulder mit Foto/Video, Grad, Sektor, Versuchen und Status
  (Projekt/Top/Flash) dokumentieren
- **Eigene Gradsysteme** — pro Halle frei definierbar, plus die üblichen Standards
  (V-Scale, Französisch) als Vorlage
- **Hangboard-Timer** — auf dem Handy und als eigenständige Wear-OS-App, die auf der Uhr
  Sätze automatisch aus der Bewegung erkennt, ganz ohne manuelles Tippen
- **Ghost Climber** — legt zwei Kletterversuche als Video übereinander, um
  Bewegungsabläufe zu vergleichen. Perspektiv-Ausrichtung (Homographie) und
  Zeit-Synchronisation (Dynamic Time Warping) sind eigener Code, kein OpenCV
- **Geräte-Abgleich** — zwei eigene Geräte (z. B. Handy und Tablet) gleichen ihren
  Datenstand direkt per Nearby Connections ab, ganz ohne Konto oder Server
- **Näherungs-Erinnerung** — erinnert per Geofencing daran, eine Session zu starten, wenn
  man länger an einer gespeicherten Halle ist
- **Homescreen-Widget** — laufende Session und Schnellstart direkt vom Startbildschirm

## Aufbau

Zwei Gradle-Module:

| Modul | Inhalt |
|---|---|
| `:app` | Handy- und Tablet-App. Single-Activity, Jetpack Compose, Room. |
| `:wear` | Wear-OS-Begleiter: Hangboard-Timer, automatische Satz-Erkennung, Sensor-Log. |

Innerhalb von `:app` (`com.boulderbuddy`):

```
data/       Room (Entities, DAOs, Migrationen), Repositories, Kamera, Sprache, CSV-Export
di/         Hilt-Module
ghost/      Ghost Climber: Pose-Erkennung (MediaPipe), Geometrie, Analyse
proximity/  Geofences, Näherungs-Benachrichtigung, Besuchsstatistik
sync/       Geräte-Abgleich (Nearby Connections + Datei-Weg)
ui/         Screens, ViewModels, Navigation, Theme, Fehlerkanal
util/       Kleinkram ohne eigene Heimat
wearsync/   Gegenstelle zur Uhr (Data Layer)
widget/     Homescreen-Widget (Glance)
```

Bewusst kein eigener `domain`-Layer: Die Repositories sind dünne Fassaden über den DAOs,
und wo echte Algorithmik anfällt — Ghost Climber, Geräte-Abgleich —, sitzt sie als
android-freies, eigenständig testbares Kotlin in `ghost/` bzw. `sync/`.

Room ist die alleinige Datenquelle der App; ihr Schema ist versioniert, und jede Änderung
bekommt eine echte Migration statt eines destruktiven Fallbacks — der Geräte-Abgleich
überträgt Daten, die ein Fallback sonst stillschweigend gelöscht hätte. Die exportierten
Schema-JSONs in `app/schemas/` sind dabei die Wahrheit über den Ist-Zustand, nicht die
Entity-Klassen im Code.

## Tech-Stack

Kotlin 2.2 · Jetpack Compose (Material 3) · Navigation Compose mit typsicheren Routen ·
Room · Hilt · DataStore · Coroutines/Flow.

Für einzelne Bereiche: CameraX (Aufnahme), Media3/ExoPlayer (Wiedergabe), MediaPipe Tasks
Vision (Pose-Erkennung), Glance (Widget), Material3 Adaptive (Tablet-Layouts), Play
Services für Location/Geofencing, Nearby Connections und den Wear Data Layer.

Gebaut mit AGP 9.2 (Built-in Kotlin) und dem Gradle-Version-Catalog in
`gradle/libs.versions.toml` — Versionen stehen dort, nicht verstreut in den
`build.gradle.kts`-Dateien.

## Bauen

Braucht ein JDK 17+ als `JAVA_HOME` — z. B. die JBR aus Android Studio:

```bash
export JAVA_HOME="/pfad/zu/Android Studio/jbr"
```

Danach:

```bash
./gradlew assembleDebug --console=plain
```

**Zwei Besonderheiten von AGP 9**, ohne die der Build nicht durchläuft (im Repo schon
gesetzt, hier steht nur, warum):

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

JVM-Tests, keine Emulation, Sekunden bis Minuten — hier liegt der Großteil: die
Ghost-Climber-Geometrie, die Statistik-Aggregation, der CSV-Export, der Geräte-Abgleich,
die Näherungs-Regeln, die Hang-Erkennung der Uhr.

```bash
python tools/pruefe_migrationen.py
```

Fährt jede Room-Migration gegen echtes SQLite und vergleicht das Ergebnis mit den
exportierten Schemas — jeden Zwischenschritt und jeden Startpunkt. Läuft in Sekunden ohne
Emulator.

```bash
./gradlew :app:connectedDebugAndroidTest --console=plain
```

Instrumented-Tests, brauchen Gerät oder Emulator: Rooms eigene Migrations-Validierung und
die Compose-Layout-Tests.

## Was Hardware braucht

Ohne die passenden Geräte nicht prüfbar: der Funkweg des Geräte-Abgleichs (zwei Telefone),
die Kopplung mit einer Wear-OS-Uhr, die Kalibrierung der automatischen Satz-Erkennung
(Emulatoren liefern keine echten Bewegungsdaten) und das Doze-Verhalten der
Näherungs-Erinnerung über echte Stunden.

## Dokumentation

Projektbericht und Demo-Video liegen in [`doku/`](doku/).

**Das Video zeigt nicht den aktuellen Stand von `main`** — die Aufnahme ist etwas älter als
der Code, gegen den sie hier liegt. Im Zweifel gilt der Code, nicht das Video.
