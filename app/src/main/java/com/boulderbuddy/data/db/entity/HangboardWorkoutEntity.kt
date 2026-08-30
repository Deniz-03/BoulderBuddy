package com.boulderbuddy.data.db.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** Wie das Workout entstanden ist: klassischer Timer oder sensorbasierte Auto-Erkennung. */
enum class HangboardWorkoutMode { MANUAL, AUTO }

/** Auf welchem Gerät das Workout absolviert wurde. */
enum class HangboardWorkoutOrigin { PHONE, WATCH }

/**
 * Ein abgeschlossenes Hangboard-Workout — das vereinte Modell für Phone+Uhr, manuell+auto (v6).
 *
 * „Immer speichern, verknüpfen wenn möglich": Läuft beim Persistieren eine Kletter-Session,
 * wird das Workout an sie gehängt ([sessionId] gesetzt, verschwindet mit ihr per CASCADE);
 * sonst bleibt es ein eigenständiges Hangboard-Training ([sessionId] = null).
 *
 * Die tatsächlichen Hänge-/Pausenzeiten liegen als [HangboardSegmentEntity]-Zeilen daneben:
 * beim manuellen Timer aus der Vorgabe abgeleitet (identische Dauern), bei der Auto-Erkennung
 * gemessen. Die planned*-Felder sind der Snapshot der Timer-Vorgabe — bei AUTO gibt es keine.
 */
@Entity(
    tableName = "hangboard_workout",
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
data class HangboardWorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    /** null = eigenständiges Training, gesetzt = an diese Kletter-Session gehängt. */
    val sessionId: Int?,
    val mode: HangboardWorkoutMode,
    val origin: HangboardWorkoutOrigin,
    /** Start-Timestamp (epoch millis). */
    val startedAt: Long,
    /** Abschluss-Timestamp (epoch millis). */
    val endedAt: Long,
    val plannedSets: Int?,
    val plannedHangSec: Int?,
    val plannedRestSec: Int?,
)

/**
 * Ein Satz innerhalb eines Workouts: Hängedauer + anschließende Pause (nach dem letzten
 * Satz 0). Bei MANUAL aus der Vorgabe, bei AUTO von der Detektions-State-Machine gemessen.
 */
@Entity(
    tableName = "hangboard_segment",
    foreignKeys = [
        ForeignKey(
            entity = HangboardWorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutId")],
)
data class HangboardSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val workoutId: Int,
    /** 0-basierte Satz-Reihenfolge innerhalb des Workouts. */
    val setIndex: Int,
    val hangMs: Long,
    val restMs: Long,
)

/** Ein Workout mitsamt seinen Sätzen — Lese-Modell aller Hangboard-Abfragen. */
data class HangboardWorkoutWithSegments(
    @Embedded val workout: HangboardWorkoutEntity,
    /**
     * Die Sätze — **in unbestimmter Reihenfolge**: Room sagt über die Ordnung einer
     * `@Relation` nichts zu. Heute liest sie niemand der Reihe nach (es wird gezählt und
     * summiert), wer das ändert, sortiert vorher nach [HangboardSegmentEntity.setIndex].
     */
    @Relation(parentColumn = "id", entityColumn = "workoutId")
    val segments: List<HangboardSegmentEntity>,
) {
    val totalHangMs: Long get() = segments.sumOf { it.hangMs }
}
