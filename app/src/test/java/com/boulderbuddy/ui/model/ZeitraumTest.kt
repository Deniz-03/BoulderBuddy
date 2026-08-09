package com.boulderbuddy.ui.model

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.time.LocalDate

/**
 * Die Einteilung in Zeitabschnitte — reine Funktionen über Datumsangaben, deshalb ohne Gerät
 * prüfbar. Der feste Bezugstag macht die Tests unabhängig vom Ausführungszeitpunkt; alles
 * andere wäre ein Test, der im Januar anders ausgeht als im Juli.
 */
class ZeitraumTest {

    // Ein Mittwoch. Bewusst nicht Montag oder Monatserster, damit die Verschiebung auf den
    // Abschnittsanfang überhaupt etwas zu tun hat.
    private val mittwoch = LocalDate.of(2026, 8, 5)

    @Test
    fun wocheBeginntAmMontag() {
        assertThat(eimerStart(mittwoch, Zeitraum.Woche)).isEqualTo(LocalDate.of(2026, 8, 3))
    }

    @Test
    fun montagBleibtAufSichSelbst() {
        // previousOrSame, nicht previous — sonst rutschte jeder Montag eine Woche zurück.
        val montag = LocalDate.of(2026, 8, 3)
        assertThat(eimerStart(montag, Zeitraum.Woche)).isEqualTo(montag)
    }

    @Test
    fun sonntagGehoertNochZurVorwoche() {
        // ISO-8601: die Woche endet am Sonntag. Ein Sonntag darf nicht bereits die nächste
        // Woche eröffnen, sonst landet eine Sonntags-Session im falschen Balken.
        val sonntag = LocalDate.of(2026, 8, 9)
        assertThat(eimerStart(sonntag, Zeitraum.Woche)).isEqualTo(LocalDate.of(2026, 8, 3))
    }

    @Test
    fun monatUndJahrBeginnenAmErsten() {
        assertThat(eimerStart(mittwoch, Zeitraum.Monat)).isEqualTo(LocalDate.of(2026, 8, 1))
        assertThat(eimerStart(mittwoch, Zeitraum.Jahr)).isEqualTo(LocalDate.of(2026, 1, 1))
    }

    @Test
    fun reiheEndetImAktuellenAbschnittUndIstAeltesteZuerst() {
        Zeitraum.entries.forEach { zeitraum ->
            val reihe = eimerReihe(mittwoch, zeitraum)

            assertWithMessage("$zeitraum: falsche Anzahl Abschnitte")
                .that(reihe).hasSize(zeitraum.eimer)
            assertWithMessage("$zeitraum: der letzte Abschnitt ist nicht der aktuelle")
                .that(reihe.last()).isEqualTo(eimerStart(mittwoch, zeitraum))
            assertWithMessage("$zeitraum: die Reihe ist nicht aufsteigend sortiert")
                .that(reihe).isInOrder()
        }
    }

    @Test
    fun reiheHatKeineLuecken() {
        // Der eigentliche Zweck der Reihe: ein Abschnitt ohne Aktivität muss trotzdem
        // vorkommen. Fehlte er, rückten zwei aktive Wochen optisch zusammen, zwischen denen
        // ein Monat Pause lag — und das Diagramm behauptete durchgehendes Training.
        val wochen = eimerReihe(mittwoch, Zeitraum.Woche)
        wochen.zipWithNext { a, b ->
            assertWithMessage("Sprung von $a auf $b").that(b).isEqualTo(a.plusWeeks(1))
        }

        val monate = eimerReihe(mittwoch, Zeitraum.Monat)
        monate.zipWithNext { a, b ->
            assertWithMessage("Sprung von $a auf $b").that(b).isEqualTo(a.plusMonths(1))
        }
    }

    @Test
    fun reiheLaeuftUeberJahresgrenzenHinweg() {
        // Anfang Januar reicht die Wochenreihe ins Vorjahr zurück. Ein naives „Woche minus n"
        // auf Basis der Kalenderwoche würde hier bei KW 1 anschlagen.
        val anfangJanuar = LocalDate.of(2026, 1, 7)
        val reihe = eimerReihe(anfangJanuar, Zeitraum.Woche)

        assertThat(reihe.first().year).isEqualTo(2025)
        assertThat(reihe.last()).isEqualTo(LocalDate.of(2026, 1, 5))
    }

    @Test
    fun schaltjahrEndeFebruar() {
        // 2028 ist ein Schaltjahr. `withDayOfMonth(1)` und `minusMonths` dürfen daran nicht
        // scheitern — der 29.2. existiert nur alle vier Jahre.
        val schalttag = LocalDate.of(2028, 2, 29)
        assertThat(eimerStart(schalttag, Zeitraum.Monat)).isEqualTo(LocalDate.of(2028, 2, 1))
        assertThat(eimerReihe(schalttag, Zeitraum.Monat)).hasSize(Zeitraum.Monat.eimer)
    }

    @Test
    fun labelsSindKurzUndUnterscheidbar() {
        val start = LocalDate.of(2026, 8, 3)
        assertThat(eimerLabel(start, Zeitraum.Woche)).isEqualTo("3.8.")
        assertThat(eimerLabel(start, Zeitraum.Jahr)).isEqualTo("2026")

        // Über ALLE zwölf Monate, nicht nur über den einen oben. Die erste Fassung dieses
        // Tests prüfte nur den August („Aug.", 4 Zeichen) und wäre an „Sept." vorbeigelaufen.
        //
        // Höchstens DREI Zeichen: bei zwölf Spalten auf Telefonbreite (~26 dp je Spalte)
        // brach schon „Sept." um und versetzte die Balken gegeneinander. Die frühere
        // Schranke von 5 hat genau diesen Fall durchgelassen.
        (1..12).forEach { monatNr ->
            val monat = eimerLabel(LocalDate.of(2026, monatNr, 1), Zeitraum.Monat)
            assertWithMessage("Monatslabel '$monat' ist zu lang für die Achse")
                .that(monat.length).isAtMost(3)
        }
    }

    @Test
    fun labelsEinerReiheSindEindeutig() {
        // Zwei gleich beschriftete Balken nebeneinander wären nicht zuzuordnen.
        Zeitraum.entries.forEach { zeitraum ->
            val labels = eimerReihe(mittwoch, zeitraum).map { eimerLabel(it, zeitraum) }
            assertWithMessage("$zeitraum: doppelte Beschriftungen in $labels")
                .that(labels).containsNoDuplicates()
        }
    }
}
