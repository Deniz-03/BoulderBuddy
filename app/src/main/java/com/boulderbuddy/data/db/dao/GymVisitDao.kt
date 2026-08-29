package com.boulderbuddy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.boulderbuddy.data.db.entity.GymVisitEntity
import kotlinx.coroutines.flow.Flow

/**
 * Zugriff auf das Besuchs-Log der Hallen (Gym-Näherungs-Push, M3).
 *
 * Reines Anlegen und Lesen — Besuche werden nie geändert und nie einzeln gelöscht; sie
 * verschwinden nur mit ihrer Halle (CASCADE). Die Regel „höchstens ein Besuch je Halle und
 * Tag" setzt das `GymVisitRepository` durch, mit [countForGymBetween] als Prüfung.
 */
@Dao
interface GymVisitDao {
    @Insert
    suspend fun insert(visit: GymVisitEntity): Long

    @Query("SELECT * FROM gym_visit WHERE gymId = :gymId ORDER BY timestamp")
    suspend fun getAllForGym(gymId: Int): List<GymVisitEntity>

    @Query("SELECT * FROM gym_visit WHERE gymId = :gymId ORDER BY timestamp")
    fun observeForGym(gymId: Int): Flow<List<GymVisitEntity>>

    /** Besuche eines Gyms im Zeitfenster [from, until) — Basis für den Tages-Dedupe. */
    @Query(
        "SELECT COUNT(*) FROM gym_visit " +
            "WHERE gymId = :gymId AND timestamp >= :from AND timestamp < :until"
    )
    suspend fun countForGymBetween(gymId: Int, from: Long, until: Long): Int
}
