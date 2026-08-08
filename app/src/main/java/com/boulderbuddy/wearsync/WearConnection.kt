package com.boulderbuddy.wearsync

import android.content.Context
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Meldet, ob gerade eine Uhr mit dem Phone verbunden ist.
 *
 * Als Interface hinterlegt, damit ViewModels ohne Play Services getestet werden können —
 * der Data Layer kommt nur in [WearNodeConnection] vor.
 */
interface WearConnection {
    /** `true`, sobald mindestens ein Node verbunden ist. */
    val connected: Flow<Boolean>
}

/**
 * Fragt den Verbindungsstatus über den Wear Data Layer ab (NodeClient).
 *
 * Der Data Layer bietet keinen Push für „Node kam/ging", ohne dass die Wear-App eine Capability
 * deklariert — deshalb bewusst ein schlanker Poll, solange jemand den Flow sammelt. Die Abfrage
 * ist lokal (kein Netz) und der Flow läuft nur auf sichtbaren Screens.
 */
@Singleton
class WearNodeConnection @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : WearConnection {

    override val connected: Flow<Boolean> = flow {
        // Sofort einen Wert liefern: Sammler, die diesen Flow mit anderen kombinieren, sollen
        // nicht auf die erste Node-Abfrage warten müssen (bzw. hängen, falls Play Services fehlt).
        emit(false)
        while (true) {
            emit(queryConnected())
            delay(POLL_INTERVAL_MS)
        }
    }.distinctUntilChanged()

    private suspend fun queryConnected(): Boolean = suspendCancellableCoroutine { continuation ->
        Wearable.getNodeClient(context).connectedNodes
            .addOnSuccessListener { nodes ->
                if (continuation.isActive) continuation.resume(nodes.isNotEmpty())
            }
            // Kein Play-Services / keine Uhr gekoppelt → schlicht "nicht verbunden".
            .addOnFailureListener {
                if (continuation.isActive) continuation.resume(false)
            }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 5_000L
    }
}
