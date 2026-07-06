package com.boulderbuddy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.boulderbuddy.data.db.entity.GhostAnalysisEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GhostAnalysisDao {
    @Insert
    suspend fun insert(analysis: GhostAnalysisEntity): Long

    /** Alle gespeicherten Analysen, neueste zuerst (Liste im Ghost-Climber-Einstieg). */
    @Query("SELECT * FROM ghost_analysis ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GhostAnalysisEntity>>

    @Query("SELECT * FROM ghost_analysis WHERE id = :id")
    suspend fun getById(id: Int): GhostAnalysisEntity?

    @Query("DELETE FROM ghost_analysis WHERE id = :id")
    suspend fun deleteById(id: Int)
}
