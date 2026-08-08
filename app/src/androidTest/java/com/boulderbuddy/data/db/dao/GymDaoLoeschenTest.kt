package com.boulderbuddy.data.db.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boulderbuddy.data.db.BoulderBuddyDatabase
import com.boulderbuddy.data.db.createInMemoryDatabase
import com.boulderbuddy.data.db.entity.GymEntity
import com.boulderbuddy.data.db.entity.SessionEntity
import com.boulderbuddy.data.db.entity.hallenName
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Das Versprechen des Lösch-Dialogs: „Sessions bleiben erhalten — mit allen Bouldern **und dem
 * Hallennamen**."
 *
 * Der Name ist der Teil, der stillschweigend fehlte. `session.gymName` existiert seit DB v10 als
 * Beleg, der eine Löschung überdauert, wurde beim Anlegen einer Session aber nie gefüllt — nur
 * die Seed-Session brachte ihn mit. Am Gerät hieß deshalb jede selbst angelegte Session nach dem
 * Löschen ihrer Halle „Unbekannte Halle", während der Beispieldatensatz unauffällig blieb und den
 * Fehler verdeckte.
 *
 * Diese Tests sichern das Fangnetz in [GymDao.deleteAndKeepName] ab: **auch eine Session ohne
 * gefüllten `gymName` behält den Namen**, denn Zeilen aus der Zeit vor der Korrektur gibt es
 * weiterhin.
 */
@RunWith(AndroidJUnit4::class)
class GymDaoLoeschenTest {

    private lateinit var db: BoulderBuddyDatabase
    private lateinit var gymDao: GymDao
    private lateinit var sessionDao: SessionDao

    @Before
    fun setUp() {
        db = createInMemoryDatabase()
        gymDao = db.gymDao()
        sessionDao = db.sessionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun loeschen_sichert_den_hallennamen_in_sessions_ohne_namen() = runTest {
        val gymId = gymDao.insert(GymEntity(name = "Boulder World München")).toInt()
        // Wie alle Sessions, die vor der Korrektur entstanden sind: gymName leer.
        val sessionId = sessionDao.insert(SessionEntity(gymId = gymId, date = 1_000L)).toInt()
        assertThat(sessionDao.getById(sessionId)!!.gymName).isEmpty()

        gymDao.deleteAndKeepName(gymId)

        val session = sessionDao.getById(sessionId)!!
        // Die Session überlebt, ihr Verweis auf die Halle wird zu null (SET NULL) …
        assertThat(session.gymId).isNull()
        // … und der Name steht jetzt in ihr selbst.
        assertThat(session.gymName).isEqualTo("Boulder World München")
        // Das ist der Wert, den die Oberfläche anzeigt — nicht „Unbekannte Halle".
        assertThat(session.hallenName { null }).isEqualTo("Boulder World München")
    }

    @Test
    fun loeschen_uebernimmt_den_aktuellen_namen_nach_einer_umbenennung() = runTest {
        val gymId = gymDao.insert(GymEntity(name = "Alter Name")).toInt()
        val sessionId = sessionDao.insert(
            SessionEntity(gymId = gymId, gymName = "Alter Name", date = 1_000L),
        ).toInt()
        gymDao.update(GymEntity(id = gymId, name = "Neuer Name"))

        gymDao.deleteAndKeepName(gymId)

        // Solange die Halle existiert, schlägt eine Umbenennung auf alte Sessions durch
        // (`hallenName` bevorzugt die Halle). Erhalten bleiben muss deshalb der Name, den der
        // Nutzer zuletzt gesehen hat — nicht der von der Session-Erstellung.
        assertThat(sessionDao.getById(sessionId)!!.gymName).isEqualTo("Neuer Name")
    }

    @Test
    fun loeschen_laesst_sessions_anderer_hallen_unberuehrt() = runTest {
        val geloescht = gymDao.insert(GymEntity(name = "Geht weg")).toInt()
        val bleibt = gymDao.insert(GymEntity(name = "Bleibt")).toInt()
        val fremdeSession = sessionDao.insert(
            SessionEntity(gymId = bleibt, date = 2_000L),
        ).toInt()

        gymDao.deleteAndKeepName(geloescht)

        val session = sessionDao.getById(fremdeSession)!!
        assertThat(session.gymId).isEqualTo(bleibt)
        // Kein Streuschaden: der Name der anderen Halle wird nicht vorsorglich eingetragen.
        assertThat(session.gymName).isEmpty()
    }
}
