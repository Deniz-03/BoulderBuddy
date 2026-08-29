package com.boulderbuddy.wear.data

/**
 * Vertrag für die Uhr → Phone Data-Layer-Nachricht eines fertigen Hangboard-Durchlaufs.
 *
 * Bewusst simpel gehalten (ein `MessageClient`-Event, kleine Text-Payload), damit die Uhr
 * ohne Serialisierungs-Dependency auskommt. Die Gegenseite steht im `:app`-Modul unter
 * `com.boulderbuddy.wearsync.HangboardWearListenerService` — beide Module sind getrennt,
 * deshalb ist der Vertrag dupliziert statt geteilt.
 *
 * **Das ist die Falle dieser Datei:** Pfade und Payload-Format stehen zweimal im Repo, und
 * nichts erzwingt ihre Gleichheit. Wer hier etwas ändert, muss die andere Seite von Hand
 * nachziehen; ein Fehler dabei fällt erst am gekoppelten Gerät auf.
 */
object WearSyncContract {
    /** Pfad der MessageClient-Nachricht. Muss im Phone-`WearableListenerService` übereinstimmen. */
    const val PATH_HANGBOARD_COMPLETED = "/boulderbuddy/hangboard_completed"

    /**
     * Pfad des DataItems mit einem aufgezeichneten Sensor-Log (B.5.1 Debug-Export).
     * Das CSV liegt als Asset unter [KEY_SENSOR_LOG_ASSET]; [KEY_SENSOR_LOG_NAME] trägt den
     * Dateinamen, [KEY_SENSOR_LOG_TIMESTAMP] erzwingt ein neues DataItem je Export
     * (identische DataItems würden vom System sonst dedupliziert und nicht zugestellt).
     */
    const val PATH_SENSOR_LOG = "/boulderbuddy/sensor_log"
    const val KEY_SENSOR_LOG_ASSET = "log"
    const val KEY_SENSOR_LOG_NAME = "name"
    const val KEY_SENSOR_LOG_TIMESTAMP = "timestamp"

    /** Payload = "completedSets;totalSets;hangSec;restSec;date" (epoch millis). */
    fun encode(completedSets: Int, totalSets: Int, hangSec: Int, restSec: Int, date: Long): ByteArray =
        "$completedSets;$totalSets;$hangSec;$restSec;$date".toByteArray(Charsets.UTF_8)

    /**
     * Pfad eines fertigen **Auto**-Workouts (gemessene Segmente). Teilt den Präfix des
     * manuellen Pfads, damit der bestehende Manifest-`pathPrefix`-Filter beide zustellt.
     */
    const val PATH_HANGBOARD_AUTO_COMPLETED = "/boulderbuddy/hangboard_completed/auto"

    /** Payload = "startedAt;endedAt;hangMs:restMs,hangMs:restMs,…" (epoch millis). */
    fun encodeAuto(startedAt: Long, endedAt: Long, segments: List<Pair<Long, Long>>): ByteArray {
        val segmentText = segments.joinToString(",") { (hangMs, restMs) -> "$hangMs:$restMs" }
        return "$startedAt;$endedAt;$segmentText".toByteArray(Charsets.UTF_8)
    }

    /**
     * DataItem-Pfad der vom Phone publizierten Presets (§0 Säule 4, Phone → Uhr).
     * [KEY_PRESETS]: StringArrayList, je Eintrag "name;sets;hangSec;restSec".
     * [KEY_LAST_USED]: "sets;hangSec;restSec" der zuletzt am Phone genutzten Config.
     */
    const val PATH_HANGBOARD_PRESETS = "/boulderbuddy/hangboard_presets"
    const val KEY_PRESETS = "presets"
    const val KEY_LAST_USED = "last_used"
}
