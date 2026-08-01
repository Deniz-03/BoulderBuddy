package com.boulderbuddy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.boulderbuddy.data.db.entity.HangboardSegmentEntity
import com.boulderbuddy.data.db.entity.HangboardWorkoutEntity
import com.boulderbuddy.data.db.entity.HangboardWorkoutWithSegments
import kotlinx.coroutines.flow.Flow

@Dao
interface HangboardWorkoutDao {

    @Insert
    suspend fun insertWorkout(workout: HangboardWorkoutEntity): Long

    @Insert
    suspend fun insertSegments(segments: List<HangboardSegmentEntity>)

    /** Legt Workout + Sätze atomar an; die Segmente bekommen die frische Workout-ID. */
    @Transaction
    suspend fun insertWithSegments(
        workout: HangboardWorkoutEntity,
        segments: List<HangboardSegmentEntity>,
    ): Long {
        val workoutId = insertWorkout(workout)
        insertSegments(segments.map { it.copy(workoutId = workoutId.toInt()) })
        return workoutId
    }

    /** Workouts einer Kletter-Session, neueste zuerst. */
    @Transaction
    @Query("SELECT * FROM hangboard_workout WHERE sessionId = :sessionId ORDER BY endedAt DESC")
    fun observeBySession(sessionId: Int): Flow<List<HangboardWorkoutWithSegments>>

    /** Alle Workouts (Phone+Uhr, manuell+auto, mit und ohne Session) — Historie & Statistik. */
    @Transaction
    @Query("SELECT * FROM hangboard_workout ORDER BY endedAt DESC")
    fun observeAll(): Flow<List<HangboardWorkoutWithSegments>>
}
