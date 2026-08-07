package com.boulderbuddy.di

import android.content.Context
import androidx.room.Room
import com.boulderbuddy.data.db.ALLE_MIGRATIONEN
import com.boulderbuddy.data.db.BoulderBuddyDatabase
import com.boulderbuddy.data.db.SeedData
import com.boulderbuddy.data.db.dao.GhostAnalysisDao
import com.boulderbuddy.data.db.dao.GradeDao
import com.boulderbuddy.data.db.dao.GradeSystemDao
import com.boulderbuddy.data.db.dao.GymDao
import com.boulderbuddy.data.db.dao.HangboardTemplateDao
import com.boulderbuddy.data.db.dao.HangboardWorkoutDao
import com.boulderbuddy.data.db.dao.MedienDao
import com.boulderbuddy.data.db.dao.RouteDao
import com.boulderbuddy.data.db.dao.SessionDao
import com.boulderbuddy.data.db.dao.StandMetaDao
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
            // Echte Migrationen statt `fallbackToDestructiveMigration` (Sync-Plan S0): der
            // Geräte-Abgleich überträgt Daten, die ein Schema-Update sonst gelöscht hätte.
            // Bewusst ohne destruktiven Fallback — fehlt ein Pfad, soll die App laut scheitern
            // statt still zu leeren.
            .addMigrations(*ALLE_MIGRATIONEN)
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
    fun provideHangboardWorkoutDao(db: BoulderBuddyDatabase): HangboardWorkoutDao =
        db.hangboardWorkoutDao()

    @Provides
    fun provideGhostAnalysisDao(db: BoulderBuddyDatabase): GhostAnalysisDao =
        db.ghostAnalysisDao()

    @Provides
    fun provideStandMetaDao(db: BoulderBuddyDatabase): StandMetaDao = db.standMetaDao()

    @Provides
    fun provideMedienDao(db: BoulderBuddyDatabase): MedienDao = db.medienDao()
}
