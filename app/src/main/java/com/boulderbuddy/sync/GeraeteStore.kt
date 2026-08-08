package com.boulderbuddy.sync

import javax.inject.Qualifier

/**
 * Unterscheidet den DataStore der Geräte-Identität vom DataStore der App-Einstellungen.
 * Beide sind `DataStore<Preferences>`; ohne Qualifier wüsste Hilt nicht, welcher gemeint ist.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeraeteStore
