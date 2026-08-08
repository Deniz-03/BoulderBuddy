package com.boulderbuddy.data.repository

import com.boulderbuddy.data.db.dao.HangboardWorkoutDao
import com.boulderbuddy.data.db.entity.HangboardSegmentEntity
import com.boulderbuddy.data.db.entity.HangboardWorkoutEntity
import com.boulderbuddy.data.db.entity.HangboardWorkoutWithSegments
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Kapselt den Zugriff auf das vereinte Hangboard-Workout-Modell (v6): Workouts mit ihren
 * Sätzen, egal ob Phone/Uhr oder manuell/auto.
 *
 * [observeBySession] speist den "Hangboard-Training"-Block der Session-Detailansicht,
 * [observeAll] Historie und Statistik (inkl. eigenständiger Workouts ohne Session).
 */
interface HangboardWorkoutRepository {
    /** Workouts einer Kletter-Session, neueste zuerst. */
    fun observeBySession(sessionId: Int): Flow<List<HangboardWorkoutWithSegments>>

    /** Alle Workouts (auch eigenständige), neueste zuerst. */
    fun observeAll(): Flow<List<HangboardWorkoutWithSegments>>

    /** Legt ein abgeschlossenes Workout samt Sätzen an und gibt seine neue ID zurück. */
    suspend fun create(
        workout: HangboardWorkoutEntity,
        segments: List<HangboardSegmentEntity>,
    ): Int
}

class HangboardWorkoutRepositoryImpl @Inject constructor(
    private val hangboardWorkoutDao: HangboardWorkoutDao,
) : HangboardWorkoutRepository {

    override fun observeBySession(sessionId: Int): Flow<List<HangboardWorkoutWithSegments>> =
        hangboardWorkoutDao.observeBySession(sessionId)

    override fun observeAll(): Flow<List<HangboardWorkoutWithSegments>> =
        hangboardWorkoutDao.observeAll()

    override suspend fun create(
        workout: HangboardWorkoutEntity,
        segments: List<HangboardSegmentEntity>,
    ): Int = hangboardWorkoutDao.insertWithSegments(workout, segments).toInt()
}
