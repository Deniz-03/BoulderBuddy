package com.boulderbuddy.wearsync

import android.util.Log
import com.boulderbuddy.data.db.entity.HangboardSegmentEntity
import com.boulderbuddy.data.db.entity.HangboardWorkoutEntity
import com.boulderbuddy.data.db.entity.HangboardWorkoutMode
import com.boulderbuddy.data.db.entity.HangboardWorkoutOrigin
import com.boulderbuddy.data.repository.HangboardWorkoutRepository
import com.boulderbuddy.data.repository.SessionRepository
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Empfängt einen auf der Uhr abgeschlossenen Hangboard-Durchlauf (Wear Data Layer, MessageClient)
 * und speichert ihn **immer** als Hangboard-Workout (§0 Säule 2): Läuft auf dem Phone eine
 * Session, wird er an sie gehängt, sonst als eigenständiges Training (`sessionId = null`).
 * Die Verknüpfungs-Entscheidung fällt hier — beim Persistieren, nicht auf der Uhr.
 *
 * Vertrag mit der Uhr: `com.boulderbuddy.wear.data.WearSyncContract`
 * (Pfad + Payload-Format identisch dupliziert, da getrennte Module).
 */
@AndroidEntryPoint
class HangboardWearListenerService : WearableListenerService() {

    @Inject lateinit var sessionRepository: SessionRepository
    @Inject lateinit var hangboardWorkoutRepository: HangboardWorkoutRepository

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != PATH_HANGBOARD_COMPLETED) return
        val run = parse(String(event.data, Charsets.UTF_8)) ?: run {
            Log.w(TAG, "Ungültige Payload: ${String(event.data, Charsets.UTF_8)}")
            return
        }
        // onMessageReceived läuft bereits auf einem Hintergrund-Thread → runBlocking ist ok.
        runBlocking {
            val sessionId = sessionRepository.observeActive().first()?.id
            // Segmente aus der Vorgabe ableiten (manueller Uhr-Timer, letzter Satz ohne Pause).
            val segments = List(run.completedSets) { i ->
                HangboardSegmentEntity(
                    workoutId = 0,
                    setIndex = i,
                    hangMs = run.hangSec * 1000L,
                    restMs = if (i < run.completedSets - 1) run.restSec * 1000L else 0L,
                )
            }
            // Dauer des Durchlaufs rückrechnen — die Uhr überträgt nur den Abschluss-Zeitpunkt.
            val durationMs = segments.sumOf { it.hangMs + it.restMs }
            hangboardWorkoutRepository.create(
                HangboardWorkoutEntity(
                    sessionId = sessionId,
                    mode = HangboardWorkoutMode.MANUAL,
                    origin = HangboardWorkoutOrigin.WATCH,
                    startedAt = run.date - durationMs,
                    endedAt = run.date,
                    plannedSets = run.totalSets,
                    plannedHangSec = run.hangSec,
                    plannedRestSec = run.restSec,
                ),
                segments,
            )
            Log.d(
                TAG,
                if (sessionId != null) "Uhr-Workout in Session $sessionId gespeichert."
                else "Uhr-Workout als eigenständiges Training gespeichert.",
            )
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
