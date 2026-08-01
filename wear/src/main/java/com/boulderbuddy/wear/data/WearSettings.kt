package com.boulderbuddy.wear.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Timer-Konfiguration der Uhr (Sätze / Hang / Pause) — Pendant zum Phone-`TimerConfig`. */
data class WearTimerConfig(
    val sets: Int = 6,
    val hangSec: Int = 7,
    val restSec: Int = 3,
)

private val Context.dataStore by preferencesDataStore(name = "wear_settings")

/**
 * Lokale Persistenz der zuletzt genutzten Timer-Konfiguration (§0 Säule 4): Die Uhr merkt
 * sich Sätze/Hang/Pause über App-Starts hinweg, statt bei jedem Öffnen auf 6/7/3
 * zurückzuspringen. Kein Hilt auf der Uhr → schlankes `object` mit Context-Parameter.
 */
object WearSettings {
    private val KEY_SETS = intPreferencesKey("timer_sets")
    private val KEY_HANG_SEC = intPreferencesKey("timer_hang_sec")
    private val KEY_REST_SEC = intPreferencesKey("timer_rest_sec")

    fun timerConfig(context: Context): Flow<WearTimerConfig> =
        context.dataStore.data.map { prefs ->
            WearTimerConfig(
                sets = prefs[KEY_SETS] ?: 6,
                hangSec = prefs[KEY_HANG_SEC] ?: 7,
                restSec = prefs[KEY_REST_SEC] ?: 3,
            )
        }

    suspend fun setTimerConfig(context: Context, config: WearTimerConfig) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SETS] = config.sets
            prefs[KEY_HANG_SEC] = config.hangSec
            prefs[KEY_REST_SEC] = config.restSec
        }
    }
}
