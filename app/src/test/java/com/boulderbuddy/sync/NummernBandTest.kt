package com.boulderbuddy.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Nummernbänder und die feste Tabellenliste (Sync-Plan E8, E4, E15).
 *
 * Beides ist reine Logik ohne Android — und beides sind Stellen, an denen ein Fehler Daten
 * kostet statt nur einen Transfer.
 */
class NummernBandTest {

    @Test
    fun das_rechnende_geraet_zaehlt_unten_weiter_das_andere_oben() {
        val phone = "11111111-aaaa"
        val tablet = "99999999-zzzz"
        // Gerechnet hat das Phone, also steht seine ID in der gemeinsamen Herkunft.
        val erzeugtVon = phone

        assertThat(NummernBand.ausHerkunft(erzeugtVon, phone)).isEqualTo(0)
        assertThat(NummernBand.ausHerkunft(erzeugtVon, tablet)).isEqualTo(1)
    }

    @Test
    fun die_baender_folgen_aus_der_herkunft_ohne_dass_ein_geraet_die_fremde_id_kennt() {
        // Der Fehler, den das ersetzt: frueher kam das Band aus dem Vergleich der beiden
        // Geraete-IDs. Ueber den Datei-Weg kennt aber nur die einlesende Seite beide — die
        // abgebende blieb ohne Band und fiel auf 0 zurueck. Bei jeder zweiten Paarung landeten
        // damit BEIDE im selben Fenster (Ablauf 7).
        //
        // `erzeugtVon` steht dagegen auf beiden Geraeten gleich in `stand_meta`, und jedes
        // kennt seine eigene ID. Mehr braucht es nicht — und zwar fuer JEDE Paarung.
        val paare = listOf(
            "aaa" to "zzz",
            "zzz" to "aaa",
            "m" to "m2",
            "00000000-0000" to "ffffffff-ffff",
        )

        for ((a, b) in paare) {
            for (rechner in listOf(a, b)) {
                val bandA = NummernBand.ausHerkunft(rechner, a)
                val bandB = NummernBand.ausHerkunft(rechner, b)
                assertThat(bandA).isNotEqualTo(bandB)
            }
        }
    }

    @Test
    fun ein_geraet_ohne_herkunft_faengt_unten_an() {
        // Noch nie abgeglichen: es gibt keine Gegenseite, also auch keine Kollision.
        assertThat(NummernBand.ausHerkunft(null, "aaa")).isEqualTo(0)
    }

    @Test
    fun leere_tabelle_beginnt_bei_eins_beziehungsweise_ein_fenster_hoeher() {
        assertThat(naechsteNummerNach(band = 0, idsInDerTabelle = emptyList())).isEqualTo(1)
        assertThat(naechsteNummerNach(band = 1, idsInDerTabelle = emptyList()))
            .isEqualTo(NummernBand.FENSTER + 1)
    }

    @Test
    fun beide_geraete_zaehlen_ueber_dem_gemeinsamen_hoechstwert_weiter() {
        // Genau die Lage nach einem Abgleich: eigene und übernommene Zeilen liegen
        // zusammen in der Tabelle. Entscheidend ist, dass die nächsten Nummern beider
        // Geräte ÜBER allem Vorhandenen liegen — sonst greift SQLites
        // `max(sequenz, größte id) + 1` und macht die Rückstellung wirkungslos.
        val ids = listOf(1, 2, 7, 1_000_004, 1_000_009)

        val naechsteUnten = naechsteNummerNach(band = 0, idsInDerTabelle = ids)
        val naechsteOben = naechsteNummerNach(band = 1, idsInDerTabelle = ids)

        assertThat(naechsteUnten).isEqualTo(1_000_010)
        assertThat(naechsteOben).isEqualTo(1_000_009 + NummernBand.FENSTER + 1)
        assertThat(naechsteUnten).isGreaterThan(ids.max())
        assertThat(naechsteOben).isGreaterThan(ids.max())
    }

    @Test
    fun die_fenster_beider_geraete_ueberschneiden_sich_nicht() {
        // Das ist der eigentliche Zweck: solange kein Gerät zwischen zwei Abgleichen mehr
        // als FENSTER Zeilen anlegt, kann keine Nummer doppelt vergeben werden.
        val ids = listOf(1, 2, 7)
        val untenErste = naechsteNummerNach(band = 0, ids)
        val obenErste = naechsteNummerNach(band = 1, ids)

        val untenLetzteVorKollision = untenErste + NummernBand.FENSTER - 1

        assertThat(untenLetzteVorKollision).isLessThan(obenErste)
    }

    @Test
    fun eine_erstbegegnung_bei_der_alles_fremd_war_kollidiert_nicht() {
        // Alles kam vom anderen Gerät; die eigene Tabelle hat keine einzige eigene Nummer.
        val nurFremde = listOf(1, 2, 3)

        assertThat(naechsteNummerNach(band = 0, nurFremde)).isEqualTo(4)
        assertThat(naechsteNummerNach(band = 1, nurFremde))
            .isEqualTo(3 + NummernBand.FENSTER + 1)
    }

    @Test
    fun jede_elterntabelle_steht_vor_ihren_kindern() {
        // Das ist die Voraussetzung dafür, dass Einfügen in Listenreihenfolge und Löschen
        // in umgekehrter Reihenfolge keinen Fremdschlüssel verletzt (E4).
        val position = STAND_TABELLEN.withIndex().associate { (i, t) -> t.name to i }

        for (tabelle in STAND_TABELLEN) {
            for (bezug in tabelle.eltern) {
                assertThat(position).containsKey(bezug.elternTabelle)
                assertThat(position.getValue(bezug.elternTabelle))
                    .isLessThan(position.getValue(tabelle.name))
            }
        }
    }

    @Test
    fun keine_metatabelle_steht_in_der_abgleichsliste() {
        // stand_meta beschreibt den Stand, sie ist nicht Teil davon (Ablauf 19).
        val abgeglichen = STAND_TABELLEN.map { it.name }.toSet()

        assertThat(abgeglichen.intersect(META_TABELLEN)).isEmpty()
    }

    @Test
    fun die_id_ist_kein_verglichener_inhalt() {
        // Die Nummer ist der Schlüssel des Vergleichs, nicht sein Gegenstand — stünde sie
        // in den Spalten, wäre jede Zeile trivial „gleich".
        for (tabelle in STAND_TABELLEN) {
            assertThat(tabelle.spalten).doesNotContain("id")
        }
    }

    @Test
    fun kinderVon_findet_alle_direkten_kinder() {
        // ghost_analysis hängt seit v12 ebenfalls an der Session — allerdings mit AUF_NULL,
        // nicht mit KASKADE wie die beiden anderen. `kinderVon` unterscheidet das nicht (das
        // tun erst die Aufrufer), also steht es hier mit in der Liste.
        assertThat(kinderVon("session").map { it.name })
            .containsExactly("route", "hangboard_workout", "ghost_analysis")
        assertThat(kinderVon("ghost_analysis")).isEmpty()
    }
}
