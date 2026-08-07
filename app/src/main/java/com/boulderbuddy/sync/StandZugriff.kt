package com.boulderbuddy.sync

import android.content.ContentValues
import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.boulderbuddy.data.db.entity.StandMetaEntity

/**
 * Zwischen SQLite und dem reinen Standmodell aus [Stand] (Sync-Plan S6).
 *
 * Diese Datei ist die einzige Stelle, an der die Vergleichslogik auf eine Datenbank trifft.
 * Alles Entscheidende passiert in [abgleich] und [anwenden] — hier wird nur gelesen und
 * geschrieben, damit ein Fehler an dieser Stelle bestenfalls einen Transfer kostet.
 */

/**
 * Wie eine Abfrage ausgeführt wird.
 *
 * Bewusst eine Funktion statt eines Datenbank-Typs: gelesen wird sowohl aus der eigenen
 * Room-Datenbank als auch aus einer **fremden Datei**, die gerade übertragen wurde. Letztere
 * darf auf keinen Fall über Room geöffnet werden — Room würde ihre Schema-Version prüfen und
 * gegebenenfalls migrieren, also die Datei verändern, die man nur ansehen wollte.
 */
typealias Abfrage = (String) -> Cursor

/**
 * Liest einen vollständigen Stand.
 *
 * Gelesen werden **nur die Spalten aus [STAND_TABELLEN]**. Das ist keine Sparsamkeit,
 * sondern die Umsetzung von E15: was hier nicht ankommt, kann auch nicht versehentlich in
 * einen Vergleich geraten.
 */
fun liesStand(abfrage: Abfrage): Stand {
    val tabellen = STAND_TABELLEN.associate { tabelle ->
        val spalten = listOf("id") + tabelle.spalten
        val zeilen = HashMap<Int, Zeile>()
        abfrage("SELECT ${spalten.joinToString(", ") { "`$it`" }} FROM `${tabelle.name}`")
            .use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getInt(0)
                    zeilen[id] = tabelle.spalten
                        .withIndex()
                        .associate { (i, name) -> name to feldAus(cursor, i + 1) }
                }
            }
        tabelle.name to (zeilen as Map<Int, Zeile>)
    }
    return Stand(tabellen)
}

fun liesStand(db: SupportSQLiteDatabase): Stand = liesStand { db.query(it) }

private fun feldAus(cursor: Cursor, spalte: Int): Feld = when (cursor.getType(spalte)) {
    Cursor.FIELD_TYPE_NULL -> Feld.Leer
    Cursor.FIELD_TYPE_INTEGER -> Feld.Zahl(cursor.getLong(spalte))
    Cursor.FIELD_TYPE_FLOAT -> Feld.Komma(cursor.getDouble(spalte))
    else -> Feld.Text(cursor.getString(spalte))
}

private fun Zeile.alsContentValues(id: Int): ContentValues {
    val werte = ContentValues()
    werte.put("id", id)
    for ((name, feld) in this) {
        when (feld) {
            is Feld.Leer -> werte.putNull(name)
            is Feld.Text -> werte.put(name, feld.wert)
            is Feld.Zahl -> werte.put(name, feld.wert)
            is Feld.Komma -> werte.put(name, feld.wert)
        }
    }
    return werte
}

/**
 * Wendet die Anweisungen an — **in genau einer Transaktion**, zusammen mit der neuen
 * Herkunft und der Sequenz-Rückstellung.
 *
 * Alles in einer Transaktion zu halten ist der Grund, warum der Alltags-Abgleich ohne
 * Prozess-Neustart auskommt (Ablauf 10): Die Uhr darf mitten hinein schreiben, ohne dass ein
 * halb angewendeter Stand sichtbar wird. Nur die Erstbegegnung ersetzt die Datei und braucht
 * den Neustart (E10).
 *
 * Die Reihenfolge in [Anweisungen] ist bereits richtig (Eltern vor Kindern beim Schreiben,
 * Kinder vor Eltern beim Löschen) — hier wird sie nur befolgt, nicht neu erfunden.
 *
 * @param band eigenes Nummernband; bestimmt, worauf `sqlite_sequence` zurückgesetzt wird (E8).
 */
fun wendeAn(
    db: SupportSQLiteDatabase,
    operationen: List<Operation>,
    neueMeta: StandMetaEntity,
    band: Int,
) {
    db.beginTransaction()
    try {
        for (op in operationen) {
            when (op) {
                is Operation.Einfuegen ->
                    db.insert(op.tabelle, CONFLICT_REPLACE, op.zeile.alsContentValues(op.id))

                is Operation.Aendern ->
                    db.update(
                        op.tabelle,
                        CONFLICT_REPLACE,
                        op.zeile.alsContentValues(op.id),
                        "id = ?",
                        arrayOf(op.id),
                    )

                is Operation.Loeschen ->
                    db.delete(op.tabelle, "id = ?", arrayOf(op.id))
            }
        }

        schreibeStandMeta(db, neueMeta)
        setzeSequenzenZurueck(db, band)

        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}

/** Ersetzt die eine Zeile in `stand_meta` — nie ergänzen, es gibt genau einen Stand (E3). */
fun schreibeStandMeta(db: SupportSQLiteDatabase, meta: StandMetaEntity) {
    val werte = ContentValues().apply {
        put("id", StandMetaEntity.EINZIGE_ZEILE)
        put("generation", meta.generation)
        put("erzeugtVon", meta.erzeugtVon)
        if (meta.basiertAuf == null) putNull("basiertAuf") else put("basiertAuf", meta.basiertAuf)
    }
    db.insert("stand_meta", CONFLICT_REPLACE, werte)
}

fun liesStandMeta(abfrage: Abfrage): StandMeta? =
    abfrage("SELECT generation, erzeugtVon, basiertAuf FROM stand_meta LIMIT 1").use { c ->
        if (!c.moveToFirst()) return null
        StandMeta(
            generation = c.getLong(0),
            erzeugtVon = c.getString(1),
            basiertAuf = if (c.isNull(2)) null else c.getLong(2),
        )
    }

fun liesStandMeta(db: SupportSQLiteDatabase): StandMeta? = liesStandMeta { db.query(it) }

/**
 * Schema-Version einer Datei, ohne sie zu öffnen wie eine eigene Datenbank.
 *
 * Room legt seine Version in `PRAGMA user_version` ab. Das ist die einzige Auskunft, die man
 * einer fremden Standdatei entlocken darf, bevor feststeht, ob man sie überhaupt lesen
 * kann (E7).
 */
fun liesSchemaVersion(abfrage: Abfrage): Int =
    abfrage("PRAGMA user_version").use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

/** Wie viele Zeilen je Tabelle — für die Erstbegegnungs-Frage mit Zahlen (E10). */
fun zaehleZeilen(abfrage: Abfrage, tabelle: String): Int =
    abfrage("SELECT COUNT(*) FROM `$tabelle`").use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

/**
 * Stellt `sqlite_sequence` je Tabelle auf das eigene Band zurück (E8).
 *
 * Ohne das zählte dieses Gerät nach einem Abgleich im **fremden** Band weiter — die
 * übernommenen Zeilen haben ja hohe Nummern, und `AUTOINCREMENT` merkt sich die höchste je
 * vergebene. Der nächste Abgleich fände dann zwei verschiedene Zeilen unter derselben
 * Nummer (Ablauf 7).
 */
fun setzeSequenzenZurueck(db: SupportSQLiteDatabase, band: Int) {
    for (tabelle in STAND_TABELLEN) {
        val ids = mutableListOf<Int>()
        db.query("SELECT id FROM `${tabelle.name}`").use { c ->
            while (c.moveToNext()) ids += c.getInt(0)
        }
        val seq = sequenzFuerBand(band, ids)

        // sqlite_sequence hat erst einen Eintrag, wenn die Tabelle einmal beschrieben wurde.
        val vorhanden = db.query(
            "SELECT COUNT(*) FROM sqlite_sequence WHERE name = ?",
            arrayOf(tabelle.name),
        ).use { c -> c.moveToFirst() && c.getInt(0) > 0 }

        if (vorhanden) {
            db.execSQL(
                "UPDATE sqlite_sequence SET seq = ? WHERE name = ?",
                arrayOf(seq, tabelle.name),
            )
        } else {
            db.execSQL(
                "INSERT INTO sqlite_sequence (name, seq) VALUES (?, ?)",
                arrayOf(tabelle.name, seq),
            )
        }
    }
}

/**
 * Setzt **nur die Datentabellen** auf einen früheren Stand zurück (E13, Ablauf 24).
 *
 * `stand_meta` und die `generation` bleiben, wie der Abgleich sie hinterlassen hat. Aus
 * Sicht des Modells ist das Rückgängigmachen **eine neue Änderung, keine Rückkehr** — nur so
 * wandert sie beim nächsten Abgleich zum anderen Gerät, statt dort rückgängig gemacht zu
 * werden.
 *
 * @param vorher der Stand aus `vorher.db`
 */
fun setzeDatentabellenZurueck(db: SupportSQLiteDatabase, vorher: Stand, band: Int) {
    db.beginTransaction()
    try {
        // Von unten nach oben leeren, von oben nach unten füllen — sonst hängt ein Kind
        // ohne Elternzeile in der Luft.
        for (tabelle in STAND_TABELLEN.reversed()) {
            db.delete(tabelle.name, null, null)
        }
        for (tabelle in STAND_TABELLEN) {
            for ((id, zeile) in vorher.zeilen(tabelle.name)) {
                db.insert(tabelle.name, CONFLICT_REPLACE, zeile.alsContentValues(id))
            }
        }
        setzeSequenzenZurueck(db, band)
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}

/** `SQLiteDatabase.CONFLICT_REPLACE`, ohne die Framework-Klasse zu importieren. */
private const val CONFLICT_REPLACE = 5
