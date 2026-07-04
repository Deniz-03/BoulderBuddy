package com.boulderbuddy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.boulderbuddy.data.db.entity.HangboardSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HangboardSessionDao {
    @Insert
    suspend fun insert(session: HangboardSessionEntity): Long

    /** Hangboard-Durchläufe einer Session, neueste zuerst. */
    @Query("SELECT * FROM hangboard_session WHERE sessionId = :sessionId ORDER BY date DESC")
    fun observeBySession(sessionId: Int): Flow<List<HangboardSessionEntity>>

    /** Alle Hangboard-Durchläufe (sessionübergreifend) — Basis der Hangboard-Statistik. */
    @Query("SELECT * FROM hangboard_session ORDER BY date DESC")
    fun observeAll(): Flow<List<HangboardSessionEntity>>
}
