package com.boulderbuddy.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Gradsystem einer Halle (z.B. "Farbsystem Halle Nord"). Gym 1:N GradeSystem.
 * Löscht sich mit, wenn das zugehörige [GymEntity] entfernt wird.
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
    val gymId: Int,
    val name: String,
)
