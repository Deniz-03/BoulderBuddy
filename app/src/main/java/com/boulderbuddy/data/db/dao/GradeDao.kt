package com.boulderbuddy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.boulderbuddy.data.db.entity.GradeEntity
import kotlinx.coroutines.flow.Flow

/**
 * Zugriff auf die einzelnen Grade eines Gradsystems.
 *
 * Sortiert wird durchgängig nach `sortOrder` — die Reihenfolge ist die Schwierigkeit und
 * darf nicht alphabetisch entstehen ("V10" käme sonst vor "V2").
 */
@Dao
interface GradeDao {
    @Insert
    suspend fun insert(grade: GradeEntity): Long

    @Insert
    suspend fun insertAll(grades: List<GradeEntity>)

    @Update
    suspend fun update(grade: GradeEntity)

    @Query("SELECT * FROM grade WHERE systemId = :systemId ORDER BY sortOrder")
    fun observeBySystem(systemId: Int): Flow<List<GradeEntity>>

    /** Alle Grade (hallenübergreifend) — zum Auflösen von `Route.gradeId` in den ViewModels. */
    @Query("SELECT * FROM grade")
    fun observeAll(): Flow<List<GradeEntity>>

    @Query("SELECT * FROM grade WHERE id = :gradeId")
    suspend fun getById(gradeId: Int): GradeEntity?
}
