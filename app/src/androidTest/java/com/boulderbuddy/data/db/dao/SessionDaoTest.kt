package com.boulderbuddy.data.db.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boulderbuddy.data.db.BoulderBuddyDatabase
import com.boulderbuddy.data.db.createInMemoryDatabase
import com.boulderbuddy.data.db.entity.GymEntity
import com.boulderbuddy.data.db.entity.SessionEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DAO-Tests für [SessionDao] gegen eine echte (In-Memory) Room-DB. Fokus: der Aktiv-Marker
 * `endedAt IS NULL` ([SessionDao.observeActive]) und die Row-ID-Rückgabe von [SessionDao.insert].
 */
@RunWith(AndroidJUnit4::class)
class SessionDaoTest {

    private lateinit var db: BoulderBuddyDatabase
    private lateinit var sessionDao: SessionDao
    private var gymId = 0

    @Before
    fun setUp() = runTest {
        db = createInMemoryDatabase()
        sessionDao = db.sessionDao()
        // Sessions brauchen eine Halle (FK).
        gymId = db.gymDao().insert(GymEntity(name = "Testhalle")).toInt()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insert_returnsRowId_andGetByIdReturnsSession() = runTest {
        val id = sessionDao.insert(SessionEntity(gymId = gymId, date = 1_000L)).toInt()

        assertThat(id).isGreaterThan(0)
        val loaded = sessionDao.getById(id)
        assertThat(loaded).isNotNull()
        assertThat(loaded!!.date).isEqualTo(1_000L)
    }

    @Test
    fun observeActive_returnsOnlySessionWithNullEndedAt() = runTest {
        // Beendete Session.
        sessionDao.insert(SessionEntity(gymId = gymId, date = 1_000L, endedAt = 2_000L))
        // Aktive Session.
        val activeId = sessionDao.insert(SessionEntity(gymId = gymId, date = 3_000L)).toInt()

        val active = sessionDao.observeActive().first()
        assertThat(active).isNotNull()
        assertThat(active!!.id).isEqualTo(activeId)
    }

    @Test
    fun endSession_clearsActiveMarker() = runTest {
        val id = sessionDao.insert(SessionEntity(gymId = gymId, date = 1_000L)).toInt()
        assertThat(sessionDao.observeActive().first()).isNotNull()

        sessionDao.endSession(id, endedAt = 5_000L)

        assertThat(sessionDao.observeActive().first()).isNull()
        assertThat(sessionDao.getById(id)!!.endedAt).isEqualTo(5_000L)
    }

    @Test
    fun observeActive_returnsNewestActiveSession() = runTest {
        sessionDao.insert(SessionEntity(gymId = gymId, date = 1_000L))
        val newerId = sessionDao.insert(SessionEntity(gymId = gymId, date = 9_000L)).toInt()

        // ORDER BY date DESC LIMIT 1 → die neuere aktive Session.
        assertThat(sessionDao.observeActive().first()!!.id).isEqualTo(newerId)
    }
}
