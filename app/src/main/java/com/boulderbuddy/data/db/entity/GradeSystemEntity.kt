package com.boulderbuddy.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Gradsystem (z.B. "Farbsystem Halle Nord", "V-Scale"). Gym 1:N GradeSystem.
 *
 * [gymId] ist nullable: globale Standard-Systeme (V-Scale, Französisch) haben keinen
 * Gym-Anker (`gymId == null`), hallenspezifische/Custom-Systeme schon.
 *
 * **Beim Löschen der Halle wird ihr System global statt gelöscht** (v10, vorher CASCADE).
 * Ein gelöschtes System hätte seine Grade mitgenommen, und jeder damit bewertete Boulder
 * hätte seine Schwierigkeit verloren (`route.gradeId` SET NULL) — für Boulder, die es
 * weiterhin gibt. Der Zustand „ohne Gym-Anker" beschreibt genau, was dann zutrifft: das
 * System existiert noch und gehört zu keiner Halle mehr.
 */
@Entity(
    tableName = "grade_system",
    foreignKeys = [
        ForeignKey(
            entity = GymEntity::class,
            parentColumns = ["id"],
            childColumns = ["gymId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("gymId")],
)
data class GradeSystemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val gymId: Int? = null,
    val name: String,
)
