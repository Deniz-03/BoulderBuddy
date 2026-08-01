package com.boulderbuddy.data.repository

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
 * Repository-Tests für [SessionRepositoryImpl] über die echte Room-DB. Prüft die Kernlogik der
 * Datenschicht: [SessionRepository.create] gibt die neue ID zurück, [SessionRepository.endSession]
 * beendet die aktive Session ([SessionRepository.observeActive] fällt auf `null`).
 */
@RunWith(AndroidJUnit4::class)
class SessionRepositoryTest {

    private lateinit var db: BoulderBuddyDatabase
    private lateinit var repository: SessionRepository
    private var gymId = 0

    @Before
    fun setUp() = runTest {
        db = createInMemoryDatabase()
        repository = SessionRepositoryImpl(db.sessionDao())
        gymId = db.gymDao().insert(GymEntity(name = "Testhalle")).toInt()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun create_returnsNewId_andSessionBecomesActive() = runTest {
        val id = repository.create(SessionEntity(gymId = gymId, date = 1_000L))

        assertThat(id).isGreaterThan(0)
        assertThat(repository.getById(id)).isNotNull()
        assertThat(repository.observeActive().first()?.id).isEqualTo(id)
    }

    @Test
    fun endSession_setsEndedAt_soNoActiveSessionRemains() = runTest {
        val id = repository.create(SessionEntity(gymId = gymId, date = 1_000L))

        repository.endSession(id, endedAt = 7_000L)

        assertThat(repository.observeActive().first()).isNull()
        assertThat(repository.getById(id)?.endedAt).isEqualTo(7_000L)
    }
}
