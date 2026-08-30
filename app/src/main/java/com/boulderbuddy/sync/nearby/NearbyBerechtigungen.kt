package com.boulderbuddy.sync.nearby

import android.Manifest
import android.annotation.SuppressLint
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
    /*
     * `InlinedApi` unterdrueckt, und zwar aus zwei Gruenden gemeinsam:
     *
     * 1. Die Namen sind reine Zeichenketten, die der Compiler an der Aufrufstelle einsetzt.
     *    Auf Android 8 entsteht daraus kein Zugriff auf etwas Nichtvorhandenes, sondern der
     *    Text "android.permission.BLUETOOTH_ADVERTISE" - unbenutzt, aber harmlos.
     * 2. Die Verzweigung schuetzt ohnehin. Lint sieht das nur nicht, weil die Version als
     *    Parameter hereinkommt und nicht als `Build.VERSION.SDK_INT` dasteht - und genau das
     *    ist Absicht: nur so laesst sich jede Version im Test durchspielen, ohne auf ihr zu
     *    laufen (siehe ProtokollTest, die drei
     *    Versions-Tests am Ende).
     */
    @SuppressLint("InlinedApi")
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
