package com.boulderbuddy.data.export

import com.boulderbuddy.data.db.entity.RouteEntity
import com.boulderbuddy.data.db.entity.SessionEntity
import com.boulderbuddy.data.model.RouteStatus
import com.google.common.truth.Truth.assertThat
import java.util.TimeZone
import org.junit.Test

/**
 * Der CSV-Export war die einzige Ausgabe der App ohne Test — geprüft wurde er bisher nur, indem
 * jemand die Datei am Gerät aufmachte, und der SAF-Dialog ist per adb kaum zu bedienen.
 *
 * Geprüft wird deshalb hier, was die Datei unbrauchbar macht, ohne dass es auffällt: falsches
 * Quoting (eine Notiz mit Komma verschiebt alle folgenden Spalten), fehlendes BOM (Excel zeigt
 * „Ãœ" statt „Ü"), verlorene Sessions ohne Routen, und IDs statt Namen.
 */
class SessionCsvTest {

    // Feste Zeitzone im Test: sonst hinge das erwartete Datum davon ab, wo der Rechner steht.
    private val utc = TimeZone.getTimeZone("UTC")
    private val start = 1_700_000_000_000L // 2023-11-14 22:13 UTC
    private val ende = 1_700_003_600_000L // eine Stunde später

    private fun session(
        id: Int = 1,
        gymId: Int? = 1,
        gymName: String = "Boulderwelt",
        gradeSystemId: Int? = 10,
        durationMin: Int? = 90,
        notes: String? = null,
        endedAt: Long? = ende,
    ) = SessionEntity(
        id = id,
        gymId = gymId,
        gymName = gymName,
        gradeSystemId = gradeSystemId,
        date = start,
        durationMin = durationMin,
        notes = notes,
        endedAt = endedAt,
    )

    private fun route(
        id: Int = 100,
        sessionId: Int = 1,
        gradeId: Int? = 50,
        name: String = "Dachrinne",
        sektor: String? = "A",
        attempts: Int = 3,
        status: RouteStatus = RouteStatus.SENT,
        color: String? = "red",
        notes: String? = null,
    ) = RouteEntity(
        id = id,
        sessionId = sessionId,
        gradeId = gradeId,
        name = name,
        sektor = sektor,
        attempts = attempts,
        status = status,
        color = color,
        notes = notes,
    )

    private fun baue(
        sessions: List<SessionEntity>,
        routes: List<RouteEntity> = emptyList(),
        gradeLabels: Map<Int, String> = mapOf(50 to "6b"),
        systemNames: Map<Int, String> = mapOf(10 to "Hausfarben"),
        gymNames: Map<Int, String> = mapOf(1 to "Boulderwelt"),
    ) = SessionCsv.baue(
        sessions = sessions,
        routesBySession = routes.groupBy { it.sessionId },
        gradeLabels = gradeLabels,
        systemNames = systemNames,
        gymNames = gymNames,
        zeitzone = utc,
    )

    /** Zeilen ohne BOM und ohne den Zeilenumbruch am Ende. */
    private fun zeilen(csv: String) = csv.removePrefix("﻿").split("\r\n").dropLast(1)

    @Test
    fun beginnt_mit_bom_und_kopfzeile() {
        val csv = baue(listOf(session()), listOf(route()))

        // Ohne das BOM liest Excel die Datei als ANSI — aus "Fingerlöcher" wird "FingerlÃ¶cher".
        assertThat(csv.first()).isEqualTo('﻿')
        assertThat(zeilen(csv).first()).isEqualTo(SessionCsv.HEADER.joinToString(","))
    }

    @Test
    fun zeilen_enden_mit_crlf() {
        val csv = baue(listOf(session()), listOf(route()))

        assertThat(csv.endsWith("\r\n")).isTrue()
        // Kein nacktes \n: das waere ein Zeilenumbruch innerhalb eines Feldes.
        assertThat(csv.replace("\r\n", "")).doesNotContain("\n")
    }

    @Test
    fun ids_stehen_als_namen_in_der_datei() {
        val csv = baue(listOf(session()), listOf(route()))

        // Halle, Gradsystem und Grad-Label — ohne die waere das CSV ohne die DB nicht lesbar.
        assertThat(zeilen(csv)[1]).isEqualTo(
            "1,2023-11-14 22:13,2023-11-14 23:13,Boulderwelt,Hausfarben,90,," +
                "Dachrinne,A,6b,red,3,SENT,",
        )
    }

    @Test
    fun eine_session_ohne_routen_behaelt_ihre_zeile() {
        val csv = baue(listOf(session()))

        val zeile = zeilen(csv)[1]
        assertThat(zeilen(csv)).hasSize(2)
        assertThat(zeile).startsWith("1,2023-11-14 22:13,2023-11-14 23:13,Boulderwelt,Hausfarben,90,")
        // Die sieben Route-Spalten bleiben leer, statt zu fehlen — sonst verrutscht die Tabelle.
        assertThat(zeile.split(",")).hasSize(SessionCsv.HEADER.size)
    }

    @Test
    fun jede_route_bekommt_eine_zeile_mit_denselben_session_spalten() {
        val csv = baue(
            listOf(session()),
            listOf(route(id = 100, name = "Dachrinne"), route(id = 101, name = "Ecke")),
        )

        val datenzeilen = zeilen(csv).drop(1)
        assertThat(datenzeilen).hasSize(2)
        assertThat(datenzeilen.map { it.substringBefore(",Dachrinne").substringBefore(",Ecke") })
            .containsExactly(
                "1,2023-11-14 22:13,2023-11-14 23:13,Boulderwelt,Hausfarben,90,",
                "1,2023-11-14 22:13,2023-11-14 23:13,Boulderwelt,Hausfarben,90,",
            )
    }

    @Test
    fun ein_komma_in_der_notiz_verschiebt_keine_spalte() {
        val csv = baue(listOf(session(notes = "gut gelaufen, nur die Ecke nicht")))

        assertThat(zeilen(csv)[1]).contains("\"gut gelaufen, nur die Ecke nicht\"")
        assertThat(zeilen(csv)[1].split(",")).hasSize(SessionCsv.HEADER.size + 1)
    }

    @Test
    fun anfuehrungszeichen_werden_verdoppelt() {
        val csv = baue(listOf(session()), listOf(route(name = "Der \"Ofen\"")))

        assertThat(zeilen(csv)[1]).contains("\"Der \"\"Ofen\"\"\"")
    }

    @Test
    fun ein_zeilenumbruch_im_feld_wird_gequotet() {
        val csv = baue(listOf(session(notes = "erste Zeile\nzweite Zeile")))

        assertThat(csv).contains("\"erste Zeile\nzweite Zeile\"")
    }

    @Test
    fun ein_semikolon_braucht_keine_quotes() {
        // Getrennt wird mit Komma — ein Semikolon ist hier gewoehnlicher Text.
        val csv = baue(listOf(session(notes = "Satz eins; Satz zwei")))

        assertThat(zeilen(csv)[1]).contains(",Satz eins; Satz zwei,")
        assertThat(zeilen(csv)[1]).doesNotContain("\"")
    }

    @Test
    fun umlaute_bleiben_umlaute() {
        val csv = baue(listOf(session()), listOf(route(name = "Fingerlöcher", sektor = "Höhle")))

        assertThat(zeilen(csv)[1]).contains("Fingerlöcher,Höhle")
    }

    @Test
    fun eine_geloeschte_halle_hinterlaesst_keine_leere_zelle() {
        // gymId == null heisst: die Halle ist weg. Der Export ist ein Beleg — wo trainiert
        // wurde, muss trotzdem dastehen.
        val csv = baue(listOf(session(gymId = null, gymName = "Boulderwelt")), gymNames = emptyMap())

        assertThat(zeilen(csv)[1]).contains(",Boulderwelt,")
    }

    @Test
    fun eine_laufende_session_heisst_aktiv_statt_leer() {
        val csv = baue(listOf(session(endedAt = null, durationMin = null)))

        assertThat(zeilen(csv)[1]).startsWith("1,2023-11-14 22:13,aktiv,")
    }

    @Test
    fun unbekannte_ids_werden_zu_leeren_zellen_statt_zu_zahlen() {
        // Kann nach einem Abgleich vorkommen: die Zeile ist weg, die ID stand noch da.
        val csv = baue(
            listOf(session(gradeSystemId = 999)),
            listOf(route(gradeId = 999)),
            gradeLabels = emptyMap(),
            systemNames = emptyMap(),
        )

        // Weder "999" noch "null" darf in der Datei stehen.
        assertThat(zeilen(csv)[1]).doesNotContain("999")
        assertThat(zeilen(csv)[1]).doesNotContain("null")
    }

    @Test
    fun ohne_sessions_bleibt_nur_die_kopfzeile() {
        val csv = baue(emptyList())

        assertThat(zeilen(csv)).hasSize(1)
    }
}
