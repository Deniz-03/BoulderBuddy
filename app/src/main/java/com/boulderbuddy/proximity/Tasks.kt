package com.boulderbuddy.proximity

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Minimaler Task→Coroutine-Adapter für die Play-Services-APIs (FusedLocation, Geofencing).
 * Bewusst selbst geschrieben statt `kotlinx-coroutines-play-services` zu ziehen —
 * das Feature braucht nur dieses eine Muster.
 */
internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}
