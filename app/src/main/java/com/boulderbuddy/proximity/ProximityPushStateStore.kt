package com.boulderbuddy.proximity

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistiert den Push-Cooldown-Zustand: `lastNotifiedAt` pro Gym (M4).
 *
 * Entscheidung (Plan §6, "eins von beiden"): DataStore-Key pro Gym statt einer Spalte auf
 * `GymEntity` — der Zeitstempel ist flüchtiger Politik-Zustand der App, keine Domänen-Eigen-
 * schaft der Halle. So bleibt das DB-Schema stabil (kein v7 nur für einen Cooldown) und
 * Room-Flows, die Gyms beobachten (UI-Listen), re-emittieren nicht bei jedem Push.
 */
@Singleton
class ProximityPushStateStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /** Zeitpunkt des letzten Push für [gymId]; `null` = nie gepusht. */
    suspend fun lastNotifiedAt(gymId: Int): Long? =
        dataStore.data.first()[notifiedKey(gymId)]

    suspend fun setLastNotifiedAt(gymId: Int, timestamp: Long) {
        dataStore.edit { it[notifiedKey(gymId)] = timestamp }
    }

    private fun notifiedKey(gymId: Int) = longPreferencesKey("gym_notified_$gymId")
}
