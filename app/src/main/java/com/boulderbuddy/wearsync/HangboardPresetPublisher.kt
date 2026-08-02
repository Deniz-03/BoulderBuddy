package com.boulderbuddy.wearsync

import android.content.Context
import android.util.Log
import com.boulderbuddy.data.repository.HangboardRepository
import com.boulderbuddy.data.settings.SettingsRepository
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publiziert die Hangboard-Presets + die zuletzt genutzte Timer-Config als DataItem an die
 * Uhr (§0 Säule 4, §2 Kanal 2). DataItems werden vom System synchronisiert und **gecacht** —
 * die Uhr liest auch nach Trennung/Neustart den letzten bekannten Stand.
 *
 * Wird einmalig aus [com.boulderbuddy.BoulderBuddyApp] gestartet und läuft app-weit: jede
 * Preset-Änderung (anlegen/löschen) und jede Config-Übernahme aktualisiert das DataItem.
 * Contract-Pfad + Format sind im `WearSyncContract` der Uhr gespiegelt (getrennte Module).
 */
@Singleton
class HangboardPresetPublisher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val hangboardRepository: HangboardRepository,
    private val settingsRepository: SettingsRepository,
) {

    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(
                hangboardRepository.observeAll(),
                settingsRepository.timerConfig,
            ) { presets, config ->
                PutDataMapRequest.create(PATH_HANGBOARD_PRESETS).apply {
                    dataMap.putStringArrayList(
                        KEY_PRESETS,
                        ArrayList(presets.map { "${sanitize(it.name)};${it.sets};${it.hangSec};${it.restSec}" }),
                    )
                    dataMap.putString(
                        KEY_LAST_USED,
                        "${config.sets};${config.hangSec};${config.restSec}",
                    )
                }.asPutDataRequest().setUrgent()
            }.collect { request ->
                Wearable.getDataClient(context).putDataItem(request)
                    .addOnFailureListener { e ->
                        // Best effort: ohne Play Services/Uhr verpufft der Sync geräuschlos.
                        Log.w(TAG, "Preset-Publish fehlgeschlagen", e)
                    }
            }
        }
    }

    // Semikolons trennen die Felder des Eintrags — aus Namen heraushalten.
    private fun sanitize(name: String): String = name.replace(';', ',')

    private companion object {
        const val TAG = "PresetPublisher"
        // Müssen mit dem WearSyncContract der Uhr übereinstimmen (getrennte Module).
        const val PATH_HANGBOARD_PRESETS = "/boulderbuddy/hangboard_presets"
        const val KEY_PRESETS = "presets"
        const val KEY_LAST_USED = "last_used"
    }
}
