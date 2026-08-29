package com.boulderbuddy.data.repository

import com.boulderbuddy.data.db.dao.GhostAnalysisDao
import com.boulderbuddy.data.db.entity.GhostAnalysisEntity
import com.boulderbuddy.ghost.GhostArtifactStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Kapselt den Zugriff auf gespeicherte Ghost-Climber-Analysen (Phase 7.5, M5).
 *
 * Die Zeile und ihre Dateien gehören zusammen, auch wenn sie an zwei Orten liegen: die
 * Analyse in der Datenbank, die Pose-Spuren als JSON im `GhostArtifactStore`. Deshalb ist
 * [delete] die einzige Stelle, die beides kennt — sonst hinterließe jedes Löschen Dateien,
 * auf die nichts mehr zeigt.
 */
interface GhostAnalysisRepository {
    /** Alle gespeicherten Analysen, neueste zuerst. */
    fun observeAll(): Flow<List<GhostAnalysisEntity>>

    /** Analysen, die an dieser Session hängen; neueste zuerst. */
    fun observeBySession(sessionId: Int): Flow<List<GhostAnalysisEntity>>

    suspend fun getById(id: Int): GhostAnalysisEntity?

    /** Legt eine Analyse an und gibt ihre neue ID zurück. */
    suspend fun create(analysis: GhostAnalysisEntity): Int

    /** Löscht die Analyse samt ihrer Pose-Spuren, sofern keine andere sie noch braucht. */
    suspend fun delete(id: Int)
}

class GhostAnalysisRepositoryImpl @Inject constructor(
    private val ghostAnalysisDao: GhostAnalysisDao,
    private val artifactStore: GhostArtifactStore,
) : GhostAnalysisRepository {

    override fun observeAll(): Flow<List<GhostAnalysisEntity>> = ghostAnalysisDao.observeAll()

    override fun observeBySession(sessionId: Int): Flow<List<GhostAnalysisEntity>> =
        ghostAnalysisDao.observeBySession(sessionId)

    override suspend fun getById(id: Int): GhostAnalysisEntity? = ghostAnalysisDao.getById(id)

    override suspend fun create(analysis: GhostAnalysisEntity): Int =
        ghostAnalysisDao.insert(analysis).toInt()

    /**
     * Erst die Zeile, dann die verwaisten Dateien.
     *
     * Die Reihenfolge ist wichtig: gefragt wird, was **nach** dem Löschen noch gebraucht
     * wird. Andersherum stünde die eigene Zeile noch in der Antwort und schützte ihre
     * eigenen Spuren vor dem Aufräumen.
     *
     * Bricht das Löschen der Datei fehl, bleibt sie eben liegen — das ist der alte Zustand
     * und kein Grund, den Nutzer mit einem Fehler zu behelligen.
     */
    override suspend fun delete(id: Int) {
        val analyse = ghostAnalysisDao.getById(id) ?: return
        ghostAnalysisDao.deleteById(id)

        val nochGebraucht = ghostAnalysisDao.nochGenutzteSpurPfade().toSet()
        setOf(analyse.refKeypointsPath, analyse.cmpKeypointsPath)
            .filterNot { it in nochGebraucht }
            .forEach { artifactStore.loeschePoseTrack(it) }
    }
}
