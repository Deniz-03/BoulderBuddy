package com.boulderbuddy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.boulderbuddy.data.db.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Zugriff auf die Kletter-Sessions.
 *
 * Der Aktiv-Marker ist `endedAt IS NULL` — es gibt kein eigenes Statusfeld. Daran hängt mehr
 * als die Anzeige: Widget, Näherungs-Push und der Geräte-Abgleich fragen darüber, ob gerade
 * eine Session läuft.
 */
@Dao
interface SessionDao {
    /** Legt eine Session an; gibt die neue Row-ID zurück. */
    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    /** Beendet eine Session, indem [SessionEntity.endedAt] gesetzt wird. */
    @Query("UPDATE session SET endedAt = :endedAt WHERE id = :sessionId")
    suspend fun endSession(sessionId: Int, endedAt: Long)

    /**
     * Schreibt nur die Notiz — eine einzelne Anweisung statt Laden, Kopieren, Zurückschreiben.
     * Die Notiz wird beim Tippen gespeichert (siehe `AlteSessionScreen`), und dafür soll ein
     * Tastendruck nicht drei Datenbankzugriffe kosten.
     */
    @Query("UPDATE session SET notes = :notes WHERE id = :sessionId")
    suspend fun updateNotes(sessionId: Int, notes: String?)

    @Query("SELECT * FROM session WHERE id = :sessionId")
    suspend fun getById(sessionId: Int): SessionEntity?

    @Query("SELECT * FROM session ORDER BY date DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    /** Die aktive Session (Aktiv-Marker `endedAt IS NULL`); `null`, wenn keine läuft. */
    @Query("SELECT * FROM session WHERE endedAt IS NULL ORDER BY date DESC LIMIT 1")
    fun observeActive(): Flow<SessionEntity?>
}
