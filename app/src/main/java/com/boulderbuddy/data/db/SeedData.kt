package com.boulderbuddy.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Befüllt eine frisch erstellte DB mit Beispieldaten (ein Gym + Gradsystem + Grade +
 * eine aktive Session), damit die App nach dem ersten Start nicht leer wirkt.
 *
 * Wird beim Aufbau der DB (Phase 4, Hilt) als [RoomDatabase.Callback] registriert.
 * [onCreate] läuft nur einmal — beim allerersten Erstellen der Datei.
 */
object SeedData : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        seed(db)
    }

    /**
     * Nach einer destruktiven Migration (Versionssprung) sind alle Tabellen neu erstellt und
     * LEER — Room ruft dann [onDestructiveMigration] statt [onCreate]. Ohne dieses Re-Seed
     * fehlten sonst die Standard-Gradsysteme (V-Scale/Französisch) & Beispieldaten.
     */
    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
        super.onDestructiveMigration(db)
        seed(db)
    }

    private fun seed(db: SupportSQLiteDatabase) {
        // Gym.
        //
        // Die Spalten sind hier ausgeschrieben, auch die mit Entity-Default: `geofenceRadiusMeters`
        // und `proximityAlertsEnabled` sind `NOT NULL` und haben ihren Standardwert **nur in
        // Kotlin**, nicht im SQL-Schema (Begründung in MIGRATION_7_8). Ein `INSERT`, der sie
        // ausließ, scheiterte deshalb mit `NOT NULL constraint failed` — und zwar erst bei einer
        // Neuinstallation, wo dieses Seed als einziges läuft. Wer hier eine Spalte ergänzt,
        // ergänzt sie mit.
        db.execSQL(
            "INSERT INTO gym " +
                "(id, name, location, latitude, longitude, geofenceRadiusMeters, " +
                "proximityAlertsEnabled, defaultGradeSystemId) " +
                "VALUES (1, 'Boulder World München', 'München', NULL, NULL, 150, 1, 1)"
        )

        // Gradsystem des Gyms (V-Scale als Halle-Standard; Farbe hängt an der Route, nicht am Grad)
        db.execSQL(
            "INSERT INTO grade_system (id, gymId, name) VALUES (1, 1, 'Halle Nord')"
        )

        // Grade des Gradsystems (label, Sortierreihenfolge) — reine Schwierigkeit, keine Farbe.
        val grades = listOf("V0", "V1", "V2", "V3", "V4")
        grades.forEachIndexed { index, label ->
            db.execSQL(
                "INSERT INTO grade (id, systemId, label, sortOrder) " +
                    "VALUES (${index + 1}, 1, '$label', $index)"
            )
        }

        // Globale Standard-Gradsysteme (gymId = NULL → hallenübergreifend wählbar).
        db.execSQL("INSERT INTO grade_system (id, gymId, name) VALUES (2, NULL, 'V-Scale')")
        db.execSQL("INSERT INTO grade_system (id, gymId, name) VALUES (3, NULL, 'Französisch')")

        // Ab ID 6 weiter, da die Halle-Grade oben 1..5 belegen.
        var nextGradeId = grades.size + 1
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

        // Eine aktive Beispiel-Session (endedAt = NULL → läuft noch), mit dem Halle-Gradsystem.
        // `gymName` ist der Beleg, der eine gelöschte Halle überdauert (v10) — ebenfalls
        // NOT NULL und deshalb hier gesetzt.
        db.execSQL(
            "INSERT INTO session " +
                "(id, gymId, gymName, gradeSystemId, date, durationMin, notes, endedAt) " +
                "VALUES (1, 1, 'Boulder World München', 1, " +
                "${System.currentTimeMillis()}, NULL, NULL, NULL)"
        )

        // Ein paar Beispiel-Boulder in der aktiven Session, damit die App nach dem ersten
        // Start nicht leer wirkt. status wird als Enum-Name gespeichert (siehe Converters).
        // (name, sektor, gradeId, attempts, status, colorKey)
        data class SeedRoute(
            val name: String,
            val sektor: String,
            val gradeId: Int,
            val attempts: Int,
            val status: String,
            val color: String,
        )
        val routes = listOf(
            SeedRoute("Dachrinne", "A", 4, 1, "SENT", "red"),
            SeedRoute("Slab Talk", "A", 3, 3, "PROJECT", "blue"),
            SeedRoute("Warmup", "D", 2, 1, "SENT", "green"),
        )
        routes.forEachIndexed { index, r ->
            db.execSQL(
                "INSERT INTO route (id, sessionId, gradeId, name, sektor, attempts, status, color, mediaUri, notes) " +
                    "VALUES (${index + 1}, 1, ${r.gradeId}, '${r.name}', '${r.sektor}', " +
                    "${r.attempts}, '${r.status}', '${r.color}', NULL, NULL)"
            )
        }

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
