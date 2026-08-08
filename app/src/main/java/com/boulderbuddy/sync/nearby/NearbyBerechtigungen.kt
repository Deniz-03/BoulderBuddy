package com.boulderbuddy.sync.nearby

import android.Manifest
import android.os.Build

/**
 * Welche Berechtigungen Nearby Connections auf welcher Android-Version braucht
 * (Sync-Plan S4).
 *
 * Als reine Funktion der SDK-Version, nicht als feste Liste: die Anforderungen haben sich
 * zwischen Android 11, 12 und 13 zweimal geändert, und eine zu große Liste ist genauso
 * kaputt wie eine zu kleine — Android verweigert eine Anfrage nach einer Berechtigung, die
 * für die eigene `targetSdk` gar nicht mehr gilt.
 *
 * Der unangenehme Teil steht in [brauchtStandort]: **bis Android 12 verlangt Nearby den
 * genauen Standort.** Für einen Abgleich zwischen zwei eigenen Geräten ist das schwer zu
 * erklären, und unerklärt wirkt es wie Schnüffelei — deshalb muss es vorher begründet
 * werden, nicht per Systemdialog aus dem Nichts kommen.
 */
object NearbyBerechtigungen {

    /**
     * @param sdk `Build.VERSION.SDK_INT`; als Parameter, damit jede Version testbar ist,
     *   ohne auf ihr zu laufen.
     */
    fun fuer(sdk: Int): List<String> = buildList {
        if (sdk >= Build.VERSION_CODES.S) {
            // Ab Android 12 sind die Bluetooth-Rechte aufgeteilt und laufzeitpflichtig.
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            // Davor genügen die alten Installationsrechte — sie stehen nur im Manifest.
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (sdk >= Build.VERSION_CODES.TIRAMISU) {
            // Ab Android 13 ersetzt NEARBY_WIFI_DEVICES den Standort — mit
            // `neverForLocation` im Manifest, damit klar ist, dass nicht geortet wird.
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            // Bis Android 12 gibt es keinen anderen Weg: Nearby braucht den genauen
            // Standort, weil WLAN- und Bluetooth-Scans daraus ableitbar wären.
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    /** Läuft der Abgleich auf dieser Version nur mit Standortfreigabe? */
    fun brauchtStandort(sdk: Int): Boolean = sdk < Build.VERSION_CODES.TIRAMISU

    /**
     * Was dem Nutzer **vor** dem Systemdialog gesagt wird.
     *
     * Ein Systemdialog „Standort erlauben?" mitten in einem Abgleich zwischen zwei eigenen
     * Geräten beantwortet niemand mit Ja, der nicht weiß, warum gefragt wird.
     */
    fun begruendung(sdk: Int): String = if (brauchtStandort(sdk)) {
        "Zum Finden des anderen Geräts braucht Android auf dieser Version die " +
            "Standortfreigabe — sie wird nur fürs Suchen per Bluetooth und WLAN gebraucht. " +
            "BoulderBuddy fragt den Standort nicht ab und speichert ihn nirgends."
    } else {
        "Zum Finden des anderen Geräts braucht die App Zugriff auf Bluetooth und die " +
            "WLAN-Suche in der Nähe. Beides wird nur während des Abgleichs benutzt."
    }
}
