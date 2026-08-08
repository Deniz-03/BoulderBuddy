# Testplan Emulator: Wear OS und Tablet

Stand: Branch `PixelBugfixes` (10 Commits über `main`, DB v11).
Zweck wie beim Pixel-Durchlauf: **Bugs finden**, nicht Features abnehmen. Beide Ziele sind bisher
kaum am Gerät gelaufen — die Wear-App gar nicht in diesem Projektabschnitt, das Tablet zuletzt
über Screenshots während der Layout-Arbeit.

## 0. Lage

| Gerät | Serial | Auflösung | dp | Android | App |
|---|---|---|---|---|---|
| Pixel Tablet | `emulator-5554` | 2560×1600 @ 320 dpi | **1280×800** | 15 (SDK 35) | installiert (alter Stand) |
| Wear OS Large Round | `emulator-5556` | 454×454 @ 320 dpi | **227×227** | 14 (SDK 34) | **nicht installiert** |

Die dp-Zahlen entscheiden über das halbe Testprogramm:

* **1280 dp quer → `Breite.Weit`** (≥ 840 dp): Zwei-Pane-Layouts *und* SideNav.
* **800 dp hoch → `Breite.Mittel`** (≥ 600, < 840): SideNav, aber **kein** Zwei-Pane.
* Das Tablet wechselt also **beim Drehen die Layout-Klasse**. Genau dafür steckt der ganze
  NavHost in einem Lambda statt zweimal im Baum
  ([AppNavigation.kt:144](app/src/main/java/com/boulderbuddy/ui/navigation/AppNavigation.kt:144)) —
  „zwei NavHost-Aufrufe wären zwei getrennte Back-Stacks". Diese Zusage ist am Tablet prüfbar
  und sonst nirgends.

```bash
./gradlew :app:assembleDebug :wear:assembleDebug
```

```bash
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

```bash
adb -s emulator-5556 install -r wear/build/outputs/apk/debug/wear-debug.apk
```

**Grundregel:** ein `FATAL EXCEPTION` im Logcat ist immer ein Fehlschlag, auch wenn die
Oberfläche es überspielt.

---

## Teil A — Wear OS

### A0 Voraussetzung: ist die Uhr überhaupt mit dem Telefon gekoppelt?

Alles unter A5 (Preset-Sync, Meldung ans Telefon) hängt am Wearable Data Layer, und der braucht
eine **Kopplung**, die zwei Emulatoren nicht von selbst haben. Vor A5 also klären, ob ein Node da
ist — sonst prüft man dort nur, dass nichts passiert.

**Erwartung, falls ungekoppelt:** die Uhr funktioniert eigenständig weiter (Timer, Auto, Log);
nur der Austausch fällt aus. Fällt stattdessen etwas **aus** oder hängt, ist das der Befund.

### A1 Start und Menü
**Schritte:** App öffnen.
**Erwartet:** Menü mit den drei Einträgen (Manuell / Auto / Sensor-Log), auf dem **runden**
Display vollständig lesbar — nichts an den Rändern abgeschnitten, nichts unter dem Rand
verschwunden. Scrollbar, falls länger als der Screen.

### A2 Timer konfigurieren
**Schritte:** Manuell → Sätze, Hang und Pause je hoch und runter stellen, an die Grenzen
(Sätze 1…30, Hang 1…120, Pause 0…300).
**Erwartet:** Werte bleiben in den Grenzen, keine negative Anzeige, Bedienelemente auf 227 dp
noch treffbar (Fingergröße!).

### A3 Konfiguration ist während des Laufs gesperrt
**Schritte:** Timer starten, dann Sätze/Zeiten ändern wollen.
**Erwartet:** geht nicht (`updateConfig` kehrt bei `running` zurück). **Das ist der Gegenentwurf
zum Telefon**, wo genau dieser Fall den Durchlauf verwarf (F3) — hier ist er von vornherein
verhindert. Zu prüfen ist, ob die Oberfläche das **zeigt** oder ob die Knöpfe nur tot sind.

### A4 ⚠️ Durchlauf mit Pause = 0 — Verdacht W1
**Schritte:** 3 Sätze / 2 s Hang / **0 s Pause**, komplett laufen lassen, mit Stoppuhr messen.
**Erwartet:** ~6 s.
**Verdacht W1:** [TimerViewModel.kt:150](wear/src/main/java/com/boulderbuddy/wear/presentation/TimerViewModel.kt:150)
hat exakt das Muster, das am Telefon F4 war — `delay(1000)`, dann herunterzählen, dann prüfen.
`restSec` ist auf `0..300` begrenzt, 0 also erreichbar. **Erwarteter Fehler: ~8 s statt 6.**
Am Telefon war die Abweichung 8,112 s; dort ist der Fix ein `while` statt `if`.

### A5 Was die Uhr dem Telefon meldet
**Schritte:** einen Durchlauf zu Ende bringen; danach am Telefon (bzw. Tablet) die
Hangboard-Historie ansehen.
**Erwartet:** das Workout taucht dort auf (`PATH_HANGBOARD_COMPLETED`). Ohne Kopplung: nichts —
dann ist A5 „n. a.", kein Fehler.

### A6 Preset-Sync
**Schritte:** am Telefon ein Preset anlegen, an der Uhr die Preset-Liste öffnen.
**Erwartet:** Preset erscheint (`PATH_HANGBOARD_PRESETS`). Ohne Kopplung entfällt der Test.

### A7 Auto-Hang-Erkennung
**Schritte:** Auto-Modus starten, den Emulator „bewegen" (Sensordaten im Extended-Controls-Panel
oder per `adb emu sensor set acceleration x:y:z`).
**Erwartet:** Dienst startet als Foreground Service mit Notification, Zustand ändert sich
sichtbar. **Realistische Grenze:** ohne echte Sensordaten ist eine Erkennung nicht auszulösen —
geprüft wird hier, dass der Dienst startet, sich beenden lässt und die Notification wieder
verschwindet. Alles Weitere gehört ans Handgelenk.

### A8 Sensor-Log
**Schritte:** Sensor-Log-Screen öffnen, Aufzeichnung starten, kurz laufen lassen, stoppen.
**Erwartet:** Aufzeichnung startet ohne Absturz, der Wakelock hängt nicht (Akku-Anzeige),
Export an das Telefon (`PATH_SENSOR_LOG`) nur mit Kopplung.

### A9 Rundes Display und Drehung
**Schritte:** alle Screens ansehen; falls der AVD es erlaubt, in den ambient/always-on-Modus.
**Erwartet:** kein Text unter der Rundung, keine Knöpfe halb außerhalb.

### A10 App verlassen und zurück
**Schritte:** während eines laufenden Timers zur Uhr-Startseite, nach 20 s zurück.
**Erwartet:** Timer läuft weiter und zeigt den richtigen Stand — dieselbe Zusage wie am Telefon,
wo sie gehalten hat.

---

## Teil B — Tablet

### B1 Zwei-Pane im Sessions-Tab
**Schritte:** Sessions-Tab öffnen (quer, 1280 dp).
**Erwartet:** Liste **und** Detail nebeneinander; eine Auswahl in der Liste wechselt das Detail
ohne Navigation; **kein Zurück-Pfeil** im Detail, solange die Liste daneben steht
([SessionRoute.kt:26](app/src/main/java/com/boulderbuddy/ui/screens/SessionRoute.kt:26)).

### B2 Zwei-Pane in der Boulder-Übersicht
**Schritte:** Boulder-Ansicht öffnen, Kacheln durchtippen.
**Erwartet:** Raster + Detail nebeneinander, Auswahl wechselt das Detail.

### B3 ⚠️ Drehen wechselt die Layout-Klasse — Verdacht T1
**Schritte:** im Sessions-Tab eine Session auswählen, **auf Hochformat drehen** (800 dp →
`Mittel`, kein Zwei-Pane mehr), dann zurück auf Querformat.
**Erwartet:** kein Absturz; die Auswahl bzw. der Verlauf geht nicht verloren; im Hochformat gibt
es einen sinnvollen Einzel-Screen mit Rückweg.
**Verdacht T1:** Das ist der Grenzfall, für den der NavHost bewusst nur einmal im Baum steht. Der
Wechsel Zwei-Pane ↔ Einzel-Pane bei erhaltenem Back-Stack ist die riskanteste Stelle des
Tablet-Layouts und bisher nur per Screenshot geprüft, nie durch echtes Drehen.

### B4 SideNav statt BottomNav
**Erwartet:** Navigation an der linken Seite, keine Leiste unten; über beiden Spalten ein
durchgehendes Band in Chrome-Farbe hinter der Statusleiste, **keine senkrechte Kante durch die
Systemsymbole** ([AppNavigation.kt:519](app/src/main/java/com/boulderbuddy/ui/navigation/AppNavigation.kt:519)).

### B5 Formulare bleiben eine Spalte
**Schritte:** Neue Session, Boulder hinzufügen, Gym-Editor, Einstellungen ansehen.
**Erwartet:** Inhalt auf Textspaltenbreite begrenzt und zentriert — kein Eingabefeld über
1248 dp, kein Label links und sein Schalter 1240 dp weiter rechts.

### B6 Diagramme und Heatmap hören auf zu wachsen
**Schritte:** Statistik-Tab, alle Zeiträume durchschalten.
**Erwartet:** Balken und Heatmap-Zellen gedeckelt (die Fehlerklasse, für die
`InhaltsBreiteTest` existiert); Verlaufs-Diagramme nebeneinander statt untereinander.

### B7 ⚠️ Die neuen Korrekturen am breiten Layout — Verdacht T2
**Schritte:** (a) Boulder-Formular: Namensfeld antippen, prüfen ob Speichern erreichbar bleibt.
(b) Session-Formular ausfüllen, drehen (Layout-Klasse wechselt!), Zustand prüfen.
**Erwartet:** wie am Telefon — Speichern über der Tastatur, Eingaben überstehen die Drehung.
**Verdacht T2:** Beide Fixes (F5 `inhaltsAbstandMitTastatur`, F2 `rememberSaveable`) sind **nur
am Telefon** verifiziert. Am Tablet kommt erschwerend hinzu, dass die Drehung zusätzlich die
Layout-Klasse wechselt — `rememberSaveable` muss das überstehen, und die Tastatur ist auf 800 dp
Höhe anteilig größer.

### B8 Der neue Hinweis auf laufende Sessions
**Erwartet:** erscheint auch im breiten Layout an der richtigen Stelle und bricht die Spalte
nicht.

### B9 Widget auf dem Tablet
**Schritte:** Widget auf den Homescreen legen, Dark Mode umschalten.
**Erwartet:** Inhalt stimmt, Theme folgt dem App-Schalter (der Fall, der schon einmal
cremefarben stehen blieb).

### B10 Kernfluss am Tablet
**Schritte:** Session anlegen → Boulder anlegen → beenden → Notiz nachtragen.
**Erwartet:** funktioniert wie am Telefon; besonders die **Notiz** (F10) und der **Hallenname
nach dem Löschen** (F9) sind hier noch nie geprüft worden.

### B11 Migration v10 → v11 auf gewachsenem Bestand
**Schritte:** Auf dem Tablet liegt eine **ältere Datenbank** (Stand vor v11). Die neue APK
darüber installieren, **ohne** zu deinstallieren.
**Erwartet:** Start ohne `IllegalStateException`, Daten vollständig, `istStandard` korrekt
gesetzt (V-Scale/Französisch geschützt, alles andere löschbar). **Das ist der einzige echte
Migrationstest auf gewachsenen Daten**, den dieser Durchlauf hergibt — auf dem Pixel wurde die
DB mehrfach gelöscht.

---

## Anhang: Verdachtsliste

| # | Verdacht | Ort | Test |
|---|---|---|---|
| W1 | Pause = 0 kostet auch auf der Uhr eine Sekunde je Satz | [TimerViewModel.kt:150](wear/src/main/java/com/boulderbuddy/wear/presentation/TimerViewModel.kt:150) | A4 |
| T1 | Layout-Klassen-Wechsel beim Drehen (Weit ↔ Mittel) verliert Zustand oder stürzt | [AppNavigation.kt:144](app/src/main/java/com/boulderbuddy/ui/navigation/AppNavigation.kt:144) | B3 |
| T2 | F5/F2 sind nur am Telefon verifiziert | `Insets.kt`, `rememberSaveable` | B7 |
| T3 | v11-Migration auf gewachsenem Bestand ungeprüft | `MIGRATION_10_11` | B11 |

**Nicht im Plan:** Ghost Climber (rechenintensiv, am Emulator ohne Aussagekraft), Näherungs-Push
(braucht Standortwechsel), Nearby-Abgleich (bräuchte beide Emulatoren gekoppelt — separat wert,
aber nicht Teil dieses Durchlaufs).
