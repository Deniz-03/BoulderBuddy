package com.boulderbuddy.wear.data

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Sendet Ereignisse der Uhr an das gekoppelte Phone über den Wear Data Layer (MessageClient).
 *
 * Companion-Prinzip: Die Uhr funktioniert auch ohne Phone (reiner Timer). Ist kein Node
 * verbunden oder kein Phone gekoppelt, scheitert der Sende-Versuch — das Tracking in die aktive
 * Phone-Session ist rein additiv, die Uhr bleibt für sich benutzbar.
 *
 * **Geräuschlos darf das aber nicht sein.** Die Uhr legt Workouts nirgends lokal ab; was nicht
 * ankommt, ist weg. Wer ein Ergebnis anzeigt, fragt deshalb über `onResult`, ob es ankam —
 * [sendSensorLog] hat das immer so gemacht, [sendAutoWorkoutCompleted] tut es seit dem
 * Emulator-Durchlauf vom 08.08. ebenfalls.
 */
object PhoneConnector {
    private const val TAG = "PhoneConnector"

    /**
     * Meldet einen bis zum Ende absolvierten Hangboard-Durchlauf an alle verbundenen Nodes.
     * Das Phone entscheidet selbst, ob es eine aktive Session hat, in die es den Durchlauf trägt.
     */
    fun sendHangboardCompleted(
        context: Context,
        completedSets: Int,
        totalSets: Int,
        hangSec: Int,
        restSec: Int,
        date: Long = System.currentTimeMillis(),
    ) {
        val payload = WearSyncContract.encode(completedSets, totalSets, hangSec, restSec, date)
        val messageClient = Wearable.getMessageClient(context)
        Wearable.getNodeClient(context).connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    Log.d(TAG, "Kein verbundener Node — Durchlauf bleibt lokal auf der Uhr.")
                    return@addOnSuccessListener
                }
                nodes.forEach { node ->
                    messageClient.sendMessage(
                        node.id,
                        WearSyncContract.PATH_HANGBOARD_COMPLETED,
                        payload,
                    ).addOnFailureListener { e ->
                        Log.w(TAG, "Senden an ${node.displayName} fehlgeschlagen", e)
                    }
                }
            }
            .addOnFailureListener { e -> Log.w(TAG, "Node-Abfrage fehlgeschlagen", e) }
    }

    /**
     * Meldet ein beendetes **Auto**-Workout (gemessene Segmente) an alle verbundenen Nodes.
     * Das Phone entscheidet beim Persistieren über die Session-Verknüpfung (§0 Säule 2).
     *
     * [onResult] meldet, ob die Übertragung **tatsächlich** geklappt hat.
     *
     * Vorher lief das rein als Fire-and-Forget, und der Auto-Screen schrieb trotzdem „An Phone
     * übertragen" — auch dann, wenn gar kein Node verbunden war. Am Emulator ohne
     * Companion-App war das nachweislich falsch: die Uhr meldete den Erfolg, auf dem Tablet kam
     * nichts an. Schwerer wiegt, was daraus folgt: die Uhr legt Workouts **nirgends lokal ab**
     * (`WearSettings` kennt nur die Timer-Konfiguration). Ohne Verbindung ist der Durchlauf
     * also weg — und der Nutzer las, er sei angekommen.
     */
    fun sendAutoWorkoutCompleted(
        context: Context,
        startedAt: Long,
        endedAt: Long,
        segments: List<Pair<Long, Long>>,
        onResult: (Boolean) -> Unit = {},
    ) {
        val payload = WearSyncContract.encodeAuto(startedAt, endedAt, segments)
        val messageClient = Wearable.getMessageClient(context)
        Wearable.getNodeClient(context).connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    Log.d(TAG, "Kein verbundener Node — Auto-Workout konnte nicht übertragen werden.")
                    onResult(false)
                    return@addOnSuccessListener
                }
                // Erfolg heißt: mindestens ein Node hat die Nachricht angenommen.
                val offen = AtomicInteger(nodes.size)
                val geglueckt = AtomicBoolean(false)
                nodes.forEach { node ->
                    messageClient.sendMessage(
                        node.id,
                        WearSyncContract.PATH_HANGBOARD_AUTO_COMPLETED,
                        payload,
                    ).addOnSuccessListener {
                        geglueckt.set(true)
                        if (offen.decrementAndGet() == 0) onResult(geglueckt.get())
                    }.addOnFailureListener { e ->
                        Log.w(TAG, "Senden an ${node.displayName} fehlgeschlagen", e)
                        if (offen.decrementAndGet() == 0) onResult(geglueckt.get())
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Node-Abfrage fehlgeschlagen", e)
                onResult(false)
            }
    }

    /**
     * Überträgt ein aufgezeichnetes Sensor-Log (B.5.1) als DataItem-Asset ans Phone, das es
     * in den Downloads ablegt. DataItems werden vom System synchronisiert & gecacht — der
     * Export überlebt damit auch eine kurzzeitig getrennte Verbindung.
     */
    fun sendSensorLog(context: Context, file: File, onResult: (Boolean) -> Unit = {}) {
        val request = PutDataMapRequest.create(WearSyncContract.PATH_SENSOR_LOG).apply {
            dataMap.putAsset(
                WearSyncContract.KEY_SENSOR_LOG_ASSET,
                Asset.createFromBytes(file.readBytes()),
            )
            dataMap.putString(WearSyncContract.KEY_SENSOR_LOG_NAME, file.name)
            // Erzwingt ein "geändertes" DataItem je Export (sonst Deduplizierung durch das System).
            dataMap.putLong(WearSyncContract.KEY_SENSOR_LOG_TIMESTAMP, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(context).putDataItem(request)
            .addOnSuccessListener {
                Log.d(TAG, "Sensor-Log ${file.name} übertragen (${file.length()} Bytes).")
                onResult(true)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Sensor-Log-Export fehlgeschlagen", e)
                onResult(false)
            }
    }
}
