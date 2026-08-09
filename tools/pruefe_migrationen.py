"""Faehrt die Migrationen aus Migrations.kt gegen ein echtes SQLite und vergleicht das
Ergebnis mit den exportierten Room-Schemas.

Aufruf aus dem Repo-Wurzelverzeichnis:  python tools/pruefe_migrationen.py

Ersetzt nicht MigrationTest.kt auf dem Geraet (nur der prueft Rooms eigene Validierung),
faengt aber Syntaxfehler, Schema-Abweichungen und kaputten Datenumzug ohne Emulator ab —
und ist damit die Absicherung, die man beim Schreiben einer Migration tatsaechlich laufen
laesst. Wer die Schema-Version erhoeht, ergaenzt unten die Datenpruefung fuer den neuen
Schritt; die Schema-Gleichheit findet die neue Version von selbst."""
import json
import os
import re
import sqlite3
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KT = os.path.join(REPO, "app", "src", "main", "java", "com", "boulderbuddy", "data", "db", "Migrations.kt")
SCHEMAS = os.path.join(REPO, "app", "schemas", "com.boulderbuddy.data.db.BoulderBuddyDatabase")
NEUESTE = max(int(f[:-5]) for f in os.listdir(SCHEMAS) if f.endswith(".json"))


def lies_migrationen(pfad):
    """{(von, nach): [sql, ...]} aus den execSQL-Aufrufen der Kotlin-Datei."""
    quelle = open(pfad, encoding="utf-8").read()
    grenzen = [(m.start(), int(m.group(1)), int(m.group(2)))
               for m in re.finditer(r"object : Migration\((\d+), (\d+)\)", quelle)]
    ergebnis = {}
    for i, (start, von, nach) in enumerate(grenzen):
        ende = grenzen[i + 1][0] if i + 1 < len(grenzen) else len(quelle)
        ergebnis[(von, nach)] = execsql_aufrufe(quelle[start:ende])
    return ergebnis


def execsql_aufrufe(block):
    """Sammelt je execSQL(...) die verketteten String-Literale. Klammern in Strings
    zaehlen nicht mit, sonst endet der Aufruf bei 'ON DELETE CASCADE )'."""
    aufrufe = []
    for m in re.finditer(r"db\.execSQL\(", block):
        i = m.end()
        tiefe = 1
        teile = []
        while tiefe > 0:
            c = block[i]
            if c == '"':
                j = i + 1
                lit = []
                while block[j] != '"':
                    if block[j] == "\\":
                        lit.append(block[j + 1])
                        j += 2
                    else:
                        lit.append(block[j])
                        j += 1
                teile.append("".join(lit))
                i = j + 1
                continue
            if c == "(":
                tiefe += 1
            elif c == ")":
                tiefe -= 1
            i += 1
        aufrufe.append("".join(teile))
    return aufrufe


def schema_sql(version):
    d = json.load(open(os.path.join(SCHEMAS, f"{version}.json"), encoding="utf-8"))
    anweisungen = []
    for t in d["database"]["entities"]:
        anweisungen.append(t["createSql"].replace("${TABLE_NAME}", t["tableName"]))
        for idx in t.get("indices", []):
            anweisungen.append(idx["createSql"].replace("${TABLE_NAME}", t["tableName"]))
    return anweisungen


def normalisiert(con):
    """Beschreibt das Schema so, wie Rooms TableInfo es vergleicht: ueber die Pragmas.

    Bewusst NICHT ueber den SQL-Text aus sqlite_master — der unterscheidet sich nach einer
    Migration immer (Spaltenreihenfolge, doppelte statt Backtick-Quotes nach RENAME), ohne
    dass Room das stoert. Ein Textvergleich wuerde hier Fehlalarm auf Fehlalarm melden und
    die echten Abweichungen verstecken."""
    schema = {}
    tabellen = [r[0] for r in con.execute(
        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
    )]
    for t in sorted(tabellen):
        spalten = {}
        for _, name, typ, notnull, default, pk in con.execute(f"PRAGMA table_info(`{t}`)"):
            # defaultValue absichtlich mitgefuehrt: eine DEFAULT-Klausel, die die Entity
            # nicht kennt, ist genau der Fehler, den dieser Vergleich finden soll.
            spalten[name] = (typ.upper(), bool(notnull), pk, default)
        fks = sorted(
            (r[2], r[3], r[4], r[5], r[6])  # Zieltabelle, von, nach, on_update, on_delete
            for r in con.execute(f"PRAGMA foreign_key_list(`{t}`)")
        )
        indizes = {}
        for _, name, unique, herkunft, _part in con.execute(f"PRAGMA index_list(`{t}`)"):
            if herkunft != "c":  # von UNIQUE/PK erzeugte Indizes vergleicht Room nicht
                continue
            indizes[name] = (
                bool(unique),
                tuple(r[2] for r in con.execute(f"PRAGMA index_info(`{name}`)")),
            )
        schema[t] = (spalten, tuple(fks), indizes)
    return schema


def neu(version):
    con = sqlite3.connect(":memory:")
    for sql in schema_sql(version):
        con.execute(sql)
    return con


def fahre(con, migrationen, von, bis):
    for v in range(von, bis):
        for sql in migrationen[(v, v + 1)]:
            con.execute(sql)


fehler = []
migrationen = lies_migrationen(KT)
print("gefundene Migrationen:", sorted(migrationen), "\n")

# 1. Schema-Gleichheit fuer jeden Zwischenschritt: v_n migriert == v_n frisch erzeugt.
for ziel in range(2, NEUESTE + 1):
    gewandert = neu(1)
    fahre(gewandert, migrationen, 1, ziel)
    frisch = neu(ziel)
    a, b = normalisiert(gewandert), normalisiert(frisch)
    if a == b:
        print(f"[ok] v1 -> v{ziel} ergibt exakt das exportierte Schema v{ziel}")
    else:
        fehler.append(f"Schema v{ziel} weicht ab")
        print(f"[FEHLER] v1 -> v{ziel}:")
        for tab in sorted(set(a) | set(b)):
            if a.get(tab) != b.get(tab):
                print(f"    Tabelle {tab}")
                print("      migriert:", a.get(tab))
                print("      erwartet:", b.get(tab))

# Auch die Zwischenstarts pruefen — ein Geraet kommt von irgendeiner Version, nicht nur v1.
for start in range(2, NEUESTE):
    gewandert = neu(start)
    fahre(gewandert, migrationen, start, NEUESTE)
    if normalisiert(gewandert) == normalisiert(neu(NEUESTE)):
        print(f"[ok] v{start} -> v{NEUESTE} ergibt exakt das exportierte Schema v{NEUESTE}")
    else:
        fehler.append(f"v{start} -> v{NEUESTE} weicht ab")
        print(f"[FEHLER] v{start} -> v{NEUESTE} weicht ab")

# 2. Daten ueberleben den ganzen Weg von v1 bis zur neuesten Version.
con = neu(1)
con.executescript("""
INSERT INTO gym VALUES (1, 'Halle Nord', 'Berlin');
INSERT INTO grade_system VALUES (1, 1, 'Farbsystem');
INSERT INTO grade VALUES (1, 1, '6b', 'red', 0);
INSERT INTO session VALUES (1, 1, 1000, 90, 'guter Tag', 2000);
INSERT INTO route VALUES (1, 1, 1, 3, 'TOP', NULL, 'zweiter Versuch');
INSERT INTO hangboard_template VALUES (1, '7-3 Max Hangs', 6, 7, 3, 180);
""")
fahre(con, migrationen, 1, NEUESTE)
zeile = con.execute("SELECT name, sektor, color, attempts, notes FROM route WHERE id=1").fetchone()
if zeile == ("", None, None, 3, "zweiter Versuch"):
    print(f"[ok] route ueberlebt v1 -> v{NEUESTE} mit Defaults fuer die neuen Spalten")
else:
    fehler.append("route-Daten")
    print("[FEHLER] route:", zeile)
zeile = con.execute("SELECT gymId, gradeSystemId, notes FROM session WHERE id=1").fetchone()
if zeile == (1, None, "guter Tag"):
    print(f"[ok] session ueberlebt v1 -> v{NEUESTE}")
else:
    fehler.append("session-Daten")
    print("[FEHLER] session:", zeile)
zeile = con.execute("SELECT label, sortOrder FROM grade WHERE id=1").fetchone()
if zeile == ("6b", 0):
    print("[ok] grade ueberlebt den Verlust der color-Spalte")
else:
    fehler.append("grade-Daten")
    print("[FEHLER] grade:", zeile)

# 3. v5 -> v6: aus einem Durchlauf werden Workout + Saetze.
con = neu(5)
con.executescript("""
INSERT INTO gym VALUES (1, 'Halle Nord', NULL);
INSERT INTO session VALUES (1, 1, NULL, 1000, NULL, NULL, NULL);
INSERT INTO hangboard_session VALUES (1, 1, 4, 6, 7, 3, 100000);
INSERT INTO hangboard_session VALUES (2, 1, 0, 6, 7, 3, 200000);
""")
fahre(con, migrationen, 5, 6)
w = con.execute(
    "SELECT id, sessionId, mode, origin, startedAt, endedAt, plannedSets, plannedHangSec, "
    "plannedRestSec FROM hangboard_workout ORDER BY id").fetchall()
erwartet_w = [
    (1, 1, "MANUAL", "PHONE", 100000 - 37000, 100000, 6, 7, 3),
    (2, 1, "MANUAL", "PHONE", 200000, 200000, 6, 7, 3),
]
if w == erwartet_w:
    print("[ok] hangboard_session wird zu hangboard_workout (inkl. abgebrochenem Durchlauf)")
else:
    fehler.append("workout-Umzug")
    print("[FEHLER] workout:", w, "erwartet", erwartet_w)
s = con.execute(
    "SELECT workoutId, setIndex, hangMs, restMs FROM hangboard_segment "
    "ORDER BY workoutId, setIndex").fetchall()
erwartet_s = [(1, 0, 7000, 3000), (1, 1, 7000, 3000), (1, 2, 7000, 3000), (1, 3, 7000, 0)]
if s == erwartet_s:
    print("[ok] Saetze abgeleitet, nach dem letzten Satz keine Pause")
else:
    fehler.append("segment-Umzug")
    print("[FEHLER] segmente:", s)

# 4. v6 -> v7: Keypoint-Pfade werden geraeteunabhaengig (Sync-Plan Ablauf 31 / E15).
con = neu(6)
con.executescript("""
INSERT INTO ghost_analysis VALUES (1,
  'content://com.boulderbuddy.fileprovider/aufnahmen/a.mp4',
  'content://com.boulderbuddy.fileprovider/aufnahmen/b.mp4',
  '/data/user/0/com.boulderbuddy/files/ghost/pose_aaa.json',
  '/data/user/0/com.boulderbuddy/files/ghost/pose_bbb.json',
  '[]', '[]', 'OVERLAY', 1000);
INSERT INTO ghost_analysis VALUES (2,
  'content://x/a.mp4', 'content://x/b.mp4',
  '/data/data/com.boulderbuddy/files/ghost/pose_ccc.json',
  'ghost/pose_ddd.json',
  '[]', '[]', 'OVERLAY', 2000);
""")
fahre(con, migrationen, 6, 7)
pfade = con.execute(
    "SELECT refKeypointsPath, cmpKeypointsPath FROM ghost_analysis ORDER BY id").fetchall()
erwartet_p = [
    ("ghost/pose_aaa.json", "ghost/pose_bbb.json"),
    # Zweite Zeile: anderer filesDir-Praefix, und ein bereits relativer Pfad bleibt, wie er ist.
    ("ghost/pose_ccc.json", "ghost/pose_ddd.json"),
]
if pfade == erwartet_p:
    print("[ok] Keypoint-Pfade sind nach v7 relativ — auf jedem Geraet derselbe Wert")
else:
    fehler.append("keypoint-Pfade")
    print("[FEHLER] Pfade:", pfade, "erwartet", erwartet_p)
if all(not p.startswith("/") for zeile in pfade for p in zeile):
    print("[ok] kein absoluter Pfad mehr in ghost_analysis")
else:
    fehler.append("absoluter Pfad geblieben")
    print("[FEHLER] es steht noch ein absoluter Pfad in ghost_analysis")

# stand_meta wird leer angelegt: eine fehlende Zeile heisst "noch nie abgeglichen".
anzahl = con.execute("SELECT COUNT(*) FROM stand_meta").fetchone()[0]
if anzahl == 0:
    print("[ok] stand_meta ist nach der Migration leer (= noch nie abgeglichen)")
else:
    fehler.append("stand_meta nicht leer")
    print("[FEHLER] stand_meta hat", anzahl, "Zeilen")

# 5. v7 -> v8: `gym` wird neu angelegt — Bestandshallen muessen das ueberleben, und die
# Sessions muessen weiter an derselben Halle haengen (die id ist ihr Fremdschluessel).
con = neu(7)
con.executescript("""
INSERT INTO gym VALUES (1, 'Boulderwelt', 'Musterstr. 1');
INSERT INTO gym VALUES (7, 'Kletterhalle Nord', NULL);
INSERT INTO session VALUES (1, 7, NULL, 5000, 90, NULL, NULL);
""")
fahre(con, migrationen, 7, 8)
hallen = con.execute(
    "SELECT id, name, location, latitude, longitude, geofenceRadiusMeters,"
    " proximityAlertsEnabled FROM gym ORDER BY id").fetchall()
erwartet_g = [
    (1, "Boulderwelt", "Musterstr. 1", None, None, 150, 1),
    # Die id bleibt erhalten — sonst zeigte die Session unten ins Leere.
    (7, "Kletterhalle Nord", None, None, None, 150, 1),
]
if hallen == erwartet_g:
    print("[ok] Hallen ueberleben v7 -> v8 ohne Koordinaten, mit 150 m und Alerts an")
else:
    fehler.append("gym v7->v8")
    print("[FEHLER] gym:", hallen, "erwartet", erwartet_g)

if con.execute("SELECT gymId FROM session WHERE id = 1").fetchone() == (7,):
    print("[ok] Session haengt nach dem Neuanlegen von gym noch an derselben Halle")
else:
    fehler.append("session.gymId nach v7->v8")
    print("[FEHLER] session.gymId zeigt nicht mehr auf Halle 7")

# gym_visit ist neu und startet leer: Besuche werden ab jetzt gesammelt, nicht rueckwirkend.
anzahl = con.execute("SELECT COUNT(*) FROM gym_visit").fetchone()[0]
if anzahl == 0:
    print("[ok] gym_visit wird leer angelegt")
else:
    fehler.append("gym_visit nicht leer")
    print("[FEHLER] gym_visit hat", anzahl, "Zeilen")

# 6. v8 -> v9: das Standard-Gradsystem kommt dazu, Bestandshallen haben keins.
con = neu(8)
con.executescript("""
INSERT INTO gym VALUES (1, 'Boulderwelt', NULL, NULL, NULL, 150, 1);
""")
fahre(con, migrationen, 8, 9)
zeile = con.execute("SELECT name, defaultGradeSystemId FROM gym WHERE id = 1").fetchone()
if zeile == ("Boulderwelt", None):
    print("[ok] Bestandshallen ueberstehen v8 -> v9 ohne Standard-Gradsystem")
else:
    print("[FEHLER] gym nach v8->v9:", zeile, "erwartet ('Boulderwelt', None)")
    fehler.append("gym v8->v9")

# Bewusst KEIN Fremdschluessel auf grade_system: der zeigte zurueck auf gym und machte aus dem
# Abgleichs-Baum einen Zyklus (siehe GymEntity). Hier wird geprueft, dass wirklich keiner steht —
# sonst faellt es erst beim Abgleich auf, und dort zur Laufzeit.
bezuege = con.execute("PRAGMA foreign_key_list(`gym`)").fetchall()
if not bezuege:
    print("[ok] gym hat weiterhin keinen Fremdschluessel (kein Zyklus mit grade_system)")
else:
    print("[FEHLER] gym hat Fremdschluessel:", bezuege)
    fehler.append("gym-Fremdschluessel")

# 7. v9 -> v10: Loeschen einer Halle darf keine Historie mitnehmen. Der eigentliche Test ist
# nicht die Migration, sondern das DELETE danach — genau dort schlug frueher CASCADE zu.
con = neu(9)
con.executescript("""
INSERT INTO gym VALUES (1, 'Boulderwelt', NULL, NULL, NULL, 150, 1, NULL);
INSERT INTO grade_system VALUES (10, 1, 'Hausfarben');
INSERT INTO grade VALUES (100, 10, 'gelb', 0);
INSERT INTO session VALUES (1, 1, 10, 5000, 90, NULL, 6000);
INSERT INTO route VALUES (1000, 1, 100, 'Ecke', NULL, 2, 'TOP', 'gelb', NULL, NULL);
""")
fahre(con, migrationen, 9, 10)

zeile = con.execute("SELECT gymId, gymName FROM session WHERE id = 1").fetchone()
if zeile == (1, "Boulderwelt"):
    print("[ok] Session bekommt bei v9 -> v10 den Hallennamen als Schnappschuss")
else:
    print("[FEHLER] session nach v9->v10:", zeile, "erwartet (1, 'Boulderwelt')")
    fehler.append("session v9->v10")

# Jetzt das, wofuer der ganze Umbau da war.
#
# `commit()` muss vor das PRAGMA: Pythons sqlite3 haelt nach den INSERTs oben eine
# Transaktion offen, und `PRAGMA foreign_keys` ist innerhalb einer Transaktion ein
# stillschweigender No-Op. Ohne das prueft das DELETE gar keine Fremdschluessel und der
# Test bestuende auch mit CASCADE — er wuerde also genau das nicht messen, worum es geht.
con.commit()
con.execute("PRAGMA foreign_keys=ON")
con.execute("DELETE FROM gym WHERE id = 1")

zeile = con.execute("SELECT gymId, gymName FROM session WHERE id = 1").fetchone()
if zeile == (None, "Boulderwelt"):
    print("[ok] geloeschte Halle: Session bleibt und weiss weiterhin, wo sie war")
else:
    print("[FEHLER] session nach DELETE gym:", zeile, "erwartet (None, 'Boulderwelt')")
    fehler.append("session ueberlebt Loeschen nicht")

if con.execute("SELECT COUNT(*) FROM route WHERE id = 1000").fetchone()[0] == 1:
    print("[ok] geloeschte Halle: der Boulder in der Session bleibt")
else:
    print("[FEHLER] route wurde mitgeloescht")
    fehler.append("route ueberlebt Loeschen nicht")

zeile = con.execute("SELECT gymId FROM grade_system WHERE id = 10").fetchone()
if zeile == (None,):
    print("[ok] geloeschte Halle: ihr Gradsystem bleibt und wird global")
else:
    print("[FEHLER] grade_system nach DELETE gym:", zeile, "erwartet (None,)")
    fehler.append("grade_system ueberlebt Loeschen nicht")

if con.execute("SELECT gradeId FROM route WHERE id = 1000").fetchone() == (100,):
    print("[ok] geloeschte Halle: der Boulder behaelt seine Schwierigkeit")
else:
    print("[FEHLER] route.gradeId ist nach dem Loeschen weg")
    fehler.append("route.gradeId verloren")

# 8. v10 -> v11: `istStandard` markiert die mitgelieferten Systeme. Vorher stand dafuer
# `gymId IS NULL` — und das traf nach dem Loeschen einer Halle auch deren eigenes System (v10
# setzt `gymId` auf NULL), das damit dauerhaft unloeschbar wurde. Geprueft wird deshalb beides:
# wen die Migration markiert, und was nach dem Loeschen der Halle davon uebrig bleibt.
con = neu(10)
con.executescript("""
INSERT INTO gym VALUES (1, 'Boulderwelt', NULL, NULL, NULL, 150, 1, NULL);
INSERT INTO grade_system VALUES (1, NULL, 'V-Scale');
INSERT INTO grade_system VALUES (2, NULL, 'Französisch');
INSERT INTO grade_system VALUES (3, 1, 'Hausfarben');
INSERT INTO grade_system VALUES (4, NULL, 'Meine Skala');
-- Gleicher Name wie ein Standard, aber an einer Halle: das ist ein eigenes System.
INSERT INTO grade_system VALUES (5, 1, 'V-Scale');
""")
fahre(con, migrationen, 10, 11)

systeme = con.execute("SELECT id, istStandard FROM grade_system ORDER BY id").fetchall()
erwartet_sys = [(1, 1), (2, 1), (3, 0), (4, 0), (5, 0)]
if systeme == erwartet_sys:
    print("[ok] v10 -> v11 markiert genau die mitgelieferten Systeme als Standard")
else:
    print("[FEHLER] grade_system nach v10->v11:", systeme, "erwartet", erwartet_sys)
    fehler.append("istStandard v10->v11")

# Der Fall, der den Umbau ausgeloest hat: Halle weg, System bleibt — und bleibt loeschbar.
# `commit()` muss vor das PRAGMA, sonst ist es ein stillschweigender No-Op (siehe oben) und
# `gymId` bliebe auf 1 stehen, weil SET_NULL gar nicht ausgeloest wuerde.
con.commit()
con.execute("PRAGMA foreign_keys=ON")
con.execute("DELETE FROM gym WHERE id = 1")

verwaist = con.execute(
    "SELECT id, gymId, istStandard FROM grade_system WHERE id IN (3, 5) ORDER BY id").fetchall()
if verwaist == [(3, None, 0), (5, None, 0)]:
    print("[ok] geloeschte Halle: ihre Systeme verlieren die Halle, gelten aber nicht als Standard")
else:
    print("[FEHLER] grade_system nach DELETE gym:", verwaist, "erwartet [(3, None, 0), (5, None, 0)]")
    fehler.append("istStandard nach Hallen-Loeschung")

# 9. Fremdschluessel sind nach der Migration konsistent.
con.execute("PRAGMA foreign_keys=ON")
verletzt = con.execute("PRAGMA foreign_key_check").fetchall()
if not verletzt:
    print("[ok] keine Fremdschluessel-Verletzung nach der Migration")
else:
    fehler.append("foreign_key_check")
    print("[FEHLER] foreign_key_check:", verletzt)

print()
if fehler:
    print("FEHLGESCHLAGEN:", ", ".join(fehler))
    sys.exit(1)
print("alle Pruefungen bestanden")
