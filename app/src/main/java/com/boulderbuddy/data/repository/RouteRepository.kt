package com.boulderbuddy.data.repository

import com.boulderbuddy.data.db.dao.RouteDao
import com.boulderbuddy.data.db.entity.RouteEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Kapselt den Zugriff auf [RouteEntity]-Daten (geloggte Boulder/Routen).
 *
 * [observeBySession] speist SessionDetail/AlteSession, [observeAll] die Boulder-Übersicht.
 */
interface RouteRepository {
    /** Routen einer Session, in Anlege-Reihenfolge. */
    fun observeBySession(sessionId: Int): Flow<List<RouteEntity>>

    /** Alle Routen (für die Boulder-Übersicht), neueste zuerst. */
    fun observeAll(): Flow<List<RouteEntity>>

    /** Einzelne Route laden; `null`, wenn keine mit [routeId] existiert. */
    suspend fun getById(routeId: Int): RouteEntity?

    /** Legt eine Route an und gibt ihre neue ID zurück. */
    suspend fun create(route: RouteEntity): Int

    /** Aktualisiert eine bestehende Route (Status, Versuche, Notiz, Media, …). */
    suspend fun update(route: RouteEntity)
}

class RouteRepositoryImpl @Inject constructor(
    private val routeDao: RouteDao,
) : RouteRepository {

    override fun observeBySession(sessionId: Int): Flow<List<RouteEntity>> =
        routeDao.observeBySession(sessionId)

    override fun observeAll(): Flow<List<RouteEntity>> = routeDao.observeAll()

    override suspend fun getById(routeId: Int): RouteEntity? = routeDao.getById(routeId)

    override suspend fun create(route: RouteEntity): Int = routeDao.insert(route).toInt()

    override suspend fun update(route: RouteEntity) = routeDao.update(route)
}
