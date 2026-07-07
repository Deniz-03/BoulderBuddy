package com.boulderbuddy.proximity

import com.boulderbuddy.data.db.entity.GymVisitEntity
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * JVM-Tests für die Smart-Push-Politik (M4): Toggles, Session-Unterdrückung,
 * Cooldown (24 h) und die Muster-Dämpfung (untypischer Slot → 72 h) — komplett ohne Android.
 */
class ProximityNotificationPolicyTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")

    // Dienstag, 07.07.2026, 18:00 lokal — passt zum Muster der [typicalStats].
    private val tuesdayEvening: Long =
        ZonedDateTime.of(2026, 7, 7, 18, 0, 0, 0, zone).toInstant().toEpochMilli()

    // Freitag, 10.07.2026, 08:00 lokal — passt NICHT zum Muster.
    private val fridayMorning: Long =
        ZonedDateTime.of(2026, 7, 10, 8, 0, 0, 0, zone).toInstant().toEpochMilli()

    // 5× Dienstag 18 Uhr → belastbares Muster mit Di/18h als typischem Slot.
    private val typicalStats: GymVisitStats = GymVisitStats.fromVisits(
        listOf(2, 9, 16, 23, 30).map { day ->
            GymVisitEntity(
                gymId = 1,
                timestamp = ZonedDateTime.of(2026, 6, day, 18, 0, 0, 0, zone)
                    .toInstant().toEpochMilli(),
                source = GymVisitEntity.SOURCE_GEOFENCE,
            )
        },
        zone,
    )

    private fun decide(
        masterEnabled: Boolean = true,
        gymAlertsEnabled: Boolean = true,
        hasActiveSession: Boolean = false,
        lastSessionEndedAt: Long? = null,
        lastNotifiedAt: Long? = null,
        stats: GymVisitStats = GymVisitStats(),
        now: Long = tuesdayEvening,
    ): PolicyDecision = ProximityNotificationPolicy.decide(
        masterEnabled = masterEnabled,
        gymAlertsEnabled = gymAlertsEnabled,
        hasActiveSession = hasActiveSession,
        lastSessionEndedAt = lastSessionEndedAt,
        lastNotifiedAt = lastNotifiedAt,
        stats = stats,
        now = now,
        zone = zone,
    )

    @Test
    fun notify_whenAllRulesPass_andNoPattern() {
        // Neues Gym ohne Muster: pusht trotzdem (kein hartes Muster-Gate).
        assertThat(decide()).isEqualTo(PolicyDecision.NOTIFY)
    }

    @Test
    fun disabled_whenMasterToggleOff() {
        assertThat(decide(masterEnabled = false)).isEqualTo(PolicyDecision.DISABLED)
    }

    @Test
    fun disabled_whenGymToggleOff() {
        assertThat(decide(gymAlertsEnabled = false)).isEqualTo(PolicyDecision.DISABLED)
    }

    @Test
    fun suppressed_whileSessionIsActive() {
        assertThat(decide(hasActiveSession = true)).isEqualTo(PolicyDecision.ACTIVE_SESSION)
    }

    @Test
    fun suppressed_shortlyAfterLastSessionAtThisGym() {
        val oneHourAgo = tuesdayEvening - TimeUnit.HOURS.toMillis(1)
        assertThat(decide(lastSessionEndedAt = oneHourAgo))
            .isEqualTo(PolicyDecision.POST_SESSION_QUIET)
    }

    @Test
    fun notify_whenLastSessionEndedLongAgo() {
        val yesterday = tuesdayEvening - TimeUnit.HOURS.toMillis(26)
        assertThat(decide(lastSessionEndedAt = yesterday)).isEqualTo(PolicyDecision.NOTIFY)
    }

    @Test
    fun cooldown_whenNotifiedWithin24Hours() {
        val sixHoursAgo = tuesdayEvening - TimeUnit.HOURS.toMillis(6)
        assertThat(decide(lastNotifiedAt = sixHoursAgo)).isEqualTo(PolicyDecision.COOLDOWN)
    }

    @Test
    fun notify_whenCooldownExpired() {
        val twoDaysAgo = tuesdayEvening - TimeUnit.HOURS.toMillis(48)
        assertThat(decide(lastNotifiedAt = twoDaysAgo)).isEqualTo(PolicyDecision.NOTIFY)
    }

    @Test
    fun typicalSlot_usesBaseCooldown() {
        // Di 18h ist der typische Slot → normaler 24-h-Cooldown, 25 h Abstand reicht.
        val yesterdayEvening = tuesdayEvening - TimeUnit.HOURS.toMillis(25)
        assertThat(decide(stats = typicalStats, lastNotifiedAt = yesterdayEvening))
            .isEqualTo(PolicyDecision.NOTIFY)
    }

    @Test
    fun untypicalSlot_extendsCooldown() {
        // Fr 8h ist untypisch: 25 h seit letztem Push reichen NICHT (72-h-Dämpfung).
        val twentyFiveHoursAgo = fridayMorning - TimeUnit.HOURS.toMillis(25)
        assertThat(decide(stats = typicalStats, lastNotifiedAt = twentyFiveHoursAgo, now = fridayMorning))
            .isEqualTo(PolicyDecision.UNTYPICAL_SLOT_COOLDOWN)
    }

    @Test
    fun untypicalSlot_notifiesAfterExtendedCooldown() {
        val fourDaysAgo = fridayMorning - TimeUnit.HOURS.toMillis(96)
        assertThat(decide(stats = typicalStats, lastNotifiedAt = fourDaysAgo, now = fridayMorning))
            .isEqualTo(PolicyDecision.NOTIFY)
    }

    @Test
    fun untypicalSlot_withoutPriorPush_stillNotifies() {
        // Dämpfung wirkt nur über den Cooldown — ohne früheren Push wird auch im
        // untypischen Slot gepusht (kein hartes Gate).
        assertThat(decide(stats = typicalStats, lastNotifiedAt = null, now = fridayMorning))
            .isEqualTo(PolicyDecision.NOTIFY)
    }
}
