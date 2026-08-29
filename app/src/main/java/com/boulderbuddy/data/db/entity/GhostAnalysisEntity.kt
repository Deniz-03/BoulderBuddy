package com.boulderbuddy.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
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
 *
 * **An einer Session hängen darf sie trotzdem** (v12), nach dem Muster der
 * Hangboard-Workouts: „immer speichern, verknüpfen wenn möglich". [sessionId] ist der
 * einzige Unterschied zwischen „Analyse vom Sofa aus" und „das habe ich in der Session
 * gemacht" — beide sind gültig, deshalb nullable.
 *
 * **Anders als das Workout überlebt die Analyse aber das Löschen ihrer Session**
 * (`SET NULL` statt `CASCADE`). Hinter ihr stecken Minuten Rechenzeit und eigene
 * Artefaktdateien im GhostArtifactStore, die beim Kaskadieren als Leichen liegenblieben;
 * ein Workout dagegen ist ohne seine Session kaum mehr als eine Handvoll Zahlen. Wer eine
 * Session aufräumt, meint die Session — nicht die Videoanalyse, die zufällig darin lag.
 */
@Entity(
    tableName = "ghost_analysis",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("sessionId")],
)
data class GhostAnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    /** null = eigenständige Analyse, gesetzt = in dieser Kletter-Session entstanden. */
    val sessionId: Int? = null,
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
