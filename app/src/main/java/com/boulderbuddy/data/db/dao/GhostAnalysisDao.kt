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
     * Schreibt zurück, was eine Nachbearbeitung ändern kann: Anker (in der Homographie),
     * Routenpfad und der daraus folgende Modus-Vorschlag.
     *
     * Bewusst nur diese drei Spalten und kein `@Update` der ganzen Zeile. Videos und
     * Spur-Pfade bleiben dieselben — es ist ja dieselbe Analyse —, und `sessionId` wie
     * `createdAt` sollen ausdrücklich stehen bleiben: eine Korrektur verschiebt eine Analyse
     * nicht in eine andere Session und macht sie nicht neu.
     */
    @Query(
        "UPDATE ghost_analysis SET homographyCmpJson = :homographie, " +
            "routePathJson = :routenpfad, suggestedMode = :modus WHERE id = :id"
    )
    suspend fun aktualisiereAuswertung(
        id: Int,
        homographie: String,
        routenpfad: String,
        modus: String,
    )

    /**
     * Löscht nur die Zeile. Die Dateien dahinter räumt das Repository ab — es muss dafür
     * erst wissen, welche Spuren danach noch gebraucht werden ([nochGenutzteSpurPfade]).
     */
    @Query("DELETE FROM ghost_analysis WHERE id = :id")
    suspend fun deleteById(id: Int)

    /**
     * Alle Spur-Pfade, die noch an einer Analyse hängen — beide Spalten in einem Ergebnis.
     *
     * Zwei Analysen können sich dieselbe Spur teilen: wer denselben Versuch einmal als
     * Referenz und einmal als Vergleich benutzt, zeigt aus zwei Zeilen auf dieselbe Datei.
     * Deshalb wird beim Aufräumen gegen diese Liste geprüft und nicht gegen die gelöschte
     * Zeile allein.
     */
    @Query(
        "SELECT refKeypointsPath FROM ghost_analysis " +
            "UNION SELECT cmpKeypointsPath FROM ghost_analysis"
    )
    suspend fun nochGenutzteSpurPfade(): List<String>
}
