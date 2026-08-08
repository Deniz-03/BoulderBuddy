package com.boulderbuddy.proximity

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt-EntryPoint für die Geofencing-BroadcastReceiver: Receiver haben keinen
 * `@AndroidEntryPoint`-Automatismus für Konstruktor-Injektion, holen sich ihre
 * Abhängigkeiten daher über `EntryPointAccessors.fromApplication` (Plan §12.3).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ProximityEntryPoint {
    fun proximityEventHandler(): ProximityEventHandler
    fun geofenceManager(): GeofenceManager
}
