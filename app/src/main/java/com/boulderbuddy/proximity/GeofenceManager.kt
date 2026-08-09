package com.boulderbuddy.proximity

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.boulderbuddy.data.repository.GymRepository
import com.boulderbuddy.data.settings.SettingsRepository
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registriert für jedes Gym mit Koordinaten einen Geofence (Gym-Näherungs-Push, M2).
 *
 * [refreshGeofences] ist idempotent (erst alles entfernen, dann neu registrieren) und wird
 * von allen relevanten Stellen aufgerufen: Gym-Editor-Save (Koordinaten geändert),
 * Master-Toggle in den Einstellungen und Boot ([GeofenceBootReceiver] — Geofences überleben
 * keinen Reboot). Trigger ist DWELL statt ENTER: gepusht wird erst, wenn der Nutzer wirklich
 * an der Halle BLEIBT — kein Drive-by-Fehlalarm.
 */
@Singleton
class GeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gymRepository: GymRepository,
    private val settingsRepository: SettingsRepository,
) {
    private val geofencingClient by lazy { LocationServices.getGeofencingClient(context) }

    // Ein PendingIntent für ALLE Geofences; das Rückmapping läuft über requestId = gymId.
    // FLAG_MUTABLE ab API 31 nötig: das System hängt die GeofencingEvent-Daten an den Intent.
    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        val mutableFlag =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag,
        )
    }

    /**
     * Entfernt alle Geofences und registriert sie neu für jedes Gym mit Koordinaten und
     * aktivem Pro-Gym-Toggle — sofern Master-Toggle an und Hintergrund-Standort erteilt.
     * Master-Toggle aus ⇒ es bleibt beim Entfernen (Plan §12: "aus" räumt alles ab).
     */
    // Die Freigabe wird unten geprüft (hasBackgroundLocationPermission), und ein Entzug
    // zwischen Prüfung und Aufruf fängt der catch-Block ab. Lint sieht keins von beidem.
    @SuppressLint("MissingPermission")
    suspend fun refreshGeofences() {
        removeAllGeofences()

        // Jeder Ausstieg sagt, warum. Das Abräumen oben passiert immer und lautlos; ohne diese
        // Zeilen sieht ein „nichts registriert" im Logcat genauso aus wie ein abgestürzter
        // Aufruf, und man sucht am falschen Ende. Aufgefallen im Gerätelauf am 09.08.2026:
        // der Master-Toggle „aus" hinterließ überhaupt keine Spur.
        if (!settingsRepository.proximityAlertsEnabled.first()) {
            Log.d(TAG, "Geofences entfernt: Erinnerungen sind ausgeschaltet")
            return
        }
        if (!hasBackgroundLocationPermission(context)) {
            Log.w(TAG, "Geofences nicht registriert: Hintergrund-Standort fehlt")
            return
        }

        val geofencedGyms = gymRepository.observeAll().first().filter {
            it.latitude != null && it.longitude != null && it.proximityAlertsEnabled
        }
        if (geofencedGyms.isEmpty()) {
            Log.d(TAG, "Geofences entfernt: keine Halle mit Koordinaten und aktiver Erinnerung")
            return
        }

        val geofences = geofencedGyms.map { gym ->
            Geofence.Builder()
                .setRequestId(gym.id.toString())
                .setCircularRegion(
                    gym.latitude!!,
                    gym.longitude!!,
                    gym.geofenceRadiusMeters.toFloat(),
                )
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_DWELL)
                .setLoiteringDelay(LOITERING_DELAY_MILLIS)
                .build()
        }
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_DWELL)
            .addGeofences(geofences)
            .build()

        try {
            geofencingClient.addGeofences(request, geofencePendingIntent).await()
            Log.d(TAG, "${geofences.size} Geofence(s) registriert")
        } catch (e: Exception) {
            // z.B. SecurityException (Permission zwischenzeitlich entzogen) oder
            // GEOFENCE_NOT_AVAILABLE (Standortdienste aus) — Feature degradiert still.
            Log.w(TAG, "Geofence-Registrierung fehlgeschlagen", e)
        }
    }

    private suspend fun removeAllGeofences() {
        try {
            geofencingClient.removeGeofences(geofencePendingIntent).await()
        } catch (e: Exception) {
            Log.w(TAG, "Geofence-Entfernen fehlgeschlagen", e)
        }
    }

    companion object {
        private const val TAG = "GeofenceManager"

        /**
         * DWELL-Verweildauer, bevor der Trigger feuert (~2 min) — verhindert Drive-by-Auslöser.
         * Empirisch zu kalibrieren (M5).
         */
        const val LOITERING_DELAY_MILLIS = 120_000
    }
}
