package com.boulderbuddy.proximity

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Dünner Wrapper um den [com.google.android.gms.location.FusedLocationProviderClient] für den
 * Standort-Button im Gym-Editor (M1). `getCurrentLocation` statt des deprecated `lastLocation`:
 * fordert aktiv einen frischen Fix an, statt einen (evtl. alten) Cache-Wert zu liefern.
 */
@Singleton
class GymLocationClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(context) }

    /**
     * Aktueller Standort mit hoher Genauigkeit; `null`, wenn das System keinen Fix liefern kann
     * (z.B. Standortdienste aus). Wirft [SecurityException], wenn die Foreground-Location-
     * Permission fehlt — der Aufrufer (Screen) stellt den Runtime-Permission-Flow sicher.
     */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): Location? = suspendCancellableCoroutine { continuation ->
        val cancellation = CancellationTokenSource()
        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
            .addOnSuccessListener { location -> continuation.resume(location) }
            .addOnFailureListener { error -> continuation.resumeWithException(error) }
        continuation.invokeOnCancellation { cancellation.cancel() }
    }
}
