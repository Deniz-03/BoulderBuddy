package com.boulderbuddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.boulderbuddy.ui.theme.BoulderBuddyTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.dotPattern

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BoulderBuddyTheme {
                // Navigation kommt hier rein (NavHost) — erst wenn die Screens stehen.
                //
                // TODO: Noch fehlende Wireframe-Screens bauen:
                //   - Boulder-Detailansicht (#6) — einzelner Boulder: Foto, Grade-Badge,
                //     Stats (Versuche/Status/Rating), Notiz, Edit-Icon im Header.
                //     Verlinkt von BoulderUebersichtScreen (RouteCard.onClick) und
                //     vom Session-Detail.
                //   - Statistiken (#10) — Quick-Stats, BarChart (Grade-Verteilung),
                //     ActivityHeatmap. Bottom-Nav-Tab (BottomNavTab.Stats).
                //
                // Ghost Climber (#12): bewusst Post-MVP, kein Wireframe — nur Bottom-Nav-
                //   Platzhalter, noch nicht zu bauen.
                //
                // Danach: NavHost verdrahten (alle TODO "Navigation ..." in den Screens)
                //   und Daten-Schicht (Room + ViewModels) anbinden.


            }
        }
    }
}