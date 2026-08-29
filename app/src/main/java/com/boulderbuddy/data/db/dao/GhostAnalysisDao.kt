package com.boulderbuddy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.boulderbuddy.data.db.entity.GhostAnalysisEntity
import kotlinx.coroutines.flow.Flow

/**
 * Zugriff auf die gespeicherten Ghost-Climber-Analysen.
 *
 * Die Zeilen sind schlank: sie enthalten nur PFADE auf die Pose-Spuren im
 * `GhostArtifactStore` und die URIs der Videos. Das heißt auch, dass Löschen hier nur die
 * halbe Miete ist — siehe [deleteById].
 */
@Dao
interface GhostAnalysisDao {
    @Insert
    suspend fun insert(analysis: GhostAnalysisEntity): Long

    /** Alle gespeicherten Analysen, neueste zuerst (Liste im Ghost-Climber-Einstieg). */
    @Query("SELECT * FROM ghost_analysis ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GhostAnalysisEntity>>

    /** Analysen einer Session, neueste zuerst (Block in der Session-Ansicht). */
    @Query("SELECT * FROM ghost_analysis WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    fun observeBySession(sessionId: Int): Flow<List<GhostAnalysisEntity>>

    @Query("SELECT * FROM ghost_analysis WHERE id = :id")
    suspend fun getById(id: Int): GhostAnalysisEntity?

    /**
     * Löscht die Analyse-Zeile — **nicht** die Dateien dahinter.
     *
     * BEFUND B1 (Kommentarpflege): Die Pose-Spuren im `GhostArtifactStore`
     * (`filesDir/ghost/pose_<hash>.json`, je nach Videolänge einige hundert kB) bleiben
     * liegen, und es gibt nirgends ein Aufräumen. Solange dasselbe Video noch einmal
     * analysiert wird, ist das ein nützlicher Cache; ist das Video weg, ist es totes
     * Gewicht, das nie wieder verschwindet.
     */
    @Query("DELETE FROM ghost_analysis WHERE id = :id")
    suspend fun deleteById(id: Int)
}
