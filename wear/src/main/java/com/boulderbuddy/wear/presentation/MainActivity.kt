package com.boulderbuddy.wear.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * Einstiegspunkt der Wear-App. Zeigt direkt den Hangboard-Timer ([WearApp]).
 * Kein Hilt auf der Uhr (bewusst: kleines Timer-Modul, Aufwand/Nutzen — siehe PHASE7_PLAN 7.2).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp()
        }
    }
}
