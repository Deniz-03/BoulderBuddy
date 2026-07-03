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

        // Gym
        db.execSQL(
            "INSERT INTO gym (id, name, location) VALUES (1, 'Boulder World München', 'München')"
        )

        // Gradsystem des Gyms
        db.execSQL(
            "INSERT INTO grade_system (id, gymId, name) VALUES (1, 1, 'Farbsystem Halle Nord')"
        )

        // Grade des Gradsystems (label, Hex-Farbe, Sortierreihenfolge)
        val grades = listOf(
            Triple("Gelb", "#F4C20D", 0),
            Triple("Grün", "#2E9E52", 1),
            Triple("Blau", "#2F6FE0", 2),
            Triple("Rot", "#D64541", 3),
            Triple("Lila", "#8E44AD", 4),
        )
        grades.forEachIndexed { index, (label, color, order) ->
            db.execSQL(
                "INSERT INTO grade (id, systemId, label, color, sortOrder) " +
                    "VALUES (${index + 1}, 1, '$label', '$color', $order)"
            )
        }

        // Eine aktive Beispiel-Session (endedAt = NULL → läuft noch)
        db.execSQL(
            "INSERT INTO session (id, gymId, date, durationMin, notes, endedAt) " +
                "VALUES (1, 1, ${System.currentTimeMillis()}, NULL, NULL, NULL)"
        )

        // Ein paar Beispiel-Boulder in der aktiven Session, damit die App nach dem ersten
        // Start nicht leer wirkt. status wird als Enum-Name gespeichert (siehe Converters).
        // (routeId, gradeId, name, sektor, attempts, status)
        val routes = listOf(
            Triple("Dachrinne", "A", Triple(4, 1, "SENT")),
            Triple("Slab Talk", "A", Triple(3, 3, "PROJECT")),
            Triple("Warmup", "D", Triple(2, 1, "SENT")),
        )
        routes.forEachIndexed { index, (name, sektor, meta) ->
            val (gradeId, attempts, status) = meta
            db.execSQL(
                "INSERT INTO route (id, sessionId, gradeId, name, sektor, attempts, status, mediaUri, notes) " +
                    "VALUES (${index + 1}, 1, $gradeId, '$name', '$sektor', $attempts, '$status', NULL, NULL)"
            )
        }
    }
}
