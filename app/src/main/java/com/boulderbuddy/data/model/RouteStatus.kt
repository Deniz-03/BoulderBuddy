package com.boulderbuddy.data.model

/**
 * Status einer geloggten Route/Boulder in der Datenschicht.
 *
 * Persistierter Wert einer [com.boulderbuddy.data.db.entity.RouteEntity]. Wird als String in
 * der DB abgelegt (siehe [com.boulderbuddy.data.db.Converters]).
 *
 * Bewusst getrennt von der UI-Enum `BoulderStatus` (TOP/FLASH/PROJEKT) in `ui/screens/`:
 * Die UI-Enum ist eine abgeleitete Darstellung (z.B. Flash = SENT mit `attempts == 1`).
 * Das Mapping zwischen beiden entsteht in Phase 6, hier wird nicht gedoppelt.
 */
enum class RouteStatus {
    /** Angelegt, noch nicht abgeschlossen. */
    OPEN,

    /** Erfolgreich getoppt. */
    SENT,

    /** Projekt – dranbleiben, noch nicht getoppt. */
    PROJECT,

    /** Übersprungen / abgebrochen. */
    SKIP,
}
