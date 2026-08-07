# Geräte abgleichen — Plan

Stand 06.08.2026, achte Fassung. **Letzte Fassung vor dem Bauen.** Anschluss an
`TABLET_PLAN.md`: das Tablet-UI steht, aber es gibt nichts darauf anzuzeigen.

Der Plan ist in fünf Runden an Abläufen durchgespielt worden:

| Runde | Was sie geändert hat |
|---|---|
| 1 (1–6) | Weg: Nearby statt Cloud-Ordner. Modell: Gedächtnis statt reinem Ersetzen |
| 2 (7–14) | Nummernbänder, Baum statt Zeilen, echte Migrationen als Voraussetzung |
| 3 (15–22) | Wer rechnet und wer entscheidet, ein Schritt rückgängig, Metatabellen, Payload-Größe |
| 4 (23–30) | Rückgängig war umkehrbar; Android-Backup klont die Geräte-Identität |
| 5 (31–37) | **Jede Ghost-Analyse wäre ein Dauerkonflikt**; **beide Geräte hätten nach dem Abgleich verschiedene Herkunft**; die alte `-wal` frisst die neue Datenbank |

Runde 5 hat zwei Fehler gefunden, die **jeden Abgleich dauerhaft** betroffen hätten, nicht nur
einen Sonderfall — und einer davon macht den Plan nebenbei kürzer.

## Ausgangslage (am Gerät nachgesehen, nicht angenommen)

| Was | Stand |
|---|---|
| Datenhaltung | Room, rein lokal (`BoulderBuddyDatabase`, Schema v6, 9 Tabellen) |
| Primärschlüssel | **durchgehend `@PrimaryKey(autoGenerate = true) val id: Int`** |
| Migrationen | **keine** — `fallbackToDestructiveMigration(dropAllTables = true)` |
| Größe der DB | 115 KB auf dem Testgerät (4 KB `.db` + 111 KB `.db-wal`) |
| Keypoint-Pfade in der DB | **absolut** (`GhostArtifactStore.poseTrackPath` → `absolutePath`, gespeichert in `GhostClimberViewModel:429`) |
| `applicationId` | `com.boulderbuddy`, **kein** Suffix je Build-Typ → FileProvider-URIs sind geräteunabhängig |
| Backup-Regeln | **leere Android-Studio-Vorlagen** — es wird alles gesichert, auch DataStore und `filesDir` |
| Widget-Auffrischung | nur bei Session-Start und -Ende |
| minSdk / targetSdk | 26 / 36 |
| Play Services | schon drin (`play-services-wearable`, für die Uhr) |
| Berechtigungen heute | `VIBRATE`, `RECORD_AUDIO`, `CAMERA` — kein Bluetooth, kein Standort, kein Foreground Service |

Die Fremdschlüssel bilden einen Baum, und der ist für den Abgleich wesentlich (Ablauf 8):

```
Gym
├── GradeSystem (gymId?, CASCADE) → Grade (systemId, CASCADE)
└── Session (gymId, CASCADE; gradeSystemId?, SET_NULL)
    ├── Route (sessionId, CASCADE; gradeId?, SET_NULL)
    └── HangboardWorkout (sessionId?, CASCADE) → HangboardSegment (workoutId, CASCADE)

HangboardTemplate, GhostAnalysis — ohne Fremdschlüssel
```

**Der CSV-Export ist kein Kandidat.** `SessionExporter` löst IDs gegen Namen auf — genau das
macht die Datei als Eingabe unbrauchbar; außerdem kennt er weder Hangboard noch Ghost.

**Die DB steht im WAL-Modus.** 4 KB in der Datei, 111 KB im `-wal`. Vor jedem Lesen
`PRAGMA wal_checkpoint(FULL)`, beim Ersetzen `-wal` und `-shm` mit löschen (Ablauf 34) — beides
dieselbe Falle wie beim Einspielen von Testdaten (`TABLET_PLAN.md`).

## Der Weg: Nearby Connections

Gefordert war ein Android-nativer Weg. Gewählt: **Nearby Connections**
(`com.google.android.gms:play-services-nearby`), Strategie `P2P_POINT_TO_POINT`.

- Kein fremder Dienst, kein Konto, kein Ordner — die Geräte reden direkt.
- Dieselbe Play-Services-Familie, über die die App schon mit der Uhr spricht.
- Nearby hebt selbstständig auf Wi-Fi Direct; realistisch 10–20 MB/s, die 2–3 GB des ersten
  Abgleichs sind Minuten statt eines Nachmittags Upload.
- **Beide Stände liegen gleichzeitig vor** — deshalb bestimmt die App die Richtung selbst.

| Was es kostet | |
|---|---|
| Beide Apps offen, Geräte nah beieinander | unvermeidbar; Hintergrund-Advertising wäre ein Akkufresser |
| Ab Android 13 | `NEARBY_WIFI_DEVICES` (`neverForLocation`), `BLUETOOTH_ADVERTISE`, `_CONNECT`, `_SCAN` |
| Bis Android 12 | zusätzlich **`ACCESS_FINE_LOCATION`** — hässlich für einen Abgleich, aber Nearby verlangt es. Vorher erklären |
| Große Übertragungen | Foreground Service (`dataSync`, ab API 34 mit `FOREGROUND_SERVICE_DATA_SYNC`) |
| Play Services | vorausgesetzt — die App hängt ohnehin daran |

**Der Datei-Weg bleibt als Zweitweg** (S8): Datensicherung, Gerätewechsel, und „die Geräte
finden sich partout nicht".

---

# Runden 1–4, zusammengefasst

**1 Zum ersten Mal.** Die erste Begegnung ist per Definition ein Konflikt → eigener Fall mit
Zahlen statt Fachbegriffen (E10). 3 GB brechen ab, wenn der Bildschirm ausgeht → Foreground
Service, fortsetzbar über die Inhaltsadressierung (E5).

**2 Der Alltag.** Direkter Nachfolger → die App **fragt nichts**. Ein Abgleich ohne Rückmeldung
fühlt sich an wie nichts → immer eine Bilanz.

**3 Etwas löschen.** Funktioniert. Am anderen Gerät verschwindet etwas kommentarlos → die Bilanz
benennt Löschungen. Videos bleiben liegen → „Speicherplatz freigeben" (E11).

**4 Der Ablauf, der reines Ersetzen bricht.** Analyse am Tablet, Session am Phone, dazwischen
kein Abgleich. Das ist **die gewollte Arbeitsteilung**; die Uhr verschärft sie. → **E4:
Gedächtnis.**

**5 Wenn etwas schiefgeht.** Abbruch folgenlos; Abgleich während laufender Session gesperrt (E9).

**6 Was der Nutzer nie sieht.** Ein Knopf: **„Geräte abgleichen"**, keine Fachbegriffe.

**7 Zwei neue Zeilen, dieselbe Nummer.** Beide Geräte zählen ab demselben Stand weiter. →
**E8: Nummernbänder** (+ `sqlite_sequence` danach zurücksetzen).

**8 Session gelöscht, Boulder dazugelegt.** → **E4: von oben nach unten entlang der
Fremdschlüssel**; Teilbaum gegen Teilbaum ist ein Konflikt.

**9 App-Update mit Schemawechsel.** `fallbackToDestructiveMigration` löscht die Daten. →
**S0: echte Room-Migrationen als Voraussetzung**, inklusive `basis.db`.

**10 Die Uhr meldet sich mitten im Abgleich.** Zeilenweises Anwenden in einer Transaktion →
kein Prozess-Neustart mehr im Alltag.

**11–14** Gleichzeitiges Drücken → feste Rollen über die Geräte-ID. Fehlendes Video → läuft
durch. Tablet neu aufgesetzt → Erstbegegnung. Einstellungen → gewinnen als Block.

**15 Abbruch nach dem Anwenden.** → Regel **„gleiche Nummer, gleicher Inhalt, der Basis
unbekannt = einig"**; erst anwenden, **dann** `basis.db`.

**16–17 Wer entscheidet, wer rechnet.** → **E12: der Nutzer entscheidet einmal pro Abgleich, das
Gerät mit dem Knopfdruck rechnet und schickt das Ergebnis. Nie über Zeitstempel.**

**18 Der Medien-Umzug läuft zweimal getrennt.** Geht nur gut, weil der Name allein aus dem
SHA-256 folgt → **Anforderung an S3.**

**19 `stand_meta` reist als Tabelle mit.** → Metatabellen vom Zeilenabgleich ausnehmen.

**20 Die Löschung war ein Versehen.** → **E13: ein Schritt rückgängig.**

**21 Es geht schon vorher nicht.** Platz, laufende Session auf der Gegenseite → **vorher
prüfen** (E9).

**22 BYTES-Payload ist gedeckelt.** → Stand als FILE-Payload.

**23 „Phone gewinnt" ≠ „Phone ersetzt alles".** → Die Konfliktantwort gilt **nur den
Konflikten** (E12).

**24 Rückgängig, und der nächste Abgleich hebt es auf.** → **E13: nur die Datentabellen
zurücksetzen**, `basis.db`/`stand_meta`/`generation` bleiben. Ein Undo ist **eine neue
Änderung**, keine Rückkehr.

**25–30** Aufräumen muss `basis.db` und `vorher.db` mitzählen · Android-Backup klont die
Geräte-Identität → **E14** · empfangene Payload-Dateien liegen doppelt und müssen weg ·
`refreshBoulderWidget` nach dem Abgleich · Keypoint-Kollision ist harmlos · gescheiterter
Checkpoint muss abbrechen.

---

# Runde 5 — was jeden Abgleich betroffen hätte

### Ablauf 31 — Jede Ghost-Analyse wäre ein Dauerkonflikt

`GhostAnalysisEntity.refKeypointsPath` und `cmpKeypointsPath` enthalten **absolute** Pfade —
`GhostArtifactStore.poseTrackPath` gibt `absolutePath` zurück, und genau das landet in der Zeile
(`GhostClimberViewModel:429`).

Der Plan sah bisher vor, diese Pfade beim Übernehmen auf den lokalen `filesDir` umzuschreiben.
Genau das ist die Falle: **nach dem Umschreiben trägt dieselbe Analyse auf beiden Geräten einen
anderen Wert.** Beim nächsten Abgleich vergleicht der Zeilenvergleich Inhalt gegen Inhalt und
findet: gleiche Nummer, verschiedener Inhalt, in der Basis so nicht vorhanden.

> **Das ist per Definition ein Konflikt — für jede Analyse, bei jedem Abgleich, für immer.**
> Der Nutzer bekäme jedes Mal eine Frage zu Einträgen, an denen niemand etwas geändert hat.
>
> Es fällt beim Testen auf zwei Emulatoren *nicht* auf, weil beide `/data/user/0/…` benutzen.
> Sichtbar würde es beim Zweitnutzer, im Arbeitsprofil — oder gar nicht, bis es jemanden trifft.
> Ein Vergleich, der eine gerätelokale Spalte einschließt, ist auch dann falsch, wenn er
> zufällig funktioniert.
>
> **Lösung (E15): den Pfad relativ zu `filesDir` speichern** (`ghost/pose_<sha1>.json`) und erst
> beim Lesen auflösen. Dann ist die Spalte geräteunabhängig, der Vergleich stimmt — **und die
> Umschreibung beim Übernehmen entfällt ersatzlos.** Ein Fehler weniger und ein Schritt weniger.
>
> Die Medien-URIs sind davon nicht betroffen: `applicationId` ist `com.boulderbuddy` ohne
> Suffix je Build-Typ, die FileProvider-Autorität also auf beiden Geräten gleich. **Das gehört
> aufgeschrieben**, denn ein später eingeführtes `applicationIdSuffix = ".debug"` würde
> genau denselben Dauerkonflikt für jede Route mit Foto erzeugen.

### Ablauf 32 — Nach dem Abgleich haben beide Geräte verschiedene Herkunft

E3 sagte bisher, `stand_meta` werde nach dem Abgleich neu berechnet — „neue `generation`,
**eigenes** `erzeugtVon`".

Das ist falsch, und zwar an der Wurzel: `stand_meta` beschreibt den **gemeinsamen** Stand. Setzt
jedes Gerät sein eigenes `erzeugtVon`, haben die beiden nach einem erfolgreichen Abgleich
**verschiedene Herkunft für denselben Stand** — und die Lagebestimmung beim nächsten Mal liest
daraus „auseinandergelaufen", wo Einigkeit herrscht.

> **Lösung (E3, korrigiert): `stand_meta` wird vom rechnenden Gerät bestimmt und von beiden
> unverändert übernommen.** `erzeugtVon` ist die ID desjenigen, der gerechnet hat — auf beiden
> Geräten dieselbe. Nach einem Abgleich sind die drei Werte auf beiden Seiten identisch; alles
> andere wäre ein Widerspruch in sich.

### Ablauf 33 — Die Erstbegegnung kopiert die fremde Identität mit

Bei der Erstbegegnung wird die **Datei ersetzt** (E10). In dieser Datei steckt `stand_meta` des
anderen Geräts — samt dessen `erzeugtVon` und der dort vermerkten Bandvergabe.

Ablauf 19 hatte die Metatabellen nur für den *zeilenweisen* Weg ausgenommen. Der Dateiweg war
nicht mitgedacht.

> **Lösung: nach dem Ersetzen wird `stand_meta` ausdrücklich neu geschrieben** — mit der
> gemeinsamen Herkunft aus E3 und dem **neu vergebenen** Band des empfangenden Geräts (E8).
> Ohne das übernimmt der Empfänger das Band des Senders und beide vergeben Nummern aus
> demselben Bereich; wir wären zurück bei Ablauf 7, ausgerechnet beim allerersten Abgleich.

### Ablauf 34 — Die alte `-wal` frisst die neue Datenbank

Bei der Erstbegegnung wird `boulderbuddy.db` durch die empfangene Datei ersetzt. Daneben liegen
noch `boulderbuddy.db-wal` und `-shm` des alten Standes.

> SQLite spielt beim nächsten Öffnen den vorhandenen WAL über die Datei — **also den alten Stand
> über den neuen.** Ergebnis: ein Mischmasch oder eine beschädigte Datenbank.
>
> Dieses Projekt ist über genau diese Falle schon einmal gestolpert, beim Einspielen von
> Testdaten (`TABLET_PLAN.md`: „plus `-wal`/`-shm`!"). Beim Nutzer wäre sie ungleich teurer.
>
> **Lösung: Room schließen, `-wal` und `-shm` löschen, dann die Datei ersetzen, dann neu
> starten.** In dieser Reihenfolge, als eine Routine, nicht verteilt über drei Stellen.

### Ablauf 35 — Der Änderungs-Schalter meldet die eigenen Schreibvorgänge

`geaendertSeitAbgleich` wird über Rooms `InvalidationTracker` gesetzt (E3). Der feuert bei
**jedem** Schreibzugriff — auch bei denen, die der Abgleich selbst macht. Und er feuert
asynchron: der Abgleich setzt den Schalter am Ende auf „nichts geändert", und kurz darauf
meldet der Tracker die gerade angewendeten Zeilen nach und setzt ihn wieder.

> Das Gerät hielte sich unmittelbar nach einem erfolgreichen Abgleich für verändert.
>
> **Lösung, zweiteilig.** Erstens: das Nachmelden während des Anwendens unterdrücken. Zweitens
> und wichtiger — **der Schalter ist ein Hinweis fürs UI, niemals eine Grundlage für
> Entscheidungen.** Was wirklich geändert wurde, sagt allein der Vergleich mit `basis.db`. Steht
> der Schalter falsch, ist die Anzeige ungenau; der Abgleich rechnet trotzdem richtig. Eine
> Optimierung darf nie zur Wahrheitsquelle werden.

### Ablauf 36 — Rückgängig nach der Erstbegegnung

`vorher.db` wird auch bei der Erstbegegnung geschrieben (E10/E13) — gerade dort, weil dort am
meisten verworfen wird. Macht der Nutzer sie rückgängig, gilt E13: nur die Datentabellen zurück,
Herkunft bleibt. Also gilt sein alter Stand als **neue Änderung** und würde beim nächsten
Abgleich mit dem Stand des anderen Geräts verschmolzen — zwei Datensätze, die nie
zusammengehörten, mit doppelten Hallen und doppelten Sessions.

> **Lösung: das Rückgängigmachen einer Erstbegegnung setzt die Kopplung mit zurück** — das Gerät
> gilt wieder als „noch nie abgeglichen", `basis.db` wird verworfen, das Band freigegeben. Der
> nächste Abgleich stellt dann wieder die Erstbegegnungs-Frage, und der Nutzer kann sich anders
> entscheiden. Genau das will er ja, wenn er hier rückgängig macht.

### Ablauf 37 — Die Medien des Verlierers bleiben liegen

Nach einer Erstbegegnung verweist der Stand nicht mehr auf die Videos des empfangenden Geräts.
Sie bleiben in `filesDir/aufnahmen` und werden von `vorher.db` noch gehalten (E11/E25).

> **Kein Fehler, aber es gehört gesagt:** solange das Rückgängigmachen möglich sein soll, kann
> der Platz nicht frei werden. „Speicherplatz freigeben" muss das benennen — *„Gibt 2,1 GB
> frei. Danach lässt sich der letzte Abgleich nicht mehr rückgängig machen."* Dann ist es eine
> Entscheidung und keine Überraschung.

---

## Entscheidungen

### E1 — Eine Aktion, die App bestimmt die Richtung
„Geräte abgleichen", auf beiden Geräten derselbe Knopf.

### E2 — Übertragen wird die Datei, angewendet werden Zeilen
Übertragen wird die SQLite-Datei nach **geprüftem** `wal_checkpoint(FULL)` (Ablauf 30);
angewendet werden Zeilenoperationen in einer Transaktion (Ablauf 10). Ausnahme: die
Erstbegegnung (E10).

### E3 — Herkunft: `generation`, `erzeugtVon`, `basiertAuf` — auf beiden Geräten gleich
Ein-Zeilen-Tabelle `stand_meta`, reist mit, wird aber **nie als Daten übernommen** (Ablauf 19)
und **nie je Gerät verschieden gesetzt** (Ablauf 32): sie beschreibt den gemeinsamen Stand und
wird vom rechnenden Gerät bestimmt, von beiden unverändert übernommen.

Lokal in DataStore: Geräte-ID, Nummernband (E8), `geaendertSeitAbgleich` — **letzterer nur als
Hinweis fürs UI, nie als Entscheidungsgrundlage** (Ablauf 35). Schema **v7**.

### E4 — Gedächtnis: der letzte gemeinsame Stand, als Baum verglichen

Nach jedem erfolgreichen Abgleich eine Kopie der DB als `basis.db` (115 KB). Verglichen wird
**von oben nach unten entlang der Fremdschlüssel** (Ablauf 8), über eine fest gelistete
Tabellenmenge (Ablauf 19), und **nur über geräteunabhängige Spalten** (Ablauf 31, E15):

| Fall | Ergebnis |
|---|---|
| Zeile nur auf einer Seite neu | übernehmen |
| Gleiche Nummer, gleicher Inhalt, der Basis unbekannt | **einig** — nichts tun (Ablauf 15) |
| Gegenüber der Basis auf einer Seite gelöscht, andere unberührt | löschen, überall |
| Dieselbe Zeile beidseitig geändert | **Konflikt** → E12 |
| Teilbaum gelöscht gegen Teilbaum geändert/ergänzt | **Konflikt** → E12 |
| Verwendeter Grad gelöscht (`SET_NULL`) | Route verliert den Grad; steht in der Bilanz |

**Warum das der Vorgabe nicht widerspricht:** Eine Löschung gilt weiter für alle Geräte — sie
wird sogar sicherer erkannt, weil sie sich von „hat es nie gehabt" unterscheiden lässt.
Änderungen bleiben absolut: bei echtem Konflikt gewinnt eine Seite ganz. Nach dem Abgleich haben
beide Geräte denselben Stand.

### E5 — Medien inhaltsadressiert
SHA-256 als Name, lokal `filesDir/aufnahmen/<sha256>.<endung>`. Beim Abgleich Hash-Listen
tauschen, nur Fehlendes übertragen. **Der Name ist der Hash** — nach S3 wird nie wieder gehasht,
nur neue Aufnahmen einmal beim Speichern.

### E6 — Der Stand sind die Daten, nicht die Geräte-Einstellungen
Gradsystem-Wahl, Timer-Konfiguration, Anzeigename reisen mit. Dark Mode und Haptik **nicht**.

### E7 — Schema-Version bricht ab, statt zu raten
Höhere Schema-Version auf der Gegenseite ⇒ ablehnen, mit dem Hinweis, welches Gerät zu
aktualisieren ist.

### E8 — Jedes Gerät hat sein eigenes Nummernband
Beim ersten Abgleich vergeben, in DataStore gemerkt, in `stand_meta` festgehalten. **Nach jedem
Abgleich `sqlite_sequence` je Tabelle auf das eigene Band zurücksetzen.** Beim Empfänger einer
Erstbegegnung wird das Band **nach** dem Dateiersatz neu geschrieben, nicht aus der Datei
übernommen (Ablauf 33).

### E9 — Ein Abgleich läuft nicht, wenn er nicht sauber laufen kann
Vor dem Verbindungsaufbau: laufende Session **auf einer der beiden Seiten** · zu wenig freier
Speicher (**doppelter Bedarf**, Ablauf 27) · höheres Schema der Gegenseite · Gedächtnis nach
Schemawechsel verloren. Während des Abgleichs: gescheiterter Checkpoint oder unplausible
Zeilenzahlen brechen ab (Ablauf 30).

### E10 — Die Erstbegegnung ist ein eigener Fall
Frage mit Zahlen statt Fachbegriffen. Hier wird die **Datei ersetzt** und der Prozess neu
gestartet. Die Routine dazu, in dieser Reihenfolge (Ablauf 34): `vorher.db` schreiben (E13) →
Room schließen → `-wal` und `-shm` löschen → Datei ersetzen → `stand_meta` neu schreiben (E3)
und Band vergeben (E8) → neu starten.

### E11 — Speicherplatz freigeben, ausdrücklich und getrennt
Löscht lokale Medien, die **weder der aktuelle Stand noch `basis.db` noch `vorher.db`** nennen
(Ablauf 25), sowie liegengebliebene Nearby-Payload-Dateien (Ablauf 27). Zeigt vorher, wie viel
es wäre — **und sagt, wenn dabei die Möglichkeit zum Rückgängigmachen verfällt** (Ablauf 37).

### E12 — Ein Gerät rechnet, ein Mensch entscheidet — und die Antwort gilt nur den Konflikten
**Wer den Knopf gedrückt hat, rechnet** und schickt der Gegenseite das *Ergebnis*, nicht die
Aufgabe (Ablauf 17). Bei gleichzeitigem Drücken gewinnt die kleinere Geräte-ID.

**Konflikte entscheidet der Nutzer, einmal pro Abgleich**, mit einer Liste des Betroffenen. **Die
Antwort gilt ausschließlich für die konflikthaften Einträge** (Ablauf 23) — sonst wäre das
Gedächtnis umsonst. Die Frage lautet *„Bei diesen 2 Einträgen: welches Gerät soll gewinnen?"*.

**Ausdrücklich nicht über Zeitstempel:** die Gerätezeit ist nicht verlässlich.

### E13 — Ein Schritt rückgängig, und zwar als neue Änderung
Vor jedem Anwenden eine Kopie des eigenen Standes als `vorher.db` (115 KB), auch bei der
Erstbegegnung.

**Zurückgesetzt werden nur die Datentabellen.** `basis.db`, `stand_meta` und die `generation`
bleiben, wie der Abgleich sie hinterlassen hat. Aus Sicht des Modells ist das Rückgängigmachen
**eine neue Änderung, keine Rückkehr** — nur so wandert sie beim nächsten Abgleich weiter
(Ablauf 24). **Ausnahme: nach einer Erstbegegnung wird die Kopplung mit zurückgesetzt**
(Ablauf 36) — das Gerät gilt wieder als „noch nie abgeglichen".

Das UI verspricht genau das, was die Funktion kann: **„Nimmt zurück, was der Abgleich auf diesem
Gerät geändert hat."**

### E14 — Geräte-Identität und Medien gehören nicht in die Android-Sicherung
`backup_rules.xml` und `data_extraction_rules.xml` (heute leere Vorlagen) schließen aus:
**Geräte-ID und Nummernband** aus DataStore (Ablauf 26) sowie **`filesDir/aufnahmen` und
`filesDir/ghost`** (Quota, und sie gehören in den Abgleich).

### E15 — In der Datenbank stehen nur geräteunabhängige Verweise
Keypoint-Pfade **relativ zu `filesDir`** statt absolut (Ablauf 31). Medien-URIs sind es bereits,
weil `applicationId` keinen Build-Typ-Suffix hat — **wer einen einführt, bricht den Abgleich**
und muss die URIs dann ebenfalls relativ ablegen.

Der Grundsatz dahinter, weil er über diese zwei Spalten hinausgeht: **eine Spalte, die auf zwei
Geräten verschieden aussehen kann, darf nicht in einer Zeile stehen, die verglichen wird.**

## Die Medien

| Sorte | Woher | Wo liegt die Datei | Übertragbar |
|---|---|---|---|
| `content://…fileprovider/…` | eigener Kamera-Screen | `filesDir/aufnahmen` — gehört der App | ja |
| `content://media/…` | Galerie-Picker | MediaStore **dieses** Geräts | **nein — toter Verweis** |

Entschieden: **mitkopieren.** Danach gehören alle Medien der App.

> **Nebenbefund, eigener Fehler, blockierend für S3.** `GhostClimberScreen:348` ruft, anders als
> `RouteHinzufuegenScreen:119`, **kein** `takePersistableUriPermission`. Der Umzug scheiterte
> ausgerechnet bei den Ghost-Videos an abgelaufenen Berechtigungen. **Vor S3 beheben.**

Die Ghost-Artefakte (`GhostArtifactStore`, JSON in `filesDir/ghost`) reisen mit. Dass zwei
Analysen desselben Videos denselben Dateinamen benutzen, ist gewollt und harmlos (Ablauf 29).
Dass beim Übernehmen Pfade umgeschrieben werden müssten, entfällt mit E15.

## Schritte

### S0 — Echte Room-Migrationen (Voraussetzung, nicht Teil)
`fallbackToDestructiveMigration` ersetzen. Ohne das löscht das nächste Schema-Update genau die
Daten, die dieser Plan übertragbar machen soll (Ablauf 9). Schema-JSONs v1–v6 liegen unter
`app/schemas/`. **Vorher nichts anderes bauen.**

### S1 — Schema v7: Herkunft, Nummernbänder, relative Pfade, Backup-Regeln
`stand_meta`; Geräte-ID und Band in DataStore; `sqlite_sequence`-Verwaltung (E8); **Keypoint-
Pfade von absolut auf relativ umstellen** (E15, mit Migration der Bestandszeilen); die
Backup-Ausschlüsse aus E14. Alles, was zur Identität gehört — und nichts davon gehört ans Ende.

### S2 — Der Vergleich, als reine Logik
Das Herzstück, vollständig ohne Android testbar:
- `darfIchLesen(fremdesSchema, meinesSchema)`
- `lage(meine, fremde)` → Erstbegegnung / gleich / eine Seite weiter / beide weiter
- `abgleich(basis, meine, fremde)` → je Teilbaum: neu, gelöscht, einig, konflikthaft (E4)
- `anwenden(ergebnis, konfliktEntscheidung)` — mit der Reichweite aus E12

Die abzugleichenden Tabellen **und Spalten** stehen als Liste im Code (E15). Zuerst und allein,
mit Tests: ein Fehler hier kostet Daten, überall sonst höchstens einen Transfer.

### S3 — Medien inhaltsadressiert (E5)
Einmaliger Umzug auf `<sha256>.<endung>`, Galerie-URIs in die App holen, URIs umschreiben.
**Muss deterministisch sein** (Ablauf 18). **Vorher den Fehler in `GhostClimberScreen` beheben.**

### S4 — Nearby-Verbindung
Berechtigungen (beide Zweige), feste Rollen über die Geräte-ID, Advertising/Discovery,
Bestätigung über die vierstellige Zahl, Abbruchbehandlung. Noch ohne Nutzdaten.

### S5 — Übertragung
Handschlag, Schema-Version, Hash-Listen und Abgleich-Ergebnis als BYTES; **Stand und Medien als
FILE-Payloads** (Ablauf 22). Foreground Service (`dataSync`), Fortschritt. Reihenfolge:
Vorprüfungen (E9) → Hash-Listen → fehlende Medien → **zuletzt** der Stand. **Empfangene
Payload-Dateien in jedem Fall aufräumen, auch beim Abbruch** (Ablauf 27).

### S6 — Anwenden
`vorher.db` schreiben (E13) → Ergebnis aus S2 in **einer Transaktion**, einschließlich der neuen
`stand_meta` (E3) → `sqlite_sequence` zurücksetzen (E8) → **danach** `basis.db` als Kopie des
Ergebnisses (Ablauf 15) → `refreshBoulderWidget` (Ablauf 28). Die Erstbegegnung läuft stattdessen
über die Routine in E10.

### S7 — UI
Der eine Knopf, Fortschritt, Bilanz in Zeilen (Zusammengeführtes, Löschungen, Konflikt-Verluste,
fehlende Videos), Erstbegegnungs-Dialog, Konfliktfrage mit Reichweite (E12), „Letzten Abgleich
rückgängig machen" mit ehrlichem Versprechen (E13), Vorprüfungs-Meldungen (E9), das Angebot auf
dem Tablet-Home. Sprache ohne Fachbegriffe (Ablauf 6).

### S8 — Zweitweg über die Datei
Export/Import derselben Stand-Datei über SAF — jetzt zusätzlich begründet, weil die Medien aus
der Android-Sicherung ausgeschlossen sind (E14).

### S9 — Absichern
- JVM-Tests für alles aus S2: gelöscht-gegen-nie-gehabt · beidseitig geändert · Erstbegegnung ·
  gleiche Nummer für verschiedene Zeilen (7) · Teilbaum gelöscht gegen ergänzt (8) · gleiche
  Nummer, gleicher Inhalt = einig (15) · Metatabellen unangetastet (19) · Konfliktantwort
  betrifft nur die Konflikte (23) · **gerätelokale Spalten sind nicht Teil des Vergleichs (31)**.
- Instrumented-Tests, jeweils über **zwei** Abgleiche, nicht einen:
  Ablauf 4 (beide ergänzen, beides bleibt) · 3 (Löschung propagiert) · 8 (Konfliktfrage) ·
  15 (Abbruch nach dem Anwenden, dann erneut) · 24 (rückgängig, dann erneut — die
  Wiederherstellung muss ankommen) · **31 (zwei Abgleiche mit einer Ghost-Analyse: der zweite
  darf keinen Konflikt melden)** · **32/33 (nach der Erstbegegnung: gleiche Herkunft, ungleiche
  Bänder)** · Migration v6→v7 samt `basis.db`.
  Achtung: `connectedDebugAndroidTest` deinstalliert die App und löscht die Daten
  (`TABLET_PLAN.md`).
- Am Gerät alle Abläufe durchspielen, mit echten Videos.

## Reihenfolge

**S0 zuerst und allein.** Dann S1 → S2 → S3, alles ohne Nearby prüfbar; S2 ist die einzige
Stelle, an der ein Fehler Daten kostet. Dann S4 allein, dann S5/S6, dann S7. S8 zum Schluss,
S9 begleitend.

## Was die fünf Runden gelehrt haben

Die Funde folgten einem Muster, das für das Bauen wichtiger ist als jede einzelne Entscheidung:

**Runde 2 hat die Architektur gedreht** (ID-Kollision, Fremdschlüssel-Baum). **Runde 3 hat eine
Lücke geschlossen**, die vier Fassungen lang niemandem auffiel, weil sie wie ein Detail aussah:
wer eigentlich entscheidet. **Runde 4 fand zwei Fehler, die erst beim zweiten Durchlauf
zuschlagen** — deshalb laufen die Instrumented-Tests jetzt über zwei Abgleiche. **Runde 5 fand
zwei, die auf zwei Emulatoren nie aufgefallen wären**, weil beide `/data/user/0/` benutzen und
dieselbe Herkunft harmlos aussieht, solange man nur einmal abgleicht.

Daraus die zwei Regeln fürs Bauen: **jeder Test läuft mindestens zweimal durch**, und **keine
Spalte, die auf zwei Geräten verschieden aussehen kann, darf in einen Vergleich geraten.**

## Offene Fragen

**O1 — Wie oft kommt der echte Konflikt vor?** Zwei Fälle bleiben: dieselbe Zeile beidseitig
geändert, Teilbaum gelöscht gegen geändert. Erwartung: selten genug für eine Frage pro Abgleich.
Falls nicht, wäre die Alternative eine Auswahl je Zeile — eine Bildschirmseite mehr, keine
andere Architektur.

**O2 — Automatisch abgleichen, wenn sich die Geräte sehen?** Würde Ablauf 4 entschärfen, setzt
aber Hintergrund-Advertising voraus (Akku). Erst nach S0–S9.

**O3 — Bleibt es bei zwei Geräten?** Bei drei wäre `basis.db` mehrdeutig (Basis gegenüber
*welchem* Gerät?) — es bräuchte eine Basis je Gegenstelle. Die Nummernbänder (E8) und die
gemeinsame Herkunft (E3) sind darauf vorbereitet.

**O4 — Braucht das Löschen selbst eine Sicherung?** Das Rückgängigmachen (E13) fängt nur den
Abgleich, nicht eine versehentliche eigene Löschung. Eine Rückfrage vor dem Löschen einer
Session mit Inhalt wäre die einfachere Antwort, gehört aber nicht in diesen Plan.

## Was bewusst nicht in diesem Plan steht

**Kein Backend, kein Konto.** Bei einem Nutzer wäre das ein Server, eine Registrierung, eine
Datenschutzerklärung und eine laufende Rechnung für ein Problem, das zwei Geräte im selben Raum
direkt lösen.

**Kein Cloud-Ordner als Hauptweg.** Dort ist immer nur eine Seite sichtbar. Als Zweitweg (S8)
bleibt die Datei.

**Keine Konfliktlösung über Zeitstempel.** Siehe E12.

**Kein Android-Auto-Backup als Ersatz.** Greift nur bei einer Neuinstallation, überträgt nicht
zwischen zwei genutzten Geräten — und trägt nach E14 die Medien gar nicht mehr.
