package com.boulderbuddy.proximity

import com.boulderbuddy.data.db.entity.GymVisitEntity
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * JVM-Tests für das gelernte Besuchsmuster ([GymVisitStats]) und die Kalendertag-Grenzen
 * ([calendarDayBounds]) — der ohne Hardware demonstrierbar korrekte Kern des Näherungs-Push.
 */
class GymVisitStatsTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")

    /** Besuch am gegebenen lokalen Zeitpunkt (Berlin). */
    private fun visitAt(
        year: Int = 2026,
        month: Int = 6,
        day: Int,
        hour: Int,
        minute: Int = 0,
    ): GymVisitEntity = GymVisitEntity(
        gymId = 1,
        timestamp = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone)
            .toInstant().toEpochMilli(),
        source = GymVisitEntity.SOURCE_GEOFENCE,
    )

    private fun millisOf(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    // --- fromVisits: Histogramme ---------------------------------------------------------

    @Test
    fun fromVisits_emptyList_returnsEmptyStats() {
        val stats = GymVisitStats.fromVisits(emptyList(), zone)

        assertThat(stats.totalVisits).isEqualTo(0)
        assertThat(stats.lastVisitAt).isNull()
        assertThat(stats.visitsByDayOfWeek).isEmpty()
        assertThat(stats.visitsByHour).isEmpty()
        assertThat(stats.hasEstablishedPattern).isFalse()
    }

    @Test
    fun fromVisits_buildsHistogramsAndLastVisit() {
        // 2026-06-02 ist ein Dienstag; 06-04 ein Donnerstag.
        val visits = listOf(
            visitAt(day = 2, hour = 18),   // Di 18h
            visitAt(day = 9, hour = 19),   // Di 19h
            visitAt(day = 4, hour = 18),   // Do 18h
        )

        val stats = GymVisitStats.fromVisits(visits, zone)

        assertThat(stats.totalVisits).isEqualTo(3)
        assertThat(stats.lastVisitAt).isEqualTo(visits[1].timestamp) // 09.06. ist der späteste
        assertThat(stats.visitsByDayOfWeek).containsExactly(
            DayOfWeek.TUESDAY, 2,
            DayOfWeek.THURSDAY, 1,
        )
        assertThat(stats.visitsByHour).containsExactly(18, 2, 19, 1)
    }

    // --- isTypicalSlot -------------------------------------------------------------------

    @Test
    fun isTypicalSlot_withoutEstablishedPattern_isFalse() {
        // Nur 4 Besuche (< MIN_VISITS_FOR_PATTERN = 5) → kein belastbares Muster.
        val visits = listOf(2, 9, 16, 23).map { visitAt(day = it, hour = 18) } // 4× Di 18h
        val stats = GymVisitStats.fromVisits(visits, zone)

        assertThat(stats.hasEstablishedPattern).isFalse()
        assertThat(stats.isTypicalSlot(millisOf(2026, 6, 30, 18), zone)).isFalse()
    }

    @Test
    fun isTypicalSlot_matchingDayAndHour_isTrue() {
        // 5× Dienstag um 18 Uhr → Di/18h ist DER typische Slot.
        val visits = listOf(2, 9, 16, 23, 30).map { visitAt(day = it, hour = 18) }
        val stats = GymVisitStats.fromVisits(visits, zone)

        // Dienstag, 07.07.2026, 18 Uhr → typisch. Auch 19 Uhr (±1-h-Fenster) noch typisch.
        assertThat(stats.isTypicalSlot(millisOf(2026, 7, 7, 18), zone)).isTrue()
        assertThat(stats.isTypicalSlot(millisOf(2026, 7, 7, 19), zone)).isTrue()
    }

    @Test
    fun isTypicalSlot_wrongDay_isFalse() {
        val visits = listOf(2, 9, 16, 23, 30).map { visitAt(day = it, hour = 18) } // 5× Di 18h
        val stats = GymVisitStats.fromVisits(visits, zone)

        // Freitag (10.07.2026) 18 Uhr: Stunde passt, Wochentag nicht.
        assertThat(stats.isTypicalSlot(millisOf(2026, 7, 10, 18), zone)).isFalse()
    }

    @Test
    fun isTypicalSlot_wrongHour_isFalse() {
        val visits = listOf(2, 9, 16, 23, 30).map { visitAt(day = it, hour = 18) } // 5× Di 18h
        val stats = GymVisitStats.fromVisits(visits, zone)

        // Dienstag 8 Uhr morgens: Wochentag passt, Stunde weit weg vom 18-h-Fenster.
        assertThat(stats.isTypicalSlot(millisOf(2026, 7, 7, 8), zone)).isFalse()
    }

    @Test
    fun isTypicalSlot_secondaryDayWithHalfTheVisits_isStillTypical() {
        // 4× Di 18h + 2× Do 18h: Do erreicht die Hälfte des Maximums → auch typisch.
        val visits =
            listOf(2, 9, 16, 23).map { visitAt(day = it, hour = 18) } +
                listOf(4, 11).map { visitAt(day = it, hour = 18) }
        val stats = GymVisitStats.fromVisits(visits, zone)

        assertThat(stats.isTypicalSlot(millisOf(2026, 7, 9, 18), zone)).isTrue() // Do 18h
    }

    // --- calendarDayBounds (Tages-Dedupe) --------------------------------------------------

    @Test
    fun calendarDayBounds_containsSameDayButNotNeighbours() {
        val noon = millisOf(2026, 7, 7, 12)
        val bounds = calendarDayBounds(noon, zone)

        assertThat(bounds.startInclusive).isEqualTo(millisOf(2026, 7, 7, 0))
        assertThat(bounds.endExclusive).isEqualTo(millisOf(2026, 7, 8, 0))
        assertThat(millisOf(2026, 7, 7, 0) in bounds).isTrue()      // Mitternacht gehört zum Tag
        assertThat(millisOf(2026, 7, 7, 23, 59) in bounds).isTrue()
        assertThat(millisOf(2026, 7, 8, 0) in bounds).isFalse()     // nächster Tag exklusiv
        assertThat(millisOf(2026, 7, 6, 23, 59) in bounds).isFalse()
    }

    @Test
    fun calendarDayBounds_lateEveningAndNextMorning_areDifferentDays() {
        // Besuch 23:30 und Besuch am Folgetag 00:30 dürfen NICHT dedupliziert werden.
        val evening = calendarDayBounds(millisOf(2026, 7, 7, 23, 30), zone)
        val nextMorning = millisOf(2026, 7, 8, 0, 30)

        assertThat(nextMorning in evening).isFalse()
    }
}
