package com.boulderbuddy.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.boulderbuddy.data.db.entity.StandMetaEntity
import kotlinx.coroutines.flow.Flow

/**
 * Zugriff auf die Ein-Zeilen-Tabelle `stand_meta` (Sync-Plan E3).
 *
 * Kein `insert` — die Zeile wird immer ersetzt, nie ergänzt: es gibt genau einen Stand.
 */
@Dao
interface StandMetaDao {

    /** Herkunft des aktuellen Standes; `null` = noch nie abgeglichen (Erstbegegnung). */
    @Query("SELECT * FROM stand_meta WHERE id = ${StandMetaEntity.EINZIGE_ZEILE}")
    suspend fun lies(): StandMetaEntity?

    @Query("SELECT * FROM stand_meta WHERE id = ${StandMetaEntity.EINZIGE_ZEILE}")
    fun beobachte(): Flow<StandMetaEntity?>

    @Upsert(entity = StandMetaEntity::class)
    suspend fun schreibe(meta: StandMetaEntity)

    /** Verwirft die Kopplung — das Gerät gilt wieder als „noch nie abgeglichen" (Ablauf 36). */
    @Query("DELETE FROM stand_meta")
    suspend fun loesche()
}
