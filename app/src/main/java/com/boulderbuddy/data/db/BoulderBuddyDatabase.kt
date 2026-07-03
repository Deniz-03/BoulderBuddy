package com.boulderbuddy.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.boulderbuddy.data.db.dao.GradeDao
import com.boulderbuddy.data.db.dao.GradeSystemDao
import com.boulderbuddy.data.db.dao.GymDao
import com.boulderbuddy.data.db.dao.HangboardTemplateDao
import com.boulderbuddy.data.db.dao.RouteDao
import com.boulderbuddy.data.db.dao.SessionDao
import com.boulderbuddy.data.db.entity.GradeEntity
import com.boulderbuddy.data.db.entity.GradeSystemEntity
import com.boulderbuddy.data.db.entity.GymEntity
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
    ],
    version = 1,
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

    companion object {
        const val NAME = "boulderbuddy.db"
    }
}
