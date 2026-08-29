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
        // Koordinaten, Radius und der Pro-Gym-Schalter beschreiben die Halle, nicht das Gerät:
        // sie sehen auf Phone und Tablet gleich aus und gehören damit in den Vergleich. Der
        // *globale* Näherungs-Schalter dagegen steht im DataStore und wird nie abgeglichen —
        // welches Gerät den Push schickt, ist eine Geräte-Entscheidung.
        //
        // `defaultGradeSystemId` zeigt auf `grade_system`, steht hier aber NICHT als
        // [Elternbezug]: `grade_system.gymId` verweist bereits zurück auf `gym`, und ein
        // zweiter Bezug in die Gegenrichtung machte aus dem Baum einen Zyklus — die
        // Reihenfolge „Eltern vor Kindern" gäbe es dann nicht mehr. Die Spalte reist als
        // gewöhnlicher Wert mit; zeigt sie nach einem Abgleich ins Leere, liest der Editor
        // „kein Standard" (siehe GymEntity).
        spalten = listOf(
            "name", "location", "latitude", "longitude",
            "geofenceRadiusMeters", "proximityAlertsEnabled", "defaultGradeSystemId",
        ),
    ),
    Tabelle(
        name = "grade_system",
        // `istStandard` reist mit: ob ein System zur App gehört, ist keine Geräte-Eigenschaft.
        spalten = listOf("gymId", "name", "istStandard"),
        // Seit v10 AUF_NULL statt KASKADE: eine gelöschte Halle nimmt ihr Gradsystem nicht
        // mit, es wird global. Sonst verlören Boulder, die es weiter gibt, ihre Schwierigkeit.
        eltern = listOf(Elternbezug("gymId", "gym", Loeschregel.AUF_NULL)),
    ),
    Tabelle(
        name = "grade",
        spalten = listOf("systemId", "label", "sortOrder"),
        eltern = listOf(Elternbezug("systemId", "grade_system", Loeschregel.KASKADE)),
    ),
    Tabelle(
        name = "session",
        // `gymName` ist der Beleg, in welcher Halle die Session lief — er muss mitreisen,
        // sonst stünde auf dem anderen Gerät „Unbekannte Halle", sobald die Halle dort fehlt.
        spalten = listOf(
            "gymId", "gymName", "gradeSystemId", "date", "durationMin", "notes", "endedAt",
        ),
        eltern = listOf(
            // Seit v10 AUF_NULL statt KASKADE. Der Unterschied ist keiner von Geschmack:
            // mit KASKADE hätte eine auf dem anderen Gerät gelöschte Halle hier sämtliche
            // Sessions samt Bouldern mitgenommen, sobald der Abgleich sie nachzieht.
            Elternbezug("gymId", "gym", Loeschregel.AUF_NULL),
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
        // `sessionId` reist mit (v12): in welcher Session eine Analyse entstanden ist, ist
        // Nutzer-Zuordnung und kein Gerätezustand — sie sieht auf Phone und Tablet gleich aus.
        spalten = listOf(
            "sessionId", "refMediaUri", "cmpMediaUri", "refKeypointsPath", "cmpKeypointsPath",
            "homographyCmpJson", "routePathJson", "suggestedMode", "createdAt",
        ),
        // AUF_NULL wie in der Entity: eine gelöschte Session nimmt ihre Analysen nicht mit.
        eltern = listOf(Elternbezug("sessionId", "session", Loeschregel.AUF_NULL)),
    ),
    // Besuche (Gym-Näherungs-Push M3). Steht hinter `gym`, weil es ihr Kind ist.
    //
    // Dass sie mitabgeglichen werden, ist eine Entscheidung gegen zwei Alternativen: „ist eh
    // gerätelokal, lass weg" hätte bedeutet, dass `setzeDatentabellenZurueck` beim Rückgängig
    // alle `gym`-Zeilen löscht und die Besuche per CASCADE **stillschweigend mitnimmt** —
    // genau die Sorte Datenverlust, gegen die dieser Abgleich gebaut ist. Ein Besuch ist
    // außerdem echte Nutzer-Historie („wann war ich in welcher Halle"), nicht Gerätezustand.
    //
    // Preis: das Tages-Dedupe (ein Besuch je Gym und Tag) hält nur pro Gerät. Zwei Geräte,
    // die am selben Tag dieselbe Halle sehen, ergeben nach dem Abgleich zwei Zeilen; die
    // gelernten Muster zählen den Tag dann doppelt. Das setzt voraus, dass beide Geräte
    // mitfahren — bei Phone + Tablet zu Hause der seltene Fall, und die Folge ist eine
    // leicht schiefe Statistik statt verlorener Daten.
    Tabelle(
        name = "gym_visit",
        spalten = listOf("gymId", "timestamp", "source"),
        eltern = listOf(Elternbezug("gymId", "gym", Loeschregel.KASKADE)),
    ),
)

/** Tabellen, die zwar in der Datei mitreisen, aber nie zeilenweise abgeglichen werden. */
val META_TABELLEN: Set<String> = setOf("stand_meta", "sqlite_sequence", "room_master_table")

/** Nachschlagen nach Namen — die Baumlogik braucht das ständig. */
val STAND_TABELLEN_NACH_NAME: Map<String, Tabelle> = STAND_TABELLEN.associateBy { it.name }

/** Die Kinder einer Tabelle, in derselben Reihenfolge wie [STAND_TABELLEN]. */
fun kinderVon(tabelle: String): List<Tabelle> =
    STAND_TABELLEN.filter { kind -> kind.eltern.any { it.elternTabelle == tabelle } }
