package com.boulderbuddy

import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boulderbuddy.proximity.ProximityIntent
import com.boulderbuddy.ui.navigation.AppNavigation
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.viewmodel.ThemeViewModel
import com.boulderbuddy.widget.WidgetIntent
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val themeViewModel: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Randlos schon vor dem ersten Frame — sonst springt das Layout sichtbar, sobald der
        // Effekt in der Composition greift. Die Icon-Helligkeit stellt dieser Aufruf noch
        // nach dem System ein; korrigiert wird sie unten, sobald das App-Theme feststeht.
        enableEdgeToEdge()
        // Optionales Sprungziel aus dem Homescreen-Widget (7.4c) bzw. der Näherungs-
        // Notification (M4, gleiches Intent-Extra-Muster); nur beim Start-Intent.
        val widgetNavTarget = intent?.getStringExtra(WidgetIntent.EXTRA_NAV_TARGET)
        // Nur für TARGET_ACTIVE_SESSION gesetzt: die Session, in die das Widget springt.
        val widgetSessionId = intent
            ?.getIntExtra(WidgetIntent.EXTRA_SESSION_ID, -1)
            ?.takeIf { it > 0 }
        // Gym fürs Vorbefüllen von SessionErstellen (nur von der Näherungs-Notification gesetzt).
        val navGymId = intent
            ?.getIntExtra(ProximityIntent.EXTRA_GYM_ID, -1)
            ?.takeIf { it > 0 }
        setContent {
            // Dark-Mode-Override aus den Einstellungen; null = dem System folgen (7.4a).
            val darkModeOverride by themeViewModel.darkModeOverride.collectAsStateWithLifecycle()
            val darkTheme = darkModeOverride ?: isSystemInDarkTheme()

            /*
             * Die Icons der Status- und Navigationsleiste müssen dem THEME DER APP folgen,
             * nicht dem des Systems.
             *
             * `enableEdgeToEdge()` ohne Argumente entscheidet anhand der Ressourcen-
             * Konfiguration — und die kennt nur den System-Schalter. Steht das System auf
             * hell und der App-Schalter oben auf dunkel, zeichnet Android dunkle Icons auf
             * unser dunkles Chrome. Sie verschwinden schlicht.
             *
             * Deshalb hier statt einmalig in `onCreate`: `darkTheme` ist erst in der
             * Composition bekannt und kann sich zur Laufzeit ändern. Der Effekt läuft bei
             * jedem Wechsel neu.
             */
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        TRANSPARENT, TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        TRANSPARENT, TRANSPARENT,
                    ) { darkTheme },
                )
                onDispose {}
            }

            BoulderBuddyTheme(darkTheme = darkTheme) {
                // Die Fensterbreite wird nicht mehr von hier durchgereicht: sie steht über
                // `aktuelleBreite()` (ui/theme/Breite.kt) überall in der Composition zur
                // Verfügung. Ein Parameter hätte sie nur bis zur Navigation getragen, während
                // sie inzwischen auch einzelne Screens und Raster interessiert.
                AppNavigation(
                    initialNavTarget = widgetNavTarget,
                    initialNavSessionId = widgetSessionId,
                    initialNavGymId = navGymId,
                )
            }
        }
    }
}
