package com.boulderbuddy.proximity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Registriert die Geofences nach einem Reboot neu (Gym-Näherungs-Push, M2) —
 * Geofence-Registrierungen überleben keinen Neustart des Geräts.
 */
class GeofenceBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val geofenceManager = EntryPointAccessors
            .fromApplication(context.applicationContext, ProximityEntryPoint::class.java)
            .geofenceManager()

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                geofenceManager.refreshGeofences()
            } catch (e: Exception) {
                Log.w(TAG, "Geofence-Re-Registrierung nach Boot fehlgeschlagen", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "GeofenceBootReceiver"
    }
}
