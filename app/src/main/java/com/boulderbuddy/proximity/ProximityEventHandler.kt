package com.boulderbuddy.proximity

import android.util.Log
import com.boulderbuddy.data.repository.GymVisitRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verarbeitet eine Geofence-DWELL-Ankunft an einem Gym (Gym-Näherungs-Push).
 *
 * Vom [GeofenceBroadcastReceiver] via Hilt-EntryPoint aufgerufen (Receiver können nicht
 * direkt injizieren). M2: Gerüst; M3 ergänzt das Besuchs-Logging, M4 die Push-Politik.
 */
@Singleton
class ProximityEventHandler @Inject constructor(
    private val gymVisitRepository: GymVisitRepository,
) {
    suspend fun onGymDwell(gymId: Int, timestamp: Long) {
        Log.d(TAG, "DWELL an Gym $gymId")
        // M3: Besuch loggen (Tages-Dedupe im Repository).
        // M4: Push-Politik prüfen und ggf. Notification zeigen.
    }

    private companion object {
        const val TAG = "ProximityEventHandler"
    }
}
