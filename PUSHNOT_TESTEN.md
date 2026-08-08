# Gym-Näherungs-Push zuhause testen (M5)

Das Feature ist gebaut (M0–M4), aber noch nie an einem echten Standortwechsel gelaufen. Diese
Datei ist die Antwort auf „wie prüfe ich das, ohne zur Halle zu fahren".

Ein Satz vorweg, der alles andere erklärt: **du musst nirgends hinfahren.** Der Geofence wird mit
`INITIAL_TRIGGER_DWELL` registriert — bist du beim Registrieren schon *innerhalb* des Radius,
startet die Verweiluhr sofort und der Trigger feuert nach ~2 Minuten. Eine Halle mit deinen
Wohnzimmer-Koordinaten ist damit ein vollwertiger Testfall. Das ist Workflow 1, und für 90 % der
Fragen der einzige, den du brauchst.

---

## 0. Die Kette — was alles stimmen muss, bevor etwas passiert

Wenn nichts kommt, liegt es fast immer an einem Glied hier. Von oben nach unten prüfen:

| # | Bedingung | Wo | Woran du merkst, dass es fehlt |
|---|---|---|---|
| 1 | Master-Toggle „Gym-Erinnerungen" an | Einstellungen | `refreshGeofences` kehrt sofort zurück, kein Log |
| 2 | Standort *und* „Immer erlauben" erteilt | System-Dialoge beim Einschalten | Logcat: `Geofences nicht registriert: Hintergrund-Standort fehlt` |
| 3 | Benachrichtigungen erlaubt | Dialog nach dem Standort | Push wird still verworfen, Rest läuft |
| 4 | Halle hat Koordinaten | Einstellungen → Hallen verwalten → Halle | Halle wird beim Registrieren übersprungen |
| 5 | Pro-Gym-Toggle „Erinnerungen aktiv" an | derselbe Screen | dito |
| 6 | Standortdienste am Gerät an | System | Logcat: `Geofence-Registrierung fehlgeschlagen` |
| 7 | Keine laufende Session | — | Logcat: `→ ACTIVE_SESSION` |
| 8 | Letzte Session **an dieser Halle** > 3 h her | — | `→ POST_SESSION_QUIET` |
| 9 | Letzter Push für diese Halle > 24 h her | — | `→ COOLDOWN` ← **der häufigste Stolperstein beim Iterieren** |

Punkt 7 gilt global (irgendeine laufende Session unterdrückt jeden Push), Punkt 8 nur für *diese*
Halle — und daran hängt eine Falle, siehe unten.

### Wie eine Session überhaupt an eine Halle kommt

Im „Neue Session"-Formular gibt man keine Halle aus einer Liste an, sondern einen **freien Text**.
`SessionErstellenViewModel.createSession()` macht daraus ein **find-or-create über den Namen**
(getrimmt, Groß-/Kleinschreibung egal); die Session speichert danach eine echte `gymId`. Punkt 8
vergleicht also IDs, nicht Strings — aber der Weg dorthin führt über den Namen.

**Die Folge:** wer den vorbefüllten Namen antippt und abändert, legt eine *zweite* Halle ohne
Koordinaten an. Die gefencte Halle bekommt die Session dann nie, `lastSessionEndedAt` bleibt für sie
leer, und `POST_SESSION_QUIET` feuert nicht — obwohl man gerade dort trainiert hat. Auch der
`SESSION`-Besuch landet an der falschen Halle, das gelernte Muster der richtigen wächst also nur
noch aus Geofence-Ankünften.

Beim normalen Ablauf passiert das nicht: der Deep-Link der Notification füllt das Feld mit dem
*exakten* Hallennamen vor, unverändert übernommen trifft der Vergleich immer. Fürs Testen heißt
das: **Session über den Notification-Tap starten oder den Namen exakt aus „Hallen verwalten"
kopieren**, sonst prüft man Punkt 8 an einer Halle, die gar nicht gemeint war.

Punkt 2 ist zweistufig, weil Android es erzwingt: erst der normale Standort-Dialog, dann ein
*zweiter* Weg über die System-Einstellungen für „Immer erlauben". Die App führt dich da durch,
aber wenn du beim zweiten Schritt abbrichst, ist alles andere umsonst — Punkt 1 sieht dann grün
aus, obwohl nichts registriert ist.

### Dein Logcat-Fenster

Lass das nebenher laufen, es zeigt jedes Glied der Kette:

```bash
adb logcat -s GeofenceManager:D GeofenceReceiver:D ProximityEventHandler:D
```

Die eine Zeile, auf die es ankommt:

```
ProximityEventHandler: DWELL an Gym 1 (Boulder World München) → NOTIFY
```

Statt `NOTIFY` kann dort `DISABLED`, `ACTIVE_SESSION`, `POST_SESSION_QUIET`, `COOLDOWN` oder
`UNTYPICAL_SLOT_COOLDOWN` stehen. Die Politik sagt dir also selbst, warum sie geschwiegen hat —
du musst nie raten.

---

## Workflow 1 — Zuhause, ohne Mock-Location (der wichtigste)

Prüft die komplette Kette am echten Gerät: Geofence-Registrierung, DWELL-Trigger, Besuchs-Logging,
Politik, Notification, Deep-Link.

1. Einstellungen → **Hallen verwalten** → eine Halle öffnen (oder anlegen).
2. **„Aktuellen Standort übernehmen"** — du stehst gerade zuhause, also ist die Halle jetzt dein
   Wohnzimmer. Alternativ „Koordinaten manuell eingeben".
3. Radius auf **50 m** (Minimum des Reglers). Klein halten, sonst deckt der Geofence die halbe
   Nachbarschaft ab und du kannst später nicht mehr unterscheiden, ob „drinnen" stimmt.
4. Pro-Gym-Toggle **„Erinnerungen aktiv"** an, speichern.
5. Einstellungen → **„Gym-Erinnerungen"** einschalten. Jetzt kommen die Dialoge: Standort →
   „Immer erlauben" → Benachrichtigungen. Alle drei durchklicken.
6. Logcat sollte sagen: `1 Geofence(s) registriert`.
7. **Warten.** ~2 min Verweildauer plus Play-Services-Latenz — in der Praxis 2–8 Minuten. Bildschirm
   darf ausgehen, App darf zu sein; genau das ist ja der Punkt.
8. Push kommt → antippen → „Neue Session" mit vorbefülltem Ort.

**Wiederholen:** Master-Toggle aus/an registriert die Geofences neu und stößt den Initial-Trigger
erneut an. Aber der 24-h-Cooldown blockt den zweiten Push — vorher Abschnitt „Cooldown
zurücksetzen" unten.

---

## Workflow 2 — Ankunft statt „schon da" (Emulator)

Workflow 1 testet den Fall „App wird eingeschaltet, während ich drinnen bin". Der echte Alltagsfall
ist umgekehrt: **von draußen nach drinnen**. Dafür braucht es einen bewegten Standort, und den gibt
nur der Emulator her (ein echtes Gerät bräuchte eine Mock-Location-App aus den Entwickleroptionen).

Voraussetzung: AVD **mit Play Services** — ohne die gibt es keine Geofencing-API. Der laufende
Pixel-Tablet-AVD hat sie.

```bash
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

1. Erst **weit weg** positionieren (Reihenfolge: **Längengrad zuerst**, das ist die klassische
   Falle):

```bash
adb -s emulator-5554 emu geo fix 11.5820 48.1351
```

2. In der App eine Halle mit Koordinaten **daneben** anlegen — z. B. 48.1400 / 11.5820 (gut 500 m
   entfernt), Radius 150 m. Feature einschalten (im Emulator „Immer erlauben" ebenso durchklicken).
3. Logcat: `1 Geofence(s) registriert`, **kein** DWELL — richtig so, du bist draußen.
4. Jetzt „hinfahren":

```bash
adb -s emulator-5554 emu geo fix 11.5820 48.1400
```

5. Der Emulator sendet den Fix nur einmal. Play Services braucht laufende Positionen, um die
   Grenzüberschreitung zu bemerken — den Befehl deshalb ein paar Mal wiederholen (alle ~20 s über
   2–3 Minuten). Dann kommt ENTER → nach Verweildauer DWELL.

Das ist der Workflow, mit dem du **Radius und Verweildauer kalibrierst**: verschiedene Abstände
durchspielen und schauen, ab wann er zuverlässig anschlägt.

---

## Workflow 3 — Nur der Deep-Link (10 Sekunden, kein Geofence)

Wenn du nicht die Erkennung, sondern nur das Verhalten *nach* dem Tap prüfen willst — was die
Notification auslöst, lässt sich direkt schicken:

```bash
adb shell am start -n com.boulderbuddy/.MainActivity --es com.boulderbuddy.widget.NAV_TARGET new_session --ei com.boulderbuddy.proximity.GYM_ID 1
```

Erwartung: „Neue Session" öffnet sich, **Halle/Ort ist mit dem Namen von Gym 1 vorbefüllt**.
(Verifiziert — genau so verhält es sich.) Die `1` ist die Gym-ID; mit anderen Zahlen prüfst du
andere Hallen. Eine unbekannte ID lässt das Feld leer, statt zu crashen.

Das umgeht Geofence, Politik und Cooldown vollständig — dafür sagt es dir auch nichts über sie.

---

## Workflow 4 — Übersteht ein Neustart die Geofences?

Geofences überleben keinen Reboot; `GeofenceBootReceiver` registriert sie neu. Der einzige Weg, das
zu prüfen, ist ein echter Neustart:

```bash
adb reboot
```

Nach dem Hochfahren, **ohne die App zu öffnen**:

```bash
adb logcat -d -s GeofenceManager:D
```

Erwartung: `N Geofence(s) registriert`. Kommt nichts, feuert nach jedem Neustart nie wieder ein
Push, bis die App einmal von Hand geöffnet wurde — der stillste denkbare Fehler und deshalb der
Test, den man nicht auslassen sollte.

---

## Workflow 5 — Die Politik gezielt provozieren

Jede Entscheidung lässt sich herstellen. Alle prüfst du an derselben Logcat-Zeile.

| Erwartung | So stellst du sie her |
|---|---|
| `NOTIFY` | Workflow 1, alles sauber |
| `DISABLED` | Pro-Gym-Toggle aus, Master an → registriert erst gar nicht |
| `ACTIVE_SESSION` | Session starten, dann Master-Toggle aus/an |
| `POST_SESSION_QUIET` | Session starten **und beenden**, dann Master-Toggle aus/an (wirkt 3 h). Namen dabei **nicht** ändern — sonst hängt die Session an einer neuen Halle und der Fall tritt nie ein |
| `COOLDOWN` | zweiter Durchlauf innerhalb 24 h |
| `UNTYPICAL_SLOT_COOLDOWN` | braucht ≥ 5 Besuche im Muster — realistisch nur über die Zeit |

Die letzten beiden hängen an gelernten Mustern und Zeitabständen, die man am Gerät kaum in einer
Sitzung herstellt. **Genau dafür sind die JVM-Tests da** — dort ist „jetzt" ein Parameter:

```bash
./gradlew :app:testDebugUnitTest --tests "*ProximityNotificationPolicyTest" --tests "*GymVisitStatsTest"
```

Die decken Cooldown-Grenzen, Muster-Dämpfung und Tages-Dedupe vollständig ab. Am Gerät musst du nur
noch prüfen, dass die *Verdrahtung* stimmt — dass die Politik überhaupt mit den richtigen Werten
aufgerufen wird.

### Cooldown zurücksetzen

Der Zeitstempel steht als `gym_notified_<gymId>` im DataStore, nicht in der DB. Zurücksetzen:

```bash
adb shell am force-stop com.boulderbuddy
```
```bash
adb shell run-as com.boulderbuddy rm files/datastore/settings.preferences_pb
```

Das Force-Stop muss zuerst kommen — DataStore hält den Stand im Speicher und schriebe ihn sonst
wieder hin. **Achtung:** die Datei enthält auch Dark-Mode, Standard-Gradsystem, Timer-Vorgaben und
den Master-Toggle. Nach dem Löschen ist das Feature aus und muss neu eingeschaltet werden (was
praktischerweise gleich die Geofences neu registriert).

Alternative ohne Kollateralschaden: die Halle löschen und neu anlegen — sie bekommt eine neue ID und
damit einen frischen Key. Kostet aber ihre Besuchs-Historie.

---

## Was du beim Testen kalibrierst (das eigentliche M5)

Die Zahlen sind gesetzt, aber geraten. Alle stehen als benannte Konstante mit Kommentar:

| Wert | Default | Wo |
|---|---|---|
| Geofence-Radius | 150 m | pro Halle im Editor, `GymEntity.DEFAULT_GEOFENCE_RADIUS_METERS` |
| Verweildauer vor Trigger | 120 s | `GeofenceManager.LOITERING_DELAY_MILLIS` |
| Cooldown je Halle | 24 h | `ProximityNotificationPolicy.BASE_COOLDOWN_MILLIS` |
| Cooldown bei untypischer Zeit | 72 h | `ProximityNotificationPolicy.UNTYPICAL_SLOT_COOLDOWN_MILLIS` |
| Ruhe nach Session-Ende | 3 h | `ProximityNotificationPolicy.POST_SESSION_QUIET_MILLIS` |
| Besuche bis „Muster belastbar" | 5 | `GymVisitStats.MIN_VISITS_FOR_PATTERN` |

Der Radius ist der einzige, den du empirisch messen *musst*: zu klein und GPS-Ungenauigkeit im
Gebäude verhindert den Trigger, zu groß und der Bäcker gegenüber löst ihn aus. 150 m ist ein
Startwert, kein Ergebnis.

---

## Fallstricke

- **Cooldown beim Iterieren.** Der zweite Testlauf am selben Tag schweigt — und zwar korrekt. Wer
  das vergisst, sucht den Fehler im Geofencing.
- **Geofence-Latenz.** Play Services meldet nicht auf die Sekunde. Nach der Verweildauer können
  weitere Minuten vergehen, besonders im Stromsparmodus. Vor dem Aufgeben 10 Minuten warten.
- **Doze und Hersteller-Akkukiller.** Nach längerem Liegenlassen drosselt Android Geofences. Wenn
  der Push im Alltag ausbleibt, obwohl der Testlauf ging: Batterieoptimierung für BoulderBuddy
  ausnehmen und gegenprüfen. Das ist eine echte offene Frage für M5, keine Test-Panne.
- **`adb emu geo fix` nimmt Längengrad zuerst.** Vertauscht landest du im Indischen Ozean und
  wunderst dich über Stille.
- **Emulator ohne Play Services** hat keine Geofencing-API — das Feature ist dort tot, ohne dass
  jemand etwas sagt.
- **„Immer erlauben" wird gern übersehen.** Der zweite Dialog sieht aus wie eine Wiederholung des
  ersten. Ohne ihn passiert nichts, und der Toggle steht trotzdem auf an.
- **Standortdienste am Gerät.** Systemweit aus → `Geofence-Registrierung fehlgeschlagen` im Log,
  sonst keine Rückmeldung.

---

## Ohne Gerät: was die JVM abdeckt

```bash
./gradlew :app:testDebugUnitTest
```

Politik (Cooldowns, Muster-Dämpfung, Session-Unterdrückung), Besuchsstatistik (Histogramme,
typischer Slot) und Tages-Dedupe laufen vollständig in der JVM. Was dort grün ist, musst du am
Gerät nicht nachrechnen — am Gerät prüfst du Erkennung, Berechtigungen, Hintergrund-Verhalten und
Verdrahtung.

Für die Datenbank zusätzlich:

```bash
python tools/pruefe_migrationen.py
```
