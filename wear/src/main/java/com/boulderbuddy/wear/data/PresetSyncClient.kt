package com.boulderbuddy.wear.data

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Ein vom Phone synchronisiertes Preset (§0 Säule 4). */
data class WearPreset(
    val name: String,
    val sets: Int,
    val hangSec: Int,
    val restSec: Int,
)

/**
 * Liest die vom Phone publizierten Hangboard-Presets (DataItem unter
 * [WearSyncContract.PATH_HANGBOARD_PRESETS]) und hält sie aktuell. DataItems werden vom
 * System gecacht — der letzte bekannte Stand ist auch ohne verbundenes Phone lesbar.
 */
object PresetSyncClient {
    private const val TAG = "PresetSync"

    /** Beobachtet die Preset-Liste: initialer Cache-Stand + jede Änderung vom Phone. */
    fun observePresets(context: Context): Flow<List<WearPreset>> = callbackFlow {
        val client = Wearable.getDataClient(context)

        val listener = DataClient.OnDataChangedListener { events ->
            events.forEach { event ->
                if (event.type != DataEvent.TYPE_CHANGED) return@forEach
                if (event.dataItem.uri.path != WearSyncContract.PATH_HANGBOARD_PRESETS) return@forEach
                trySend(parse(DataMapItem.fromDataItem(event.dataItem).dataMap))
            }
        }
        client.addListener(listener)

        // Initial: letzter gecachter Stand (überlebt Trennung & App-Neustart).
        client.dataItems
            .addOnSuccessListener { buffer ->
                buffer.use { items ->
                    items.firstOrNull { it.uri.path == WearSyncContract.PATH_HANGBOARD_PRESETS }
                        ?.let { trySend(parse(DataMapItem.fromDataItem(it).dataMap)) }
                }
            }
            .addOnFailureListener { e -> Log.w(TAG, "Preset-Initial-Read fehlgeschlagen", e) }

        awaitClose { client.removeListener(listener) }
    }

    // Einträge im Format "name;sets;hangSec;restSec"; defekte Einträge überspringen.
    private fun parse(dataMap: DataMap): List<WearPreset> =
        dataMap.getStringArrayList(WearSyncContract.KEY_PRESETS)
            .orEmpty()
            .mapNotNull { entry ->
                val parts = entry.split(';')
                if (parts.size != 4) return@mapNotNull null
                WearPreset(
                    name = parts[0],
                    sets = parts[1].toIntOrNull() ?: return@mapNotNull null,
                    hangSec = parts[2].toIntOrNull() ?: return@mapNotNull null,
                    restSec = parts[3].toIntOrNull() ?: return@mapNotNull null,
                )
            }
}
