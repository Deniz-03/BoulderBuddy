package com.boulderbuddy

import android.app.Application
import com.boulderbuddy.wearsync.HangboardPresetPublisher
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

/**
 * Application-Einstiegspunkt der App. [HiltAndroidApp] erzeugt den Hilt-Komponentenbaum
 * (SingletonComponent) und ist die Wurzel jeder Dependency Injection.
 *
 * Registriert in `AndroidManifest.xml` via `android:name=".BoulderBuddyApp"`.
 */
@HiltAndroidApp
class BoulderBuddyApp : Application() {

    @Inject lateinit var hangboardPresetPublisher: HangboardPresetPublisher

    // App-weiter Scope für Hintergrund-Sync (lebt so lange wie der Prozess).
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Presets + zuletzt genutzte Timer-Config an die Uhr publizieren (§0 Säule 4).
        hangboardPresetPublisher.start(applicationScope)
    }
}
