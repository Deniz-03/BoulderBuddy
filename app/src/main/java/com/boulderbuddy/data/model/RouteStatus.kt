package com.boulderbuddy.data.model

/**
 * Status einer geloggten Route/Boulder in der Datenschicht.
 *
 * Persistierter Wert einer [com.boulderbuddy.data.db.entity.RouteEntity]. Wird als String in
 * der DB abgelegt (siehe [com.boulderbuddy.data.db.Converters]).
 *
 * Bewusst getrennt von der UI-Enum `BoulderStatus` (TOP/FLASH/PROJEKT) in `ui/screens/`:
 * Die UI-Enum ist eine abgeleitete Darstellung (z.B. Flash = SENT mit `attempts == 1`).
 * Das Mapping steht an einer Stelle — `RouteStatus.toBoulderStatus` in `ui/model/UiMappers.kt`
 * — und wird hier nicht gedoppelt.
 */
enum class RouteStatus {
    /** Angelegt, noch nicht abgeschlossen. */
    OPEN,

    /** Erfolgreich getoppt. */
    SENT,

    /** Projekt – dranbleiben, noch nicht getoppt. */
    PROJECT,

    /**
     * Übersprungen / abgebrochen.
     *
     * Wird von der App derzeit **nirgends geschrieben** — kein Formular und kein Seed
     * vergibt ihn. Er bleibt, weil eine ältere Datenbank ihn enthalten kann und die
     * Anzeige ihn deshalb behandeln muss (`toBoulderStatus` bildet ihn auf PROJEKT ab).
     * Wer „überspringen" wieder anbieten will, findet den Wert also schon vor.
     */
    SKIP,
}
