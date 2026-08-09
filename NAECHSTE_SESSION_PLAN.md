# Plan für die nächste Session — was ohne Uhr und Tablet geht

Erstellt: 08.08.2026 · Ausgangsstand: `origin/main` = `0a53276` (PR #15, `PixelBugfixes` gemergt)

> **Lage:** Alle acht Branches sind in `origin/main` enthalten — Gegenprobe
> `git rev-list --count origin/main..<branch>` = 0 für jeden lokalen und Remote-Branch.
> `GhostWarpSmoothing` existiert nicht mehr (nur noch der Tag `ghost-s7-stabil`).
> `:app:` und `:wear:testDebugUnitTest` laufen grün.
>
> **Verfügbar morgen:** Pixel 6a per adb. **Nicht verfügbar:** Wear-Gerät, Tablet, zweites Gerät.
>
> **Build-Reminder:** `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`, dann
> `& ".\gradlew.bat" assembleDebug --console=plain`.

---

## Schritt 0 — Ankommen (5 min)

Lokales `main` liegt 15 Commits zurück, ohne eigene Commits:

```bash
git checkout main
```

```bash
git pull --ff-only
```

Danach neuen Arbeitsbranch aufmachen, nicht auf `main` bauen.

---

## Block A — Die vier kleinen Schulden (halber Vormittag, kein Gerät nötig)

Alle vier sind unabhängig voneinander, jede ist ein eigener Commit.

### A1 · Migrationsprüfung auf v11 ziehen

`tools/pruefe_migrationen.py` prüft die Kette strukturell bis zur neuesten Version (`NEUESTE`
liest sich aus den Schema-Dateien), aber die **Datenprüfungen** enden bei
`fahre(con, migrationen, 9, 10)`. Die Spalte `istStandard` aus v11 — die den Fehler „Gradsystem
einer gelöschten Halle wird unlöschbar" behoben hat — ist damit nur über die Instrumented-Tests
gedeckt, also nur mit Emulator.

**Zu tun:** Einen `fahre(con, migrationen, 10, 11)`-Block nach dem Muster der bestehenden
ergänzen: ein selbst angelegtes System (mit und ohne Halle) und ein Standard-System durch die
Migration schicken und nachrechnen, dass `istStandard` danach genau die App-Systeme markiert.

> **Falle aus Bugs & Fixes Nr. 4 (08.08.):** `PRAGMA foreign_keys=ON` ist innerhalb einer offenen
> Transaktion ein stillschweigender No-Op. Vor das Pragma gehört ein `con.commit()`, sonst prüft
> der Test nichts von dem, wofür er geschrieben wurde.

### A2 · Test für den CSV-Export

`data/export/SessionExporter.kt` hat **keinen einzigen Test**, und der Pixel-Durchlauf führt
T9.4 ausdrücklich als ungeprüft: „der Escaping-Code liest sich korrekt, ist aber ungeprüft".
Der SAF-Dialog ist per adb schlecht bedienbar — genau deshalb gehört diese Stelle in die JVM.

**Zu prüfen:** Verdopplung von `"` innerhalb eines Feldes, Felder mit `;`/Komma/Zeilenumbruch,
UTF-8-BOM am Anfang (Excel-Umlaute), eine Session **ohne** Routen behält ihre eine Zeile,
aufgelöste Namen statt IDs (Halle, Gradsystem, Grad-Label).

Der Exporter zieht `@ApplicationContext` und DAOs — für den Test die CSV-Erzeugung von der
URI-Schreiberei trennen, falls sie es noch nicht ist. Das ist die eigentliche Arbeit an A2.

### A3 · README

Das Repo-Wurzelverzeichnis hat vierzehn Planungsdokumente und **kein README**. Die
Abgabe-Checkliste im Vault verlangt eines ausdrücklich.

**Inhalt:** was die App ist, Modulaufbau (`:app` / `:wear`), Tech-Stack in drei Zeilen, wie man
baut (inkl. der beiden AGP-9-Besonderheiten: Hilt ≥ 2.60, `android.disallowKotlinSourceSets=false`),
wie man Tests startet, und eine Landkarte der Planungsdokumente — die findet sonst niemand.

### A4 · App-Icon

`app/src/main/res/drawable/ic_launcher_foreground.xml` enthält unverändert das
Android-Studio-Robotergesicht (zwei Kreisaugen, zwei Antennen). Das ist bei einer Abgabe das
erste, was jemand sieht.

**Zu tun:** Vordergrund als Vektor aus der bestehenden Palette (`ui/theme/Color.kt`) zeichnen,
Hintergrund als Volltonfläche, `monochrome` mitziehen (steht schon im `adaptive-icon`, muss also
als einfarbige Silhouette funktionieren — beim Zeichnen mitdenken, nicht nachträglich).

---

## Block B — CSV-Import (der größte echte Funktionsblock)

Der TODO-Punkt steht seit Wochen: Export gibt es, Import nicht. `find app/src/main -iname "*Import*"`
liefert nichts. Der Geräte-Abgleich zählt nicht als Ersatz — der liest die SQLite-Datei, und
`SessionExporter` löst IDs gegen Namen auf, taugt also nicht als Eingabeformat für sich selbst.

**Genau deshalb ist A2 zuerst dran:** Der Import muss dieselbe Zerlegung rückwärts können. Ein
Parser, der gegen den getesteten Erzeuger gebaut wird, ist deutlich billiger als einer, der
gegen eine Vermutung gebaut wird.

**Vor dem Bauen zu entscheiden** (drei Fragen, die den Aufwand bestimmen):

1. **Namen → IDs:** Was passiert, wenn die CSV eine Halle nennt, die es nicht gibt? Anlegen
   (wie früher „find-or-create") oder zurückweisen? Anlegen widerspricht der Entscheidung aus
   dem Gym-Push-Umbau, dass Hallen **nirgends mehr implizit entstehen** — sie hätten wieder keine
   Koordinaten. Vorschlag: zurückweisen und die fehlenden Hallen benennen.
2. **Doppelte:** Eine bereits importierte Session ein zweites Mal einlesen — zusammenführen wie
   der Geräte-Abgleich (mit Gedächtnis) oder stumpf anhängen? Vorschlag: anhängen, aber vorher
   zählen und fragen. Der Abgleich hat für „zusammenführen" eine ganze Maschinerie; die hier zu
   wiederholen wäre unverhältnismäßig.
3. **Teilfehler:** Zeile 40 von 200 ist kaputt — alles verwerfen oder den Rest übernehmen?
   Vorschlag: alles oder nichts, in einer Transaktion, mit Bericht.

**Aufbau:** reiner Parser (JVM-testbar, kein Android) → Auflösung gegen die DB → ein
`ActivityResultContracts.OpenDocument("text/csv")` in den Einstellungen, direkt neben dem
Export-Eintrag. Der Bericht danach als Dialog: *n* Sessions, *m* Boulder, *k* Zeilen übersprungen.

---

## Block C — Rest-Testlauf am Pixel (braucht das Telefon, sonst nichts)

Aus `PIXEL_TESTLAUF_ERGEBNIS.md` nie angefasst. Der Plan (`PIXEL_TESTPLAN.md`) nennt zu jedem
Test Vorbedingung und erwartetes Ergebnis, ist also direkt abarbeitbar.

| Teil | Umfang | Warum jetzt |
|---|---|---|
| **T9.4** CSV-Export | 1 Test | Nach A2/B ohnehin fällig — jetzt gegen echten Speicher |
| **T10** Kamera, Galerie, Sprache | 7 Tests | Der einzige Bereich, in dem noch nie jemand einen Fehler gesucht hat |
| **T11.1–11.5** Widget | 5 Tests | Inkl. Widget-Theme, das laut TODO weiterhin von der App abweicht |
| **T12** Näherungs-Push | 6 Tests | **Geht ohne Fahrt:** `INITIAL_TRIGGER_DWELL` löst aus, wenn das Gerät beim Registrieren schon im Radius steht — eine Halle mit den eigenen Wohnungskoordinaten ist ein vollwertiger Testfall (Workflow 1 in `PUSHNOT_TESTEN.md`). 2–8 min Wartezeit je Durchgang einplanen |
| **T14.1** Ghost-Flow | 1 Test | Bis zur Analyse, ohne Bewertung der Qualität |

Vorher `adb shell pm clear com.boulderbuddy` — auf dem Gerät stehen noch die Testdaten vom
08.08. (3 Sessions, davon 2 „Unbekannte Halle", die Seed-Halle ist gelöscht).

Logcat-Fenster nebenher, ein `FATAL EXCEPTION` ist immer ein Fehlschlag:

```bash
adb logcat -c ; adb logcat AndroidRuntime:E System.err:W GeofenceManager:D GeofenceReceiver:D ProximityEventHandler:D Abgleich:D *:S
```

---

## Block D — Polish, falls Zeit bleibt

### D1 · Eine Entscheidung, eine Zeile

**BottomNav im Session-Detail am Telefon.** Seit dem T1-Fix (List-Detail als *ein* Ziel für alle
Breiten) liegt das Detail im Tab statt darüber, die BottomNav bleibt also stehen. Das war eine
KI-Entscheidung, weil die Rückfrage offen blieb. Einmal ansehen und bestätigen — oder umdrehen,
es ist eine Zeile.

### D2 · Strings herausziehen

`:app` benutzt **null** `stringResource`; es stehen 71 hartkodierte `Text("…")` im Code, und
`values/strings.xml` kennt sieben Einträge (App-Name, Widget, Abgleichs-Service). Das ist der
greifbarste Teil des TODO-Punkts „insgesamt Android-nativer gestalten" und die erwartbarste
Anmerkung in einer Bewertung.

Nicht in einem Rutsch — pro Screen ein Commit, sonst wird der Diff unlesbar und die Prüfung
wertlos. Sinnvoller Anfang: die Screens, die in der Doku als Screenshots auftauchen.

Im selben Zug: **16 Stellen mit `contentDescription = null`** durchgehen. Bei rein dekorativen
Icons ist `null` richtig — bei allem, was allein steht oder eine Aktion trägt, ist es eine Lücke.

### D3 · Ghost Climber

Aus der Stabilisierungsrunde offen, nach Hebelwirkung sortiert:

1. **`worldLandmarks()` auswerten** — größter Hebel. Der Rest-Morph sitzt in den Armen
   (0,50–0,63 % gegen 0,33–0,37 % bei den Beinen), überkopf und verdeckt, in 2D nicht von echter
   Verkürzung zu trennen. Kosten: Datenmodell + Cache-Key.
2. **One-Euro feintunen** (`ONE_EURO_MIN_CUTOFF_HZ` 1.5, `ONE_EURO_BETA` 0.015) — **gegen die
   Unruhe-Kennzahl messen, nicht nach Gefühl.** Eine trägere Einstellung (0.5/0.03) wurde früher
   schon als unnatürlich verworfen.
3. **`geometry` importiert `pose`** (`transformedBy` ruft `enforceRigidSkeleton`) — azyklisch,
   aber die falsche Richtung; in den Aufrufer hochziehen.

Zum Messen: frische Analyse nötig (Cache-Key `mp-heavy-20`), Debug-Chip an, Spur vom Gerät
ziehen und offline auswerten:

```bash
adb exec-out run-as com.boulderbuddy cat files/ghost/pose_<hash>.json > spur.json
```

Referenz der letzten Messung: `Puls 1,4× · Unruhe 0,40 % · Morph 0,41 % · Verkürzung 19,5 % ·
Kollaps 9,7 %`. **Puls hat bei 365 Frames einen Rauschboden von ~1,3** — Werte bis ~1,5 sind
Rauschen, nicht Fortschritt.

---

## Block E — Vault nachziehen (30–45 min, zum Abschluss)

Im Vault steht deutlich mehr offen als im Code. Nichts davon ist Arbeit, alles davon ist Abgabe.

- **`01 – Planung & Konzept/Requirements.md`: kein einziges Häkchen gesetzt** — Must-Haves bis
  Nice-to-Haves sind gebaut, inklusive Responsive Layouts, Video-Support, Export, Dark Mode,
  Widget, Speech-to-Text und Smartwatch-Automatisierung.
- **`00 – Projektübersicht.md`** und der TODO-Block „Entwicklung (Woche 5–8)" — dieselbe Lage
  (Session starten/beenden, Route anlegen, Hangboard-Timer, Statistik alle unangehakt).
- **`03 – Architektur & Tech/Multi-Device-Strategie.md`:** 7.1.5 (Tablet-Emulator) und 7.2.6
  (Wear-Emulator) stehen als offen — beides ist am 08.08. geprüft, Ergebnis in
  `EMULATOR_TESTLAUF_ERGEBNIS.md`.
- **`TODO.md`, zwei überholte Einträge:**
  - „Branch `GhostWarpSmoothing` nach `main` mergen" — der Branch existiert nicht mehr, sein
    Inhalt ist in `origin/main`.
  - „die Aggregation der Verlaufs-Diagramme im ViewModel hat keinen Test" —
    `StatistikVerlaufTest.kt` deckt beide Diagramme ab, mit festem Stichtag statt `LocalDate.now()`.
  - „`main` pushen" / „`PixelBugfixes` nach `main`" — beides erledigt (PR #15).
- **`05 – Dokumentation (Modul)/Abgabe-Checkliste.md`: 22 von 22 Punkten offen**, Frist ohne
  Datum. Mindestens die Build-/Geräte-Zeilen sind belegbar abhakbar.

---

## Was morgen ausdrücklich **nicht** geht

Nicht anfangen, es fehlt die Hardware:

- **Nearby-Funkweg** (zweites Gerät) — wahrscheinlichste Bruchstelle bleibt der Empfang von
  FILE-Payloads: `asFile()?.asJavaFile()` kann ab Android 11 `null` liefern.
- **Wear-Kopplung** — Workout-Meldung, Preset-Sync und Sensor-Log-Export sind ohne
  Companion-App auf keinem Weg prüfbar.
- **Kalibrierung der Auto-Erkennung** — der Detektor misst jeden Satz zu kurz, im Mittel 4,3 s,
  maximal 8,8 s (Bugs & Fixes, Wear Nr. 2). Der Emulator liefert stehende Sensorwerte; das
  gehört ans Handgelenk.
- **Lokale Warteschlange für Uhr-Workouts** — baubar und unit-testbar ohne Uhr, aber ohne
  Abnahme am Gerät würde sie ungeprüft liegen bleiben. Erst wenn die Uhr wieder da ist.
- **Doze-Verhalten** des Näherungs-Pushes über echte Stunden (T12.6 geht angerissen, das
  Langzeitverhalten nicht).

---

## Reihenfolge in einem Satz

**A1–A4** (kleine Schulden, kein Gerät) → **A2 dann B** (Export-Test trägt den Import) →
**C** (Pixel-Testlauf, findet die Fehler) → **D** und **E** als Puffer, wobei E vor Abgabe
Pflicht ist und D nicht.
