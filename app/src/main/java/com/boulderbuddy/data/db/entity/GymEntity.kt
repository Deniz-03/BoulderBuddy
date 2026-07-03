package com.boulderbuddy.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Ein Kletter-/Boulderhalle. Wurzel der Hierarchie (1:N GradeSystem, 1:N Session).
 */
@Entity(tableName = "gym")
data class GymEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val location: String? = null,
)
