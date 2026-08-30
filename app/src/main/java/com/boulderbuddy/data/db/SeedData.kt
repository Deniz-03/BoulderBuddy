package com.boulderbuddy.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Befüllt eine frisch erstellte DB mit dem, was die App zum Loslegen braucht — und mit
 * nichts darüber hinaus: die beiden Standard-Gradsysteme samt Graden und die Timer-Presets.
 *
 * **Bewusst ohne Halle, Session und Boulder.** Bis zur Abgabe legte dieses Seed eine Halle
 * („Boulder World München") und darin eine *laufende* Session mit drei Bouldern an. Die App
 * startete damit in einem Zustand, den niemand hergestellt hatte: eine Session, die man nicht
 * begonnen hat, fremde Tops auf dem Home-Screen, eine Halle in einer Stadt, in der man
 * vielleicht nie war. Was hier steht, muss für jeden Nutzer stimmen — auf erfundene Namen
 * trifft das nie zu, auf die V-Scale schon.
 *
 * Ohne Halle beginnt der Einstieg beim Session-Anlegen mit „Erste Halle anlegen"
 * (`SessionErstellenScreen`) — dieser Leerzustand ist vorgesehen und kein Sonderfall.
 *
 * Wird beim Aufbau der DB als [RoomDatabase.Callback] registriert (siehe `di/DatabaseModule`).
 * [onCreate] läuft nur einmal — beim allerersten Erstellen der Datei.
 *
 * **Die Falle dieser Datei sind die ausgeschriebenen INSERTs.** Sie umgehen Room und damit
 * jede Entity-Voreinstellung: eine neue `NOT NULL`-Spalte ohne SQL-Default lässt hier den
 * Start scheitern. Und zwar ausschließlich bei einer **Neuinstallation** — auf einem Gerät
 * mit vorhandener Datenbank läuft dieses Seed nie, der Fehler bleibt also beim Testen
 * unsichtbar. Wer eine Spalte ergänzt, ergänzt sie in den betroffenen INSERTs mit.
 */
object SeedData : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        seed(db)
    }

    /**
     * Nach einer destruktiven Migration sind alle Tabellen neu erstellt und LEER — Room ruft
     * dann [onDestructiveMigration] statt [onCreate], und ohne Re-Seed fehlten die
     * Standard-Gradsysteme.
     *
     * **Dieser Pfad ist derzeit tot.** Seit dem Geräte-Abgleich (Sync-Plan S0) baut
     * `DatabaseModule` ohne `fallbackToDestructiveMigration`; ein Versionssprung ohne
     * passende Migration bricht ab, statt zu löschen. Der Rückruf bleibt als Netz stehen,
     * falls je wieder ein destruktiver Fallback gesetzt wird — er kostet nichts und wäre im
     * Ernstfall genau das, was man vergisst.
     */
    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
        super.onDestructiveMigration(db)
        seed(db)
    }

    private fun seed(db: SupportSQLiteDatabase) {
        // Die Standard-Gradsysteme (gymId = NULL → hallenübergreifend wählbar).
        //
        // Die IDs beginnen bei 2, nicht bei 1: die 1 gehörte dem halleneigenen System
        // „Halle Nord" der Beispiel-Halle. Beide sind raus, die Lücke bleibt — `AUTOINCREMENT`
        // zählt ab dem Höchstwert weiter, und eine Umnummerierung wäre eine Änderung an IDs,
        // die der Geräte-Abgleich als Schlüssel führt.
        //
        // `istStandard = 1` schützt sie vor dem Löschen — der Marker hängt seit v11 nicht mehr
        // an `gymId`, weil das inzwischen auch „Halle wurde gelöscht" bedeuten kann.
        db.execSQL(
            "INSERT INTO grade_system (id, gymId, name, istStandard) " +
                "VALUES (2, NULL, 'V-Scale', 1)"
        )
        db.execSQL(
            "INSERT INTO grade_system (id, gymId, name, istStandard) " +
                "VALUES (3, NULL, 'Französisch', 1)"
        )

        // Durchlaufende Grad-IDs über beide Systeme.
        var nextGradeId = 1
        fun seedGrades(systemId: Int, labels: List<String>) {
            labels.forEachIndexed { order, label ->
                db.execSQL(
                    "INSERT INTO grade (id, systemId, label, sortOrder) " +
                        "VALUES ($nextGradeId, $systemId, '$label', $order)"
                )
                nextGradeId++
            }
        }
        // V-Scale: V0–V15. Französisch (Fontainebleau, Bouldern): 4 bis 8c (≈ V15).
        seedGrades(2, (0..15).map { "V$it" })
        seedGrades(
            3,
            listOf(
                "4", "5", "5+", "6a", "6a+", "6b", "6b+",
                "6c", "6c+", "7a", "7a+", "7b", "7b+", "7c", "7c+",
                "8a", "8a+", "8b", "8b+", "8c",
            ),
        )

        // Standard-Hangboard-Presets (7.3a). repRestSec wird vom Timer aktuell nicht genutzt = restSec.
        // (name, sets, hangSec, restSec, repRestSec)
        data class SeedPreset(
            val name: String,
            val sets: Int,
            val hangSec: Int,
            val restSec: Int,
        )
        val presets = listOf(
            SeedPreset("Repeater 7/3", 6, 7, 3),
            SeedPreset("Max Hangs 10/60", 5, 10, 60),
            SeedPreset("Warmup 5/10", 4, 5, 10),
        )
        presets.forEachIndexed { index, p ->
            db.execSQL(
                "INSERT INTO hangboard_template (id, name, sets, hangSec, restSec, repRestSec) " +
                    "VALUES (${index + 1}, '${p.name}', ${p.sets}, ${p.hangSec}, ${p.restSec}, ${p.restSec})"
            )
        }
    }
}
