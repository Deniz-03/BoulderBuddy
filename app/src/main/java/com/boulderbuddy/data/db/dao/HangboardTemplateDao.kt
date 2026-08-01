package com.boulderbuddy.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.boulderbuddy.data.db.entity.HangboardTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HangboardTemplateDao {
    @Insert
    suspend fun insert(template: HangboardTemplateEntity): Long

    @Update
    suspend fun update(template: HangboardTemplateEntity)

    @Delete
    suspend fun delete(template: HangboardTemplateEntity)

    @Query("SELECT * FROM hangboard_template ORDER BY name")
    fun observeAll(): Flow<List<HangboardTemplateEntity>>
}
