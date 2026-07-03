package com.boulderbuddy.ui.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.boulderbuddy.data.model.RouteStatus
import com.boulderbuddy.ui.screens.BoulderStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// =============================================================================
// UiMappers — die in Phase 5.5 auf Phase 6 verschobene Mapping-Schicht.
//
// Übersetzt Datenschicht-Werte (Entities/Enums) in das, was die Screens brauchen:
//   - Grade-Hexfarbe → Compose-Color
//   - RouteStatus (OPEN/SENT/PROJECT/SKIP) → UI-BoulderStatus (TOP/FLASH/PROJEKT)
//   - epoch-millis → deutsche Datums-Strings
//
// Bewusst zentral, damit die Regeln (z.B. "Flash = SENT mit attempts == 1") nur
// an EINER Stelle stehen und nicht in jedem ViewModel neu erfunden werden.
// =============================================================================

/**
 * Parst einen Hex-Farbstring in eine Compose-[Color].
 * Akzeptiert `#RRGGBB` und `#AARRGGBB` (mit oder ohne führendes `#`).
 * Fällt bei ungültigem Wert auf ein neutrales Grau zurück, statt zu werfen —
 * eine kaputte Farbe soll nie die ganze Liste crashen.
 */
fun parseHexColor(hex: String): Color {
    val cleaned = hex.trim().removePrefix("#")
    val value = cleaned.toLongOrNull(16) ?: return FallbackGrey
    return when (cleaned.length) {
        6 -> Color(0xFF000000L or value)  // RGB → volle Deckkraft ergänzen
        8 -> Color(value)                 // AARRGGBB → direkt übernehmen
        else -> FallbackGrey
    }
}

private val FallbackGrey = Color(0xFF888888)

/** Compose-[Color] → "#RRGGBB"-Hexstring (ohne Alpha), passend zu [parseHexColor]. */
fun Color.toHexRgb(): String = "#%06X".format(0xFFFFFF and toArgb())

/**
 * Bildet den persistierten [RouteStatus] auf die abgeleitete UI-Darstellung
 * [BoulderStatus] ab. [attempts] entscheidet über Flash vs. Top.
 *
 * Regel: SENT mit höchstens einem Versuch = Flash, sonst Top. Alles nicht
 * Getoppte (PROJECT/OPEN/SKIP) erscheint als Projekt.
 */
fun RouteStatus.toBoulderStatus(attempts: Int): BoulderStatus = when (this) {
    RouteStatus.SENT -> if (attempts <= 1) BoulderStatus.FLASH else BoulderStatus.TOP
    RouteStatus.PROJECT -> BoulderStatus.PROJEKT
    RouteStatus.OPEN -> BoulderStatus.PROJEKT
    RouteStatus.SKIP -> BoulderStatus.PROJEKT
}

/** `true`, wenn der Status als "geschafft" zählt (Top oder Flash). */
val RouteStatus.istGetoppt: Boolean get() = this == RouteStatus.SENT

// --- Datum ------------------------------------------------------------------

private val dayMonthFormatter = DateTimeFormatter.ofPattern("d. MMMM", Locale.GERMAN)

/** epoch-millis → lokales Datum in der Zeitzone des Geräts. */
fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

/** epoch-millis → "12. Juni". */
fun formatDayMonth(millis: Long): String = millis.toLocalDate().format(dayMonthFormatter)

/**
 * Menschliche Kurzform relativ zu [today]: "Heute", "Gestern" oder "12. Juni".
 * [today] injizierbar für deterministische Tests/Previews.
 */
fun formatRelativeDay(millis: Long, today: LocalDate = LocalDate.now()): String {
    val date = millis.toLocalDate()
    return when (date) {
        today -> "Heute"
        today.minusDays(1) -> "Gestern"
        else -> date.format(dayMonthFormatter)
    }
}

/**
 * Dauer in Millisekunden als kurze Stundenangabe, z.B. "1.5h". Unter einer Stunde
 * werden Minuten gezeigt ("45min").
 */
fun formatDurationShort(millis: Long): String {
    val totalMinutes = (millis / 60_000).coerceAtLeast(0)
    return if (totalMinutes < 60) {
        "${totalMinutes}min"
    } else {
        val hours = totalMinutes / 60.0
        String.format(Locale.GERMAN, "%.1fh", hours)
    }
}
