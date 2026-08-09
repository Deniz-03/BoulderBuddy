plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.boulderbuddy"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.boulderbuddy"
        minSdk = 26
        //noinspection OldTargetApi
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // MigrationTestHelper liest die exportierten Schemas aus den Test-Assets (Sync-Plan S0).
    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // Liefert BuildConfig.VERSION_NAME für die Versionsanzeige in den Einstellungen.
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Reicht `-Dghost.spur=<datei>` an die Test-JVM durch. Ohne das landet die Eigenschaft nur
// im Gradle-Daemon, und die Diagnose-Tests der Ghost-Pipeline (Tiefendiagnose,
// One-Euro-Sweep) überspringen sich still — sie brauchen eine echte Spur vom Gerät, die
// nicht im Repository liegt.
tasks.withType<Test>().configureEach {
    System.getProperty("ghost.spur")?.let { systemProperty("ghost.spur", it) }
}

dependencies {
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))

    // Material3 Adaptive (Tablet-Layouts) — Phase 7.1
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.compose.material3.windowsizeclass)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore (persistente App-Einstellungen: gewähltes Grade-System, Timer-Config)
    implementation(libs.androidx.datastore.preferences)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Coil
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Media3/ExoPlayer (Phase 7.3c): Video-Wiedergabe für Routen-Medien.
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // CameraX: eigener Aufnahme-Screen (Boulder-Foto/-Video, Ghost-Climber-Aufnahmen).
    // Der eigene Screen statt des Kamera-Intents, weil Ghost eine berechenbare
    // Auflösung braucht — der Intent liefert, was die Kamera-App gerade für richtig hält.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.view)

    // Glance (Phase 7.4c): Homescreen-App-Widget (aktive Session / Tops / Schnellstart Timer).
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // MediaPipe Pose Landmarker (Phase 7.5b, Stufe 3): On-Device-Skelett-Erkennung mit echtem
    // visibility/presence je Landmark; Modell-Asset in assets/ (MediaPipe lädt nicht selbst nach).
    // Homographie + Dynamic Time Warping werden selbst in Kotlin implementiert (kein OpenCV).
    implementation(libs.mediapipe.tasks.vision)

    // Kotlinx Serialization (type-safe navigation)
    implementation(libs.kotlinx.serialization.json)

    // Wear Data Layer (Phase 7.2): empfängt fertige Hangboard-Durchläufe von der Uhr.
    implementation(libs.play.services.wearable)

    // Gym-Näherungs-Push: FusedLocation (Standort-Button im Gym-Editor) + Geofencing-API.
    implementation(libs.play.services.location)

    // Nearby Connections (Sync-Plan S4): Phone↔Tablet reden direkt miteinander — kein Konto,
    // kein fremder Dienst, dieselbe Play-Services-Familie wie die Uhr. Nearby hebt selbständig
    // auf Wi-Fi Direct, die erste Übertragung ist damit Minuten statt eines Nachmittags.
    implementation(libs.play.services.nearby)

    // Für den von Hilt generierten ServiceComponent-Code (@CanIgnoreReturnValue) — Phase 7.2.
    implementation(libs.error.prone.annotations)

    // Unit-Tests (JVM) — Phase 7.6: ViewModel-/Flow-Tests mit virtueller Zeit.
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.androidx.arch.core.testing)

    // Instrumented Tests (Emulator/Gerät) — Phase 7.6: DAO/Repository (Room in-memory) + Compose.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.truth)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}