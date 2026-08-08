package com.boulderbuddy.data.repository

import com.boulderbuddy.data.db.dao.GymDao
import com.boulderbuddy.data.db.entity.GymEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Kapselt den Zugriff auf [GymEntity]-Daten (Hallen).
 *
 * Speist die Gym-Auswahl bei der Session-Erstellung und die Gym-CRUD in den Einstellungen.
 */
interface GymRepository {
    /** Alle Hallen, alphabetisch nach Name. */
    fun observeAll(): Flow<List<GymEntity>>

    /** Einzelne Halle laden; `null`, wenn keine mit [gymId] existiert. */
    suspend fun getById(gymId: Int): GymEntity?

    /** Legt eine Halle an und gibt ihre neue ID zurück. */
    suspend fun create(gym: GymEntity): Int

    /** Aktualisiert eine bestehende Halle. */
    suspend fun update(gym: GymEntity)

    /**
     * Löscht eine Halle. Sessions und ihre Boulder bleiben erhalten — sie tragen den
     * Hallennamen selbst (`session.gymName`, ab DB v10).
     */
    suspend fun delete(gymId: Int)

    /** Anzahl der Sessions an dieser Halle — Grundlage der Rückfrage vor dem Löschen. */
    suspend fun countSessions(gymId: Int): Int
}

class GymRepositoryImpl @Inject constructor(
    private val gymDao: GymDao,
) : GymRepository {

    override fun observeAll(): Flow<List<GymEntity>> = gymDao.observeAll()

    override suspend fun getById(gymId: Int): GymEntity? = gymDao.getById(gymId)

    override suspend fun create(gym: GymEntity): Int = gymDao.insert(gym).toInt()

    override suspend fun update(gym: GymEntity) = gymDao.update(gym)

    override suspend fun delete(gymId: Int) = gymDao.deleteById(gymId)

    override suspend fun countSessions(gymId: Int): Int = gymDao.countSessions(gymId)
}
