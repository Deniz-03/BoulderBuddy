package com.boulderbuddy.di

import android.content.Context
import androidx.room.Room
import com.boulderbuddy.data.db.BoulderBuddyDatabase
import com.boulderbuddy.data.db.SeedData
import com.boulderbuddy.data.db.dao.GhostAnalysisDao
import com.boulderbuddy.data.db.dao.GradeDao
import com.boulderbuddy.data.db.dao.GradeSystemDao
import com.boulderbuddy.data.db.dao.GymDao
import com.boulderbuddy.data.db.dao.GymVisitDao
import com.boulderbuddy.data.db.dao.HangboardSessionDao
import com.boulderbuddy.data.db.dao.HangboardTemplateDao
import com.boulderbuddy.data.db.dao.RouteDao
import com.boulderbuddy.data.db.dao.SessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt-Modul, das die Room-Datenbank und ihre DAOs im [SingletonComponent] bereitstellt.
 * Damit sind DB und DAOs app-weit als Singletons injizierbar (Repositories ab Phase 5).
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BoulderBuddyDatabase =
        Room.databaseBuilder(
            context,
            BoulderBuddyDatabase::class.java,
            BoulderBuddyDatabase.NAME,
        )
            // Beispieldaten beim ersten Erstellen der DB (siehe SeedData.onCreate).
            .addCallback(SeedData)
            // Pre-Release ohne Bestandsnutzer: Schema-Änderungen (z.B. v1→v2, Route.name/sektor)
            // verwerfen die alte DB, statt Migrationen zu pflegen. Vor dem ersten Release durch
            // echte Migrationen ersetzen.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideGymDao(db: BoulderBuddyDatabase): GymDao = db.gymDao()

    @Provides
    fun provideGradeSystemDao(db: BoulderBuddyDatabase): GradeSystemDao = db.gradeSystemDao()

    @Provides
    fun provideGradeDao(db: BoulderBuddyDatabase): GradeDao = db.gradeDao()

    @Provides
    fun provideSessionDao(db: BoulderBuddyDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideRouteDao(db: BoulderBuddyDatabase): RouteDao = db.routeDao()

    @Provides
    fun provideHangboardTemplateDao(db: BoulderBuddyDatabase): HangboardTemplateDao =
        db.hangboardTemplateDao()

    @Provides
    fun provideHangboardSessionDao(db: BoulderBuddyDatabase): HangboardSessionDao =
        db.hangboardSessionDao()

    @Provides
    fun provideGhostAnalysisDao(db: BoulderBuddyDatabase): GhostAnalysisDao =
        db.ghostAnalysisDao()

    @Provides
    fun provideGymVisitDao(db: BoulderBuddyDatabase): GymVisitDao = db.gymVisitDao()
}
