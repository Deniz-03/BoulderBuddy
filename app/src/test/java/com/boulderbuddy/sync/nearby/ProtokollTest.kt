package com.boulderbuddy.sync.nearby

import android.os.Build
import com.boulderbuddy.sync.Feld
import com.boulderbuddy.sync.Operation
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * Handschlag, Rollenwahl und Protokoll (Sync-Plan S4/S5).
 *
 * Ohne zwei Geräte ist das der einzige Teil des Funkwegs, der sich prüfen lässt — und
 * zugleich der, an dem ein Fehler wehtut: rechnen beide, stehen hinterher zwei Geräte mit
 * verschiedenen Daten da, ohne dass es jemandem auffällt.
 */
class ProtokollTest {

    private fun hallo(
        id: String,
        gedrueckt: Boolean = false,
        schema: Int = 7,
        session: Boolean = false,
        platz: Long = 1_000_000_000,
        groesse: Long = 120_000,
        protokoll: Int = PROTOKOLL_VERSION,
    ) = Nachricht.Hallo(
        protokoll = protokoll,
        geraeteId = id,
        schemaVersion = schema,
        laufendeSession = session,
        hatGedrueckt = gedrueckt,
        standGroesse = groesse,
        freierPlatz = platz,
    )

    // -- Rollenwahl --------------------------------------------------------

    @Test
    fun wer_gedrueckt_hat_rechnet() {
        val phone = hallo("aaa", gedrueckt = true)
        val tablet = hallo("zzz", gedrueckt = false)

        assertThat(ichRechne(phone, tablet)).isTrue()
        assertThat(ichRechne(tablet, phone)).isFalse()
    }

    @Test
    fun bei_gleichzeitigem_druecken_gewinnt_die_kleinere_geraete_id() {
        // Ablauf 11: irgendeine feste Regel muss es geben, und beide Geräte müssen ohne
        // Rückfrage zur selben Antwort kommen.
        val phone = hallo("aaa", gedrueckt = true)
        val tablet = hallo("zzz", gedrueckt = true)

        assertThat(ichRechne(phone, tablet)).isTrue()
        assertThat(ichRechne(tablet, phone)).isFalse()
    }

    @Test
    fun genau_eine_seite_rechnet_immer() {
        // Die eigentliche Eigenschaft: nie beide, nie keiner — egal in welcher Lage.
        val faelle = listOf(
            hallo("aaa", gedrueckt = true) to hallo("zzz", gedrueckt = true),
            hallo("aaa", gedrueckt = true) to hallo("zzz", gedrueckt = false),
            hallo("aaa", gedrueckt = false) to hallo("zzz", gedrueckt = true),
            hallo("aaa", gedrueckt = false) to hallo("zzz", gedrueckt = false),
            hallo("zzz", gedrueckt = true) to hallo("aaa", gedrueckt = false),
        )

        for ((meine, fremde) in faelle) {
            assertThat(ichRechne(meine, fremde)).isNotEqualTo(ichRechne(fremde, meine))
        }
    }

    // -- Vorprüfungen (E9) -------------------------------------------------

    @Test
    fun beide_geraete_kommen_zum_selben_handschlag_ergebnis() {
        val phone = hallo("aaa", gedrueckt = true)
        val tablet = hallo("zzz")

        val ausSichtDesPhones = pruefeHandschlag(phone, tablet)
        val ausSichtDesTablets = pruefeHandschlag(tablet, phone)

        assertThat(ausSichtDesPhones).isEqualTo(Handschlag.Bereit(ichRechne = true))
        assertThat(ausSichtDesTablets).isEqualTo(Handschlag.Bereit(ichRechne = false))
    }

    @Test
    fun eine_laufende_session_auf_einer_der_beiden_seiten_verhindert_den_abgleich() {
        val ruhig = hallo("aaa", gedrueckt = true)
        val beschaeftigt = hallo("zzz", session = true)

        // Und zwar von beiden Seiten aus gesehen — mit dem jeweils passenden Text.
        val hier = pruefeHandschlag(beschaeftigt, ruhig) as Handschlag.Abbruch
        val dort = pruefeHandschlag(ruhig, beschaeftigt) as Handschlag.Abbruch

        assertThat(hier.grund).contains("diesem Gerät")
        assertThat(dort.grund).contains("anderen Gerät")
    }

    @Test
    fun ein_hoeheres_schema_der_gegenseite_bricht_ab_und_nennt_das_richtige_geraet() {
        val alt = hallo("aaa", schema = 7, gedrueckt = true)
        val neu = hallo("zzz", schema = 8)

        val aufDemAlten = pruefeHandschlag(alt, neu) as Handschlag.Abbruch
        val aufDemNeuen = pruefeHandschlag(neu, alt) as Handschlag.Abbruch

        assertThat(aufDemAlten.grund).contains("dieses Gerät")
        assertThat(aufDemNeuen.grund).contains("andere Gerät")
    }

    @Test
    fun zu_wenig_platz_wird_vorher_bemerkt_und_zwar_mit_doppeltem_bedarf() {
        // Ablauf 27: die empfangene Datei liegt neben dem eigenen Stand, und Nearby legt
        // zusätzlich seine eigene Kopie der Payload ab.
        val voll = hallo("aaa", gedrueckt = true, platz = 150_000, groesse = 120_000)
        val leer = hallo("zzz", platz = 1_000_000_000, groesse = 120_000)

        assertThat(pruefeHandschlag(voll, leer)).isInstanceOf(Handschlag.Abbruch::class.java)
        // Knapp über dem Doppelten reicht.
        val geradeSo = voll.copy(freierPlatz = 240_001)
        assertThat(pruefeHandschlag(geradeSo, leer)).isInstanceOf(Handschlag.Bereit::class.java)
    }

    @Test
    fun verschiedene_protokoll_fassungen_weigern_sich_sauber() {
        val alt = hallo("aaa", gedrueckt = true, protokoll = 1)
        val neu = hallo("zzz", protokoll = 2)

        assertThat(pruefeHandschlag(alt, neu)).isInstanceOf(Handschlag.Abbruch::class.java)
    }

    // -- Medien ------------------------------------------------------------

    @Test
    fun nur_fehlende_medien_werden_uebertragen() {
        // Der Gewinn der Inhaltsadressierung: zwei Namenslisten vergleichen genügt (E5).
        val meine = listOf("aaa.mp4", "bbb.jpg", "ccc.mp4")
        val fremde = listOf("bbb.jpg", "ddd.mp4")

        assertThat(fehlendeMedien(meine, fremde)).containsExactly("aaa.mp4", "ccc.mp4").inOrder()
        assertThat(fehlendeMedien(fremde, meine)).containsExactly("ddd.mp4")
    }

    @Test
    fun ohne_unterschied_wird_kein_medium_uebertragen() {
        val gleich = listOf("aaa.mp4", "bbb.jpg")

        assertThat(fehlendeMedien(gleich, gleich)).isEmpty()
    }

    // -- Serialisierung ----------------------------------------------------

    @Test
    fun nachrichten_ueberstehen_den_weg_durch_json() {
        val json = Json
        val nachrichten = listOf<Nachricht>(
            hallo("aaa", gedrueckt = true).copy(generation = 3, erzeugtVon = "zzz", basiertAuf = 2),
            Nachricht.Abbruch("kein Platz"),
            Nachricht.Medienliste(listOf("aaa.mp4")),
            Nachricht.DateiFolgt(42L, DateiArt.MEDIUM, "aaa.mp4", 1234),
            Nachricht.Fertig,
            Nachricht.ErstbegegnungEntschieden(meinStandGewinnt = true),
        )

        for (n in nachrichten) {
            val zurueck = json.decodeFromString<Nachricht>(json.encodeToString(n))
            assertThat(zurueck).isEqualTo(n)
        }
    }

    @Test
    fun das_ergebnis_ueberlebt_die_uebertragung_mit_allen_feldtypen() {
        // Das rechnende Gerät schickt das Ergebnis, nicht die Aufgabe (E12). Kommt dabei
        // ein Feldtyp durcheinander, stehen hinterher falsche Daten drüben.
        val paket = Anweisungspaket(
            operationen = listOf(
                Operation.Einfuegen(
                    "route", 7,
                    mapOf(
                        "name" to Feld.Text("Dachrinne"),
                        "attempts" to Feld.Zahl(3),
                        "sektor" to Feld.Leer,
                        "irgendwas" to Feld.Komma(1.5),
                    ),
                ),
                Operation.Aendern("gym", 1, mapOf("name" to Feld.Text("Halle Süd"))),
                Operation.Loeschen("session", 9),
            ),
            generation = 4,
            erzeugtVon = "aaa",
            basiertAuf = 3,
        )

        val zurueck = Json.decodeFromString<Anweisungspaket>(Json.encodeToString(paket))

        assertThat(zurueck).isEqualTo(paket)
    }

    @Test
    fun ein_grosses_ergebnis_passt_nicht_mehr_in_eine_bytes_payload() {
        // Der Grund, warum Anweisungen als DATEI gehen (Ablauf 22): Nearby deckelt BYTES,
        // und ein Abgleich nach zwei Wochen Urlaub sprengt das mühelos.
        val viele = Anweisungspaket(
            operationen = (1..500).map {
                Operation.Einfuegen(
                    "route", it,
                    mapOf("name" to Feld.Text("Boulder Nummer $it"), "notes" to Feld.Leer),
                )
            },
            generation = 1,
            erzeugtVon = "aaa",
            basiertAuf = null,
        )

        assertThat(Json.encodeToString(viele).toByteArray().size).isGreaterThan(BYTES_OBERGRENZE)
    }

    // -- Berechtigungen ----------------------------------------------------

    @Test
    fun ab_android_13_wird_kein_standort_mehr_verlangt() {
        val rechte = NearbyBerechtigungen.fuer(Build.VERSION_CODES.TIRAMISU)

        assertThat(rechte).contains(android.Manifest.permission.NEARBY_WIFI_DEVICES)
        assertThat(rechte).doesNotContain(android.Manifest.permission.ACCESS_FINE_LOCATION)
        assertThat(NearbyBerechtigungen.brauchtStandort(Build.VERSION_CODES.TIRAMISU)).isFalse()
    }

    @Test
    fun bis_android_12_verlangt_nearby_den_genauen_standort() {
        val rechte = NearbyBerechtigungen.fuer(Build.VERSION_CODES.S)

        assertThat(rechte).contains(android.Manifest.permission.ACCESS_FINE_LOCATION)
        assertThat(rechte).contains(android.Manifest.permission.BLUETOOTH_SCAN)
        assertThat(NearbyBerechtigungen.brauchtStandort(Build.VERSION_CODES.S)).isTrue()
        // Und das muss vorher erklärt werden, nicht per Systemdialog aus dem Nichts kommen.
        assertThat(NearbyBerechtigungen.begruendung(Build.VERSION_CODES.S))
            .contains("Standort")
    }

    @Test
    fun vor_android_12_werden_die_neuen_bluetooth_rechte_nicht_angefragt() {
        // Android verweigert eine Anfrage nach einer Berechtigung, die es noch nicht gibt.
        val rechte = NearbyBerechtigungen.fuer(Build.VERSION_CODES.R)

        assertThat(rechte).doesNotContain(android.Manifest.permission.BLUETOOTH_SCAN)
        assertThat(rechte).doesNotContain(android.Manifest.permission.BLUETOOTH_CONNECT)
        assertThat(rechte).contains(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
