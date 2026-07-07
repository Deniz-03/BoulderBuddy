package com.boulderbuddy.data.repository

import com.boulderbuddy.data.db.dao.GymVisitDao
import com.boulderbuddy.data.db.entity.GymVisitEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * JVM-Tests für die Besuchs-Logging-Entscheidung (M3): der Tages-Dedupe von
 * [GymVisitRepositoryImpl.logVisit] — ein Besuch = ein Kalendertag, quellenunabhängig.
 * Läuft gegen einen In-Memory-Fake des DAOs (kein Android nötig).
 */
class GymVisitRepositoryTest {

    private class FakeGymVisitDao : GymVisitDao {
        val visits = MutableStateFlow<List<GymVisitEntity>>(emptyList())
        private var nextId = 1

        override suspend fun insert(visit: GymVisitEntity): Long {
            val id = nextId++
            visits.value = visits.value + visit.copy(id = id)
            return id.toLong()
        }

        override suspend fun getAllForGym(gymId: Int): List<GymVisitEntity> =
            visits.value.filter { it.gymId == gymId }.sortedBy { it.timestamp }

        override fun observeForGym(gymId: Int): Flow<List<GymVisitEntity>> =
            visits.map { list -> list.filter { it.gymId == gymId }.sortedBy { it.timestamp } }

        override suspend fun countForGymBetween(gymId: Int, from: Long, until: Long): Int =
            visits.value.count { it.gymId == gymId && it.timestamp in from until until }
    }

    private val dao = FakeGymVisitDao()
    private val repository = GymVisitRepositoryImpl(dao)

    // logVisit dedupliziert über den LOKALEN Kalendertag (systemDefault) — die Test-
    // Zeitstempel werden deshalb ebenfalls in der System-Zone gebaut.
    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun logVisit_firstVisitOfDay_isStored() = runTest {
        val stored = repository.logVisit(1, at(2026, 7, 7, 18), GymVisitEntity.SOURCE_GEOFENCE)

        assertThat(stored).isTrue()
        assertThat(dao.getAllForGym(1)).hasSize(1)
    }

    @Test
    fun logVisit_secondVisitSameDay_isDeduped_evenAcrossSources() = runTest {
        // Geofence-Ankunft am Nachmittag, Session-Start am Abend: EIN Besuch.
        repository.logVisit(1, at(2026, 7, 7, 16), GymVisitEntity.SOURCE_GEOFENCE)
        val stored = repository.logVisit(1, at(2026, 7, 7, 19), GymVisitEntity.SOURCE_SESSION)

        assertThat(stored).isFalse()
        assertThat(dao.getAllForGym(1)).hasSize(1)
    }

    @Test
    fun logVisit_nextCalendarDay_isStoredAgain() = runTest {
        // 23:30 und Folgetag 00:30 sind verschiedene Kalendertage — beide zählen.
        repository.logVisit(1, at(2026, 7, 7, 23, 30), GymVisitEntity.SOURCE_GEOFENCE)
        val stored = repository.logVisit(1, at(2026, 7, 8, 0, 30), GymVisitEntity.SOURCE_GEOFENCE)

        assertThat(stored).isTrue()
        assertThat(dao.getAllForGym(1)).hasSize(2)
    }

    @Test
    fun logVisit_differentGymsSameDay_areIndependent() = runTest {
        repository.logVisit(1, at(2026, 7, 7, 16), GymVisitEntity.SOURCE_GEOFENCE)
        val stored = repository.logVisit(2, at(2026, 7, 7, 19), GymVisitEntity.SOURCE_GEOFENCE)

        assertThat(stored).isTrue()
        assertThat(dao.getAllForGym(1)).hasSize(1)
        assertThat(dao.getAllForGym(2)).hasSize(1)
    }
}
