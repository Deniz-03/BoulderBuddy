# Geräte-Testplan BoulderBuddy — alles, was am Pixel prüfbar ist

Stand: Branch `PushNot`, Commit `076eea6`, DB v10.
Zweck: **Bugs finden**, nicht Features abnehmen. Die App läuft grundsätzlich; gesucht sind die
Fehler, die bei ernsthaftem Durchspielen auffallen — falsche Zustände nach Drehen, Rückkehr aus
dem Hintergrund, gelöschte Bezüge, doppelte Navigation, stehengebliebene Anzeigen.

---

## 0. Vor dem ersten Test

### 0.1 Gerätelage (bitte prüfen, bevor es losgeht)

Aktuell meldet `adb` **kein angestecktes Telefon**, sondern nur:

| Gerät | Status | Was das bedeutet |
|---|---|---|
| `emulator-5554` — Pixel Tablet | online | **Breites Layout** (SideNav, Zwei-Pane). Deckt Teil 12 ab, aber nicht die Telefon-Pfade. |
| `emulator-5562` | offline | vermutlich die Wear-AVD; für Teil 14 nötig |

Die Tests unten sind für das **Telefon** geschrieben (Kompakt-Layout: BottomNav, Push-Navigation).
Am Tablet greifen an mehreren Stellen andere Code-Pfade (`isWideLayout` in
[AppNavigation.kt:200](app/src/main/java/com/boulderbuddy/ui/navigation/AppNavigation.kt:200)),
die Erwartungen stimmen dort teilweise nicht. Also: **Pixel anstecken, USB-Debugging bestätigen.**

```bash
adb devices -l
```

Mehrere Geräte → jedem Befehl `-s <serial>` mitgeben.

### 0.2 Frischen Build installieren

```bash
./gradlew :app:assembleDebug
```

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 0.3 Logcat-Fenster nebenher

Ein Fenster für Abstürze und stille Fehler:

```bash
adb logcat -c ; adb logcat AndroidRuntime:E System.err:W GeofenceManager:D GeofenceReceiver:D ProximityEventHandler:D Abgleich:D *:S
```

**Grundregel für jeden Test unten:** ein `FATAL EXCEPTION` im Logcat ist immer ein Fehlschlag,
auch wenn die Oberfläche das überspielt.

### 0.4 Zwei Ausgangszustände

Viele Fehler zeigen sich nur in genau einem davon:

* **Zustand A „frisch"** — nie installiert, Seed-Daten:
  ```bash
  adb uninstall com.boulderbuddy
  ```
  danach neu installieren. (Genau hier schlug in der Vergangenheit `SeedData` fehl, wenn eine
  neue NOT-NULL-Spalte hinzukam — auf einem gewachsenen Gerät ist das unsichtbar.)
* **Zustand B „gewachsen"** — der bestehende Datenbestand am Gerät, mit Sessions, Hallen,
  eigenen Gradsystemen.

Teil 1 und Teil 13 laufen in A, alles andere in B.

### 0.5 Nützliche Griffe

| Zweck | Befehl |
|---|---|
| App hart beenden | `adb shell am force-stop com.boulderbuddy` |
| Prozesstod simulieren (App im Hintergrund lassen!) | `adb shell am kill com.boulderbuddy` |
| Drehen erzwingen | `adb shell settings put system user_rotation 1` (0 = hoch) |
| Auto-Rotate an | `adb shell settings put system accelerometer_rotation 1` |
| Dark Mode System | `adb shell cmd uimode night yes` / `no` |
| Screenshot | `adb exec-out screencap -p > shot.png` |
| Berechtigungen zurücksetzen | `adb shell pm reset-permissions com.boulderbuddy` |
| DB herausziehen | `adb shell run-as com.boulderbuddy cat databases/boulderbuddy.db > db.sqlite` |
| Deep-Link „Neue Session" | `adb shell am start -n com.boulderbuddy/.MainActivity --es com.boulderbuddy.widget.NAV_TARGET new_session --ei com.boulderbuddy.proximity.GYM_ID 1` |

---

## Teil 1 — Kaltstart & Erstinstallation (Zustand A)

### T1.1 Erste Installation startet ohne Absturz
**Schritte:** deinstallieren, installieren, App öffnen.
**Erwartet:** Home erscheint, Seed-Daten sichtbar: eine laufende Session „Boulder World München"
mit 3 Bouldern, Statistik zeigt Zahlen ≠ 0, Timer hat 3 Presets.
**Fehlerbild, auf das es hier ankommt:** Absturz beim ersten Start oder eine leere App trotz
Seed — dann ist ein `INSERT` in
[SeedData.kt](app/src/main/java/com/boulderbuddy/data/db/SeedData.kt) an einer NOT-NULL-Spalte
gescheitert. Im Logcat nach `NOT NULL constraint failed` suchen.

### T1.2 Seed ist vollständig verdrahtet
**Schritte:** Einstellungen → Grading-Systeme verwalten; Hallen verwalten öffnen.
**Erwartet:** 3 Systeme (Halle Nord 5 Grade, V-Scale 16, Französisch 20), eine Halle
„Boulder World München" ohne Koordinaten.

### T1.3 Zweiter Start liest die bestehende DB
**Schritte:** App beenden (`force-stop`), erneut öffnen.
**Erwartet:** derselbe Stand, kein Re-Seed, keine doppelten Hallen/Systeme.

### T1.4 Migrationskette
**Schritte:** falls eine ältere APK greifbar ist: alte Version installieren, Daten anlegen,
neue Version drüber installieren (`-r`, **ohne** Deinstallation).
**Erwartet:** Start ohne `IllegalStateException: Migration didn't properly handle`, Daten
vollständig. Ohne alte APK: entfällt, dafür `python tools/pruefe_migrationen.py`.

---

## Teil 2 — Home

### T2.1 Kennzahlen stimmen
**Schritte:** Home ansehen, Zahlen notieren; mit Statistik-Tab und Sessions-Liste vergleichen.
**Erwartet:** „Sessions/Woche" = Sessions der letzten 7 Tage inkl. heute; „Tops gesamt" = Summe
aller getoppten Boulder über alle Sessions; „Top-Grade" stammt aus dem in den Einstellungen
gewählten Standard-Grading.

### T2.2 Top-Grade folgt dem Standard-Grading
**Schritte:** Einstellungen → Standard-Grading auf ein anderes System stellen → zurück auf Home.
**Erwartet:** Top-Grade-Kachel wechselt Label **und** System-Namen; hat das neue System keine
getoppten Boulder, steht „–" statt eines fremden Grades.

### T2.3 Untertitel der letzten Session
**Schritte:** eine Session laufen lassen und die Karte auf Home ansehen; danach eine Session
prüfen, die **gestern** gestartet wurde und noch läuft (per DB-Manipulation oder Gerätedatum).
**Erwartet:** korrekte Zeitangabe.
**Verdacht V2:** [HomeViewModel.kt:160](app/src/main/java/com/boulderbuddy/ui/viewmodel/HomeViewModel.kt:160)
setzt für jede laufende Session hart „Heute · läuft gerade" — eine über Nacht offen gebliebene
Session behauptet damit, sie sei von heute.

### T2.4 Schnellzugriffe
**Schritte:** jede Kachel/jeden Knopf auf Home antippen, jeweils mit Zurück wieder heraus.
**Erwartet:** „Session starten" → Neue Session; „Boulder hinzufügen" → Formular **mit** der
aktiven Session als Ziel; „Alle Boulder" → Übersicht mit weiterhin sichtbarer BottomNav und
markiertem Sessions-Tab; „Letzte Session" → passende Detailansicht.

### T2.5 Ohne aktive Session
**Schritte:** laufende Session beenden → Home.
**Erwartet:** keine „Boulder hinzufügen"-Kachel mehr, Karte zeigt die Session als abgeschlossen.

---

## Teil 3 — Session anlegen

### T3.1 Hallen-Chips: Reihenfolge und Vorauswahl
**Vorbedingung:** mindestens 3 Hallen, in verschiedenen Hallen zuletzt trainiert.
**Erwartet:** zuletzt benutzte Halle steht **links** und ist vorausgewählt; „+ Neue Halle" steht
am Ende.

### T3.2 Grading folgt der Halle, aber die eigene Wahl gewinnt
**Schritte:** Halle A (Standard-Grading X) wählen → Chip X ist markiert. Anderes System Y
antippen. Dann Halle B wählen.
**Erwartet:** nach dem Hallenwechsel ist der Standard von B markiert, **nicht** Y. Innerhalb
derselben Halle bleibt Y stehen.

### T3.3 Neue Halle aus dem Formular heraus
**Schritte:** „+ Neue Halle" → Editor → Name + Standard-Grading setzen → Speichern.
**Erwartet:** zurück im Session-Formular ist die neue Halle **ausgewählt**, ihr Standard-Grading
markiert.

### T3.4 Editor abbrechen
**Schritte:** „+ Neue Halle" → nichts eingeben → Zurück-Pfeil.
**Erwartet:** vorherige Auswahl unverändert, keine namenlose Halle in der Verwaltung.

### T3.5 Speichern ohne Namen
**Schritte:** im Editor Namensfeld leer lassen → Speichern.
**Erwartet:** nichts passiert (kein Schließen, keine leere Halle). Prüfen, ob der Nutzer
**erkennt**, warum — ein Knopf, der stumm nichts tut, ist selbst ein Befund.

### T3.6 Session starten
**Schritte:** Halle + Grading + Notiz → „Session starten".
**Erwartet:** direkt in der aktiven Session; **Zurück führt nach Home**, nicht ins Formular.

### T3.7 Zustand nach Drehen
**Schritte:** Formular ausfüllen (Halle B statt Vorauswahl, System Y, Notiztext) → drehen.
**Erwartet:** alle drei Eingaben stehen noch.
**Verdacht V10:** `selectedGymId`, `selectedSystemId` und `notiz` liegen in `remember`, nicht in
`rememberSaveable`
([SessionErstellenScreen.kt:59](app/src/main/java/com/boulderbuddy/ui/screens/SessionErstellenScreen.kt:59)) —
Drehen dürfte die Auswahl auf die Vorauswahl zurückwerfen und die Notiz löschen.

### T3.8 Ohne jede Halle
**Schritte:** alle Hallen löschen → Neue Session.
**Erwartet:** Knopf heißt „Erste Halle anlegen" und führt in den Editor; kein „Session starten".

---

## Teil 4 — Aktive Session & Boulder

### T4.1 Boulder anlegen
**Schritte:** in der aktiven Session „Boulder hinzufügen": Grad, Farbe, Sektor, Name, Versuche,
Status, Notiz → Speichern.
**Erwartet:** Boulder erscheint sofort in der Liste mit den eingegebenen Werten; Grad-Dropdown
enthält die Grade **des Session-Gradsystems**.

### T4.2 Boulder ohne Angaben
**Schritte:** Formular öffnen und ohne Eingaben speichern.
**Erwartet:** entweder gesperrt oder ein Boulder mit sinnvollen Vorgaben („Boulder", Grad „—") —
kein leerer Eintrag ohne jede Beschriftung, kein Absturz in der Liste.

### T4.3 Status- und Versuchslogik
**Schritte:** je einen Boulder mit Status Flash/Top/Projekt und Versuche 1 / 5 anlegen.
**Erwartet:** Statusanzeige passt zu Versuchen; Flash-Rate in der Statistik ändert sich nur bei
getoppten Bouldern mit ≤ 1 Versuch.

### T4.4 Bearbeiten
**Schritte:** Boulder öffnen → Bearbeiten → alle Felder ändern → Speichern.
**Erwartet:** Formular war korrekt vorbefüllt; Änderungen stehen in Detail, Session-Liste und
Boulder-Übersicht.

### T4.5 Session beenden
**Schritte:** „Session beenden".
**Erwartet:** Ansicht kippt ohne Neuladen auf die abgeschlossene (read-only) Variante mit Dauer;
Home hat keine aktive Session mehr; Widget ebenfalls nicht (siehe T11.4).

### T4.6 Notiz einer beendeten Session
**Schritte:** beendete Session öffnen → Notiz ändern → zurück → wieder öffnen.
**Erwartet:** Text persistiert; leerer Text bleibt leer und erzeugt keine „ "-Notiz.

### T4.7 Nach dem Beenden Boulder anlegen
**Schritte:** ohne aktive Session Home → falls erreichbar, das Boulder-Formular öffnen und
speichern.
**Erwartet:** kein Absturz und kein verwaister Boulder. (`save` bricht ohne Session still ab —
prüfen, dass der Nutzer nicht glaubt, es sei gespeichert worden.)

### T4.8 Session-Detail nach Drehen und Prozesstod
**Schritte:** Session offen → drehen; dann App in den Hintergrund, `adb shell am kill`, zurück.
**Erwartet:** dieselbe Session, Liste vollständig, kein „Session nicht gefunden".

---

## Teil 5 — Sessions-Liste & Boulder-Übersicht

### T5.1 Sortierung
**Schritte:** Sessions-Tab → Sortiermodus umschalten (alle Optionen).
**Erwartet:** Reihenfolge ändert sich plausibel; Umschalten überlebt einen Tab-Wechsel
(saveState) und ein Drehen.

### T5.2 Umschalten Sessions ↔ Boulder
**Schritte:** Sessions → „Boulder"-Ansicht → wieder „Sessions".
**Erwartet:** kein Stapeln; **Zurück** aus der Boulder-Ansicht führt nicht durch eine Kette
identischer Ansichten. Danach Home-Tab antippen → Home erscheint wirklich (der in
[AppNavigation.kt:576](app/src/main/java/com/boulderbuddy/ui/navigation/AppNavigation.kt:576)
beschriebene Nav-Quirk).

### T5.3 Filter der Boulder-Übersicht
**Schritte:** System-Filter durchklicken, dann einen Grad-Filter; anschließend alle Boulder eines
Grades löschen bzw. umgraden.
**Erwartet:** nur Systeme/Grade als Chips, die real vorkommen; nach dem Umgraden verschwindet der
leere Chip; die Auswahl fällt nicht auf einen toten Filter zurück (Liste darf nicht dauerhaft
leer bleiben, ohne dass ein Chip markiert ist).

### T5.4 Filter überlebt Navigation
**Schritte:** Filter setzen → Boulder öffnen → zurück.
**Erwartet:** Filter steht noch.

---

## Teil 6 — Hangboard-Timer

### T6.1 Durchlauf komplett
**Schritte:** 2 Sätze / 3 s Hang / 2 s Pause einstellen → Übernehmen → Play, bis „FERTIG".
**Erwartet:** Phasenfolge HANG→REST→HANG→FERTIG, Satzzähler 1/2 → 2/2, am Ende
Zusammenfassung **und** Speicherort-Zeile („In Session … gespeichert" bzw. „Als eigenständiges
Hangboard-Training gespeichert").

### T6.2 Der Durchlauf landet in der richtigen Session
**Schritte:** einmal mit laufender Session, einmal ohne.
**Erwartet:** mit Session → Workout taucht in der Session-Detailansicht auf; ohne → nur in der
Hangboard-Historie. In beiden Fällen wird die Statistik-Kachel „Hangboard" hochgezählt.

### T6.3 Einstellen während der Timer läuft
**Schritte:** Timer starten, 3 s laufen lassen, Zahnrad → **ohne Änderung** „Übernehmen".
**Erwartet:** sollte den Lauf nicht heimlich verwerfen.
**Verdacht V3:** `updateConfig` ruft `applyConfig` → `onReset`
([HangboardTimerViewModel.kt:130](app/src/main/java/com/boulderbuddy/ui/viewmodel/HangboardTimerViewModel.kt:130));
der laufende Durchlauf wird ohne Rückfrage auf Satz 1 zurückgesetzt und nicht gespeichert.
Zusätzlich prüfen: „Abbrechen" im Dialog darf gar nichts tun.

### T6.4 Presets
**Schritte:** aktuelle Werte als Preset speichern (Name mit Umlaut/Leerzeichen); Dialog erneut
öffnen, Preset antippen; „Presets bearbeiten" → Preset löschen.
**Erwartet:** Preset-Tap befüllt die Stepper, wirkt aber erst nach „Übernehmen" (bewusst so —
[HangboardTimerScreen.kt:262](app/src/main/java/com/boulderbuddy/ui/screens/HangboardTimerScreen.kt:262));
Löschen entfernt den Chip sofort; leerer Name lässt sich nicht speichern.
**Nebenbefund V6:** `applyPreset` im ViewModel wird von keinem UI-Pfad aufgerufen — toter Code
gegen die eigene KDoc.

### T6.5 Pause = 0
**Schritte:** Pause auf 0 stellen, 3 Sätze durchlaufen lassen, mit Stoppuhr mitzählen.
**Erwartet:** kein Hänger, keine Division durch 0.
**Verdacht V4:** `start()` wartet erst `delay(1000)` und prüft danach
([HangboardTimerViewModel.kt:190](app/src/main/java/com/boulderbuddy/ui/viewmodel/HangboardTimerViewModel.kt:190)) —
eine Pause von 0 s dürfte trotzdem eine Sekunde kosten.

### T6.6 Genauigkeit über einen echten Satz
**Schritte:** 6 Sätze × 7 s / 3 s (= 57 s Soll) mit Stoppuhr messen.
**Erwartet:** Abweichung im Sekundenbereich. Größere Drift ist real: der Timer zählt
`delay(1000)`-Schritte statt gegen die Uhr.

### T6.7 Timer im Hintergrund
**Schritte:** Timer starten → (a) auf Home-Tab wechseln, nach 10 s zurück; (b) App in den
Hintergrund, nach 20 s zurück; (c) Bildschirm aus, nach 20 s an.
**Erwartet:** in allen drei Fällen ein nachvollziehbares Verhalten — entweder läuft er weiter
(dann muss die Anzeige beim Zurückkommen stimmen und die Vibration in (b)/(c) gefeuert haben)
oder er pausiert sichtbar. Was **nicht** passieren darf: Anzeige steht still, Zustand springt,
oder der Durchlauf wird doppelt gespeichert.

### T6.8 Haptik-Schalter greift sofort
**Schritte:** Timer laufen lassen, Einstellungen → Haptik aus → zurück zum Timer.
**Erwartet:** ab dem nächsten Phasenwechsel keine Vibration mehr; wieder an → wieder Vibration.

### T6.9 Reset und Neustart
**Schritte:** bis FERTIG laufen lassen → Play erneut drücken.
**Erwartet:** startet einen frischen Durchlauf; nach dem zweiten Ende existieren **zwei**
Workouts in der Historie, keine drei.

---

## Teil 7 — Statistik & Hangboard-Historie

### T7.1 Kennzahlen gegen die Rohdaten
**Schritte:** Werte aus Statistik notieren, mit den Sessions/Bouldern vergleichen
(ggf. CSV-Export aus T9.4 als Gegenprobe).
**Erwartet:** Flash-Rate = getoppte mit ≤ 1 Versuch / alle getoppten; Sessions- und Tops-Zahlen
konsistent mit Home.

### T7.2 Grade-Verteilung je System
**Schritte:** System-Umschalter über der Verteilung durchklicken.
**Erwartet:** nur Systeme mit getoppten Bouldern; Balken in Grad-Reihenfolge; keine Mischung
über Systeme hinweg.

### T7.3 Verlaufs-Diagramme
**Schritte:** Woche/Monat/Jahr durchschalten, an beiden Diagrammen.
**Erwartet:** beide Diagramme zeigen dieselbe Abschnittsreihe; leere Abschnitte stehen als 0
drin; Umschalten wirkt sofort und ohne Flackern; nach Drehen bleibt die Auswahl.

### T7.4 Heatmap
**Erwartet:** 28 Tage, Zeitraum-Beschriftung passt zum heutigen Datum, heutiger Tag ist der
letzte Eintrag.

### T7.5 Leerer Zustand
**Schritte:** (in Zustand A oder mit gelöschten Daten) Statistik ohne Boulder ansehen.
**Erwartet:** „–" statt „0%"/NaN, keine leeren Achsen-Artefakte, kein Absturz.

### T7.6 Hangboard-Historie
**Schritte:** Statistik → Hangboard-Block → Historie.
**Erwartet:** alle Workouts, auch die eigenständigen; Zuordnung zur Session bzw. „ohne Session"
korrekt beschriftet; Reihenfolge neueste zuerst.

---

## Teil 8 — Hallen verwalten & Gym-Editor

### T8.1 Halle anlegen mit allem
**Schritte:** Einstellungen → Hallen verwalten → Neue Halle: Name, Adresse, „Aktuellen Standort
übernehmen", Radius auf 50 m, Erinnerungen an, Standard-Grading wählen → Speichern.
**Erwartet:** Zeile in der Liste mit farbigem Standort-Icon und „Standort hinterlegt".
Logcat: `… Geofence(s) registriert` (nur wenn der Master-Toggle an ist).

### T8.2 Standort ohne Berechtigung / ohne GPS
**Schritte:** Berechtigungen zurücksetzen, Editor → „Aktuellen Standort übernehmen" ablehnen;
danach Standortdienste am Gerät ausschalten und erneut versuchen.
**Erwartet:** verständliche Meldung („Kein Standort verfügbar …"), Knopf hängt nicht dauerhaft im
Ladezustand, kein Absturz.

### T8.3 Koordinaten manuell + löschen
**Schritte:** manuelle Eingabe mit gültigen Werten, dann mit Unsinn (`abc`, `999`, `-200`,
Komma statt Punkt), dann Koordinaten löschen.
**Erwartet:** ungültige Eingaben werden abgefangen (keine Halle bei 999° Breite), Löschen setzt
die Zeile zurück auf „Kein Standort".

### T8.4 Standard-Grading ab- und anwählen
**Schritte:** System antippen (markiert) → nochmal antippen (abgewählt) → Speichern → erneut
öffnen.
**Erwartet:** „kein Standard" wird gespeichert; im Session-Formular greift dann das erste System.

### T8.5 Besuchsmuster
**Schritte:** Halle mit ≥ 1 Session öffnen.
**Erwartet:** Zeile wie „3 Besuche · meist dienstags"; bei 1 Besuch „1 Besuch" (Singular);
ohne Besuche keine Zeile.

### T8.6 Halle löschen — Historie bleibt
**Schritte:** Halle mit mindestens einer Session und Bouldern löschen (Rückfrage nennt die Zahl).
**Erwartet:** Sessions **bleiben** und zeigen weiterhin den Hallennamen; Boulder bleiben; das
hallenspezifische Gradsystem bleibt und wird global; Halle verschwindet aus Liste und
Session-Formular; Logcat meldet neu registrierte Geofences.

### T8.7 Umbenennen schlägt auf alte Sessions durch
**Schritte:** Halle umbenennen → alte Session öffnen.
**Erwartet:** neuer Name (solange die Halle existiert) — so ist `hallenName` gedacht.

### T8.8 Doppelte Namen
**Schritte:** zwei Hallen mit exakt demselben Namen anlegen.
**Erwartet:** entweder verhindert oder in der Chip-Auswahl unterscheidbar.
**Verdacht V7:** es gibt keinen Unique-Constraint — zwei identische Chips sind für den Nutzer
nicht auseinanderzuhalten.

### T8.9 Halle löschen, die gerade eine laufende Session trägt
**Schritte:** Session starten → Halle löschen → Session öffnen, Home, Widget, Statistik prüfen.
**Erwartet:** überall der gespeicherte Hallenname, nirgends „Unbekannte Halle" oder ein Absturz.

---

## Teil 9 — Einstellungen

### T9.1 Grading-System anlegen
**Schritte:** „Grading-System erstellen": Name, mehrere Grade, Zeilen hinzufügen und wieder
entfernen (auch die mittlere), leere Zeilen stehen lassen → Anlegen.
**Erwartet:** System erscheint mit der Zahl **nicht-leerer** Grade; die Reihenfolge stimmt;
das Entfernen einer mittleren Zeile löscht die richtige.

### T9.2 System löschen, das in Gebrauch ist
**Schritte:** Custom-System einer Session zuweisen und einer Halle als Standard; dann löschen.
**Erwartet:** Bestätigung; danach zeigen betroffene Boulder „—" statt eines Grades, die App
stürzt nirgends ab, und der Gym-Editor fällt auf „kein Standard" zurück (die `defaultGradeSystemId`
ist bewusst ohne Fremdschlüssel — hier bleibt eine tote ID stehen,
[GymEntity.kt](app/src/main/java/com/boulderbuddy/data/db/entity/GymEntity.kt)).
Danach Statistik, Boulder-Übersicht und Session-Detail durchgehen.

### T9.3 Standard-Systeme sind geschützt
**Erwartet:** V-Scale und Französisch haben keinen Papierkorb, sondern „Standard".

### T9.4 CSV-Export
**Schritte:** vorher eine Session-Notiz und eine Boulder-Notiz mit **Komma, Anführungszeichen,
Zeilenumbruch und Umlauten** anlegen. Dann exportieren, Datei auf den Rechner holen und in
Excel/LibreOffice öffnen.
**Erwartet:** Toast mit Anzahl; alle Sessions inkl. der ohne Boulder; Umlaute korrekt (BOM);
Sonderzeichen brechen keine Spalten; laufende Session steht als „aktiv".
**Zusätzlich:** Export abbrechen (Dateiauswahl mit Zurück verlassen) → kein Toast, kein Absturz.

### T9.5 Dark Mode
**Schritte:** Schalter umlegen; danach das **System**-Theme umstellen (`cmd uimode night yes/no`).
**Erwartet:** App folgt dem eigenen Schalter; Statusleisten-Symbole bleiben lesbar (heller
Hintergrund → dunkle Symbole und umgekehrt); alle Screens durchklicken, besonders Diagramme,
Chips, Kamera und Ghost Climber.

### T9.6 Name
**Schritte:** Namen setzen, leeren, sehr langen Namen (60 Zeichen) und Emoji eingeben.
**Erwartet:** Begrüßung auf Home passt sich an; leer = neutrale Begrüßung; kein abgeschnittenes
Layout.

### T9.7 Version
**Erwartet:** „Über BoulderBuddy" zeigt eine plausible Versionsnummer.

---

## Teil 10 — Medien, Kamera, Spracheingabe

### T10.1 Foto aufnehmen
**Schritte:** Boulder-Formular → Aufnahme → Foto → übernehmen → speichern → Boulder öffnen.
**Erwartet:** Bild im Formular und im Detail sichtbar, übersteht das Speichern.

### T10.2 Video aufnehmen
**Schritte:** dasselbe mit Video; im Detail abspielen.
**Erwartet:** Wiedergabe startet, kein schwarzes Bild.
**Kein Ton — das ist gewollt** (Entscheidung vom 09.08.2026): der Kamera-Pfad fragt `RECORD_AUDIO`
bewusst nicht ab, und `CameraCaptureController` nimmt Ton nur auf, wenn die Freigabe ohnehin
vorliegt. Ein stummes Video ist der Preis dafür, dass die Aufnahme keine zusätzliche
Mikrofon-Freigabe erzwingt. **Ein Video ohne Tonspur ist hier also kein Befund.**

### T10.3 Kamera-Berechtigung verweigert
**Schritte:** `pm reset-permissions`, Aufnahme öffnen, Berechtigung ablehnen (auch zweimal =
„nie wieder fragen").
**Erwartet:** verständliche Meldung, Rückweg ohne Sackgasse, kein schwarzer Screen.

### T10.4 Galerie-Weg
**Schritte:** über die Medienquelle-Auswahl ein Bild aus der Galerie wählen.
**Erwartet:** wird übernommen und überlebt einen Neustart der App (URI-Berechtigung!). **Genau
hier bricht so etwas gern**: nach `force-stop` erneut öffnen und prüfen, ob das Bild noch da ist.

### T10.5 Aufnahme abbrechen
**Schritte:** Kamera öffnen → Zurück ohne Aufnahme.
**Erwartet:** Formular unverändert, kein leeres Medium gesetzt.

### T10.6 Aufnahme wird nicht doppelt übernommen
**Schritte:** Aufnahme machen → im Formular bleiben → erneut in die Kamera und wieder zurück
ohne Aufnahme → drehen.
**Erwartet:** immer genau das zuletzt gewählte Medium; die alte Aufnahme taucht nicht wieder auf
(`KAMERA_ERGEBNIS` wird quittiert).

### T10.7 Spracheingabe
**Schritte:** in Session-Notiz und Boulder-Notiz das Mikrofon nutzen: normal sprechen; nichts
sagen; mittendrin abbrechen; Berechtigung verweigern; Flugmodus (kein Netz).
**Erwartet:** erkannter Text wird **angehängt**, nicht ersetzt; jeder Fehlerfall endet mit einer
Meldung statt einem hängenden Dialog.

---

## Teil 11 — Widget & Deep-Links

### T11.1 Widget platzieren
**Schritte:** Widget auf den Homescreen legen.
**Erwartet:** zeigt aktive Session mit Hallenname, Boulderzahl und Tops — bzw. ohne Session die
Gesamt-Tops und „Session starten".

### T11.2 Widget aktualisiert sich
**Schritte:** in der App einen Boulder anlegen → Homescreen ansehen.
**Erwartet:** Zahl steigt ohne manuelles Zutun (Snapshot wird nachgezogen).

### T11.3 Widget-Sprünge
**Schritte:** jeden Bereich des Widgets antippen — mit und ohne aktive Session.
**Erwartet:** aktive Session → direkt hinein, Zurück führt nach Home; ohne Session → „Neue
Session"; Timer-Knopf → Timer-Tab.

### T11.4 Widget nach Session-Ende
**Schritte:** Session beenden → Homescreen.
**Erwartet:** Widget bietet „Session starten" an und springt **nicht** mehr in die beendete
Session.

### T11.5 Widget-Theme
**Schritte:** App-Dark-Mode umschalten, dann den System-Dark-Mode.
**Erwartet:** Widget folgt bei gesetztem Override dem App-Schalter, ohne Override dem System.
(Das war schon einmal ein Fehler: alles dunkel, Widget cremefarben.)

### T11.6 Deep-Link „Neue Session" mit Gym
```bash
adb shell am start -n com.boulderbuddy/.MainActivity --es com.boulderbuddy.widget.NAV_TARGET new_session --ei com.boulderbuddy.proximity.GYM_ID 1
```
**Erwartet:** „Neue Session" öffnet sich mit Gym 1 vorausgewählt. Mit einer **unbekannten** ID
(z. B. 9999) fällt die Auswahl auf die zuletzt benutzte Halle zurück, ohne Absturz.

### T11.7 ⚠️ Deep-Link + Drehen
**Schritte:** Deep-Link aus T11.6 auslösen → auf „Neue Session" **drehen** → dann so oft
Zurück drücken, bis Home erscheint, und dabei mitzählen.
**Erwartet:** genau **ein** Zurück führt nach Home.
**Verdacht V1 (der wichtigste dieser Liste):** `MainActivity` liest das Ziel in `onCreate`
([MainActivity.kt:34](app/src/main/java/com/boulderbuddy/MainActivity.kt:34)) und
`AppNavigation` navigiert in einem `LaunchedEffect` darauf
([AppNavigation.kt:119](app/src/main/java/com/boulderbuddy/ui/navigation/AppNavigation.kt:119)).
Beim Neuaufbau der Activity (Drehen, Prozesstod, Theme-Wechsel) liefert `getIntent()` dasselbe
Extra erneut, während der Back-Stack bereits wiederhergestellt ist → der Screen wird ein zweites
Mal auf den Stapel gelegt. Erwartetes Fehlerbild: zweimal Zurück nötig, dazwischen blitzt
dasselbe Formular nochmal auf.
**Gegenprobe:** dasselbe mit `--es … active_session --ei com.boulderbuddy.widget.SESSION_ID <id>`
und mit `timer`.

### T11.8 Deep-Link bei laufender App
**Schritte:** App offen auf dem Timer-Tab lassen, dann den Deep-Link absetzen; anschließend
Zurück.
**Erwartet:** springt ins Ziel; danach ein nachvollziehbarer Back-Stack (nicht in einen
halbtoten Zustand).

---

## Teil 12 — Näherungs-Push (Gym-Erinnerungen)

Ausführlich steht das in [PUSHNOT_TESTEN.md](PUSHNOT_TESTEN.md); hier die Kurzfassung als
Prüfliste. **Wichtig:** der 24-h-Cooldown macht den zweiten Versuch am selben Tag stumm — das
ist korrekt und kein Fehler.

### T12.1 Einschalt-Flow der Berechtigungen
**Schritte:** `pm reset-permissions` → Einstellungen → „Gym-Erinnerungen" an.
**Erwartet:** Standort-Dialog → **„Immer erlauben"**-Weg → Benachrichtigungs-Dialog, in dieser
Reihenfolge. Bei Ablehnung jeweils ein Toast; der Schalter darf nicht „an" behaupten, wenn nichts
registriert wurde — genau das ist zu prüfen (Logcat: `Geofences nicht registriert: …`).

### T12.2 Ganze Kette zuhause
**Schritte:** Halle mit dem eigenen Standort und Radius 50 m anlegen, Erinnerungen an, Master an,
2–8 Minuten warten (Bildschirm darf aus sein).
**Erwartet:** Logcat `… → NOTIFY`, Notification erscheint, Tap öffnet „Neue Session" mit der
Halle vorausgewählt.

### T12.3 Die Politik erklärt sich selbst
**Schritte:** die Fälle aus PUSHNOT_TESTEN §5 provozieren: Pro-Gym-Toggle aus (`DISABLED`),
laufende Session (`ACTIVE_SESSION`), gerade beendete Session (`POST_SESSION_QUIET`), zweiter
Lauf am selben Tag (`COOLDOWN`).
**Erwartet:** jeweils die passende Logcat-Zeile — und **keine** Notification.

### T12.4 Neustart
```bash
adb reboot
```
danach ohne die App zu öffnen:
```bash
adb logcat -d -s GeofenceManager:D
```
**Erwartet:** `N Geofence(s) registriert`. Kommt nichts, feuert nach jedem Neustart nie wieder
ein Push — der stillste denkbare Fehler.

### T12.5 Master-Toggle aus
**Schritte:** ausschalten → Logcat.
**Erwartet:** Geofences werden entfernt; kein Push mehr, auch nicht nach Warten.

### T12.6 Doze
**Schritte:**
```bash
adb shell dumpsys deviceidle force-idle
```
danach Trigger provozieren, dann `adb shell dumpsys deviceidle unforce`.
**Erwartet:** Push kommt spätestens beim Verlassen von Doze. Das ist die dokumentierte offene
Frage aus M5 — Ergebnis notieren, nicht bewerten.

---

## Teil 13 — Geräte-Abgleich

Der Funk-Weg (Nearby) ist laut Projektstand **noch nie an echten Geräten gelaufen**. Mit nur
einem Gerät ist folgendes prüfbar:

### T13.1 Abgabe erzeugen
**Schritte:** Einstellungen → Geräte abgleichen → „Abgeben" → Datei speichern.
**Erwartet:** Datei entsteht mit dem angezeigten Namen und plausibler Größe; Dialog schließt.

### T13.2 Eigenen Stand wieder einlesen
**Schritte:** dieselbe Datei über „Einlesen" auswählen.
**Erwartet:** erkennt „nichts Neues" bzw. übernimmt ohne Duplikate — **danach Sessions, Boulder
und Hallen zählen**. Verdopplungen wären ein Datenverlust-nahe Befund.

### T13.3 Unsinns-Datei
**Schritte:** eine beliebige Textdatei / ein Bild als Stand einlesen.
**Erwartet:** Fehlermeldung, kein Absturz, Datenbestand unverändert.

### T13.4 Rückgängig
**Schritte:** nach einem Einlesen „Rückgängig".
**Erwartet:** vorheriger Stand ist zurück; Zählungen wieder wie vorher.

### T13.5 Funk-Abgleich starten (nur mit zweitem Gerät sinnvoll)
**Schritte:** „Über Funk" starten; Berechtigungen durchklicken; ohne Gegenstelle 1–2 Minuten
warten; abbrechen.
**Erwartet:** Berechtigungsdialoge passend zur Android-Version, Foreground-Service-Notification
erscheint und **verschwindet beim Abbrechen wieder**; kein hängender Suchzustand.

---

## Teil 14 — Ghost Climber & Uhr (nur wenn Zeit/Hardware da)

### T14.1 Ghost-Flow bis zur Analyse
**Schritte:** Einstellungen → Ghost Climber; zwei Videos wählen (Galerie), Analyse starten,
Anker setzen, Pfad bestätigen, Ansichten umschalten, Analyse speichern und wieder laden.
**Erwartet:** jeder Schritt hat einen Rückweg; die gespeicherte Analyse lässt sich erneut öffnen;
Löschen entfernt sie.
**Achtung:** rechenintensiv — hier vor allem auf ANRs, Speicherfehler und ein blockiertes UI
achten.

### T14.2 Uhr-Anbindung
**Vorbedingung:** Wear-Emulator online (`emulator-5562` ist derzeit offline).
**Erwartet:** Smartwatch-Indikator in der Timer-Top-Bar und die Zeile in den Einstellungen
zeigen „Verbunden"; ein auf der Uhr beendetes Workout taucht in der Historie auf.

---

## Teil 15 — Robustheit quer über alles

### T15.1 Drehen auf jedem Screen
**Schritte:** jeden Screen einmal drehen — besonders solche mit Eingaben und offenen Dialogen
(Timer-Einstellungen, Grading anlegen, Lösch-Bestätigung, Namensdialog).
**Erwartet:** Eingaben bleiben, Dialoge bleiben offen (oder schließen bewusst), nichts stürzt ab.

### T15.2 Prozesstod
**Schritte:** auf einem tief verschachtelten Screen App in den Hintergrund → `adb shell am kill
com.boulderbuddy` → App aus den Recents zurückholen.
**Erwartet:** derselbe Screen mit denselben Daten; kein Sprung auf Home ohne Vorwarnung, kein
leerer Zustand.

### T15.3 Back-Stack-Marathon
**Schritte:** Home → Neue Session → Halle anlegen → zurück → Session starten → Boulder anlegen →
Kamera → zurück → speichern → Boulder öffnen → bearbeiten → zurück → Einstellungen → Hallen →
Halle → zurück ×N.
**Erwartet:** jeder Rückweg landet dort, wo man ihn erwartet; irgendwann Home; von Home schließt
Zurück die App.

### T15.4 Systemschrift und Anzeigegröße
**Schritte:**
```bash
adb shell settings put system font_scale 1.5
```
alle Screens durchgehen, danach zurück auf `1.0`.
**Erwartet:** nichts wird abgeschnitten, keine überlappenden Texte, Knöpfe bleiben erreichbar.

### T15.5 Vor-/Zurückspringen im Datum
**Schritte:** Gerätedatum einen Tag vorstellen → Home, Statistik, Heatmap ansehen; zurückstellen.
**Erwartet:** „heute"-Bezüge stimmen, keine negativen Dauern, keine leeren Diagramme.

### T15.6 Doppeltipp-Schutz
**Schritte:** „Session starten", „Speichern" und „Session beenden" jeweils **zweimal schnell**
antippen.
**Erwartet:** genau eine Session / ein Boulder entsteht. Doppelte Einträge sind ein klassischer
Fund.

### T15.7 Sehr lange Texte
**Schritte:** Hallenname, Boulder-Name und Notiz mit je 200 Zeichen füllen.
**Erwartet:** Chips und Zeilen kürzen sauber (Ellipse), das Layout bricht nicht.

### T15.8 Speicher-Rotation über alle Tabs
**Schritte:** je 5× schnell zwischen allen vier Tabs wechseln, dann Statistik und Übersicht
scrollen.
**Erwartet:** flüssig, kein Nachladeflackern, keine `ANR` im Logcat.

---

## Anhang A — Verdachtsliste aus dem Code-Durchgang

Reihenfolge = meine Einschätzung, wie wahrscheinlich das ein echter Fund ist.

| # | Verdacht | Ort | Test |
|---|---|---|---|
| V1 | Deep-Link/Widget-Ziel wird nach Drehen oder Prozesstod **erneut** navigiert → doppelter Back-Stack-Eintrag | [AppNavigation.kt:119](app/src/main/java/com/boulderbuddy/ui/navigation/AppNavigation.kt:119), [MainActivity.kt:34](app/src/main/java/com/boulderbuddy/MainActivity.kt:34) | T11.7 |
| V10 | Session-Formular verliert Halle, Grading und Notiz beim Drehen (`remember` statt `rememberSaveable`) | [SessionErstellenScreen.kt:59](app/src/main/java/com/boulderbuddy/ui/screens/SessionErstellenScreen.kt:59) | T3.7 |
| V3 | „Übernehmen" im Timer-Dialog verwirft einen laufenden Durchlauf ohne Rückfrage | [HangboardTimerViewModel.kt:130](app/src/main/java/com/boulderbuddy/ui/viewmodel/HangboardTimerViewModel.kt:130) | T6.3 |
| V2 | Laufende Session zeigt auf Home immer „Heute", auch wenn sie von gestern ist | [HomeViewModel.kt:160](app/src/main/java/com/boulderbuddy/ui/viewmodel/HomeViewModel.kt:160) | T2.3 |
| V4 | Pause = 0 s kostet trotzdem eine Sekunde | [HangboardTimerViewModel.kt:190](app/src/main/java/com/boulderbuddy/ui/viewmodel/HangboardTimerViewModel.kt:190) | T6.5 |
| V5 | Timer zählt `delay(1000)` statt gegen die Uhr → Drift; Verhalten im Hintergrund unklar | dito | T6.6, T6.7 |
| V7 | Hallen mit identischem Namen sind in der Chip-Auswahl nicht unterscheidbar | kein Unique auf `gym.name` | T8.8 |
| V8 | Tote `defaultGradeSystemId` bleibt im Gym-Editor stehen (bewusst ohne Fremdschlüssel) | [GymEntity.kt](app/src/main/java/com/boulderbuddy/data/db/entity/GymEntity.kt), [GymBearbeitenViewModel.kt:86](app/src/main/java/com/boulderbuddy/ui/viewmodel/GymBearbeitenViewModel.kt:86) | T9.2 |
| V9 | Speichern ohne Namen tut still nichts — keine Rückmeldung an den Nutzer | [GymBearbeitenViewModel.kt:199](app/src/main/java/com/boulderbuddy/ui/viewmodel/GymBearbeitenViewModel.kt:199) | T3.5 |
| V11 | Boulder speichern ohne Session bricht still ab und navigiert trotzdem zurück | [RouteHinzufuegenViewModel.kt:174](app/src/main/java/com/boulderbuddy/ui/viewmodel/RouteHinzufuegenViewModel.kt:174) | T4.7 |
| V6 | `applyPreset` ist toter Code (KDoc behauptet die Verdrahtung) | [HangboardTimerViewModel.kt:151](app/src/main/java/com/boulderbuddy/ui/viewmodel/HangboardTimerViewModel.kt:151) | T6.4 |

---

## Anhang B — Ergebnisprotokoll

Beim Durchlauf auszufüllen: `OK` / `FEHLER` / `n. a.` plus ein Satz.

| Test | Ergebnis | Anmerkung |
|---|---|---|
| T1.1 … T15.8 | | |

Für jeden Fehlschlag festhalten: **was gemacht**, **was erwartet**, **was passiert**, plus den
Logcat-Ausschnitt und einen Screenshot.
