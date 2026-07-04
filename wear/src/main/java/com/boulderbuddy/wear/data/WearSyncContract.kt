package com.boulderbuddy.wear.data

/**
 * Vertrag für die Uhr → Phone Data-Layer-Nachricht eines fertigen Hangboard-Durchlaufs.
 *
 * Bewusst simpel gehalten (ein `MessageClient`-Event, kleine Text-Payload), damit die Uhr
 * ohne Serialisierungs-Dependency auskommt. Der gleiche Vertrag existiert spiegelbildlich im
 * `:app`-Modul (`com.boulderbuddy.wear.HangboardWearListenerService`) — beide Module sind
 * getrennt, deshalb dupliziert statt geteilt.
 */
object WearSyncContract {
    /** Pfad der MessageClient-Nachricht. Muss im Phone-`WearableListenerService` übereinstimmen. */
    const val PATH_HANGBOARD_COMPLETED = "/boulderbuddy/hangboard_completed"

    /** Payload = "completedSets;totalSets;hangSec;restSec;date" (epoch millis). */
    fun encode(completedSets: Int, totalSets: Int, hangSec: Int, restSec: Int, date: Long): ByteArray =
        "$completedSets;$totalSets;$hangSec;$restSec;$date".toByteArray(Charsets.UTF_8)
}
