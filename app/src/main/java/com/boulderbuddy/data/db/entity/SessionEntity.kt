package com.boulderbuddy.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Eine Kletter-Session in einer Halle. Gym 1:N Session, Session 1:N Route.
 *
 * [endedAt] ist der Aktiv-Marker: `null` = Session läuft noch, gesetzt = beendet.
 * Entscheidet in `SessionRoute.kt`, ob `SessionDetailScreen` (aktiv) oder
 * `AlteSessionScreen` (read-only) angezeigt wird.
 */
@Entity(
    tableName = "session",
    foreignKeys = [
        ForeignKey(
            entity = GymEntity::class,
            parentColumns = ["id"],
            childColumns = ["gymId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("gymId")],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val gymId: Int,
    /** Start-Timestamp (epoch millis). */
    val date: Long,
    val durationMin: Int? = null,
    val notes: String? = null,
    /** Aktiv-Marker: `null` = läuft noch, gesetzt (epoch millis) = beendet. */
    val endedAt: Long? = null,
)
