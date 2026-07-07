package com.boulderbuddy.proximity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

// Zentrale Permission-Checks für den Gym-Näherungs-Push. Der API-29/30-Bruch bei
// Hintergrund-Standort ist der reibungsreichste Punkt des Features (Plan §12):
//  - bis API 28 deckt ACCESS_FINE_LOCATION den Hintergrund mit ab,
//  - ab API 29 ist ACCESS_BACKGROUND_LOCATION eine separate Runtime-Permission,
//  - ab API 30 ist sie nur noch nach erteilter Foreground-Permission und über den
//    System-Settings-Flow ("Immer erlauben") erteilbar.

/** Foreground-Standort (genau) erteilt? Voraussetzung für Standort-Button UND Geofencing. */
fun hasFineLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Hintergrund-Standort erteilt? Ohne ihn liefert die Geofencing-API auf API 29+ keine
 * Transitions (addGeofences wirft SecurityException). Auf API 26–28 genügt Foreground.
 */
fun hasBackgroundLocationPermission(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        hasFineLocationPermission(context)
    }
