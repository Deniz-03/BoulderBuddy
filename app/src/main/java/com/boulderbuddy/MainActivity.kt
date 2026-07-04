package com.boulderbuddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import com.boulderbuddy.ui.navigation.AppNavigation
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BoulderBuddyTheme {
                // WindowSizeClass für adaptive Layouts (Phase 7.1: Tablet). Wird an die
                // Navigation gereicht, die daraus Compact vs. Medium/Expanded ableitet.
                val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
                AppNavigation(windowSizeClass = windowSizeClass)
            }
        }
    }
}
