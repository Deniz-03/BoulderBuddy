package com.boulderbuddy.data.export

import android.content.Context
import android.net.Uri
import com.boulderbuddy.data.db.dao.GradeDao
import com.boulderbuddy.data.db.dao.GradeSystemDao
import com.boulderbuddy.data.db.dao.GymDao
import com.boulderbuddy.data.db.dao.RouteDao
import com.boulderbuddy.data.db.dao.SessionDao
import com.boulderbuddy.data.db.entity.RouteEntity
import com.boulderbuddy.data.db.entity.SessionEntity
import com.boulderbuddy.data.db.entity.hallenName
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Exportiert alle Sessions samt ihrer Routen als CSV (7.3b). Eine Zeile je Route; Sessions ohne
 * Routen bekommen eine Zeile mit leeren Route-Feldern, damit sie nicht verloren gehen.
 *
 * IDs werden gegen die Namen aufgelöst (Halle, Gradsystem, Grad-Label), damit das CSV ohne die
 * DB lesbar ist. Geschrieben wird in ein per Storage Access Framework gewähltes Dokument-[Uri].
 */
class SessionExporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sessionDao: SessionDao,
    private val routeDao: RouteDao,
    private val gradeDao: GradeDao,
    private val gradeSystemDao: GradeSystemDao,
    private val gymDao: GymDao,
) {

    /**
     * Schreibt den CSV-Gesamtexport in [uri] und gibt die Anzahl exportierter Sessions zurück.
     * Wirft, wenn der OutputStream nicht geöffnet werden kann (vom Aufrufer zu behandeln).
     */
    suspend fun exportCsv(uri: Uri): Int {
        val sessions = sessionDao.observeAll().first()
        val routesBySession = routeDao.observeAll().first().groupBy { it.sessionId }
        val gradeLabels = gradeDao.observeAll().first().associate { it.id to it.label }
        val systemNames = gradeSystemDao.observeAll().first().associate { it.id to it.name }
        val gymNames = gymDao.observeAll().first().associate { it.id to it.name }

        val csv = buildCsv(sessions, routesBySession, gradeLabels, systemNames, gymNames)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(csv.toByteArray(Charsets.UTF_8))
        } ?: error("OutputStream für $uri nicht verfügbar")
        return sessions.size
    }

    private fun buildCsv(
        sessions: List<SessionEntity>,
        routesBySession: Map<Int, List<RouteEntity>>,
        gradeLabels: Map<Int, String>,
        systemNames: Map<Int, String>,
        gymNames: Map<Int, String>,
    ): String {
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.GERMANY)
        val sb = StringBuilder()
        // BOM, damit Excel die UTF-8-Umlaute korrekt erkennt.
        sb.append('﻿')
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
    private fun escape(value: String): String {
        val needsQuote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuote) "\"$escaped\"" else escaped
    }

    private companion object {
        val HEADER = listOf(
            "session_id", "datum", "beendet", "halle", "gradsystem", "dauer_min", "session_notiz",
            "boulder", "sektor", "grad", "farbe", "versuche", "status", "boulder_notiz",
        )
        // Anzahl der Route-spezifischen Spalten (die letzten 7 der HEADER-Liste).
        const val ROUTE_COLUMNS = 7
    }
}
