package com.boulderbuddy.wearsync

import android.util.Log
import com.boulderbuddy.data.db.entity.HangboardSessionEntity
import com.boulderbuddy.data.repository.HangboardSessionRepository
import com.boulderbuddy.data.repository.SessionRepository
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Empfängt einen auf der Uhr abgeschlossenen Hangboard-Durchlauf (Wear Data Layer, MessageClient)
 * und trägt ihn — **falls auf dem Phone eine Session aktiv ist** — in genau diese Session.
 *
 * Das spiegelt `HangboardTimerViewModel.recordWorkout()`: gibt es keine aktive Session, wird
 * nichts geschrieben (kein FK-Ziel, kein Clutter). Der Timer auf der Uhr läuft trotzdem eigenständig.
 *
 * Vertrag mit der Uhr: `com.boulderbuddy.wear.data.WearSyncContract`
 * (Pfad + Payload-Format identisch dupliziert, da getrennte Module).
 */
@AndroidEntryPoint
class HangboardWearListenerService : WearableListenerService() {

    @Inject lateinit var sessionRepository: SessionRepository
    @Inject lateinit var hangboardSessionRepository: HangboardSessionRepository

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != PATH_HANGBOARD_COMPLETED) return
        val run = parse(String(event.data, Charsets.UTF_8)) ?: run {
            Log.w(TAG, "Ungültige Payload: ${String(event.data, Charsets.UTF_8)}")
            return
        }
        // onMessageReceived läuft bereits auf einem Hintergrund-Thread → runBlocking ist ok.
        runBlocking {
            val sessionId = sessionRepository.observeActive().first()?.id
            if (sessionId == null) {
                Log.d(TAG, "Keine aktive Session — Uhr-Durchlauf wird nicht getrackt.")
                return@runBlocking
            }
            hangboardSessionRepository.create(
                HangboardSessionEntity(
                    sessionId = sessionId,
                    completedSets = run.completedSets,
                    totalSets = run.totalSets,
                    hangSec = run.hangSec,
                    restSec = run.restSec,
                    date = run.date,
                )
            )
            Log.d(TAG, "Uhr-Durchlauf in Session $sessionId getrackt.")
        }
    }

    private data class WearRun(
        val completedSets: Int,
        val totalSets: Int,
        val hangSec: Int,
        val restSec: Int,
        val date: Long,
    )

    /** Payload = "completedSets;totalSets;hangSec;restSec;date". */
    private fun parse(payload: String): WearRun? {
        val parts = payload.split(';')
        if (parts.size != 5) return null
        return try {
            WearRun(
                completedSets = parts[0].toInt(),
                totalSets = parts[1].toInt(),
                hangSec = parts[2].toInt(),
                restSec = parts[3].toInt(),
                date = parts[4].toLong(),
            )
        } catch (e: NumberFormatException) {
            null
        }
    }

    private companion object {
        const val TAG = "WearListener"
        // Muss mit WearSyncContract.PATH_HANGBOARD_COMPLETED der Uhr übereinstimmen.
        const val PATH_HANGBOARD_COMPLETED = "/boulderbuddy/hangboard_completed"
    }
}
