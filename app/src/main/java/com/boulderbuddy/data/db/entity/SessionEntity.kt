package com.boulderbuddy.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Eine Kletter-Session in einer Halle. Gym 1:N Session, Session 1:N Route.
 *
 * [endedAt] ist der Aktiv-Marker: `null` = Session läuft noch, gesetzt = beendet.
 * Entscheidet in `SessionRoute.kt`, ob `SessionDetailScreen` (aktiv) oder
 * `AlteSessionScreen` (read-only) angezeigt wird.
 *
 * **Eine gelöschte Halle nimmt ihre Sessions nicht mit** (v10). Vorher hing [gymId] per
 * CASCADE an der Halle — ein Löschen hätte alle Sessions dort gelöscht und über sie
 * (`route.sessionId` CASCADE) auch jeden Boulder. Genau das darf beim Aufräumen einer
 * Hallenliste nicht passieren: Trainingshistorie ist das, was die App überhaupt sammelt.
 */
@Entity(
    tableName = "session",
    foreignKeys = [
        ForeignKey(
            entity = GymEntity::class,
            parentColumns = ["id"],
            childColumns = ["gymId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = GradeSystemEntity::class,
            parentColumns = ["id"],
            childColumns = ["gradeSystemId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("gymId"), Index("gradeSystemId")],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    /** Halle, in der die Session lief; `null` = die Halle wurde gelöscht (siehe [gymName]). */
    val gymId: Int? = null,
    /**
     * Name der Halle zum Zeitpunkt der Session — der Beleg, der eine Löschung überlebt.
     *
     * Solange [gymId] zeigt, wohin es soll, ist die **Halle** die Wahrheit: eine Umbenennung
     * schlägt dann auch auf alte Sessions durch, und das ist gewollt (dieselbe Halle, neuer
     * Name). Erst wenn die Zeile weg ist, übernimmt dieser Schnappschuss. Deshalb steht in
     * der UI überall `gym?.name ?: session.gymName` und nicht das eine oder das andere.
     */
    val gymName: String = "",
    /**
     * Für diese Session gewähltes Gradsystem — steuert die Grade-Auswahl beim Boulder-Anlegen.
     * `null` = keins gewählt (Formular fällt auf den globalen Standard/erstes System zurück).
     */
    val gradeSystemId: Int? = null,
    /** Start-Timestamp (epoch millis). */
    val date: Long,
    val durationMin: Int? = null,
    val notes: String? = null,
    /** Aktiv-Marker: `null` = läuft noch, gesetzt (epoch millis) = beendet. */
    val endedAt: Long? = null,
)

/**
 * Name der Halle dieser Session — an **einer** Stelle entschieden, weil die Regel sonst an
 * sieben Orten mitgepflegt werden müsste (Home, Sessions-Liste, Session-Detail, Widget,
 * Hangboard-Historie, CSV-Export).
 *
 * Reihenfolge: die Halle, solange [SessionEntity.gymId] auf eine zeigt (dann schlagen
 * Umbenennungen durch), sonst der Schnappschuss [SessionEntity.gymName]. `null` erst, wenn
 * beides nichts hergibt — wie die Anzeige das benennt, entscheidet der Aufrufer.
 *
 * @param nameFuerId löst eine Gym-ID zu ihrem Namen auf; `null`, wenn es sie nicht mehr gibt.
 */
fun SessionEntity.hallenName(nameFuerId: (Int) -> String?): String? =
    gymId?.let(nameFuerId) ?: gymName.ifBlank { null }
