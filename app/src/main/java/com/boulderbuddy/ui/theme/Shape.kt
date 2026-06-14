package com.boulderbuddy.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val BoulderBuddyShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(10.dp),  // Stat-Cards
    medium     = RoundedCornerShape(14.dp),  // Route-Cards
    large      = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp),  // Pills, Chips
)