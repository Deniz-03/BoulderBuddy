package com.boulderbuddy.proximity

import com.boulderbuddy.data.db.entity.GymVisitEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

// =============================================================================
// GymVisitStats — gelerntes Besuchsmuster einer Halle (Gym-Näherungs-Push, M0).
//
// Bewusst reine Kotlin-Logik ohne Android-Abhängigkeiten: Eingabe ist die rohe
// Besuchsliste + "jetzt", damit alles in JVM-Unit-Tests läuft. Die Push-Politik
// (M4) nutzt isTypicalSlot() nur zum PRIORISIEREN/DÄMPFEN — es ist kein hartes
// Gate, erste Ankünfte an einem neuen Gym sollen auch pushen.
// =============================================================================

/** Aggregiertes Besuchsmuster eines Gyms, berechnet aus den Roh-Besuchen ([fromVisits]). */
data class GymVisitStats(
    val totalVisits: Int = 0,
    /** Epoch-Millis des letzten Besuchs; `null` = nie besucht. */
    val lastVisitAt: Long? = null,
    /** Histogramm: Besuche je Wochentag. */
    val visitsByDayOfWeek: Map<DayOfWeek, Int> = emptyMap(),
    /** Histogramm: Besuche je Stunde (0–23, lokale Zeit). */
    val visitsByHour: Map<Int, Int> = emptyMap(),
) {

    /** Erst ab dieser Besuchszahl gilt ein Muster als belastbar (sonst kein "typischer Slot"). */
    val hasEstablishedPattern: Boolean
        get() = totalVisits >= MIN_VISITS_FOR_PATTERN

    /**
     * Trifft die Ankunft zum Zeitpunkt [now] ein "übliches" Zeitfenster dieses Gyms?
     *
     * Üblich = der Wochentag gehört zu den Top-Wochentagen UND die Stunde liegt in einem
     * Top-Stundenfenster (±1 h). Ohne belastbares Muster ([hasEstablishedPattern]) immer
     * `false` — die Politik behandelt diesen Fall separat (neue Gyms pushen trotzdem).
     */
    fun isTypicalSlot(now: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (!hasEstablishedPattern) return false
        val zoned = Instant.ofEpochMilli(now).atZone(zone)
        return isTypicalDay(zoned.dayOfWeek) && isTypicalHour(zoned.hour)
    }

    // Ein Wochentag ist typisch, wenn er mindestens die Hälfte des häufigsten Wochentags erreicht.
    private fun isTypicalDay(day: DayOfWeek): Boolean {
        val maxCount = visitsByDayOfWeek.values.maxOrNull() ?: return false
        val count = visitsByDayOfWeek[day] ?: 0
        return count > 0 && count * 2 >= maxCount
    }

    // Eine Stunde ist typisch, wenn ihr ±1-h-Fenster mindestens die Hälfte des stärksten
    // Stundenfensters erreicht (Fenster glättet Streuung um die übliche Ankunftszeit).
    private fun isTypicalHour(hour: Int): Boolean {
        val windowCount = hourWindowCount(hour)
        val maxWindow = (0..23).maxOf { hourWindowCount(it) }
        return windowCount > 0 && windowCount * 2 >= maxWindow
    }

    private fun hourWindowCount(hour: Int): Int =
        (-1..1).sumOf { offset -> visitsByHour[(hour + offset + 24) % 24] ?: 0 }

    companion object {
        /** Empirisch zu kalibrieren (M5): Mindest-Besuche, bevor ein Muster als belastbar gilt. */
        const val MIN_VISITS_FOR_PATTERN = 5

        /** Berechnet das Muster aus den Roh-Besuchen (pure function, JVM-testbar). */
        fun fromVisits(
            visits: List<GymVisitEntity>,
            zone: ZoneId = ZoneId.systemDefault(),
        ): GymVisitStats {
            if (visits.isEmpty()) return GymVisitStats()
            val zonedTimes = visits.map { Instant.ofEpochMilli(it.timestamp).atZone(zone) }
            return GymVisitStats(
                totalVisits = visits.size,
                lastVisitAt = visits.maxOf { it.timestamp },
                visitsByDayOfWeek = zonedTimes.groupingBy { it.dayOfWeek }.eachCount(),
                visitsByHour = zonedTimes.groupingBy { it.hour }.eachCount(),
            )
        }
    }
}

/** Kalendertag-Grenzen `[startInclusive, endExclusive)` in Epoch-Millis. */
data class DayBounds(val startInclusive: Long, val endExclusive: Long) {
    operator fun contains(timestamp: Long): Boolean =
        timestamp in startInclusive until endExclusive
}

/**
 * Grenzen des lokalen Kalendertags, in den [timestamp] fällt — Basis für den Tages-Dedupe
 * (pro Gym und Kalendertag höchstens ein [GymVisitEntity]).
 */
fun calendarDayBounds(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): DayBounds {
    val day = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    return DayBounds(
        startInclusive = day.atStartOfDay(zone).toInstant().toEpochMilli(),
        endExclusive = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
    )
}
