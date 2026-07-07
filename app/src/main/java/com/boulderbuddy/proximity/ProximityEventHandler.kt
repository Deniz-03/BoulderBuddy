package com.boulderbuddy.proximity

import android.util.Log
import com.boulderbuddy.data.db.entity.GymVisitEntity
import com.boulderbuddy.data.repository.GymVisitRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verarbeitet eine Geofence-DWELL-Ankunft an einem Gym (Gym-Näherungs-Push).
 *
 * Vom [GeofenceBroadcastReceiver] via Hilt-EntryPoint aufgerufen (Receiver können nicht
 * direkt injizieren). M3: physische Ankünfte werden als Besuch geloggt (auch ohne
 * Session-Start — Entscheidung §1.4); M4 ergänzt die Push-Politik.
 */
@Singleton
class ProximityEventHandler @Inject constructor(
    private val gymVisitRepository: GymVisitRepository,
) {
    suspend fun onGymDwell(gymId: Int, timestamp: Long) {
        // Tages-Dedupe übernimmt das Repository (ein Besuch = ein Kalendertag).
        val logged = gymVisitRepository.logVisit(
            gymId = gymId,
            timestamp = timestamp,
            source = GymVisitEntity.SOURCE_GEOFENCE,
        )
        Log.d(TAG, "DWELL an Gym $gymId — Besuch geloggt: $logged")
        // M4: Push-Politik prüfen und ggf. Notification zeigen.
    }

    private companion object {
        const val TAG = "ProximityEventHandler"
    }
}
