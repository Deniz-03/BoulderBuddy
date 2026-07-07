package com.boulderbuddy.data.db.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boulderbuddy.data.db.BoulderBuddyDatabase
import com.boulderbuddy.data.db.createInMemoryDatabase
import com.boulderbuddy.data.db.entity.GymEntity
import com.boulderbuddy.data.db.entity.GymVisitEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DAO-Tests für [GymVisitDao] gegen eine echte (In-Memory) Room-DB (Gym-Näherungs-Push, M0).
 * Fokus: Zeitfenster-Zählung ([GymVisitDao.countForGymBetween], Basis des Tages-Dedupe)
 * und die Trennung der Besuche nach Gym.
 */
@RunWith(AndroidJUnit4::class)
class GymVisitDaoTest {

    private lateinit var db: BoulderBuddyDatabase
    private lateinit var gymVisitDao: GymVisitDao
    private var gymId = 0
    private var otherGymId = 0

    @Before
    fun setUp() = runTest {
        db = createInMemoryDatabase()
        gymVisitDao = db.gymVisitDao()
        gymId = db.gymDao().insert(GymEntity(name = "Testhalle")).toInt()
        otherGymId = db.gymDao().insert(GymEntity(name = "Andere Halle")).toInt()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun visit(gym: Int = gymId, at: Long) =
        GymVisitEntity(gymId = gym, timestamp = at, source = GymVisitEntity.SOURCE_GEOFENCE)

    @Test
    fun insert_andGetAllForGym_returnsOnlyThatGymsVisitsInOrder() = runTest {
        gymVisitDao.insert(visit(at = 2_000L))
        gymVisitDao.insert(visit(at = 1_000L))
        gymVisitDao.insert(visit(gym = otherGymId, at = 1_500L))

        val visits = gymVisitDao.getAllForGym(gymId)

        assertThat(visits).hasSize(2)
        assertThat(visits.map { it.timestamp }).containsExactly(1_000L, 2_000L).inOrder()
        assertThat(visits.all { it.gymId == gymId }).isTrue()
    }

    @Test
    fun countForGymBetween_countsHalfOpenInterval() = runTest {
        gymVisitDao.insert(visit(at = 999L))    // vor dem Fenster
        gymVisitDao.insert(visit(at = 1_000L))  // Start inklusiv
        gymVisitDao.insert(visit(at = 1_999L))  // im Fenster
        gymVisitDao.insert(visit(at = 2_000L))  // Ende exklusiv

        val count = gymVisitDao.countForGymBetween(gymId, from = 1_000L, until = 2_000L)

        assertThat(count).isEqualTo(2)
    }

    @Test
    fun countForGymBetween_ignoresOtherGyms() = runTest {
        gymVisitDao.insert(visit(gym = otherGymId, at = 1_500L))

        assertThat(gymVisitDao.countForGymBetween(gymId, 0L, 10_000L)).isEqualTo(0)
    }

    @Test
    fun observeForGym_emitsInsertedVisits() = runTest {
        gymVisitDao.insert(visit(at = 1_000L))

        val observed = gymVisitDao.observeForGym(gymId).first()

        assertThat(observed).hasSize(1)
        assertThat(observed.first().source).isEqualTo(GymVisitEntity.SOURCE_GEOFENCE)
    }
}
