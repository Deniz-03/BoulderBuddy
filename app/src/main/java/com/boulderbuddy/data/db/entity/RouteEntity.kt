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
    /**
     * Farb-Key der Route ("red", "green", …, siehe `routeColorPalette`). Bewusst von
     * [gradeId] entkoppelt: dient nur dem Wiedererkennen der Routenfarbe in der Halle.
     * `null` = keine Farbe gewählt (neutraler Fallback im UI).
     */
    val color: String? = null,
    /**
     * URI eines Fotos oder Videos; Bild oder Video wird nicht gespeichert, sondern zur
     * Laufzeit am MIME-Typ erkannt (`util/MediaType.kt`).
     *
     * Immer eine `content://`-URI, aus einer von drei Quellen: Galerie-Picker (dort holt
     * `RouteHinzufuegenScreen` ausdrücklich eine dauerhafte Leseberechtigung, sonst überlebt
     * die URI keinen Neustart), eigener Aufnahme-Screen über den FileProvider, oder nach dem
     * Medien-Umzug des Geräte-Abgleichs (`sync/MedienUmzug.kt`) auf die inhaltsadressierte
     * Kopie in `filesDir/aufnahmen` umgeschrieben.
     */
    val mediaUri: String? = null,
    val notes: String? = null,
)
