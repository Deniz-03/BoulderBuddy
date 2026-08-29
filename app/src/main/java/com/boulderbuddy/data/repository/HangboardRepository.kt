package com.boulderbuddy.data.repository

import com.boulderbuddy.data.db.dao.HangboardTemplateDao
import com.boulderbuddy.data.db.entity.HangboardTemplateEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Kapselt den Zugriff auf [HangboardTemplateEntity]-Daten (Timer-Voreinstellungen).
 *
 * Speist die Preset-Auswahl im Einstell-Dialog des Hangboard-Timers: die drei mitgelieferten
 * aus `SeedData` und alles, was der Nutzer dort selbst gespeichert hat. Presets sind
 * gerätübergreifend gemeint und reisen deshalb beim Geräte-Abgleich mit.
 */
interface HangboardRepository {
    /** Alle Timer-Templates, alphabetisch nach Name. */
    fun observeAll(): Flow<List<HangboardTemplateEntity>>

    /** Legt ein Template an und gibt seine neue ID zurück. */
    suspend fun create(template: HangboardTemplateEntity): Int

    /** Aktualisiert ein bestehendes Template. */
    suspend fun update(template: HangboardTemplateEntity)

    /** Löscht ein Template. */
    suspend fun delete(template: HangboardTemplateEntity)
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

    override suspend fun delete(template: HangboardTemplateEntity) =
        hangboardTemplateDao.delete(template)
}
