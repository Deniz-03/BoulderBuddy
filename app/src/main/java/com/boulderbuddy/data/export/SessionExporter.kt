package com.boulderbuddy.data.export

import android.content.Context
import android.net.Uri
import com.boulderbuddy.data.db.dao.GradeDao
import com.boulderbuddy.data.db.dao.GradeSystemDao
import com.boulderbuddy.data.db.dao.GymDao
import com.boulderbuddy.data.db.dao.RouteDao
import com.boulderbuddy.data.db.dao.SessionDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Exportiert alle Sessions samt ihrer Routen als CSV (7.3b): liest die DAOs und schreibt in ein
 * per Storage Access Framework gewähltes Dokument-[Uri].
 *
 * Die Zerlegung selbst steht in [SessionCsv] — ohne Android, damit sie ohne Gerät prüfbar ist.
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

        val csv = SessionCsv.baue(sessions, routesBySession, gradeLabels, systemNames, gymNames)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(csv.toByteArray(Charsets.UTF_8))
        } ?: error("OutputStream für $uri nicht verfügbar")
        return sessions.size
    }
}
