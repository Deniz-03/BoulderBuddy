package com.boulderbuddy.data.repository

import com.boulderbuddy.data.db.dao.GymVisitDao
import com.boulderbuddy.data.db.entity.GymVisitEntity
import com.boulderbuddy.proximity.GymVisitStats
import com.boulderbuddy.proximity.calendarDayBounds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import javax.inject.Inject

/**
 * Kapselt das Besuchs-Log ([GymVisitEntity]) und das daraus berechnete Besuchsmuster
 * ([GymVisitStats]) für den Gym-Näherungs-Push.
 */
interface GymVisitRepository {
    /**
     * Loggt einen Besuch (Geofence-Ankunft oder Session-Start) mit Tages-Dedupe:
     * existiert für dieses Gym am selben Kalendertag schon ein Besuch, wird kein zweiter
     * angelegt (ein Besuch = ein Tag). Gibt `true` zurück, wenn ein Besuch gespeichert wurde.
     */
    suspend fun logVisit(gymId: Int, timestamp: Long, source: String): Boolean

    /** Aktuelles Besuchsmuster eines Gyms (einmalig berechnet). */
    suspend fun getStats(gymId: Int): GymVisitStats

    /** Besuchsmuster eines Gyms, live aus den Roh-Besuchen abgeleitet. */
    fun observeStats(gymId: Int): Flow<GymVisitStats>
}

class GymVisitRepositoryImpl @Inject constructor(
    private val gymVisitDao: GymVisitDao,
) : GymVisitRepository {

    override suspend fun logVisit(gymId: Int, timestamp: Long, source: String): Boolean {
        val day = calendarDayBounds(timestamp)
        val alreadyVisitedToday =
            gymVisitDao.countForGymBetween(gymId, day.startInclusive, day.endExclusive) > 0
        if (alreadyVisitedToday) return false
        gymVisitDao.insert(GymVisitEntity(gymId = gymId, timestamp = timestamp, source = source))
        return true
    }

    override suspend fun getStats(gymId: Int): GymVisitStats =
        GymVisitStats.fromVisits(gymVisitDao.getAllForGym(gymId), ZoneId.systemDefault())

    override fun observeStats(gymId: Int): Flow<GymVisitStats> =
        gymVisitDao.observeForGym(gymId).map { visits ->
            GymVisitStats.fromVisits(visits, ZoneId.systemDefault())
        }
}
