package com.boulderbuddy

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application-Einstiegspunkt der App. [HiltAndroidApp] erzeugt den Hilt-Komponentenbaum
 * (SingletonComponent) und ist die Wurzel jeder Dependency Injection.
 *
 * Registriert in `AndroidManifest.xml` via `android:name=".BoulderBuddyApp"`.
 */
@HiltAndroidApp
class BoulderBuddyApp : Application()
