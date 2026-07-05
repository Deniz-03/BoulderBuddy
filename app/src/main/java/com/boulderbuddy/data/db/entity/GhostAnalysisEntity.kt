package com.boulderbuddy.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Gespeicherte Ghost-Climber-Analyse (Phase 7.5, M5). Bewusst schlank nach Plan A.2:
 * die großen Artefakte (Keypoint-Spuren) liegen als JSON-Dateien im App-Storage
 * (GhostArtifactStore) — hier stehen nur ihre PFADE, keine BLOBs. Videos werden nie
 * kopiert, nur ihre URIs referenziert. Kleine Ergebnisse (Homographie = 9 Werte,
 * Routenpfad = wenige Stützpunkte) stehen als kompakte JSON-Strings direkt in der Zeile.
 *
 * Keine Routen-Identität (A.7 Q1, Option 1): eine Analyse hängt an zwei frei
 * gewählten Videos, nicht an einer RouteEntity.
 */
@Entity(tableName = "ghost_analysis")
data class GhostAnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val refMediaUri: String,
    val cmpMediaUri: String,
    /** Dateipfade der Pose-Spuren (JSON) im App-Storage. */
    val refKeypointsPath: String,
    val cmpKeypointsPath: String,
    /** Homographie Vergleich→Referenz, 9 Werte zeilenweise als JSON-Array. */
    val homographyCmpJson: String,
    /** Routenpfad-Polylinie im Referenzraum als JSON (Liste von GhostPoint). */
    val routePathJson: String,
    /** Vorgeschlagener Darstellungsmodus (GhostViewMode-Name). */
    val suggestedMode: String,
    /** Erstellzeitpunkt (epoch millis). */
    val createdAt: Long,
)
