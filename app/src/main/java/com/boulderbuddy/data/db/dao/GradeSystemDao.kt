package com.boulderbuddy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.boulderbuddy.data.db.entity.GradeSystemEntity
import kotlinx.coroutines.flow.Flow

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
}
