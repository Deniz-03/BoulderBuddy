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

/** Datum → "12. Juni" (gleiche Schreibweise wie die millis-Variante). */
fun formatDayMonth(date: LocalDate): String = date.format(dayMonthFormatter)

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

private val uhrzeitFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)

/**
 * Startzeitpunkt als „seit"-Angabe: heute nur die Uhrzeit, sonst mit dem Tag davor.
 *
 * Bewusst der Zeitpunkt und keine laufende Dauer: der Wert wird beim Aufbau des Zustands
 * berechnet und aktualisiert sich nicht von selbst. Eine Dauer, die stehen bleibt, während die
 * Zeit weiterläuft, wäre nach ein paar Minuten schlicht falsch — eine Uhrzeit bleibt richtig.
 */
fun formatSeit(millis: Long, today: LocalDate = LocalDate.now()): String {
    val zeit = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val uhrzeit = zeit.format(uhrzeitFormatter)
    return when (val tag = formatRelativeDay(millis, today)) {
        "Heute" -> "seit $uhrzeit"
        else -> "seit $tag, $uhrzeit"
    }
}

/**
 * Der Tag einer Session, bei einer laufenden ergänzt um den Hinweis, dass sie noch läuft.
 *
 * Vorher stand an beiden Aufrufstellen fest verdrahtet „Heute · läuft gerade" — der Tag wurde
 * für eine laufende Session gar nicht erst angesehen. Eine Session, die man abends startet und
 * nicht beendet, behauptete damit auch drei Tage später noch, sie sei von heute. Am Gerät mit
 * einer zwei Tage alten, weiterhin laufenden Session nachgestellt: Home und die Sessions-Liste
 * sagten „Heute", die Detailansicht rechnete daneben korrekt „Läuft · 48:33 h".
 */
fun formatSessionTag(
    millis: Long,
    laeuftNoch: Boolean,
    today: LocalDate = LocalDate.now(),
): String {
    val tag = formatRelativeDay(millis, today)
    return if (laeuftNoch) "$tag · läuft gerade" else tag
}

/**
 * Dauer in Millisekunden als kurze Stundenangabe, z.B. "1.5h". Unter einer Stunde
 * werden Minuten gezeigt ("45min").
 *
 * Gedacht für **Session-Dauern** (Größenordnung Stunden). Für Hängezeiten am Hangboard
 * ist [formatHangTime] zuständig — dort sind Sekunden die relevante Einheit.
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

/**
 * Hängezeit in Millisekunden. Unter einer Minute in Sekunden ("30s"), darüber als
 * Minuten mit Sekunden ("1:10min").
 *
 * Eigene Funktion statt [formatDurationShort]: ein kurzer Hangboard-Durchlauf liegt im
 * Sekundenbereich und würde beim Abrunden auf ganze Minuten als "0min" erscheinen.
 */
fun formatHangTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    if (totalSeconds < 60) return "${totalSeconds}s"
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02dmin".format(minutes, seconds)
}
