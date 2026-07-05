package com.boulderbuddy.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.boulderbuddy.data.db.dao.GhostAnalysisDao
import com.boulderbuddy.data.db.dao.GradeDao
import com.boulderbuddy.data.db.dao.GradeSystemDao
import com.boulderbuddy.data.db.dao.GymDao
import com.boulderbuddy.data.db.dao.HangboardSessionDao
import com.boulderbuddy.data.db.dao.HangboardTemplateDao
import com.boulderbuddy.data.db.dao.RouteDao
import com.boulderbuddy.data.db.dao.SessionDao
import com.boulderbuddy.data.db.entity.GhostAnalysisEntity
import com.boulderbuddy.data.db.entity.GradeEntity
import com.boulderbuddy.data.db.entity.GradeSystemEntity
import com.boulderbuddy.data.db.entity.GymEntity
import com.boulderbuddy.data.db.entity.HangboardSessionEntity
import com.boulderbuddy.data.db.entity.HangboardTemplateEntity
import com.boulderbuddy.data.db.entity.RouteEntity
import com.boulderbuddy.data.db.entity.SessionEntity

/**
 * Room-Datenbank der App. Wird ab Phase 4 per Hilt bereitgestellt.
 * Schema-Export ist in `app/build.gradle.kts` aktiviert (`room.schemaLocation`).
 */
@Database(
    entities = [
        GymEntity::class,
        GradeSystemEntity::class,
        GradeEntity::class,
        SessionEntity::class,
        RouteEntity::class,
        HangboardTemplateEntity::class,
        HangboardSessionEntity::class,
        GhostAnalysisEntity::class,
    ],
    // v2 (Phase 6): RouteEntity um name + sektor erweitert.
    // v3 (MVP-Polish): GradeSystem.gymId nullable (globale Standard-Systeme) +
    // neue hangboard_session-Tabelle (getrackte Timer-Durchläufe).
    // v4 (MVP-Polish): Farbe von der Schwierigkeit entkoppelt — Grade.color entfernt,
    // Route.color (eigener Farb-Key) ergänzt; Session.gradeSystemId (Grading pro Session).
    // v5 (Phase 7.5): neue ghost_analysis-Tabelle (gespeicherte Ghost-Climber-Analysen).
    // Keine handgeschriebene Migration nötig: der Provider nutzt destruktive Migration
    // (pre-Release, keine Bestandsnutzer — siehe DatabaseModule).
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class BoulderBuddyDatabase : RoomDatabase() {
    abstract fun gymDao(): GymDao
    abstract fun gradeSystemDao(): GradeSystemDao
    abstract fun gradeDao(): GradeDao
    abstract fun sessionDao(): SessionDao
    abstract fun routeDao(): RouteDao
    abstract fun hangboardTemplateDao(): HangboardTemplateDao
    abstract fun hangboardSessionDao(): HangboardSessionDao
    abstract fun ghostAnalysisDao(): GhostAnalysisDao

    companion object {
        const val NAME = "boulderbuddy.db"
    }
}
