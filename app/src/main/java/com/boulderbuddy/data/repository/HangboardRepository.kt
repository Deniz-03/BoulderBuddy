package com.boulderbuddy.data.repository

import com.boulderbuddy.data.db.dao.HangboardTemplateDao
import com.boulderbuddy.data.db.entity.HangboardTemplateEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Kapselt den Zugriff auf [HangboardTemplateEntity]-Daten (Timer-Voreinstellungen).
 *
 * Should-Have: Der Timer läuft in Phase 6.9 zunächst ohne Templates; dieses Repository stellt
 * sie bereit, sobald sie angebunden werden.
 */
interface HangboardRepository {
    /** Alle Timer-Templates, alphabetisch nach Name. */
    fun observeAll(): Flow<List<HangboardTemplateEntity>>

    /** Legt ein Template an und gibt seine neue ID zurück. */
    suspend fun create(template: HangboardTemplateEntity): Int

    /** Aktualisiert ein bestehendes Template. */
    suspend fun update(template: HangboardTemplateEntity)
}

class HangboardRepositoryImpl @Inject constructor(
    private val hangboardTemplateDao: HangboardTemplateDao,
) : HangboardRepository {

    override fun observeAll(): Flow<List<HangboardTemplateEntity>> =
        hangboardTemplateDao.observeAll()

    override suspend fun create(template: HangboardTemplateEntity): Int =
        hangboardTemplateDao.insert(template).toInt()

    override suspend fun update(template: HangboardTemplateEntity) =
        hangboardTemplateDao.update(template)
}
