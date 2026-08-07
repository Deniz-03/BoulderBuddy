package com.boulderbuddy.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Herkunft des Standes (Sync-Plan E3). Genau eine Zeile, [id] ist immer [EINZIGE_ZEILE].
 *
 * Beschreibt den **gemeinsamen** Stand beider Geräte, nicht den eigenen: nach einem
 * erfolgreichen Abgleich stehen hier auf Phone und Tablet dieselben drei Werte. Setzte
 * jedes Gerät eigene, läse die Lagebestimmung beim nächsten Mal „auseinandergelaufen", wo
 * Einigkeit herrscht (Ablauf 32).
 *
 * Fehlt die Zeile, hat das Gerät noch nie abgeglichen — das ist der Erstbegegnungs-Fall.
 *
 * Die Tabelle reist mit der Datei mit, wird aber **nie als Daten übernommen** (Ablauf 19):
 * sie steht nicht in der Tabellenliste des Zeilenabgleichs.
 *
 * **Das Nummernband steht bewusst nicht hier**, obwohl E8 das ursprünglich vorsah. Es ist je
 * Gerät verschieden und widerspräche damit dem Zweck dieser Tabelle; außerdem wandert die DB
 * in die Android-Sicherung, und genau das soll für die Geräte-Identität nicht passieren
 * (E14). Band und Geräte-ID liegen deshalb allein in
 * [com.boulderbuddy.sync.GeraeteIdentitaet].
 */
@Entity(tableName = "stand_meta")
data class StandMetaEntity(
    @PrimaryKey val id: Int = EINZIGE_ZEILE,
    /** Zählt mit jedem erfolgreichen Abgleich hoch. */
    val generation: Long,
    /** ID des Geräts, das diesen Stand gerechnet hat (E12) — auf beiden Geräten dieselbe. */
    val erzeugtVon: String,
    /** [generation] des Standes, aus dem dieser hervorging; `null` beim allerersten. */
    val basiertAuf: Long?,
) {
    companion object {
        const val EINZIGE_ZEILE = 1
    }
}
