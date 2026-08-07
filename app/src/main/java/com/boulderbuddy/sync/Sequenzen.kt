package com.boulderbuddy.sync

/**
 * Worauf `sqlite_sequence` einer Tabelle nach dem Abgleich zu stehen hat (E8, korrigiert —
 * die Begründung steht bei [NummernBand]).
 *
 * SQLite vergibt bei `AUTOINCREMENT` die Nummer `max(sqlite_sequence, größte id in der
 * Tabelle) + 1`. Ein Wert **unterhalb** der größten vorhandenen Nummer ist deshalb wirkungslos
 * — und genau das machte den ursprünglichen Vorschlag (auf den festen Bandanfang
 * zurücksetzen) zu einer Rückstellung, die nichts zurückstellt.
 *
 * Also andersherum: beide Geräte zählen **über** dem gemeinsamen Höchstwert weiter, in
 * getrennten Fenstern. Band 0 beginnt direkt darüber, Band 1 ein Fenster höher.
 *
 * @param band eigenes Band (0 oder 1)
 * @param idsInDerTabelle alle vorhandenen Primärschlüssel der Tabelle
 */
fun sequenzFuerBand(band: Int, idsInDerTabelle: Collection<Int>): Int {
    val hoechste = idsInDerTabelle.maxOrNull() ?: 0
    return hoechste + NummernBand.versatz(band)
}

/**
 * Nummer, mit der ein Gerät nach der Rückstellung als Nächstes einfügt. Nur zum Prüfen und
 * für Fehlermeldungen — der Abgleich entscheidet nie anhand einer Nummer, sondern immer
 * anhand des Vergleichs.
 */
fun naechsteNummerNach(band: Int, idsInDerTabelle: Collection<Int>): Int =
    sequenzFuerBand(band, idsInDerTabelle) + 1
