package com.boulderbuddy.data.repository

import com.boulderbuddy.data.db.dao.SessionDao
import com.boulderbuddy.data.db.entity.SessionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Kapselt den Zugriff auf [SessionEntity]-Daten (Sessions einer Halle).
 *
 * Die ViewModels (Phase 6) sprechen nur noch dieses Interface an, nicht den [SessionDao] direkt.
 * Aktiv-Marker einer Session ist `endedAt == null` (siehe [observeActive]).
 */
interface SessionRepository {
    /** Die gerade laufende Session (`endedAt IS NULL`); `null`, wenn keine aktiv ist. */
    fun observeActive(): Flow<SessionEntity?>

    /** Alle Sessions, neueste zuerst. */
    fun observeAll(): Flow<List<SessionEntity>>

    /** Einzelne Session laden; `null`, wenn keine mit [sessionId] existiert. */
    suspend fun getById(sessionId: Int): SessionEntity?

    /** Legt eine Session an und gibt ihre neue ID zurück. */
    suspend fun create(session: SessionEntity): Int

    /** Aktualisiert eine bestehende Session (Notizen, Dauer, …). */
    suspend fun update(session: SessionEntity)

    /** Beendet die Session, indem `endedAt` gesetzt wird (Default: jetzt). */
    suspend fun endSession(sessionId: Int, endedAt: Long = System.currentTimeMillis())
}

class SessionRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao,
) : SessionRepository {

    override fun observeActive(): Flow<SessionEntity?> = sessionDao.observeActive()

    override fun observeAll(): Flow<List<SessionEntity>> = sessionDao.observeAll()

    override suspend fun getById(sessionId: Int): SessionEntity? = sessionDao.getById(sessionId)

    override suspend fun create(session: SessionEntity): Int = sessionDao.insert(session).toInt()

    override suspend fun update(session: SessionEntity) = sessionDao.update(session)

    override suspend fun endSession(sessionId: Int, endedAt: Long) =
        sessionDao.endSession(sessionId, endedAt)
}
