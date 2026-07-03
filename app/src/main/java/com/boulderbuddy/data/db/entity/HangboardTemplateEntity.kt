package com.boulderbuddy.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Voreinstellung für den Hangboard-Timer (z.B. "7-3 Max Hangs"). Eigenständig, ohne FK.
 */
@Entity(tableName = "hangboard_template")
data class HangboardTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val sets: Int,
    val hangSec: Int,
    val restSec: Int,
    val repRestSec: Int,
)
