package com.boulderbuddy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.boulderbuddy.data.db.entity.GradeSystemEntity
import kotlinx.coroutines.flow.Flow

/**
 * Zugriff auf die Gradsysteme (V-Scale, Französisch, Hausfarben einer Halle, eigene).
 *
 * Ob ein System geschützt ist, steht in `istStandard` und nicht daran, ob es eine Halle hat
 * — dieses DAO kennt den Unterschied nicht und löscht, was ihm genannt wird. Die Prüfung
 * sitzt im `GradeRepository`.
 */
@Dao
interface GradeSystemDao {
    @Insert
    suspend fun insert(system: GradeSystemEntity): Long

    @Update
    suspend fun update(system: GradeSystemEntity)

    @Query("SELECT * FROM grade_system WHERE gymId = :gymId ORDER BY name")
    fun observeByGym(gymId: Int): Flow<List<GradeSystemEntity>>

    @Query("SELECT * FROM grade_system ORDER BY name")
    fun observeAll(): Flow<List<GradeSystemEntity>>

    /**
     * Löscht ein Gradsystem. Seine Grade verschwinden mit (FK CASCADE); Routen/Sessions, die
     * darauf verwiesen, werden auf `null` gesetzt (FK SET_NULL) — nichts geht verloren außer der
     * Grad-Zuordnung.
     */
    @Query("DELETE FROM grade_system WHERE id = :systemId")
    suspend fun deleteById(systemId: Int)
}
