package com.boulderbuddy.ui.theme

import androidx.compose.ui.unit.dp

object Dimens {
    val paddingXS  = 4.dp
    val paddingS   = 8.dp
    val paddingM   = 12.dp
    val paddingL   = 16.dp
    val paddingXL  = 24.dp

    val borderAccent = 2.dp    // farbige Route-Card-Ränder
    val borderSubtle = 0.5.dp  // dezente Chip/Card-Ränder

    val dotRadius  = 1.3.dp    // Lochstruktur Punktgröße
    val dotSpacing = 24.dp     // Lochstruktur Rasterabstand

    val swatchSize = 36.dp     // Durchmesser einer Farb-Auswahl im ColorPicker
    val iconS      = 18.dp     // Inline-/Button-Icon (PrimaryButton)
    val iconL      = 28.dp     // Icon in größeren Slots (AddRouteCard, PhotoPicker)

    // Gestrichelter Platzhalter-Rahmen (AddRouteCard, PhotoPicker).
    // radiusMedium spiegelt shapes.medium (14dp) — DrawScope hat keinen Zugriff
    // auf MaterialTheme, daher hier zentral als Token statt pro Komponente hartkodiert.
    val radiusMedium = 14.dp
    val dashLength   = 10.dp   // Strichlänge
    val dashGap      = 6.dp    // Lücke zwischen den Strichen
}

