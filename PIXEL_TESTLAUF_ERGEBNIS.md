# Testlauf am Pixel 6a — Ergebnis

Gerät: Pixel 6a (`bluejay`), Android 16 (SDK 36), 1080×2400 @ 420 dpi.
Build: Branch `PushNot`, Commit `076eea6`, DB v10, Neuinstallation vom 8.8.2026 13:05.
Durchgeführt: Teile 1–9 und 15 des [Testplans](PIXEL_TESTPLAN.md), UI-getrieben über adb,
Datenprüfung jeweils direkt gegen die SQLite-Datei der App.

**Kein einziger Absturz** über den gesamten Durchlauf — `logcat AndroidRuntime:E` blieb leer.
Die Fehler unten sind alle Verhaltens- und Datenfehler, keine Instabilität.

---

## Die Befunde, nach Schwere

### F9 — Gelöschte Halle nimmt den Hallennamen doch mit ⚠️ schwerwiegend

`SessionErstellenViewModel.createSession` setzt `gymName` **nie**
([SessionErstellenViewModel.kt:145](app/src/main/java/com/boulderbuddy/ui/viewmodel/SessionErstellenViewModel.kt:145)),
die Spalte bleibt auf ihrem Default `""`. Nur das Seed füllt sie. Damit greift der
Rückfall in `hallenName` für **jede real angelegte Session** ins Leere.

Am Gerät nachgestellt: zwei Sessions in „Testhalle Sued" angelegt, Halle gelöscht.

| Session | Herkunft | `gymName` in der DB | Anzeige danach |
|---|---|---|---|
| 1 | Seed | `Boulder World München` | „Boulder World München" ✓ |
| 2, 3 | von der App angelegt | *(leer)* | **„Unbekannte Halle"** ✗ |

Der Löschdialog verspricht dabei wörtlich: *„2 Sessions bleiben erhalten — mit allen Bouldern
**und dem Hallennamen**."* Genau das trifft nicht zu. Das gesamte v10-Feature funktioniert nur
für Seed-Daten; die Trainingshistorie verliert ihre Hallen-Zuordnung unwiederbringlich.

Der Rest von v10 hält übrigens sauber: Sessions, Boulder, Workouts und Gradsysteme überleben
das Löschen vollständig, `gymId` wird korrekt auf NULL gesetzt.

### F10 — Handgetippte Session-Notiz wird nie gespeichert ⚠️ Datenverlust

Auf dem Screen der abgeschlossenen Session speichert das Notizfeld nur beim **Fokusverlust**
([AlteSessionScreen.kt:178](app/src/main/java/com/boulderbuddy/ui/screens/AlteSessionScreen.kt:178)).
Am Gerät ließ sich kein Weg finden, der diesen Fokusverlust auslöst, bevor der Screen zerstört ist:

| Weg aus dem Feld | Notiz gespeichert? |
|---|---|
| System-Zurück (Geste/Taste) | nein |
| Zurück-Pfeil in der Titelleiste | nein |
| woanders auf den Screen tippen | nein (Fokus bleibt) |
| Spracheingabe | **ja** — der Weg ruft `onNotesChange` direkt auf |

Nachgestellt: „Guter Tag" getippt, Screen verlassen, Session wieder geöffnet → Platzhalter,
`session.notes` in der DB weiterhin leer. Wer die Reflexion nach der Session tippt, verliert sie
jedes Mal. Nur wer sie einspricht, behält sie.

### F1 — Deep-Link wird bei jedem Activity-Neuaufbau erneut ausgeführt

`MainActivity` liest das Ziel in `onCreate`, `AppNavigation` navigiert im `LaunchedEffect`
darauf. Beim Neuaufbau (Drehen, Prozesstod) liefert `getIntent()` dasselbe Extra erneut,
während der Back-Stack schon wiederhergestellt ist — der Screen landet ein zweites Mal darauf.

Kontrolliert gemessen:

| Fall | Zurück-Drücker bis Home | erwartet |
|---|---|---|
| `new_session`, ohne Drehen | 1 | 1 ✓ |
| `new_session`, zwei Drehungen | **3** | 1 ✗ |
| `active_session`, zwei Drehungen | **3** | 1 ✗ |
| `timer` (Tab-Ziel), zwei Drehungen | 1 | 1 ✓ |

Das Tab-Ziel ist verschont, weil `navigateToTab` mit `popUpTo(Home)` + `launchSingleTop`
arbeitet. Betroffen sind die beiden `navigate()`-Push-Ziele — also der Widget-Sprung in die
laufende Session und der Näherungs-Push.

### F8 — Doppeltipp auf „Session starten" legt zwei Sessions an

Zweimal schnell getippt → zwei Zeilen mit identischem Zeitstempel (`13:42:03`), beide aktiv.
Die App navigiert in eine davon, die zweite bleibt unsichtbar im Bestand liegen.

Nebenbefund aus demselben Test: die App erlaubt **beliebig viele gleichzeitig aktive Sessions**
ohne Hinweis. Nach dem Doppeltipp liefen drei parallel; Home zeigt kommentarlos die neueste.
Ob das gewollt ist, ist eine Produktfrage — auffällig ist, dass nichts darauf hinweist.

### F5 — Speichern-Knopf ist bei offener Tastatur nicht erreichbar

Im Projekt gibt es **kein** `imePadding()`, kein `WindowInsets.ime` und kein
`windowSoftInputMode` (verifiziert per Suche über `app/src/main` und Manifest). Zusammen mit
`enableEdgeToEdge()` schiebt Android das Fenster nur so weit, dass das *fokussierte* Feld
sichtbar bleibt.

Auf „Boulder hinzufügen" heißt das: nach dem Antippen des Namensfelds liegen Versuche, Status,
Notiz **und der Speichern-Knopf** unter der Tastatur. Auch nach fünf kräftigen Wischern nach
oben bleibt der Knopf unerreichbar — der Scrollbereich ist am Ende, weil das Layout die
Tastatur nicht kennt. Man muss die Tastatur erst schließen, um speichern zu können, ohne dass
irgendetwas darauf hinweist. Dasselbe Muster auf „Neue Session" („Session starten") und der
Session-Notiz.

### F2 — Formulare verlieren ihren Zustand beim Drehen

`rememberSaveable` kommt im gesamten UI-Code **nur** in `GhostClimberScreen` vor; alle anderen
Formulare nutzen `remember`. Am Gerät auf „Neue Session" nachgestellt: Notiz „Heute Projekt
arbeiten" getippt, Grading von „Halle Nord" auf „V-Scale" umgestellt, gedreht → Notiz leer,
Grading zurück auf dem Hallen-Standard. Betrifft dieselbe Klasse von Screens wie F5.

### F12 — „Grading-System erstellen" legt still eine Halle an

`createGradeSystem` hängt das neue System an die **erste beliebige** Halle — und legt, wenn es
keine gibt, kommentarlos eine Halle namens **„Meine Halle"** an
([EinstellungenViewModel.kt:185](app/src/main/java/com/boulderbuddy/ui/viewmodel/EinstellungenViewModel.kt:185)).

Am Gerät: bei 0 Hallen ein System „Meine Skala" angelegt → danach steht „Meine Halle · Kein
Standort" in der Hallen-Verwaltung und als Chip im Session-Formular. Der Nutzer hat eine Halle
bekommen, die er nie angelegt hat.

### F11 — Gradsystem einer gelöschten Halle wird dauerhaft unlöschbar

`deletable = it.gymId != null`
([EinstellungenViewModel.kt:98](app/src/main/java/com/boulderbuddy/ui/viewmodel/EinstellungenViewModel.kt:98)),
und das Löschen einer Halle setzt `grade_system.gymId` per SET NULL auf NULL (v10, gewollt).
Folge: „Halle Nord" stand nach dem Löschen seiner Halle mit dem Hinweis **„Standard"** in der
Liste — nicht mehr von V-Scale/Französisch zu unterscheiden und nie wieder löschbar.

Zusammen mit F12 ergibt das eine unangenehme Kette: das eigene System hängt an einer zufälligen
Halle, und wer diese Halle aufräumt, friert das System für immer ein.

### F3 — „Übernehmen" im Timer-Dialog verwirft den laufenden Durchlauf

Timer gestartet, bei Satz 4/6 das Zahnrad geöffnet, **ohne jede Änderung** „Übernehmen" getippt
→ zurück auf Satz 1/6, angehalten, der Durchlauf wird nicht gespeichert. Ursache ist
`updateConfig` → `applyConfig` → `onReset`. „Abbrechen" verhält sich korrekt und lässt den
Durchlauf weiterlaufen.

### F6 — Laufende Session behauptet immer „Heute"

`buildSubtitle` setzt für jede laufende Session hart „Heute · läuft gerade"
([HomeViewModel.kt:160](app/src/main/java/com/boulderbuddy/ui/viewmodel/HomeViewModel.kt:160)).
Mit einer auf den 6.8. zurückdatierten, weiterhin laufenden Session zeigte Home am 8.8.
„Heute · läuft gerade · 4 Boulder"; die Sessions-Liste ebenso. Die Detailansicht rechnet
dagegen korrekt („● Läuft · 48:33 h").

### F4 — Pause = 0 kostet trotzdem eine Sekunde je Satz

`start()` wartet erst `delay(1000)` und prüft danach. Gemessen an der von der App selbst
protokollierten Laufzeit: 3 Sätze × 2 s Hang / 0 s Pause → Soll 6 s, **ist 8,112 s**. Die zwei
Null-Pausen kosten exakt je eine Sekunde.

### F7 — „Halle anlegen" ohne Namen tut wortlos nichts

Der Knopf sieht voll aktiv aus (gleiche Füllung wie die anderen Primärknöpfe), tippt man ihn
ohne Namen, passiert nichts: keine Meldung, keine Markierung am Feld, keine Halle. `save()`
kehrt bei leerem Namen einfach zurück.

---

## Ist davon etwas auf `main` schon behoben?

**Nein — kein einziger.** `main` ist ein direkter Vorfahre von `PushNot`: `git rev-list --count
PushNot..main` = 0, die Merge-Basis ist `main`s HEAD (`8639104`), `PushNot` liegt 49 Commits
davor. Auf `main` kann deshalb nichts stehen, was `PushNot` nicht schon hätte.

Auch kein anderer Branch enthält einen Fix. Außerhalb von `PushNot` existieren nur drei Commits:
`origin/main` hat den Merge-Commit `10d6a9b` (BaseAppPolish — dessen Code ist in `PushNot`
bereits enthalten, nur die Merge-Topologie unterscheidet sich), `HangTimerWatch` hat zwei
Wear-Test-Commits. Keiner berührt einen der Befunde. `imePadding` kommt in **keinem** Branch vor,
`rememberSaveable` überall nur in `GhostClimberScreen`, `gymName` wird in **keinem** Branch beim
Session-Anlegen gesetzt.

Interessanter ist die Gegenrichtung — **wie alt ist der Fehler?**

| Befund | Stand auf `main` | Einordnung |
|---|---|---|
| F1 Deep-Link doppelt | `LaunchedEffect(initialNavTarget)` wortgleich | **alt.** `PushNot` hat die Angriffsfläche vergrößert (`active_session` + `gymId` kamen dazu) |
| F2 Formularzustand beim Drehen | `rememberSaveable` nur in `GhostClimberScreen` | **alt**, unverändert |
| F3 „Übernehmen" verwirft Durchlauf | `updateConfig` → `applyConfig` → `onReset` identisch | **alt**, unverändert |
| F4 Pause = 0 kostet 1 s | `delay(1000)` vor der Prüfung identisch | **alt**, unverändert |
| F5 Speichern unter der Tastatur | kein `imePadding`/`windowSoftInputMode` | **alt**, unverändert |
| F6 „Heute · läuft gerade" | Zeile wortgleich | **alt**, unverändert |
| F8 Doppeltipp → zwei Sessions | `createSession` ohne Schutz | **alt** (dort noch mit „find-or-create" aus dem Textfeld) |
| F11 Gradsystem unlöschbar | `deletable = it.gymId != null` identisch | **alte Zeile, neuer Auslöser** — auf `main` gibt es keinen Gym-Editor, die Halle war gar nicht löschbar |
| F12 Phantom-Halle „Meine Halle" | `createGradeSystem` wortgleich | **alt**, unverändert |
| F7 „Halle anlegen" ohne Rückmeldung | kein Gym-Editor vorhanden | **neu** mit dem Feature |
| F9 `gymName` wird nie gesetzt | Spalte existiert nicht (DB v6, `gymId: Int` non-null, CASCADE) | **neu** — das Versprechen ist neu, die Einlösung fehlt |
| F10 Notiz wird nicht gespeichert | Feld ist dort **gar nicht** speichernd (kein `onNotesChange`) | **halb behoben.** `PushNot` hat das Speichern eingebaut, es feuert nur nie |

Das heißt fürs Vorgehen: **neun der zwölf Fehler sind Altlasten**, keine Regression der
Gym-Push-Arbeit. Nur F7 und F9 kamen mit dem neuen Feature herein, F10 ist ein angefangener,
nicht fertiggestellter Fix. Ein Merge nach `main` verschlechtert nichts — er trägt die neun
Altlasten nur mit.

---

## Stand: alle zwölf behoben

Abgearbeitet auf Branch `PixelBugfixes` (von `main` nach dem Merge aller Branches). Jede
Korrektur ist am Pixel 6a gegengeprüft; die Suite läuft mit **46/46** instrumentierten Tests.

| # | Fehler | Belegte Wirkung |
|---|---|---|
| F13 | rote Testsuite | 7 Fehlschläge → 0 |
| F9 | Hallenname überlebt Löschen nicht | „Unbekannte Halle" → Hallenname bleibt |
| F10 | Notiz wird nie gespeichert | steht während des Tippens in der DB |
| F1 | Deep-Link doppelt | 3 Zurück-Drücker → 1 |
| F8 | Doppeltipp legt zwei Sessions an | 2 → 1 |
| F12 | Phantom-Halle „Meine Halle" | Hallenzahl bleibt unverändert |
| F11 | Gradsystem unlöschbar nach Hallen-Löschung | Papierkorb bleibt, nur Standards geschützt |
| F5 | Speichern unter der Tastatur | ganzes Formular erreichbar, Speichern getestet |
| F2 | Formularzustand beim Drehen | Notiz + Auswahl überstehen die Drehung |
| F3 | „Übernehmen" verwirft den Durchlauf | läuft weiter (Satz 2 → 3) |
| F4 | Pause 0 kostet eine Sekunde | 8,112 s → **6,103 s** bei 6 s Soll |
| F6 | „Heute" bei alter Session | „6. August · läuft gerade" |
| F7 | stummer Knopf | sichtbar deaktiviert ohne Namen |

**Zwei Dinge, die sich beim Beheben als falsch herausstellten** — beide standen vorher in
diesem Dokument bzw. im Zwischenbericht:

* Der erste Ansatz für F1 (`savedInstanceState == null`) hätte den Notification-Tap bei
  laufender App verschluckt. Das Gerät hat ihn widerlegt, bevor er committet wurde; der Marker
  sitzt jetzt im Intent selbst.
* Daraus entstand kurzzeitig der Verdacht, dieser Fall sei „schon vorher kaputt" gewesen. Er
  war es nicht — mein `am start` setzte die Flags nicht, die die echte Notification verwendet
  (`CLEAR_TOP|NEW_TASK`). Mit den richtigen Flags funktioniert er in beiden Ständen. Ein Fehler
  im Test, keiner in der App.

---

## Behoben: F9 und F10

Beide Korrekturen sind gebaut, mit Tests abgesichert und am Pixel gegengeprüft.

### F9 — `gymName` wird beim Anlegen mitgeschrieben

Zwei Stellen:

1. `SessionErstellenViewModel.createSession` setzt `gymName` aus der gewählten Halle. Das war
   die eigentliche Lücke — **eine fehlende Zeile**. Alles drumherum war richtig: die Spalte,
   der `hallenName`-Helfer, der Dialogtext und sogar die Migration `9→10`, die den Namen für
   **bestehende** Sessions per `COALESCE((SELECT g.name FROM gym g WHERE g.id = s.gymId), '')`
   nachträgt. Nur der Weg, auf dem neue Sessions entstehen, tat es nicht.
2. `GymDao.deleteAndKeepName` sichert den Namen vor dem Löschen in alle Sessions der Halle —
   in einer Transaktion. Das ist das Fangnetz für Zeilen, die vor dieser Korrektur entstanden
   sind; deshalb braucht es dafür **keine** eigene Migration.

Bewusst ohne `AND gymName = ''`: solange `gymId` zeigt, wohin es soll, ist die Halle die
Wahrheit (Umbenennungen schlagen durch) — überdauern soll deshalb der Name, den der Nutzer
zuletzt gesehen hat.

Am Gerät nach `pm clear` mit dem neuen Build: neu angelegte Session trägt sofort
`[Boulder World München]`; nach dem Löschen der Halle steht in der Sessions-Liste **zweimal**
„Boulder World München" statt „Unbekannte Halle".

### F10 — die Notiz wird beim Tippen gespeichert

`AlteSessionScreen` meldet jede Eingabe sofort nach oben (`onChange` ruft `onNotesChange`), der
Fokusverlust-Auslöser ist ersatzlos entfallen. Damit ein Tastendruck nicht drei
Datenbankzugriffe kostet, schreibt `SessionViewModel.updateNotes` über eine einzelne
`UPDATE`-Anweisung (`SessionDao.updateNotes`) statt Laden–Kopieren–Zurückschreiben.

Ein Detail, das sonst Zeichen verschluckt hätte: `remember(notes)` ist zu `remember` geworden.
Mit Schlüssel hätte der gerade geschriebene Wert das Feld bei jedem Rücklauf aus Room neu
aufgesetzt und beim schnellen Tippen Zeichen zwischen Schreiben und Rücklauf verloren. Der
Startwert genügt einmalig, weil `SessionRoute` den Screen erst nach dem Laden rendert.

Am Gerät: Notiz getippt → steht **während des Tippens** in der DB; zurück, Session wieder
geöffnet → „Schulter zwickt beim Mantle" steht im Feld.

### Neue Tests

| Test | sichert |
|---|---|
| `GymDaoLoeschenTest` (3 Fälle) | Name überlebt das Löschen auch ohne gefüllten `gymName`; Umbenennung gewinnt; keine Streuwirkung auf andere Hallen |
| `AlteSessionNotizTest` (2 Fälle) | jede Eingabe meldet sich sofort nach oben; bestehende Notiz wird angezeigt |

Alle 5 laufen grün auf dem Pixel; die JVM-Suite ist unverändert grün.

---

## F13 — Die Testsuite ist schon vorher rot (7 Fehlschläge)

Beim Absichern aufgefallen und **durch einen Referenzlauf mit `git stash` gegen den
unveränderten Stand bestätigt**: dieselben sieben instrumentierten Tests schlagen ohne meine
Änderungen genauso fehl. Sie sind keine Folge der Korrektur — aber sie verdecken künftige
Regressionen.

**`StandZugriffTest` (4 Fälle) — `NOT NULL constraint failed: gym.geofenceRadiusMeters`.**
Die Test-Hilfsfunktion baut eine Gym-Zeile aus nur `name` und `location`. Die mit v8
hinzugekommenen NOT-NULL-Spalten haben ihren Standardwert **nur in Kotlin**, nicht im
SQL-Schema — dieselbe Falle, die schon das Seed erwischt hat, hier nur in der Fixture. Der Test
ist damit zugleich ein Kanarienvogel für den Abgleich selbst: er schreibt Zeilen generisch aus
dem Stand, und ein Gegenüber ohne diese Felder liefe in denselben Constraint.

**`MigrationTest.v9_zu_v10…` — `assertThat(c.isNull(0)).isTrue()` schlägt fehl.**
Nach `DELETE FROM gym` bleibt `session.gymId` im Test gesetzt statt NULL. Der Kommentar dort
nimmt an, Room habe die Fremdschlüssel eingeschaltet — für die rohe Datenbank aus
`runMigrationsAndValidate` gilt das aber nicht (`PRAGMA foreign_keys` bleibt aus). Eine falsche
Annahme im Test, **kein** App-Fehler: am echten Gerät wurde `gymId` beim Löschen korrekt NULL
(T8.6).

**`InhaltsBreiteTest` (2 Fälle) — `Actual left is -434.2857.dp, expected 0.0.dp`.**
Layout-Tests mit fest erwarteten Positionen; auf dem Telefon-Fenster gehen die Werte ins
Negative. Vermutlich am Tablet geschrieben und dort grün.

---

## Was sauber lief

| Test | Ergebnis |
|---|---|
| T1.1–T1.3 Erstinstallation, Seed, Neustart | Kaltstart 973 ms, Seed vollständig (1 Halle, 3 Systeme, 41 Grade, 3 Boulder, 3 Presets), kein Re-Seed |
| T3.3 Halle aus dem Session-Formular anlegen | neue Halle kommt ausgewählt zurück, ihr Standard-Grading ist markiert |
| T3.8 kein Halle vorhanden | Knopf heißt „Erste Halle anlegen", kein „Session starten" |
| T4.1 Boulder anlegen | alle Felder korrekt gespeichert, Grade-Dropdown zeigt genau das Session-System (V0–V4) |
| T4.5 Session beenden | Rückfrage, Ansicht kippt sofort auf read-only, Dauer korrekt |
| T6.1/6.2 Timer-Durchlauf | Phasenfolge, Satzzähler, Zuordnung zur laufenden Session — alles korrekt |
| T6.4 Presets | Speichern (leerer Name gesperrt), Laden in die Stepper, Löschen — alles korrekt |
| **T6.6 Timer-Genauigkeit** | 57,219 / 57,335 / 57,427 s bei 57 s Soll — **die Drift-Sorge (V5) bestätigt sich nicht** |
| **T6.7 Timer im Hintergrund** | überlebt Tab-Wechsel, App im Hintergrund **und ausgeschalteten Bildschirm**; der bei ausgeschaltetem Bildschirm beendete Durchlauf wurde korrekt mit 57,427 s protokolliert |
| T8.6 Halle löschen | Sessions, Boulder, Workouts, Gradsysteme überleben vollständig; Rückfrage nennt die Zahl korrekt (bis auf F9) |
| T11.6 Deep-Link mit Gym-ID | Halle korrekt vorausgewählt samt ihrem Standard-Grading |
| Stabilität | kein einziger `FATAL EXCEPTION` über den gesamten Durchlauf |

---

## Nicht geprüft

* **T12 Näherungs-Push** — braucht die Berechtigungskette und 2–8 Minuten Wartezeit je Durchgang.
* **T13 Abgleich** — der Funk-Weg braucht ein zweites Gerät.
* **T10 Kamera/Galerie/Sprache**, **T14 Ghost Climber**, **T11.1–11.5 Widget** — nicht angefasst.
* **T9.4 CSV-Export** — der SAF-Dialog ist per adb schlecht bedienbar; der Escaping-Code
  (`RFC 4180`, Verdopplung von `"`, BOM) liest sich korrekt, ist aber ungeprüft.
* **T1.4 Migration von einer älteren APK** — keine alte APK vorhanden.

---

## Zustand des Geräts nach dem Lauf

Die Testdaten stehen noch drauf: 3 Sessions (2 davon „Unbekannte Halle", eine beendet), 4
Boulder, 4 Hangboard-Workouts, die Halle „Meine Halle" aus F12 und das System „Meine Skala".
Die Seed-Halle wurde im Zuge von T8.6 gelöscht. Für einen sauberen Neuanfang:

```bash
adb -s 29231JEGR18629 shell pm clear com.boulderbuddy
```

Zwei Systemeinstellungen habe ich gesetzt, um den Sperrbildschirm aus dem Weg zu haben —
zurückdrehen mit:

```bash
adb -s 29231JEGR18629 shell svc power stayon false
```

```bash
adb -s 29231JEGR18629 shell settings put system accelerometer_rotation 1
```

---

# Zweiter Durchlauf — 09.08.2026, Branch `Abgabefeinschliff`

Gerät wie oben. Build vom heutigen Stand, `pm clear` vorweg, Ausgangslage also Seed-Daten.
Abgearbeitet: **T9.4** (CSV-Export) und **Teil 10** (Medien, Kamera, Spracheingabe) — die beiden
Bereiche, die der erste Durchlauf ausdrücklich offen ließ.

**Wieder kein einziger Absturz**: der `crash`-Puffer blieb über den gesamten Lauf leer, kein ANR.

## T9.4 — CSV-Export: bestanden

Geprüft mit vorsätzlich feindlichen Daten (über die DB gesetzt, damit Zeilenumbruch und
Anführungszeichen exakt sitzen): Session-Notiz `guter Tag, aber "die Ecke" nicht\nzweite Zeile;
drittens`, Boulder-Name `Der "Ofen", oben`, Boulder-Notiz mit Umlaut und Geviertstrich, dazu eine
zweite Session **ohne** Boulder.

Die exportierte Datei byte-genau nachgerechnet:

| Erwartung | Ergebnis |
|---|---|
| UTF-8-BOM am Anfang (Excel-Umlaute) | `EF BB BF` ✓ |
| CRLF als Zeilenende | 5 × `\r\n`; die 3 nackten `\n` sitzen ausschließlich *innerhalb* gequoteter Felder ✓ |
| `"` verdoppelt | `"Der ""Ofen"", oben"` ✓ |
| Komma-Felder gequotet | ✓ |
| Semikolon **nicht** gequotet | ✓ (bei Komma-Trennung richtig — der Unit-Test hält das ausdrücklich fest, damit es niemand „repariert") |
| Umlaute, Geviertstrich | `München`, `Übergriff`, `—` alle heil ✓ |
| Session ohne Boulder behält ihre Zeile | ✓, mit sieben leeren Route-Spalten |
| laufende Session | steht als `aktiv` ✓ |
| IDs gegen Namen aufgelöst | Halle, Gradsystem, Grad-Label ✓ |
| Abbruch (Dateiauswahl mit Zurück verlassen) | zurück in den Einstellungen, kein Toast, kein Absturz ✓ |

**Damit ist auch der heute geschriebene `SessionCsvTest` belegt:** er behauptet genau dieses
Verhalten, und das Gerät bestätigt es Zeichen für Zeichen.

## Teil 10 — Medien: drei Befunde, keiner schwer

| Test | Ergebnis |
|---|---|
| T10.1 Foto aufnehmen | **OK** — Datei in `files/aufnahmen`, im Formular und im Detail sichtbar |
| T10.2 Video aufnehmen | **OK bis auf den Ton**, siehe F14 |
| T10.3 Kamera-Freigabe verweigert | **OK**, siehe F16 für eine Ungenauigkeit im Text |
| T10.4 Galerie-Weg | **OK** — Bild überlebt `force-stop`, kein `SecurityException` |
| T10.5 Aufnahme abbrechen | **OK** — Formular unverändert |
| T10.6 keine Doppelübernahme | **OK** — nach Abbruch *und* nach Drehen steht genau das zuletzt gewählte Medium |
| T10.7 Spracheingabe | **Ablehnungszweig OK**, die Erkennung selbst nicht prüfbar (kein Mikrofonsignal über adb) |

### F14 — Boulder-Videos sind immer stumm

`RECORD_AUDIO` wird im Kamera-Pfad **nie erfragt**. Die Aufnahme selbst ist korrekt abgesichert
([CameraCaptureController.kt:159](app/src/main/java/com/boulderbuddy/data/camera/CameraCaptureController.kt:159)):
Ton nur, wenn die Freigabe schon vorliegt, sonst stumm statt `SecurityException`. Das ist als
Entscheidung dokumentiert und richtig — die Folge ist trotzdem, dass praktisch **jedes** Video
ohne Ton entsteht, weil dem Nutzer die Wahl nie angeboten wird.

Am Gerät belegt: `RECORD_AUDIO granted=false`, und die 34-MB-Datei enthält keinen `soun`-Handler,
nur `vide`. Der Testplan erwartet unter T10.2 ausdrücklich „Ton vorhanden".

**Entschieden am 09.08.2026: stumm ist gewollt.** Kein Code wird geändert. Begründung: eine
Boulder-Aufnahme ist ein Bewegungsbeleg, kein Tondokument, und dafür lohnt sich keine zweite
Freigabe-Abfrage im Aufnahme-Weg. Die Erwartung „Ton vorhanden" ist aus T10.2 des Testplans
entfernt, damit derselbe Befund nicht bei jedem Durchlauf neu auftaucht.

### F15 — Der Auslöser hat keine Beschriftung

Auf dem Kamera-Screen ist der Auslöser bei (540, 2180) klickbar, trägt aber weder `text` noch
`content-desc` — auch auf keinem Kindknoten. Mit TalkBack ist die Aufnahme damit nicht
auslösbar. Die Nachbarn sind alle korrekt beschriftet (`Abbrechen`, `Foto`, `Video`,
`Kamera wechseln`), und **während** einer laufenden Videoaufnahme trägt derselbe Knopf
`Aufnahme beenden` — es fehlt also nur der Ruhezustand.

Gehört zum TODO-Punkt „16 Stellen mit `contentDescription = null` durchgehen"; dies ist die
Stelle, an der es eine Funktion unbenutzbar macht statt nur eine Dekoration unbenannt zu lassen.

**Behoben am 09.08.2026.** Der Knopf trägt seinen Namen jetzt selbst (`Modifier.semantics` mit
`Role.Button`), und der Name nennt den Modus. Die Beschriftung am Stopp-Icon ist entfallen —
sonst hätte TalkBack sie zusammen mit der des Knopfes zweimal vorgelesen.

Am Gerät gegengeprüft, alle drei Zustände:

| Zustand | Vorgelesen |
|---|---|
| Foto-Modus | `Foto aufnehmen` |
| Video-Modus, bereit | `Aufnahme starten` |
| Video-Modus, läuft | `Aufnahme beenden` (genau einmal) |

### F16 — Ablehnungstext verweist unnötig in die System-Einstellungen

Nach **einer** Ablehnung der Kamera-Freigabe verschwindet der „Freigeben"-Knopf, und der Text
sagt, man könne die Freigabe „in den System-Einstellungen der App nachtragen". Tatsächlich
genügt es, den Bildschirm zu verlassen und erneut zu öffnen — dann steht „Freigeben" wieder da
und Android zeigt den Dialog ein zweites Mal. Keine Sackgasse, aber der Text schickt den Nutzer
einen Umweg, den er nicht gehen muss. (Der Hinweis auf die Galerie als Alternative steht
korrekt daneben.)

**Behoben am 09.08.2026.** Der Kommentar im Code nannte die Regel schon richtig — „Android zeigt
den Systemdialog nach der **zweiten** Ablehnung nicht mehr" — nur behandelte der Code bereits die
erste so. `shouldShowRequestPermissionRationale` unterscheidet die beiden Fälle; daraus sind drei
Zustände statt zwei geworden. Der Knopf verschwindet erst, wenn Android wirklich nicht mehr
fragt: ein Knopf, der einen Dialog verspricht, der nicht mehr kommt, wäre schlimmer als keiner.

Am Gerät gegengeprüft (mit `pm reset-permissions` für einen unberührten Ausgangsstand — ohne das
zählt Android frühere Ablehnungen derselben Sitzung mit und der erste Fall ist nicht messbar):

| Zustand | Text | Knopf |
|---|---|---|
| noch nicht gefragt | „Zum Aufnehmen braucht die App Zugriff …" | `Freigeben` |
| einmal abgelehnt | „… gleich hier noch einmal erteilen" | `Erneut fragen` → Dialog kommt wirklich |
| zweimal abgelehnt | „… nur noch in den System-Einstellungen" | keiner |

## T14.1 — Ghost Climber bis zur Analyse: bestanden

Zwei Videos aus der Galerie (23 s und 26 s, dieselbe Aufnahmezeit — offenbar das echte
Vergleichspaar), „Posen analysieren" gestartet.

| | |
|---|---|
| Referenz | 289 Frames |
| Vergleich | 315 Frames |
| Dauer gesamt | rund 7 Minuten (~1,6 Frames/s) |
| Fortschrittsanzeige | lebendig und frameweise („Referenz: Frame 94 / 289", danach „Vergleich: Frame 99 / 315") |
| Oberfläche währenddessen | ansprechbar, `uiautomator` liefert durchgehend Antworten |
| **ANR** | **keine** |
| **Absturz** | **keiner**, `crash`-Puffer leer |

Danach sauberer Übergang in den Anker-Schritt mit verständlicher Anleitung („Tippe in beiden
Videos DIESELBEN 4+ markanten Wandpunkte …").

Damit ist der heute vorgesehene Umfang erfüllt — der Plan sah T14.1 ausdrücklich „bis zur
Analyse, ohne Bewertung der Qualität" vor. **Nicht geprüft:** Anker setzen, Pfad bestätigen,
Ansichten umschalten, Analyse speichern/laden/löschen.

## Teil 11 — Widget: zwei Befunde, beide behoben

Das Widget wurde von Hand platziert (der Pixel Launcher nimmt für Widget-Drags keine injizierten
Touch-Events an — zwei Versuche, auch als zusammenhängende `motionevent`-Geste in einem einzigen
Shell-Aufruf; das ist eine Grenze des Werkzeugs, kein Befund an der App).

| Test | Ergebnis |
|---|---|
| T11.1 Widget platzieren | **OK** — Hallenname, „läuft gerade · 5 Boulder · 4 Tops", Knöpfe „Session öffnen" und „Timer" |
| T11.2 Widget aktualisiert sich | **war kaputt** → behoben, siehe F17 |
| T11.3 Widget-Sprünge | **OK** — „Session öffnen" landet direkt in der Session, genau ein Zurück führt nach Home |
| T11.4 nach Session-Ende | **OK** — „Keine aktive Session · 6 Tops insgesamt · Session starten", kein Sprung mehr in die beendete Session |
| T11.5 Widget-Theme | **war kaputt** → behoben, siehe F17 |

### F17 — Das Widget erfährt von Änderungen nicht, die es angehen

Ein Fehler, zwei Erscheinungsformen. Die Theme-Logik selbst ist **richtig**: `paletteFuer`
wählt Auto/Dark/Light aus `WidgetData.darkModeOverride`, gespeist aus
`settingsRepository.darkMode`. Und die Datenberechnung stimmt auch. Was fehlte, war der Anstoß.

Eine Glance-Composition-Session endet ~45 s nach der letzten Composition. Danach steht auf dem
Homescreen das zuletzt gezeichnete Bild, bis ein Update-Event eine neue Session startet.
`refreshBoulderWidget` löste das bisher nach **Session-Start, Session-Ende und Abgleich** aus —
bei einem neuen Boulder und beim Dark-Mode-Schalter nicht.

Das war am Gerät sauber zu trennen:

| Beobachtung | Was sie zeigt |
|---|---|
| App hell, System dunkel, Widget dunkel | sieht nach falscher Theme-Logik aus |
| Tipp auf „Aktualisieren" → Widget wird sofort hell | **die Logik ist richtig, der Anstoß fehlte** |
| App 6 Boulder, Widget 5 | dieselbe Ursache an einer zweiten Stelle |

**Behoben** mit je einem `refreshBoulderWidget`-Aufruf in `EinstellungenViewModel.setDarkMode`
und am Ende von `RouteHinzufuegenViewModel.save` — dasselbe Muster, das die Session-Pfade schon
verwenden.

Gegengeprüft, jeweils **ohne** den Aktualisieren-Knopf:

| Fall | Ergebnis |
|---|---|
| Boulder angelegt | Widget springt sofort von 5 auf 7 Boulder / 6 Tops |
| Dark Mode aus, System bleibt dunkel | Widget wird cremefarben — der Override gewinnt, wie gedacht |
| Dark Mode wieder an | Widget wird dunkel |

Die eindeutige Messung ist die mittlere: solange App-Schalter und System übereinstimmen, lässt
sich „folgt dem Override" nicht von „folgt dem System" unterscheiden.

**Bewusst nicht mitgezogen:** das Ändern der Versuche im Boulder-Detail. Es verschiebt weder
Boulderzahl noch Tops, das Widget zeigt davon also nichts.

## Teil 12 — Näherungs-Push: läuft, zwei Befunde

Ohne Fahrt geprüft, wie in `PUSHNOT_TESTEN.md` beschrieben: der Geofence wird mit
`INITIAL_TRIGGER_DWELL` registriert, ein Gerät im Radius löst also von selbst aus. Testhallen mit
den hiesigen Koordinaten (52,137 / 9,964), Radius auf dem Standardwert 150 m. Alle Testhallen
sind nach dem Lauf gelöscht.

| Test | Ergebnis |
|---|---|
| T12.1 Berechtigungskette | **OK** — Standort (präzise) → System-Seite „Immer erlauben" → Benachrichtigungen, in dieser Reihenfolge; danach alle drei erteilt und der Schalter an |
| T12.2 ganze Kette zuhause | **OK** — Registrierung 11:09:11, `DWELL … → NOTIFY` 11:13:39 (4,5 min), Notification im `gym_proximity`-Kanal, Tap öffnet „Neue Session" mit der Halle **vorausgewählt** |
| T12.3 Politik erklärt sich | **3 von 5 Fällen belegt**, siehe unten |
| T12.4 Neustart | **OK** — Beleg unten |
| T12.5 Master-Toggle aus | **OK**, aber vorher lautlos → F19 |
| T12.6 Doze | **OK, besser als erwartet** — siehe unten |

### Die Politik, gemessen

```
DWELL an Gym 2 (Testhalle)  → ACTIVE_SESSION     (laufende Session, keine Notification)
DWELL an Gym 3 (Testhalle2) → ACTIVE_SESSION
DWELL an Gym 2 (Testhalle)  → COOLDOWN           (heute schon gepusht, keine Notification)
DWELL an Gym 3 (Testhalle2) → NOTIFY             (frische Halle → Notification)
```

Nebenbei belegt das die **Reihenfolge**: eine laufende Session unterdrückt global und *vor* dem
hallenspezifischen Cooldown — beide Hallen meldeten `ACTIVE_SESSION`, obwohl eine davon
zusätzlich im Cooldown stand.

**Nicht provoziert:** `DISABLED` und `POST_SESSION_QUIET`. `DISABLED` ist am echten Gerät kaum
herstellbar, weil eine Halle mit abgeschaltetem Pro-Gym-Toggle gar nicht erst registriert wird —
der Fall greift nur im Fenster zwischen Umschalten und nächstem Refresh. `POST_SESSION_QUIET`
hätte eine vierte Testhalle plus einen weiteren 5-Minuten-Durchgang gekostet.

### T12.4 — Neustart: bestanden, aber anders belegt als geplant

Nach `adb reboot` steht **keine** `Geofence(s) registriert`-Zeile im Log — der Puffer läuft beim
Booten über, die Zeile ist schlicht herausgefallen. Der Beleg kommt stattdessen vom Verhalten:

```
11:40:08  DWELL an Gym 2 (Testhalle)  → COOLDOWN
11:40:08  DWELL an Gym 3 (Testhalle2) → COOLDOWN
```

Fünf Minuten nach dem Boot feuern beide Geofences — **ohne dass die App je geöffnet wurde**
(der Prozess war zwischenzeitlich nur für `BoulderWidgetReceiver` gestartet worden). Sie wurden
also re-registriert, und die Politik antwortet korrekt. Das ist ein stärkerer Beleg als die
Logzeile es gewesen wäre.

### T12.6 — Doze: der Push kommt *während* Deep Idle

`dumpsys battery unplug` + `dumpsys deviceidle force-idle`, Zustand nachweislich `IDLE`:

```
11:49:56  DWELL an Gym 4 (Testhalle3) → NOTIFY   (Notification erschien)
```

Der Plan erwartete den Push „spätestens beim Verlassen von Doze". Auf diesem Gerät kommt er
**im** Doze. Damit ist die offene Frage aus M5 beantwortet — für dieses Gerät und diese
Android-Version; ein Hersteller mit aggressiverem Akku-Management kann sich anders verhalten.

### Eine Warnung, die keine ist

Beim Registrieren protokolliert das System:

```
Geofencer: registration not active, registration not permitted for registration: …
Geofencer: geofence trigger blocked - initial event filter not matched
```

Das sieht nach einem Fehlschlag aus, ist aber keiner: die Meldung kommt, bevor ein frischer
Standort-Fix vorliegt, und der Trigger feuerte danach zuverlässig. Wer beim nächsten Durchlauf
darüber stolpert, sucht sonst an der falschen Stelle.

### F18 — Die Benachrichtigung war grammatisch falsch

Sie hieß **„Bist du im Testhalle?"**. Das „im" stand fest im Text
([ProximityNotifier.kt:65](app/src/main/java/com/boulderbuddy/proximity/ProximityNotifier.kt:65))
und passt zu fast keinem Hallennamen — „im Boulder World München" wäre genauso falsch. Jede
Präposition mit Artikel setzt ein Genus voraus, das ein frei eingegebener Name nicht hat.

**Behoben:** der Name steht jetzt als Titel für sich, die Frage darunter — das umgeht die
Deklination vollständig und entspricht dem üblichen Aufbau einer Notification. Am Gerät
gegengeprüft: Titel `Kletterwerk`, Text `Bist du da? Session starten.`

### F19 — Der Master-Toggle räumte lautlos ab

`refreshGeofences` entfernt zuerst alles und steigt dann aus, wenn der Toggle aus ist — richtig,
aber ohne jede Logzeile. Im Logcat sah „ausgeschaltet" damit genauso aus wie „abgestürzt", und
der Testplan erwartet an dieser Stelle ausdrücklich eine Meldung.

**Behoben:** beide stillen Ausstiege sagen jetzt, warum. Am Gerät belegt, inklusive der zweiten
Variante beim Löschen der letzten Halle mit Koordinaten:

```
GeofenceManager: Geofences entfernt: Erinnerungen sind ausgeschaltet
GeofenceManager: Geofences entfernt: keine Halle mit Koordinaten und aktiver Erinnerung
```

## Weiterhin nicht geprüft

* **T13 Abgleich** — hier stand zuerst pauschal „zweites Gerät fehlt". Das gilt aber **nur für
  den Funkweg (T13.5)**. Die vier Datei-Tests **T13.1–T13.4** (Abgabe erzeugen, eigenen Stand
  wieder einlesen und danach zählen, Unsinns-Datei, Rückgängig) brauchen genau **ein** Gerät und
  sind schlicht nicht gemacht worden. Der Datei-Weg ist damit weiterhin nur durch JVM-Tests und
  eine Emulator-Sichtung belegt, nie am echten Gerät gegen echten Speicher. Auch von T13.5 ist
  die Hälfte allein prüfbar: Berechtigungskette, Foreground-Service-Notification und ob der
  Suchzustand beim Abbrechen sauber endet.
* **T10.7 Erkennung** — über adb kein Mikrofonsignal einspielbar.
* **T14.1 ab dem Anker-Schritt** — siehe oben.
* **T12.3 `DISABLED` und `POST_SESSION_QUIET`** — siehe Begründung oben.
* **Doze über echte Stunden** — nur der erzwungene Zustand ist gemessen, nicht das
  Langzeitverhalten mit App-Standby-Buckets.
