package com.boulderbuddy.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ein einzelner Grad innerhalb eines Gradsystems (z.B. "6b", "V4").
 * GradeSystem 1:N Grade. Löscht sich mit dem [GradeSystemEntity] mit.
 *
 * Ein Grad ist reine Schwierigkeit (nur ein Label) — die Farbe eines Boulders ist davon
 * entkoppelt und hängt an der Route selbst (siehe `RouteEntity.color`).
 *
 * `order` (Schema) ist ein SQL-Schlüsselwort → als Spalte `sortOrder` abgelegt.
 */
@Entity(
    tableName = "grade",
    foreignKeys = [
        ForeignKey(
            entity = GradeSystemEntity::class,
            parentColumns = ["id"],
            childColumns = ["systemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("systemId")],
)
data class GradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val systemId: Int,
    val label: String,
    @ColumnInfo(name = "sortOrder") val order: Int,
)
