package com.boulderbuddy.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Gradsystem (z.B. "Farbsystem Halle Nord", "V-Scale"). Gym 1:N GradeSystem.
 *
 * [gymId] ist nullable, und zwar für zwei verschiedene Fälle: mitgelieferte Standards hatten nie
 * eine Halle, und einem hallenspezifischen System wird sie beim Löschen der Halle genommen
 * (v10). Selbst angelegte Systeme bekommen ebenfalls keine — ein Gradsystem darf ohne Halle
 * bestehen. Ob es geschützt ist, sagt deshalb [istStandard] und nicht mehr [gymId].
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
    /**
     * Mitgeliefertes Standard-System (V-Scale, Französisch) — geschützt vor dem Löschen.
     *
     * Früher stand dafür `gymId == null`, und das war eine Verwechslung von „hat keine Halle"
     * mit „gehört zur App". Sie fiel auf, sobald beides auseinanderlief: eine gelöschte Halle
     * setzt `gymId` auf `NULL` (v10), und das eigene System der Halle wurde damit dauerhaft
     * unlöschbar und trug in den Einstellungen den Hinweis „Standard". Seit selbst angelegte
     * Systeme gar keine Halle mehr bekommen, träfe das jedes von ihnen.
     *
     * `defaultValue = "0"` steht bewusst auch im SQL-Schema, nicht nur in Kotlin: eine
     * `NOT NULL`-Spalte, deren Standardwert nur die Entity kennt, ist in diesem Projekt schon
     * dreimal aufgefallen — im Seed, in den Abgleich-Fixtures und beim direkten Einfügen. Mit
     * der Klausel im Schema kann ein `INSERT` ohne diese Spalte nicht scheitern, und Rooms
     * Prüfung beim Öffnen bleibt zufrieden, weil Erwartung und Wirklichkeit übereinstimmen.
     */
    @ColumnInfo(defaultValue = "0")
    val istStandard: Boolean = false,
)
