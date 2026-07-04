package com.boulderbuddy.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ein abgeschlossener Hangboard-Timer-Durchlauf, verknüpft mit der Kletter-Session, in der
 * er absolviert wurde. Session 1:N HangboardSession, verschwindet mit der Session (CASCADE).
 *
 * Wird nur angelegt, wenn tatsächlich trainiert wurde (Durchlauf bis zum Ende) — so bleibt die
 * Session-Detailansicht clutterfrei.
 */
@Entity(
    tableName = "hangboard_session",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class HangboardSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionId: Int,
    val completedSets: Int,
    val totalSets: Int,
    val hangSec: Int,
    val restSec: Int,
    /** Abschluss-Timestamp (epoch millis). */
    val date: Long,
)
