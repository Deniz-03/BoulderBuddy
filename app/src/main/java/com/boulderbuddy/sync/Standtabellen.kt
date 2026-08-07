package com.boulderbuddy.sync

/** Was beim Löschen der Elternzeile mit der Kindzeile passiert — wie in den Entities. */
enum class Loeschregel { KASKADE, AUF_NULL }

/** Ein Fremdschlüssel: [spalte] dieser Tabelle zeigt auf die `id` von [elternTabelle]. */
data class Elternbezug(
    val spalte: String,
    val elternTabelle: String,
    val regel: Loeschregel,
)

/**
 * Eine am Abgleich beteiligte Tabelle.
 *
 * [spalten] sind die verglichenen Spalten **ohne** `id` — die ist der Schlüssel, nicht Inhalt.
 * Die Liste steht ausgeschrieben und wird nicht aus dem Schema abgeleitet: eine neue Spalte
 * soll jemanden zwingen hinzusehen, ob sie überhaupt in einen Vergleich gehört (E15).
 */
data class Tabelle(
    val name: String,
    val spalten: List<String>,
    val eltern: List<Elternbezug> = emptyList(),
)

/**
 * Die abzugleichenden Tabellen, **von oben nach unten entlang der Fremdschlüssel**
 * (Sync-Plan E4, Ablauf 8). Die Reihenfolge ist Teil der Bedeutung: eine Elterntabelle steht
 * immer vor ihren Kindern, damit Einfügen in dieser und Löschen in umgekehrter Reihenfolge
 * laufen kann, ohne einen Fremdschlüssel zu verletzen.
 *
 * **Was hier nicht steht, wird nicht abgeglichen.** Insbesondere `stand_meta` nicht: sie
 * beschreibt den Stand, sie ist nicht Teil davon, und ein Zeilenabgleich über sie würde die
 * Herkunft zweier Geräte gegeneinander laufen lassen (Ablauf 19). `sqlite_sequence` und
 * Rooms `room_master_table` ebenso wenig.
 *
 * **Und was hier steht, muss auf beiden Geräten gleich aussehen können.** Das ist die Regel
 * aus Ablauf 31: eine gerätelokale Spalte macht aus jeder betroffenen Zeile einen
 * Dauerkonflikt. Deshalb sind die Keypoint-Pfade seit v7 relativ (E15). Die Medien-URIs
 * dürfen mit, weil `applicationId` keinen Build-Typ-Suffix hat und die FileProvider-Autorität
 * damit auf beiden Geräten dieselbe ist — **wer ein `applicationIdSuffix` einführt, bricht
 * den Abgleich** und muss die URIs dann ebenfalls relativ ablegen.
 */
val STAND_TABELLEN: List<Tabelle> = listOf(
    Tabelle(
        name = "gym",
        spalten = listOf("name", "location"),
    ),
    Tabelle(
        name = "grade_system",
        spalten = listOf("gymId", "name"),
        eltern = listOf(Elternbezug("gymId", "gym", Loeschregel.KASKADE)),
    ),
    Tabelle(
        name = "grade",
        spalten = listOf("systemId", "label", "sortOrder"),
        eltern = listOf(Elternbezug("systemId", "grade_system", Loeschregel.KASKADE)),
    ),
    Tabelle(
        name = "session",
        spalten = listOf("gymId", "gradeSystemId", "date", "durationMin", "notes", "endedAt"),
        eltern = listOf(
            Elternbezug("gymId", "gym", Loeschregel.KASKADE),
            Elternbezug("gradeSystemId", "grade_system", Loeschregel.AUF_NULL),
        ),
    ),
    Tabelle(
        name = "route",
        spalten = listOf(
            "sessionId", "gradeId", "name", "sektor", "attempts", "status",
            "color", "mediaUri", "notes",
        ),
        eltern = listOf(
            Elternbezug("sessionId", "session", Loeschregel.KASKADE),
            Elternbezug("gradeId", "grade", Loeschregel.AUF_NULL),
        ),
    ),
    Tabelle(
        name = "hangboard_workout",
        spalten = listOf(
            "sessionId", "mode", "origin", "startedAt", "endedAt",
            "plannedSets", "plannedHangSec", "plannedRestSec",
        ),
        eltern = listOf(Elternbezug("sessionId", "session", Loeschregel.KASKADE)),
    ),
    Tabelle(
        name = "hangboard_segment",
        spalten = listOf("workoutId", "setIndex", "hangMs", "restMs"),
        eltern = listOf(Elternbezug("workoutId", "hangboard_workout", Loeschregel.KASKADE)),
    ),
    Tabelle(
        name = "hangboard_template",
        spalten = listOf("name", "sets", "hangSec", "restSec", "repRestSec"),
    ),
    Tabelle(
        name = "ghost_analysis",
        spalten = listOf(
            "refMediaUri", "cmpMediaUri", "refKeypointsPath", "cmpKeypointsPath",
            "homographyCmpJson", "routePathJson", "suggestedMode", "createdAt",
        ),
    ),
)

/** Tabellen, die zwar in der Datei mitreisen, aber nie zeilenweise abgeglichen werden. */
val META_TABELLEN: Set<String> = setOf("stand_meta", "sqlite_sequence", "room_master_table")

/** Nachschlagen nach Namen — die Baumlogik braucht das ständig. */
val STAND_TABELLEN_NACH_NAME: Map<String, Tabelle> = STAND_TABELLEN.associateBy { it.name }

/** Die Kinder einer Tabelle, in derselben Reihenfolge wie [STAND_TABELLEN]. */
fun kinderVon(tabelle: String): List<Tabelle> =
    STAND_TABELLEN.filter { kind -> kind.eltern.any { it.elternTabelle == tabelle } }
