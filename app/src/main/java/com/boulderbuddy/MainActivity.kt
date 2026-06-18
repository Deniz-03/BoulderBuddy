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
                // Navigation kommt hier rein — erst wenn erste Screens stehen


            }
        }
    }
}