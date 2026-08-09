package com.boulderbuddy.di

import com.boulderbuddy.data.haptics.HapticPlayer
import com.boulderbuddy.data.haptics.SystemHapticPlayer
import com.boulderbuddy.data.repository.GhostAnalysisRepository
import com.boulderbuddy.data.repository.GhostAnalysisRepositoryImpl
import com.boulderbuddy.data.repository.GradeRepository
import com.boulderbuddy.data.repository.GradeRepositoryImpl
import com.boulderbuddy.data.repository.GymRepository
import com.boulderbuddy.data.repository.GymRepositoryImpl
import com.boulderbuddy.data.repository.GymVisitRepository
import com.boulderbuddy.data.repository.GymVisitRepositoryImpl
import com.boulderbuddy.data.repository.HangboardRepository
import com.boulderbuddy.data.repository.HangboardRepositoryImpl
import com.boulderbuddy.data.repository.RouteRepository
import com.boulderbuddy.data.repository.RouteRepositoryImpl
import com.boulderbuddy.data.repository.HangboardWorkoutRepository
import com.boulderbuddy.data.repository.HangboardWorkoutRepositoryImpl
import com.boulderbuddy.data.repository.SessionRepository
import com.boulderbuddy.data.repository.SessionRepositoryImpl
import com.boulderbuddy.data.settings.SettingsRepository
import com.boulderbuddy.data.settings.SettingsRepositoryImpl
import com.boulderbuddy.ghost.pose.GecachtePoseSpurQuelle
import com.boulderbuddy.ghost.pose.PoseSpurQuelle
import com.boulderbuddy.wearsync.WearConnection
import com.boulderbuddy.wearsync.WearNodeConnection
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Bindet die Repository-Interfaces an ihre Implementierungen (`@Binds`).
 * Die Impls beziehen ihre DAOs per Konstruktor-Injektion aus dem [DatabaseModule].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository

    @Binds
    @Singleton
    abstract fun bindRouteRepository(impl: RouteRepositoryImpl): RouteRepository

    @Binds
    @Singleton
    abstract fun bindGymRepository(impl: GymRepositoryImpl): GymRepository

    @Binds
    @Singleton
    abstract fun bindGradeRepository(impl: GradeRepositoryImpl): GradeRepository

    @Binds
    @Singleton
    abstract fun bindHangboardRepository(impl: HangboardRepositoryImpl): HangboardRepository

    @Binds
    @Singleton
    abstract fun bindHangboardWorkoutRepository(
        impl: HangboardWorkoutRepositoryImpl,
    ): HangboardWorkoutRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindGhostAnalysisRepository(
        impl: GhostAnalysisRepositoryImpl,
    ): GhostAnalysisRepository

    @Binds
    @Singleton
    abstract fun bindHapticPlayer(impl: SystemHapticPlayer): HapticPlayer

    @Binds
    @Singleton
    abstract fun bindWearConnection(impl: WearNodeConnection): WearConnection

    @Binds
    @Singleton
    abstract fun bindGymVisitRepository(impl: GymVisitRepositoryImpl): GymVisitRepository

    @Binds
    @Singleton
    abstract fun bindPoseSpurQuelle(impl: GecachtePoseSpurQuelle): PoseSpurQuelle
}
