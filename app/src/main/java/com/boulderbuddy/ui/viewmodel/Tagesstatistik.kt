package com.boulderbuddy.ui.viewmodel

import com.boulderbuddy.data.db.entity.GradeEntity
import com.boulderbuddy.data.db.entity.RouteEntity
import com.boulderbuddy.data.db.entity.SessionEntity
import com.boulderbuddy.ui.model.istGetoppt
import com.boulderbuddy.ui.model.toLocalDate
import java.time.LocalDate

// =============================================================================
// Tagesstatistik — wie ein einzelner Klettertag verlaufen ist
// =============================================================================
//
// Die Verlaufs-Diagramme im Statistik-Tab fassen zu Wochen, Monaten und Jahren zusammen und
// zeigen je Abschnitt den HÖCHSTEN Grad. Das beantwortet „wie entwickle ich mich", nicht
// „wie lief dieser Tag". Für den einzelnen Tag ist gerade die Reihenfolge die Aussage:
// aufgewärmt, hochgearbeitet, oben gescheitert oder eben nicht.
//
// **Die x-Achse ist die Reihenfolge, nicht die Uhrzeit.** Ein Boulder trägt keinen
// Zeitstempel (siehe `RouteEntity`); was es gibt, ist die Reihenfolge des Anlegens, und die
// ist laut `RouteDao` die des Kletterns. Für „erst V3, dann V5, zuletzt am V7 gescheitert"
// reicht das vollständig — eine echte Zeitachse bräuchte eine Schema-Änderung und würde nur
// Wartezeiten zwischen den Zügen sichtbar machen.
//
// **Je Gradsystem getrennt**, wie bei den anderen Verläufen: `order` ist nur innerhalb eines
// Systems eine Reihenfolge, und eine Kurve, die V4 mit 6b+ verbindet, behauptet einen
// Vergleich, den es nicht gibt.

/** Ein Boulder in der Kletterreihenfolge des Tages. */
data class TagesBoulderUi(
    /** 1-basierte Position innerhalb des Tages — die x-Achse. */
    val position: Int,
    val gradLabel: String,
    /** Ordnungszahl des Grades; die y-Achse rechnet damit, angezeigt wird [gradLabel]. */
    val gradOrder: Int,
    val getoppt: Boolean,
    /** Getoppt im ersten Versuch. Eigene Markierung, weil es die Aussage des Tages verändert. */
    val flash: Boolean,
    val versuche: Int,
)

/** Die Zahlen über dem Verlauf. Alle bereits als Text — der Screen rechnet nicht. */
data class TagesKennzahlenUi(
    val tops: String,
    val versuche: String,
    val topGrad: String,
    /**
     * Flashes im Verhältnis zu den Tops, etwa „2/3" — bewusst KEIN Prozentwert.
     *
     * Auf einen einzelnen Tag angewandt ist eine Quote irreführend: bei drei Tops gibt es nur
     * 0 %, 33 %, 67 % und 100 %, und „33 %" klingt nach einer Messung, wo „1 von 3" schlicht
     * die Wahrheit ist. Nebenbei passt das Verhältnis auch in eine schmale Karte, die
     * Prozentzahl tat es nicht.
     */
    val flash: String,
)

/** Verlauf und Zahlen eines Tages in EINEM Gradsystem. */
data class TagesstatistikUi(
    val boulder: List<TagesBoulderUi>,
    val kennzahlen: TagesKennzahlenUi,
)

/**
 * Baut je Gradsystem einen Tagesverlauf aus den Routen — in der Reihenfolge, in der sie
 * hereinkommen. Der Aufrufer sortiert also, nicht diese Funktion: in der Session ist das die
 * Reihenfolge des Anlegens, über mehrere Einheiten eines Tages die der Sessions.
 *
 * **Routen ohne Grad fallen heraus**, samt ihrer Versuche. Ohne Grad gibt es keine
 * y-Position und keine Systemzugehörigkeit — der Boulder gehört nirgendwohin. Die
 * Gesamtzahlen der Session-Ansicht zählen ihn weiterhin mit; die Zahlen hier beschreiben
 * ausdrücklich das, was auch in der Kurve steht.
 */
internal fun tagesstatistikJeSystem(
    routen: List<RouteEntity>,
    gradesById: Map<Int, GradeEntity>,
): Map<Int, TagesstatistikUi> {
    val mitGrad = routen.mapNotNull { route ->
        val grade = route.gradeId?.let(gradesById::get) ?: return@mapNotNull null
        grade.systemId to route
    }

    return mitGrad
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, systemRouten) ->
            val boulder = systemRouten.mapIndexed { index, route ->
                val grade = gradesById.getValue(route.gradeId!!)
                val getoppt = route.status.istGetoppt
                TagesBoulderUi(
                    position = index + 1,
                    gradLabel = grade.label,
                    gradOrder = grade.order,
                    getoppt = getoppt,
                    // Flash nur, wenn auch getoppt: ein Projekt mit einem Versuch ist kein
                    // Flash, sondern ein einziger Versuch.
                    flash = getoppt && route.attempts <= 1,
                    versuche = route.attempts,
                )
            }
            TagesstatistikUi(boulder = boulder, kennzahlen = kennzahlen(boulder))
        }
}

private fun kennzahlen(boulder: List<TagesBoulderUi>): TagesKennzahlenUi {
    val tops = boulder.filter { it.getoppt }
    return TagesKennzahlenUi(
        tops = tops.size.toString(),
        versuche = boulder.sumOf { it.versuche }.toString(),
        // Der höchste GETOPPTE Grad. Ein Projekt, an dem man gescheitert ist, gehört nicht
        // in eine Zahl, die „geschafft" bedeutet — es steht als offener Punkt in der Kurve.
        topGrad = tops.maxByOrNull { it.gradOrder }?.gradLabel ?: PLATZHALTER,
        // Ohne Top gibt es kein Verhältnis — „0/0" wäre keine Aussage, sondern ein Bruch.
        flash = if (tops.isEmpty()) PLATZHALTER else "${tops.count { it.flash }}/${tops.size}",
    )
}

/** Gedankenstrich statt „0" oder leerem Feld — es gibt den Wert nicht, er ist nicht null. */
private const val PLATZHALTER = "–"

/** Ein Klettertag in der Auswahlleiste des Statistik-Tabs. */
data class TagUi(val datum: LocalDate, val label: String)

/**
 * Dieselbe Auswertung, aber über einen ganzen Kalendertag statt über eine Session — für den
 * Statistik-Tab. Wer vormittags und abends klettert, bekommt hier EINEN Verlauf.
 *
 * Die Reihenfolge entsteht aus zwei Schlüsseln: erst die Session nach ihrem Startzeitpunkt,
 * darin die Route nach ihrer `id`. Beides zusammen ergibt die Reihenfolge des Kletterns über
 * den Tag — der einzige verfügbare Ersatz für Zeitstempel, die es an der Route nicht gibt.
 *
 * Geliefert werden nur die [maxTage] jüngsten Tage MIT Aktivität. Ein Tag ohne Klettern hat
 * keinen Verlauf, und die Auswahlleiste soll Klettertage anbieten, keinen Kalender.
 */
internal fun tagesstatistiken(
    routen: List<RouteEntity>,
    sessionsById: Map<Int, SessionEntity>,
    gradesById: Map<Int, GradeEntity>,
    maxTage: Int,
): Map<LocalDate, Map<Int, TagesstatistikUi>> {
    data class Eintrag(val tag: LocalDate, val sessionStart: Long, val route: RouteEntity)

    return routen
        .mapNotNull { route ->
            val session = sessionsById[route.sessionId] ?: return@mapNotNull null
            Eintrag(session.date.toLocalDate(), session.date, route)
        }
        .groupBy { it.tag }
        .entries
        .sortedByDescending { it.key }
        .take(maxTage)
        .associate { (tag, eintraege) ->
            val sortiert = eintraege
                .sortedWith(compareBy({ it.sessionStart }, { it.route.id }))
                .map { it.route }
            tag to tagesstatistikJeSystem(sortiert, gradesById)
        }
        // Tage, an denen kein einziger Boulder einen Grad trug, ergeben eine leere Karte —
        // sie stünden als Chip in der Leiste und zeigten dahinter nichts.
        .filterValues { it.isNotEmpty() }
}
