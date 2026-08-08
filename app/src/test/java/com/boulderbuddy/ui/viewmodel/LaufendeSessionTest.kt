package com.boulderbuddy.ui.viewmodel

import com.boulderbuddy.data.db.entity.GymEntity
import com.boulderbuddy.data.db.entity.SessionEntity
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Der Hinweis „es läuft schon eine Session" beim Anlegen einer weiteren.
 *
 * Mehrere gleichzeitig sind erlaubt — vormittags Hangboard, abends Halle. Vorher entstand die
 * zweite aber **kommentarlos**, und die erste lief unbemerkt weiter, bis sie irgendwann in der
 * Liste auffiel. Beim Doppeltipp-Fehler des Geräte-Testlaufs standen so drei gleichzeitig da.
 */
class LaufendeSessionTest {

    private fun millisAm(tag: LocalDate, stunde: Int, minute: Int): Long =
        tag.atTime(stunde, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val heute = LocalDate.now()
    private val halle = GymEntity(id = 1, name = "Boulder World München")

    @Test
    fun ohne_laufende_session_gibt_es_keinen_hinweis() {
        val sessions = listOf(
            SessionEntity(id = 1, gymId = 1, date = 1_000, endedAt = 2_000),
        )

        assertThat(laufendeSessions(sessions, listOf(halle))).isNull()
    }

    @Test
    fun nennt_halle_und_startzeit_der_laufenden_session() {
        val sessions = listOf(
            SessionEntity(id = 1, gymId = 1, date = millisAm(heute, 14, 20), endedAt = null),
        )

        val hinweis = laufendeSessions(sessions, listOf(halle))!!

        assertThat(hinweis.anzahl).isEqualTo(1)
        assertThat(hinweis.gymName).isEqualTo("Boulder World München")
        assertThat(hinweis.seit).isEqualTo("seit 14:20")
    }

    @Test
    fun bei_mehreren_zaehlt_der_hinweis_und_nennt_die_juengste() {
        val sessions = listOf(
            SessionEntity(id = 1, gymId = 1, date = millisAm(heute, 9, 0), endedAt = null),
            SessionEntity(id = 2, gymId = 2, date = millisAm(heute, 18, 30), endedAt = null),
            // Eine beendete zählt nicht mit.
            SessionEntity(id = 3, gymId = 1, date = millisAm(heute, 20, 0), endedAt = 1L),
        )
        val hallen = listOf(halle, GymEntity(id = 2, name = "Kletterwerk"))

        val hinweis = laufendeSessions(sessions, hallen)!!

        assertThat(hinweis.anzahl).isEqualTo(2)
        // Die zuletzt gestartete ist die, an die man gerade denkt — nicht die älteste.
        assertThat(hinweis.gymName).isEqualTo("Kletterwerk")
        assertThat(hinweis.seit).isEqualTo("seit 18:30")
    }

    @Test
    fun eine_geloeschte_halle_liefert_den_namen_aus_der_session() {
        val sessions = listOf(
            SessionEntity(
                id = 1,
                gymId = null,
                gymName = "Boulder World München",
                date = millisAm(heute, 14, 20),
                endedAt = null,
            ),
        )

        // Ohne die Halle greift der Schnappschuss aus der Session (v10) — der Hinweis darf
        // nicht "unbekannte Halle" sagen, nur weil die Halle gelöscht wurde.
        assertThat(laufendeSessions(sessions, emptyList())!!.gymName)
            .isEqualTo("Boulder World München")
    }

    @Test
    fun eine_session_von_gestern_nennt_den_tag_dazu() {
        val gestern = heute.minusDays(1)
        val sessions = listOf(
            SessionEntity(id = 1, gymId = 1, date = millisAm(gestern, 21, 5), endedAt = null),
        )

        // "seit 21:05" allein wäre irreführend: die Session läuft seit gestern Abend.
        assertThat(laufendeSessions(sessions, listOf(halle))!!.seit)
            .isEqualTo("seit Gestern, 21:05")
    }
}
