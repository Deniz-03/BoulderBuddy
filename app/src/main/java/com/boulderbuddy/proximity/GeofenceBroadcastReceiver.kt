package com.boulderbuddy.proximity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Empfängt die Geofence-Transitions des Systems (Gym-Näherungs-Push, M2) — auch bei
 * geschlossener App. Mappt `requestId` → Gym-ID und reicht DWELL-Ankünfte an den
 * [ProximityEventHandler] weiter.
 *
 * DB-/Repository-Zugriff läuft über den Hilt-EntryPoint + `goAsync()`: der Receiver darf
 * den Main-Thread nicht blockieren, bekommt mit goAsync aber ~10 s für asynchrone Arbeit.
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.w(TAG, "GeofencingEvent-Fehler: ${event.errorCode}")
            return
        }
        // Nur DWELL ist registriert — Guard trotzdem, falls das System anderes liefert.
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_DWELL) return

        val gymIds = event.triggeringGeofences.orEmpty()
            .mapNotNull { it.requestId.toIntOrNull() }
        if (gymIds.isEmpty()) return

        val handler = EntryPointAccessors
            .fromApplication(context.applicationContext, ProximityEntryPoint::class.java)
            .proximityEventHandler()
        val timestamp = System.currentTimeMillis()

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                gymIds.forEach { gymId -> handler.onGymDwell(gymId, timestamp) }
            } catch (e: Exception) {
                Log.w(TAG, "Geofence-Ereignis konnte nicht verarbeitet werden", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "GeofenceReceiver"
    }
}
