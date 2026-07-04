package com.boulderbuddy.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Persistente Timer-Konfiguration (letzte Einstellung wird gemerkt). Defaults = 6/7/3. */
data class TimerConfig(
    val sets: Int = 6,
    val hangSec: Int = 7,
    val restSec: Int = 3,
)

/**
 * Kapselt app-weite Einstellungen in einem Preferences-[DataStore].
 *
 * Hält das aktuell gewählte Grade-System (steuert das Grade-Dropdown beim Boulder-Anlegen)
 * und die zuletzt genutzte Timer-Konfiguration. Bewusst schlank — nur die zwei MVP-Werte.
 */
interface SettingsRepository {
    /** ID des gewählten Grade-Systems; `null`, wenn noch nichts gewählt wurde. */
    val selectedGradeSystemId: Flow<Int?>

    /** Zuletzt genutzte Timer-Konfiguration (mit Defaults, falls nie gesetzt). */
    val timerConfig: Flow<TimerConfig>

    /**
     * Expliziter Dark-Mode-Override; `null` = noch nie gesetzt → dem System folgen
     * (`isSystemInDarkTheme()`). `true`/`false` überstimmt das System dauerhaft.
     */
    val darkMode: Flow<Boolean?>

    suspend fun setSelectedGradeSystem(systemId: Int)

    suspend fun setTimerConfig(config: TimerConfig)

    suspend fun setDarkMode(enabled: Boolean)
}

class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val selectedGradeSystemId: Flow<Int?> =
        dataStore.data.map { it[KEY_GRADE_SYSTEM_ID] }

    override val timerConfig: Flow<TimerConfig> = dataStore.data.map { prefs ->
        TimerConfig(
            sets = prefs[KEY_TIMER_SETS] ?: 6,
            hangSec = prefs[KEY_TIMER_HANG_SEC] ?: 7,
            restSec = prefs[KEY_TIMER_REST_SEC] ?: 3,
        )
    }

    override val darkMode: Flow<Boolean?> =
        dataStore.data.map { it[KEY_DARK_MODE] }

    override suspend fun setSelectedGradeSystem(systemId: Int) {
        dataStore.edit { it[KEY_GRADE_SYSTEM_ID] = systemId }
    }

    override suspend fun setTimerConfig(config: TimerConfig) {
        dataStore.edit { prefs ->
            prefs[KEY_TIMER_SETS] = config.sets
            prefs[KEY_TIMER_HANG_SEC] = config.hangSec
            prefs[KEY_TIMER_REST_SEC] = config.restSec
        }
    }

    override suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { it[KEY_DARK_MODE] = enabled }
    }

    private companion object {
        val KEY_GRADE_SYSTEM_ID = intPreferencesKey("selected_grade_system_id")
        val KEY_TIMER_SETS = intPreferencesKey("timer_sets")
        val KEY_TIMER_HANG_SEC = intPreferencesKey("timer_hang_sec")
        val KEY_TIMER_REST_SEC = intPreferencesKey("timer_rest_sec")
        val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")
    }
}
