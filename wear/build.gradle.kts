plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.boulderbuddy.wear"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        // WICHTIG: gleiche applicationId wie das Phone-Modul (:app) — sonst finden sich
        // Phone & Watch über den Wear Data Layer nicht (Kopplung erfolgt per Package-Name).
        applicationId = "com.boulderbuddy"
        // Wear OS 3+ (rotierende, moderne Uhren). Data Layer verlangt kein niedrigeres minSdk.
        minSdk = 30
        //noinspection OldTargetApi
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
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
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose for Wear OS (eigene Material-Bibliothek, nicht das Handy-material3).
    implementation(libs.androidx.wear)
    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.navigation)

    // Wear Data Layer API (Uhr → Phone: fertiger Hangboard-Durchlauf).
    implementation(libs.play.services.wearable)

    // Lokale Persistenz der Timer-Konfiguration (§0 Säule 4: kein Reset auf 6/7/3).
    implementation(libs.androidx.datastore.preferences)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // JVM-Unit-Tests der Android-freien Detektions-State-Machine (B.2/B.5.4).
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
