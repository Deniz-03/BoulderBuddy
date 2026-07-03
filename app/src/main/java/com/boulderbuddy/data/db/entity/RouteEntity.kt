package com.boulderbuddy.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.boulderbuddy.data.model.RouteStatus

/**
 * Eine geloggte Route/Boulder innerhalb einer Session. Session 1:N Route, Grade 1:N Route.
 *
 * [gradeId] ist optional (Route kann ohne zugeordneten Grad geloggt werden). Beim Löschen
 * des Grades wird sie deshalb auf `null` gesetzt statt die Route zu entfernen; beim Löschen
 * der Session verschwindet die Route (CASCADE).
 */
@Entity(
    tableName = "route",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GradeEntity::class,
            parentColumns = ["id"],
            childColumns = ["gradeId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("sessionId"), Index("gradeId")],
)
data class RouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionId: Int,
    val gradeId: Int? = null,
    /** Name des Boulders (z.B. "Dachrinne"). Leer erlaubt, wenn ohne Namen geloggt. */
    val name: String = "",
    /** Sektor/Bereich in der Halle (z.B. "A"). Optional. */
    val sektor: String? = null,
    val attempts: Int = 0,
    val status: RouteStatus = RouteStatus.OPEN,
    /** URI eines Fotos oder Videos. */
    val mediaUri: String? = null,
    val notes: String? = null,
)
