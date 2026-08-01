package com.boulderbuddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
        enableEdgeToEdge()
        // Optionales Sprungziel aus dem Homescreen-Widget (7.4c); nur beim Start-Intent.
        val widgetNavTarget = intent?.getStringExtra(WidgetIntent.EXTRA_NAV_TARGET)
        // Nur für TARGET_ACTIVE_SESSION gesetzt: die Session, in die das Widget springt.
        val widgetSessionId = intent
            ?.getIntExtra(WidgetIntent.EXTRA_SESSION_ID, -1)
            ?.takeIf { it > 0 }
        setContent {
            // Dark-Mode-Override aus den Einstellungen; null = dem System folgen (7.4a).
            val darkModeOverride by themeViewModel.darkModeOverride.collectAsStateWithLifecycle()
            val darkTheme = darkModeOverride ?: isSystemInDarkTheme()
            BoulderBuddyTheme(darkTheme = darkTheme) {
                // WindowSizeClass für adaptive Layouts (Phase 7.1: Tablet). Wird an die
                // Navigation gereicht, die daraus Compact vs. Medium/Expanded ableitet.
                val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
                AppNavigation(
                    windowSizeClass = windowSizeClass,
                    initialNavTarget = widgetNavTarget,
                    initialNavSessionId = widgetSessionId,
                )
            }
        }
    }
}
