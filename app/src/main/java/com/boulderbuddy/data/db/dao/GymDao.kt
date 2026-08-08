package com.boulderbuddy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.boulderbuddy.data.db.entity.GymEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GymDao {
    @Insert
    suspend fun insert(gym: GymEntity): Long

    @Update
    suspend fun update(gym: GymEntity)

    /**
     * Löscht die Halle. Was daran hängt, regeln die Fremdschlüssel: Sessions und
     * hallenspezifische Gradsysteme werden auf `NULL` gesetzt (bleiben also), das Besuchs-Log
     * verschwindet mit (CASCADE) — es beschreibt nur diese eine Halle.
     */
    @Query("DELETE FROM gym WHERE id = :gymId")
    suspend fun deleteById(gymId: Int)

    /** Wie viele Sessions hängen an dieser Halle — für die Rückfrage vor dem Löschen. */
    @Query("SELECT COUNT(*) FROM session WHERE gymId = :gymId")
    suspend fun countSessions(gymId: Int): Int

    @Query("SELECT * FROM gym WHERE id = :gymId")
    suspend fun getById(gymId: Int): GymEntity?

    @Query("SELECT * FROM gym ORDER BY name")
    fun observeAll(): Flow<List<GymEntity>>
}
