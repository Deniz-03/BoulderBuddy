package com.boulderbuddy.data.db.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boulderbuddy.data.db.BoulderBuddyDatabase
import com.boulderbuddy.data.db.createInMemoryDatabase
import com.boulderbuddy.data.db.entity.GymEntity
import com.boulderbuddy.data.db.entity.RouteEntity
import com.boulderbuddy.data.db.entity.SessionEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DAO-Tests für [RouteDao]. Fokus: [RouteDao.observeBySession] liefert nur die Routen der
 * angefragten Session, in Anlege-Reihenfolge (ORDER BY id).
 */
@RunWith(AndroidJUnit4::class)
class RouteDaoTest {

    private lateinit var db: BoulderBuddyDatabase
    private lateinit var routeDao: RouteDao
    private var sessionA = 0
    private var sessionB = 0

    @Before
    fun setUp() = runTest {
        db = createInMemoryDatabase()
        routeDao = db.routeDao()
        val gymId = db.gymDao().insert(GymEntity(name = "Testhalle")).toInt()
        sessionA = db.sessionDao().insert(SessionEntity(gymId = gymId, date = 1_000L)).toInt()
        sessionB = db.sessionDao().insert(SessionEntity(gymId = gymId, date = 2_000L)).toInt()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun observeBySession_returnsOnlyRoutesOfThatSession_inInsertionOrder() = runTest {
        val firstA = routeDao.insert(RouteEntity(sessionId = sessionA, name = "A1")).toInt()
        routeDao.insert(RouteEntity(sessionId = sessionB, name = "B1"))
        val secondA = routeDao.insert(RouteEntity(sessionId = sessionA, name = "A2")).toInt()

        val routesA = routeDao.observeBySession(sessionA).first()

        assertThat(routesA.map { it.id }).containsExactly(firstA, secondA).inOrder()
        assertThat(routesA.map { it.name }).containsExactly("A1", "A2").inOrder()
    }

    @Test
    fun observeBySession_emptyWhenNoRoutes() = runTest {
        assertThat(routeDao.observeBySession(sessionA).first()).isEmpty()
    }
}
