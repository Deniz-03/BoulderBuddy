package com.boulderbuddy.proximity

import android.util.Log
import com.boulderbuddy.data.db.entity.GymVisitEntity
import com.boulderbuddy.data.repository.GymRepository
import com.boulderbuddy.data.repository.GymVisitRepository
import com.boulderbuddy.data.repository.SessionRepository
import com.boulderbuddy.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verarbeitet eine Geofence-DWELL-Ankunft an einem Gym (Gym-Näherungs-Push).
 *
 * Vom [GeofenceBroadcastReceiver] via Hilt-EntryPoint aufgerufen (Receiver können nicht
 * direkt injizieren). M3: physische Ankünfte werden als Besuch geloggt (auch ohne
 * Session-Start — Entscheidung §1.4). M4: danach entscheidet die
 * [ProximityNotificationPolicy], ob die "Session starten?"-Notification gezeigt wird.
 */
@Singleton
class ProximityEventHandler @Inject constructor(
    private val gymVisitRepository: GymVisitRepository,
    private val gymRepository: GymRepository,
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository,
    private val pushStateStore: ProximityPushStateStore,
    private val notifier: ProximityNotifier,
) {
    suspend fun onGymDwell(gymId: Int, timestamp: Long) {
        val gym = gymRepository.getById(gymId) ?: return

        // Besuch loggen — Tages-Dedupe übernimmt das Repository (ein Besuch = ein Kalendertag).
        gymVisitRepository.logVisit(
            gymId = gymId,
            timestamp = timestamp,
            source = GymVisitEntity.SOURCE_GEOFENCE,
        )

        // Push-Politik (M4): gedrosselt, musterbewusst, session-bewusst.
        val decision = ProximityNotificationPolicy.decide(
            masterEnabled = settingsRepository.proximityAlertsEnabled.first(),
            gymAlertsEnabled = gym.proximityAlertsEnabled,
            hasActiveSession = sessionRepository.observeActive().first() != null,
            lastSessionEndedAt = sessionRepository.observeAll().first()
                .filter { it.gymId == gymId }
                .mapNotNull { it.endedAt }
                .maxOrNull(),
            lastNotifiedAt = pushStateStore.lastNotifiedAt(gymId),
            stats = gymVisitRepository.getStats(gymId),
            now = timestamp,
        )
        Log.d(TAG, "DWELL an Gym $gymId (${gym.name}) → $decision")

        if (decision == PolicyDecision.NOTIFY) {
            notifier.showSessionReminder(gymId = gym.id, gymName = gym.name)
            pushStateStore.setLastNotifiedAt(gymId, timestamp)
        }
    }

    private companion object {
        const val TAG = "ProximityEventHandler"
    }
}
