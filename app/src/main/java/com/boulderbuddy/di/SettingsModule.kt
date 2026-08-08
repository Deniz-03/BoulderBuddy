package com.boulderbuddy.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.boulderbuddy.sync.GeraeteStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Stellt den Preferences-[DataStore] für [com.boulderbuddy.data.settings.SettingsRepository]
 * app-weit als Singleton bereit. Die Bindung des Interface an die Impl liegt im
 * [RepositoryModule].
 */
@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("settings")
        }

    /**
     * Eigener Store für Geräte-ID und Nummernband (Sync-Plan E14). Getrennt von den
     * Einstellungen, weil die Android-Backup-Regeln nur ganze Dateien ausschließen können —
     * siehe `res/xml/backup_rules.xml`.
     */
    @Provides
    @Singleton
    @GeraeteStore
    fun provideGeraeteDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile(GERAETE_STORE_NAME)
        }

    /** Dateiname ohne Endung; die Backup-Regeln nennen ihn mit `.preferences_pb`. */
    const val GERAETE_STORE_NAME = "geraet"
}
