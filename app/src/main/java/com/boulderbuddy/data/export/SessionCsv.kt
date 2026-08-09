package com.boulderbuddy.data.export

import com.boulderbuddy.data.db.entity.RouteEntity
import com.boulderbuddy.data.db.entity.SessionEntity
import com.boulderbuddy.data.db.entity.hallenName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Erzeugt den CSV-Text des Session-Exports — bewusst ohne Android, damit die Zerlegung (Quoting,
 * BOM, aufgelöste Namen, Sessions ohne Routen) in der JVM prüfbar ist statt nur am Gerät.
 *
 * [SessionExporter] bleibt der Teil, der DAOs liest und in ein SAF-[android.net.Uri] schreibt.
 */
object SessionCsv {

    val HEADER = listOf(
        "session_id", "datum", "beendet", "halle", "gradsystem", "dauer_min", "session_notiz",
        "boulder", "sektor", "grad", "farbe", "versuche", "status", "boulder_notiz",
    )

    /** Anzahl der Route-spezifischen Spalten (die letzten 7 der [HEADER]-Liste). */
    private const val ROUTE_COLUMNS = 7

    /**
     * Excel erkennt UTF-8 nur mit BOM — ohne das stehen die Umlaute falsch in der Tabelle.
     *
     * Als Escape geschrieben, nicht als Zeichen: ein echtes BOM mitten in einer Quelldatei ist
     * unsichtbar, und Werkzeuge (Lint, Editoren, `git diff`) melden es zu Recht als Verdacht.
     */
    private const val BOM = '\uFEFF'

    /**
     * Eine Zeile je Route; Sessions ohne Routen bekommen eine Zeile mit leeren Route-Feldern,
     * damit sie nicht verloren gehen. IDs werden gegen Namen aufgelöst, damit das CSV ohne die
     * DB lesbar ist.
     *
     * @param zeitzone bestimmt, wie Zeitstempel formatiert werden — als Parameter, damit der
     *   Test nicht von der Zeitzone des Rechners abhängt, auf dem er läuft.
     */
    fun baue(
        sessions: List<SessionEntity>,
        routesBySession: Map<Int, List<RouteEntity>>,
        gradeLabels: Map<Int, String>,
        systemNames: Map<Int, String>,
        gymNames: Map<Int, String>,
        zeitzone: TimeZone = TimeZone.getDefault(),
    ): String {
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.GERMANY).apply { timeZone = zeitzone }
        val sb = StringBuilder()
        sb.append(BOM)
        sb.append(HEADER.joinToString(",") { escape(it) }).append("\r\n")

        for (session in sessions) {
            val base = listOf(
                session.id.toString(),
                df.format(Date(session.date)),
                session.endedAt?.let { df.format(Date(it)) } ?: "aktiv",
                // Der Export ist ein Beleg: eine gelöschte Halle darf hier keine leere Zelle
                // hinterlassen, sonst fehlt im CSV, wo trainiert wurde.
                session.hallenName { gymNames[it] }.orEmpty(),
                session.gradeSystemId?.let { systemNames[it] }.orEmpty(),
                session.durationMin?.toString().orEmpty(),
                session.notes.orEmpty(),
            )
            val routes = routesBySession[session.id].orEmpty()
            if (routes.isEmpty()) {
                sb.append((base + List(ROUTE_COLUMNS) { "" }).joinToString(",") { escape(it) })
                    .append("\r\n")
            } else {
                for (route in routes) {
                    val row = base + listOf(
                        route.name,
                        route.sektor.orEmpty(),
                        route.gradeId?.let { gradeLabels[it] }.orEmpty(),
                        route.color.orEmpty(),
                        route.attempts.toString(),
                        route.status.name,
                        route.notes.orEmpty(),
                    )
                    sb.append(row.joinToString(",") { escape(it) }).append("\r\n")
                }
            }
        }
        return sb.toString()
    }

    // RFC 4180: Felder mit Komma/Anführungszeichen/Zeilenumbruch werden gequotet, " verdoppelt.
    // Ein Semikolon braucht bei Komma-Trennung keine Quotes — das ist kein Versehen.
    private fun escape(value: String): String {
        val needsQuote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuote) "\"$escaped\"" else escaped
    }
}
