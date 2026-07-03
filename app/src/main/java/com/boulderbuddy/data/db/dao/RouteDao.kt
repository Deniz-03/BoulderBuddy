package com.boulderbuddy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.boulderbuddy.data.db.entity.RouteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {
    @Insert
    suspend fun insert(route: RouteEntity): Long

    @Update
    suspend fun update(route: RouteEntity)

    @Query("SELECT * FROM route WHERE id = :routeId")
    suspend fun getById(routeId: Int): RouteEntity?

    /** Routen einer Session (für SessionDetail / AlteSession). */
    @Query("SELECT * FROM route WHERE sessionId = :sessionId ORDER BY id")
    fun observeBySession(sessionId: Int): Flow<List<RouteEntity>>

    /** Alle Routen (für die Boulder-Übersicht). */
    @Query("SELECT * FROM route ORDER BY id DESC")
    fun observeAll(): Flow<List<RouteEntity>>
}
