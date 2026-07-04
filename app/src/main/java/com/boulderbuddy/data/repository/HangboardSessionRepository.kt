package com.boulderbuddy.data.repository

import com.boulderbuddy.data.db.dao.HangboardSessionDao
import com.boulderbuddy.data.db.entity.HangboardSessionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Kapselt den Zugriff auf [HangboardSessionEntity]-Daten (getrackte Timer-Durchläufe).
 *
 * [observeBySession] speist den "Hangboard-Training"-Block der aktiven Session-Detailansicht.
 */
interface HangboardSessionRepository {
    /** Hangboard-Durchläufe einer Kletter-Session, neueste zuerst. */
    fun observeBySession(sessionId: Int): Flow<List<HangboardSessionEntity>>

    /** Alle Hangboard-Durchläufe (sessionübergreifend) — Basis der Hangboard-Statistik. */
    fun observeAll(): Flow<List<HangboardSessionEntity>>

    /** Legt einen abgeschlossenen Durchlauf an und gibt seine neue ID zurück. */
    suspend fun create(session: HangboardSessionEntity): Int
}

class HangboardSessionRepositoryImpl @Inject constructor(
    private val hangboardSessionDao: HangboardSessionDao,
) : HangboardSessionRepository {

    override fun observeBySession(sessionId: Int): Flow<List<HangboardSessionEntity>> =
        hangboardSessionDao.observeBySession(sessionId)

    override fun observeAll(): Flow<List<HangboardSessionEntity>> =
        hangboardSessionDao.observeAll()

    override suspend fun create(session: HangboardSessionEntity): Int =
        hangboardSessionDao.insert(session).toInt()
}
