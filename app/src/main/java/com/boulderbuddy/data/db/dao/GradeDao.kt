package com.boulderbuddy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.boulderbuddy.data.db.entity.GradeEntity
import kotlinx.coroutines.flow.Flow

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
}
