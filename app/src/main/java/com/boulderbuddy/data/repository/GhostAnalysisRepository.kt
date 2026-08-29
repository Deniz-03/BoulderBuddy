package com.boulderbuddy.data.repository

import com.boulderbuddy.data.db.dao.GhostAnalysisDao
import com.boulderbuddy.data.db.entity.GhostAnalysisEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Kapselt den Zugriff auf gespeicherte Ghost-Climber-Analysen (Phase 7.5, M5).
 * Die Artefakt-Dateien hinter den gespeicherten Pfaden verwaltet der
 * GhostArtifactStore; hier läuft nur die DB-Verwaltung der Analyse-Zeilen.
 */
interface GhostAnalysisRepository {
    /** Alle gespeicherten Analysen, neueste zuerst. */
    fun observeAll(): Flow<List<GhostAnalysisEntity>>

    /** Analysen, die an dieser Session hängen; neueste zuerst. */
    fun observeBySession(sessionId: Int): Flow<List<GhostAnalysisEntity>>

    suspend fun getById(id: Int): GhostAnalysisEntity?

    /** Legt eine Analyse an und gibt ihre neue ID zurück. */
    suspend fun create(analysis: GhostAnalysisEntity): Int

    suspend fun delete(id: Int)
}

class GhostAnalysisRepositoryImpl @Inject constructor(
    private val ghostAnalysisDao: GhostAnalysisDao,
) : GhostAnalysisRepository {

    override fun observeAll(): Flow<List<GhostAnalysisEntity>> = ghostAnalysisDao.observeAll()

    override fun observeBySession(sessionId: Int): Flow<List<GhostAnalysisEntity>> =
        ghostAnalysisDao.observeBySession(sessionId)

    override suspend fun getById(id: Int): GhostAnalysisEntity? = ghostAnalysisDao.getById(id)

    override suspend fun create(analysis: GhostAnalysisEntity): Int =
        ghostAnalysisDao.insert(analysis).toInt()

    override suspend fun delete(id: Int) = ghostAnalysisDao.deleteById(id)
}
