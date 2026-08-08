# Emulator-Durchlauf Wear OS + Tablet — Ergebnis

Durchgeführt am 08.08.2026 auf Branch `PixelBugfixes` nach dem [Plan](EMULATOR_TESTPLAN.md).
Geräte: Wear OS Large Round (`emulator-5556`, Android 14, 227 dp rund) und Pixel Tablet
(`emulator-5554`, Android 15, 1280×800 dp).

**Drei Befunde, alle behoben.** Kein Absturz auf beiden Geräten über den ganzen Lauf.

---

## Befunde

### W1 — Pause = 0 kostet auch auf der Uhr eine Sekunde je Satz ✅ behoben

Der Verdacht aus dem Plan hat sich bestätigt, und zwar deutlich: 10 Sätze à 1 s Hang **ohne**
Pause brauchten **20,7 s statt 10,0**. Ursache ist dieselbe Zeile wie am Telefon (F4) —
`delay(1000)`, herunterzählen, *dann* prüfen. Die Zustandsmaschine der Uhr ist bewusst parallel
zur Phone-Version gebaut, und der Fehler ist mit ihr mitgewandert.

Nach dem Fix (`while` statt `if`) läuft derselbe Durchlauf in **unter 11,2 s** durch.

### W2 — Die Uhr behauptete eine Übertragung, die nicht stattfand ✅ behoben

Der Auto-Screen schrieb „An Phone übertragen", sobald Sätze erkannt wurden — **bedingungslos**.
`sendAutoWorkoutCompleted` war reines Fire-and-Forget: kein verbundener Node hieß nur eine
Log-Zeile. Am Emulator ohne Companion-App war die Meldung nachweislich falsch — die Uhr meldete
Erfolg, auf dem Tablet kamen 0 Workouts an.

Schwerer wiegt, was daraus folgt: **die Uhr legt Workouts nirgends lokal ab.** `WearSettings`
kennt nur die Timer-Konfiguration. Ohne Verbindung ist der Durchlauf weg — und der Nutzer las,
er sei angekommen.

Der Screen zeigt jetzt „Wird übertragen…", „An Phone übertragen" oder **„Nicht übertragen —
kein Phone verbunden"**. Das richtige Muster stand zwei Dateien weiter längst bereit:
`sendSensorLog` fragt sein Ergebnis seit jeher über `onResult` ab.

> **Nicht behoben:** die fehlende lokale Ablage. Der Durchlauf geht ohne Verbindung weiterhin
> verloren — er wird nur nicht mehr als angekommen ausgegeben. Eine lokale Warteschlange wäre
> ein Feature, kein Fix, und gehört entschieden statt nebenbei gebaut.

### T1 — Nach dem Drehen bleibt das Tablet im Telefon-Layout ✅ behoben

Der riskanteste Punkt des Plans, und er trägt:

1. Querformat (1280 dp = `Weit`): Sessions-Tab zeigt Liste **und** Detail nebeneinander.
2. Auf Hochformat drehen (800 dp = `Mittel`): Zwei-Pane löst sich korrekt auf, die Liste bleibt.
3. Dort eine Session antippen → Vollbild-Detail mit Zurück-Pfeil. Für `Mittel` richtig.
4. **Zurück auf Querformat:** das Detail bleibt ein Vollbild-Screen mit Zurück-Pfeil — ohne
   SideNav, ohne Liste daneben, mit einem 2500 px breiten „Session beenden"-Knopf.

Der Back-Stack trägt in Schritt 3 ein Ziel (`Session(sessionId)`), das es im breiten Layout gar
nicht geben sollte: dort ist das Detail ein *Pane*, kein Screen. Kein Absturz, kein Datenverlust,
und mit einem Zurück wieder in Ordnung — aber die Zusage des Tablet-Layouts („kein Zurück-Pfeil,
solange die Liste daneben steht") ist in genau diesem Zustand gebrochen.

**Behoben — aber nicht als Redirect.** Der Fund sah nach Layout aus und war Architektur: die
Beziehung „Liste → Detail" existierte zweimal, breit als Pane und schmal als NavHost-Ziel. Die
Breiten-Verzweigung ist deshalb ganz entfallen; `ListDetailPaneScaffold` gilt jetzt für alle
Breiten. Es konnte den schmalen Fall die ganze Zeit selbst — es wurde nur nie gefragt. Zwei
`if`-Zweige fallen weg, kein neuer Mechanismus kommt hinzu.

**Der Gewinn ist größer als der Fund:** vorher verlor schon das reine Drehen von quer nach hoch
die Auswahl vollständig. Jetzt überlebt sie in beide Richtungen — am Tablet-Emulator geprüft
(quer → hoch → zurück: Zwei-Pane, dieselbe Session, kein Zurück-Pfeil) und am Pixel 6a
(Detail mit Zurück-Pfeil, Zurück-Kette Liste → Home, zwei Drehungen ohne Verlust).

`Session(sessionId)` und `BoulderDetail(boulderId)` bleiben als Routen für Sprünge von außerhalb
des Tabs; `SessionDetailScreen` hat dafür `inhaltsBreite` bekommen — als einziger Detail-Screen
fehlte es ihm, daher der 2500-px-Balken.

**Eine Nebenwirkung am Telefon:** die BottomNav bleibt im Session-Detail stehen, weil das Detail
jetzt im Tab liegt statt darüber. Vom Nutzer nicht entschieden, deshalb so gewählt, wie es sich
aus der Änderung ergibt — mit einer Zeile umkehrbar.

---

## Was sauber lief

| Test | Ergebnis |
|---|---|
| **B11 Migration v10 → v11 auf gewachsenem Bestand** | Auf dem Tablet lag eine DB vom 06.08. Das **Widget** hat nach dem Paket-Update den Prozess gestartet und die Migration lief, **bevor die App je geöffnet wurde** — ohne Absturz, Bestand vollständig (3 Systeme, 1 Session, 3 Boulder, 41 Grade) |
| **F11 auf echten Altdaten** | „Halle Nord" hat dort `gymId = NULL`, weil seine Halle am 06.08. gelöscht wurde — genau der Fall, der vor dem Fix „Standard" und unlöschbar geworden wäre. Steht korrekt auf `istStandard = 0` |
| **F6 auf echten Altdaten** | Die seit dem 06.08. laufende Session zeigt „6. August · läuft gerade" statt „Heute" |
| A1 Wear-Menü | drei Einträge, auf dem runden Display lesbar |
| A3 Konfiguration während des Laufs | Stepper sind **gar nicht vorhanden**, nicht bloß wirkungslos — besser gelöst als am Telefon, wo genau dieser Weg den Durchlauf verwarf (F3) |
| A7 Auto-Erkennung | startet als Foreground Service mit Notification, Rückfrage vor dem Beenden, Dienst und Notification danach sauber weg |
| A10 Timer im Hintergrund | läuft weiter und zu Ende |
| A0/A5/A6 ohne Kopplung | Das Tablet-Image hat **keine Wear-Companion-App** (`MISSING_COMPANION_APP`); Data Layer nicht verfügbar. Beide Apps **degradieren sauber** — Warnung im Log, kein Absturz |
| B1/B2 Zwei-Pane | Liste + Detail nebeneinander, Leerzustand rechts korrekt |
| B4 SideNav | links, durchgehendes Statusleisten-Band über beiden Spalten |
| B6 Diagramme | Balken gedeckelt, Zeitraum-Umschalter wirkt |
| **B7a F5 am Tablet** | Speichern-Knopf bei offener Tastatur erreichbar — der Phone-Fix trägt auch hier |
| **B7b F2 am Tablet** | Name *und* Versuchszahl überleben das Drehen **samt Layout-Klassen-Wechsel** |
| **B8 neuer Hinweis** | „In „Boulder World München" läuft bereits eine Session, seit 6. August, 01:20." — genau der Grund für `formatSeit`: eine nackte Uhrzeit wäre hier irreführend |

---

## Nicht geprüft

* **A5/A6 (Workout-Meldung, Preset-Sync)** — technisch unmöglich ohne Kopplung, siehe oben.
* **A4 Sensor-Log-Export**, **A9 Ambient-Modus** — der Export hängt ebenfalls an der Kopplung.
* **A7 Erkennungsqualität** — der Emulator liefert stehende Sensorwerte; die Erkennung meldete
  sofort „HÄNGT". Das ist **kein Befund**, sondern die Grenze des Emulators: Auto-Erkennung
  gehört ans Handgelenk.
* **B9 Widget-Inhalt/Theme**, **B10 Kernfluss am Tablet**, **B5 Formularbreite** (im Vorbeigehen
  gesehen, nicht systematisch), Ghost Climber, Näherungs-Push, Nearby-Abgleich.
