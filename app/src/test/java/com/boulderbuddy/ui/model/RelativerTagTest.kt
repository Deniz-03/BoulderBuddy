package com.boulderbuddy.ui.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

/**
 * Die menschliche Kurzform eines Datums — und warum sie das Jahr braucht.
 *
 * Alle Aufrufer sind Listen, die weit zurueckreichen: die Tagesauswahl der Statistik, die
 * Sessions-Historie, die Hangboard-Historie. Ohne Jahreszahl heissen zwei Tage im Abstand
 * eines Jahres gleich, und in der Tagesauswahl stehen sie als zwei Chips nebeneinander, die
 * derselbe Tag zu sein scheinen.
 */
class RelativerTagTest {

    private val heute = LocalDate.of(2026, 8, 29)

    @Test
    fun heuteUndGesternBleibenWoerter() {
        assertThat(formatRelativeDay(heute, heute)).isEqualTo("Heute")
        assertThat(formatRelativeDay(heute.minusDays(1), heute)).isEqualTo("Gestern")
    }

    @Test
    fun einTagDesselbenJahresNenntKeinJahr() {
        // Im laufenden Jahr ist die Jahreszahl reines Rauschen — sie kann nichts unterscheiden.
        assertThat(formatRelativeDay(LocalDate.of(2026, 6, 12), heute)).isEqualTo("12. Juni")
    }

    @Test
    fun einTagAusEinemAnderenJahrNenntSeinJahr() {
        assertThat(formatRelativeDay(LocalDate.of(2025, 6, 12), heute))
            .isEqualTo("12. Juni 2025")
    }

    @Test
    fun zweiTageEinJahrAuseinanderHeissenVerschieden() {
        // Der eigentliche Befund: beide waeren "27. August" gewesen.
        val juenger = formatRelativeDay(LocalDate.of(2026, 8, 27), heute)
        val aelter = formatRelativeDay(LocalDate.of(2025, 8, 27), heute)

        assertThat(juenger).isNotEqualTo(aelter)
    }

    @Test
    fun derMillis_wegLiefertDasselbe() {
        // Beide Ueberladungen muessen dasselbe sagen — sonst hiesse derselbe Tag in der
        // Statistik anders als in der Sessions-Liste.
        val tag = LocalDate.of(2025, 6, 12)
        val millis = tag.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

        assertThat(formatRelativeDay(millis, heute)).isEqualTo(formatRelativeDay(tag, heute))
    }
}
