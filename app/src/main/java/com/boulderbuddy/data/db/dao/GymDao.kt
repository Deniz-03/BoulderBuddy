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

    @Query("SELECT * FROM gym WHERE id = :gymId")
    suspend fun getById(gymId: Int): GymEntity?

    @Query("SELECT * FROM gym ORDER BY name")
    fun observeAll(): Flow<List<GymEntity>>
}
