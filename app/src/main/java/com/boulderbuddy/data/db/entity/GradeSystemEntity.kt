package com.boulderbuddy.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Gradsystem (z.B. "Farbsystem Halle Nord", "V-Scale"). Gym 1:N GradeSystem.
 *
 * [gymId] ist nullable: globale Standard-Systeme (V-Scale, Französisch) haben keinen
 * Gym-Anker (`gymId == null`), hallenspezifische/Custom-Systeme schon. Beim Löschen der
 * Halle verschwinden nur deren eigene Systeme (CASCADE); globale bleiben.
 */
@Entity(
    tableName = "grade_system",
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
data class GradeSystemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val gymId: Int? = null,
    val name: String,
)
