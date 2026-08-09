package com.boulderbuddy.ui.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Die Körnung der Verlaufs-Diagramme: ein Punkt je Woche, Monat oder Jahr.
 *
 * [eimer] ist die Anzahl der dargestellten Zeitabschnitte. Sie ist bewusst je Stufe
 * verschieden: acht Wochen sind gut zwei Monate und damit ein Trainingsblock, zwölf Monate
 * ein Jahr, fünf Jahre die Spanne, über die sich beim Klettern überhaupt etwas ändert. Eine
 * einheitliche Zahl hätte entweder zu wenige Wochen oder unlesbar viele Jahre ergeben.
 */
enum class Zeitraum(val label: String, val eimer: Int) {
    Woche("Wochen", 8),
    Monat("Monate", 12),
    Jahr("Jahre", 5),
}

/**
 * Der Anfang des Zeitabschnitts, in den [tag] fällt — Montag der Woche, Monatserster,
 * Jahresanfang. Das ist der Schlüssel, unter dem Routen einsortiert werden.
 */
fun eimerStart(tag: LocalDate, zeitraum: Zeitraum): LocalDate = when (zeitraum) {
    // Montag statt Sonntag: ISO-8601 und die Woche, wie sie hier gelesen wird.
    Zeitraum.Woche -> tag.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    Zeitraum.Monat -> tag.withDayOfMonth(1)
    Zeitraum.Jahr -> tag.withDayOfYear(1)
}

/**
 * Die dargestellte Reihe von Zeitabschnitten, **ältester zuerst**, endend mit dem Abschnitt,
 * in dem [heute] liegt.
 *
 * Die Reihe ist vollständig: Abschnitte ohne jede Aktivität stehen mit drin. Genau darum
 * geht es bei „unkumuliert über die Zeit" — eine Woche ohne Klettern ist eine Aussage und
 * darf nicht einfach fehlen, sonst rücken zwei Sessions optisch zusammen, zwischen denen
 * ein Monat lag.
 */
fun eimerReihe(heute: LocalDate, zeitraum: Zeitraum): List<LocalDate> {
    val letzter = eimerStart(heute, zeitraum)
    return (zeitraum.eimer - 1 downTo 0).map { zurueck ->
        when (zeitraum) {
            Zeitraum.Woche -> letzter.minusWeeks(zurueck.toLong())
            Zeitraum.Monat -> letzter.minusMonths(zurueck.toLong())
            Zeitraum.Jahr -> letzter.minusYears(zurueck.toLong())
        }
    }
}

private val wochenFormat = DateTimeFormatter.ofPattern("d.M.", Locale.GERMAN)
private val monatsFormat = DateTimeFormatter.ofPattern("MMM", Locale.GERMAN)

/** Kurze Beschriftung unter einem Datenpunkt. */
fun eimerLabel(start: LocalDate, zeitraum: Zeitraum): String = when (zeitraum) {
    // Das Datum des Montags — kürzer und eindeutiger als eine Kalenderwochen-Nummer, die
    // kaum jemand im Kopf hat.
    Zeitraum.Woche -> start.format(wochenFormat)
    // Auf drei Zeichen gekappt: `MMM` liefert auf Deutsch bis zu fünf („Sept.", „März"),
    // und bei zwölf Spalten auf Telefonbreite bricht das um — die Spalte wird zweizeilig
    // und ihr Balken sitzt höher als die Nachbarn. „Jan Feb Mär … Dez" bleibt eindeutig.
    Zeitraum.Monat -> start.format(monatsFormat).take(3)
    Zeitraum.Jahr -> start.year.toString()
}
