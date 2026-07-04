package com.boulderbuddy.data.db.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boulderbuddy.data.db.BoulderBuddyDatabase
import com.boulderbuddy.data.db.createInMemoryDatabase
import com.boulderbuddy.data.db.entity.GradeEntity
import com.boulderbuddy.data.db.entity.GradeSystemEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DAO-Tests für [GradeDao]. Fokus: [GradeDao.observeBySystem] liefert die Grade eines Systems
 * nach `sortOrder` sortiert (unabhängig von der Einfüge-Reihenfolge).
 */
@RunWith(AndroidJUnit4::class)
class GradeDaoTest {

    private lateinit var db: BoulderBuddyDatabase
    private lateinit var gradeDao: GradeDao
    private var systemId = 0

    @Before
    fun setUp() = runTest {
        db = createInMemoryDatabase()
        gradeDao = db.gradeDao()
        // Globales System (gymId = null erlaubt seit Schema v3).
        systemId = db.gradeSystemDao().insert(GradeSystemEntity(name = "Französisch")).toInt()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun observeBySystem_returnsGradesOrderedBySortOrder() = runTest {
        // Bewusst in "falscher" Reihenfolge einfügen.
        gradeDao.insert(GradeEntity(systemId = systemId, label = "6b", order = 2))
        gradeDao.insert(GradeEntity(systemId = systemId, label = "5c", order = 0))
        gradeDao.insert(GradeEntity(systemId = systemId, label = "6a", order = 1))

        val labels = gradeDao.observeBySystem(systemId).first().map { it.label }

        assertThat(labels).containsExactly("5c", "6a", "6b").inOrder()
    }

    @Test
    fun observeBySystem_filtersBySystem() = runTest {
        val otherSystem = db.gradeSystemDao().insert(GradeSystemEntity(name = "V-Scale")).toInt()
        gradeDao.insert(GradeEntity(systemId = systemId, label = "6a", order = 0))
        gradeDao.insert(GradeEntity(systemId = otherSystem, label = "V3", order = 0))

        val labels = gradeDao.observeBySystem(systemId).first().map { it.label }

        assertThat(labels).containsExactly("6a")
    }
}
