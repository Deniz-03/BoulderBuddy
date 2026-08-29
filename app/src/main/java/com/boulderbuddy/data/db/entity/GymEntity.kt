package com.boulderbuddy.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Eine Kletter-/Boulderhalle. Wurzel der Hierarchie (1:N GradeSystem, 1:N Session).
 *
 * Seit **v8** (Gym-Näherungs-Push, Variante B; `MIGRATION_7_8`) optional mit Koordinaten:
 * eine Halle mit `latitude`/`longitude` wird als Geofence registriert und kann
 * Näherungs-Erinnerungen auslösen; ohne Koordinaten bleibt sie ein reiner Namens-Datensatz
 * wie zuvor. Bestandshallen kamen ohne Position durch die Migration — „nicht geofenced" ist
 * ein gültiger Zustand, kein halbfertiger.
 *
 * **Eine gelöschte Halle reißt nichts mit** (v10): sowohl `session.gymId` als auch
 * `grade_system.gymId` stehen auf SET NULL. Die einzige Ausnahme ist `gym_visit`, das per
 * CASCADE mitgeht — ein Besuchs-Log ohne die besuchte Halle sagt nichts mehr aus.
 */
@Entity(tableName = "gym")
data class GymEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    /** Freitext (Adresse o.Ä.) — bewusst KEINE Koordinaten, die stehen separat. */
    val location: String? = null,
    /** Geo-Position der Halle; `null` = nicht geofenced (kein Zwang). */
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Geofence-Radius in Metern; Default 150 m, im Editor anpassbar (empirisch zu kalibrieren, M5). */
    val geofenceRadiusMeters: Int = DEFAULT_GEOFENCE_RADIUS_METERS,
    /** Näherungs-Erinnerungen pro Gym abschaltbar (zusätzlich zum globalen Master-Toggle). */
    val proximityAlertsEnabled: Boolean = true,
    /**
     * Gradsystem, das beim Anlegen einer Session in dieser Halle vorgewählt wird; `null` =
     * kein Standard, dann greift das erste vorhandene System. Die Vorwahl ist ein Vorschlag,
     * keine Bindung — in der Session lässt sich weiter jedes System wählen (Moonboard & Co.).
     *
     * **Bewusst ohne `ForeignKey`, obwohl die Spalte auf `grade_system.id` zeigt.**
     * `grade_system.gymId` verweist bereits zurück auf `gym`; ein echter Fremdschlüssel in
     * dieser Richtung machte daraus einen Zyklus. Der Geräte-Abgleich schreibt seine Tabellen
     * strikt „Eltern vor Kindern" (siehe `sync/Standtabellen.kt`) — in einem Zyklus gibt es
     * diese Reihenfolge nicht, und das Einfügen der ersten Tabelle verletzte den jeweils
     * anderen Fremdschlüssel. Deshalb eine lose Referenz: verschwindet das System, bleibt
     * hier eine tote ID stehen, und wer sie liest, fällt still auf „kein Standard" zurück.
     */
    val defaultGradeSystemId: Int? = null,
) {
    companion object {
        const val DEFAULT_GEOFENCE_RADIUS_METERS = 150
    }
}
