package com.boulderbuddy.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ein erkannter Hallen-Besuch (Gym-Näherungs-Push, Variante B). Quelle ist entweder eine
 * physische Geofence-Ankunft (DWELL) oder ein Session-Start — beides zählt fürs Lernen der
 * Besuchsmuster ([com.boulderbuddy.proximity.GymVisitStats]).
 *
 * Tages-Dedupe: pro Gym und Kalendertag wird höchstens EIN Besuch gespeichert
 * (ein Besuch = ein Tag), egal aus welcher Quelle — siehe GymVisitRepository.
 */
@Entity(
    tableName = "gym_visit",
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
data class GymVisitEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val gymId: Int,
    /** Epoch-Millis der Ankunft bzw. des Session-Starts. */
    val timestamp: Long,
    /** [SOURCE_GEOFENCE] oder [SOURCE_SESSION] — bewusst String, kein Enum-Converter nötig. */
    val source: String,
) {
    companion object {
        /** Physische Ankunft am Geofence (DWELL-Trigger). */
        const val SOURCE_GEOFENCE = "GEOFENCE"

        /** Der Nutzer hat eine Session an dieser Halle gestartet. */
        const val SOURCE_SESSION = "SESSION"
    }
}
