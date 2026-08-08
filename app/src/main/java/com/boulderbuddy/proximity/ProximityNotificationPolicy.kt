package com.boulderbuddy.proximity

import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Ergebnis der Push-Politik — als benannter Grund statt Boolean, damit Tests und Logging
 * nachvollziehen können, WARUM (nicht) gepusht wurde.
 */
enum class PolicyDecision {
    /** Alle Regeln erfüllt → Notification zeigen. */
    NOTIFY,

    /** Master-Toggle oder Pro-Gym-Toggle aus. */
    DISABLED,

    /** Eine Session läuft gerade — der Nutzer ist offensichtlich schon dabei. */
    ACTIVE_SESSION,

    /** Die letzte Session an diesem Gym endete gerade eben (Nach-Session-Ruhe). */
    POST_SESSION_QUIET,

    /** Für dieses Gym wurde innerhalb des Cooldowns schon gepusht (max 1×/Tag). */
    COOLDOWN,

    /** Untypischer Zeitslot bei belastbarem Muster → verlängerter Cooldown griff. */
    UNTYPICAL_SLOT_COOLDOWN,
}

/**
 * Smart-Push-Politik des Gym-Näherungs-Push (M4, Entscheidung §1.3): gedrosselt,
 * musterbewusst, unterdrückt bei aktiver/kürzlich beendeter Session — NICHT bei jeder
 * Geofence-Ankunft.
 *
 * Bewusst pure Kotlin (Eingaben als Werte, keine Android-/Repository-Abhängigkeit),
 * damit die komplette Politik in JVM-Unit-Tests läuft. Das Besuchsmuster moduliert nur:
 * ein untypischer Slot VERLÄNGERT den Cooldown, ist aber kein hartes Gate — an einem
 * neuen Gym ohne belastbares Muster wird normal gepusht.
 */
object ProximityNotificationPolicy {

    /** Basis-Cooldown pro Gym: max ein Push pro 24 h. Empirisch zu kalibrieren (M5). */
    val BASE_COOLDOWN_MILLIS: Long = TimeUnit.HOURS.toMillis(24)

    /**
     * Verlängerter Cooldown, wenn die Ankunft NICHT ins gelernte Muster passt (Dämpfung):
     * gepusht wird dann höchstens alle 72 h. Empirisch zu kalibrieren (M5).
     */
    val UNTYPICAL_SLOT_COOLDOWN_MILLIS: Long = TimeUnit.HOURS.toMillis(72)

    /** Nach-Session-Ruhe: so lange nach Ende der letzten Session an diesem Gym kein Push. */
    val POST_SESSION_QUIET_MILLIS: Long = TimeUnit.HOURS.toMillis(3)

    fun decide(
        /** Master-Toggle "Gym-Erinnerungen" (Einstellungen). */
        masterEnabled: Boolean,
        /** Pro-Gym-Toggle "Erinnerungen für diese Halle" (Gym-Editor). */
        gymAlertsEnabled: Boolean,
        /** Läuft gerade eine (beliebige) Session? */
        hasActiveSession: Boolean,
        /** Ende der letzten beendeten Session an DIESEM Gym; `null` = nie. */
        lastSessionEndedAt: Long?,
        /** Letzter Push für DIESES Gym; `null` = nie gepusht. */
        lastNotifiedAt: Long?,
        /** Gelerntes Besuchsmuster dieses Gyms. */
        stats: GymVisitStats,
        /** Zeitpunkt der Ankunft (epoch millis). */
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): PolicyDecision {
        if (!masterEnabled || !gymAlertsEnabled) return PolicyDecision.DISABLED
        if (hasActiveSession) return PolicyDecision.ACTIVE_SESSION
        if (lastSessionEndedAt != null && now - lastSessionEndedAt < POST_SESSION_QUIET_MILLIS) {
            return PolicyDecision.POST_SESSION_QUIET
        }

        // Muster-Modulation: untypischer Slot bei belastbarem Muster → längerer Cooldown.
        val untypicalSlot = stats.hasEstablishedPattern && !stats.isTypicalSlot(now, zone)
        val cooldown = if (untypicalSlot) UNTYPICAL_SLOT_COOLDOWN_MILLIS else BASE_COOLDOWN_MILLIS

        if (lastNotifiedAt != null && now - lastNotifiedAt < cooldown) {
            return if (untypicalSlot && now - lastNotifiedAt >= BASE_COOLDOWN_MILLIS) {
                PolicyDecision.UNTYPICAL_SLOT_COOLDOWN
            } else {
                PolicyDecision.COOLDOWN
            }
        }
        return PolicyDecision.NOTIFY
    }
}
