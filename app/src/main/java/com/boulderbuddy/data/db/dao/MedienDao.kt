package com.boulderbuddy.data.db.dao

import androidx.room.Dao
import androidx.room.Query

/** Eine Route mit ihrer Medien-URI. */
data class RoutenMedium(val id: Int, val mediaUri: String)

/** Eine Ghost-Analyse mit ihren beiden Video-URIs. */
data class GhostMedien(val id: Int, val refMediaUri: String, val cmpMediaUri: String)

/**
 * Quer über die Tabellen, in denen Medien-URIs stehen — für den einmaligen Umzug auf
 * inhaltsadressierte Namen (Sync-Plan S3).
 *
 * Bewusst ein eigenes DAO statt Anbau an `RouteDao`/`GhostAnalysisDao`: der Umzug ist keine
 * fachliche Abfrage, sondern eine Wartungsaufgabe, und er soll auch als solche zu erkennen
 * sein.
 */
@Dao
interface MedienDao {

    @Query("SELECT id, mediaUri FROM route WHERE mediaUri IS NOT NULL AND mediaUri != ''")
    suspend fun routenMitMedien(): List<RoutenMedium>

    @Query("UPDATE route SET mediaUri = :uri WHERE id = :id")
    suspend fun setzeRoutenMedium(id: Int, uri: String)

    @Query("SELECT id, refMediaUri, cmpMediaUri FROM ghost_analysis")
    suspend fun ghostMedien(): List<GhostMedien>

    @Query("UPDATE ghost_analysis SET refMediaUri = :ref, cmpMediaUri = :cmp WHERE id = :id")
    suspend fun setzeGhostMedien(id: Int, ref: String, cmp: String)
}
