package com.boulderbuddy.ui.viewmodel

import com.boulderbuddy.data.db.entity.GradeEntity
import com.boulderbuddy.data.db.entity.RouteEntity
import com.boulderbuddy.data.db.entity.SessionEntity
import com.boulderbuddy.data.model.RouteStatus
import com.boulderbuddy.ui.model.Zeitraum
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Die Aggregation der beiden Verlaufs-Diagramme ([routenVerlauf], [gradVerlauf]).
 *
 * **Festes `heute` statt `LocalDate.now()`.** Beide Funktionen bekommen den Stichtag als
 * Parameter — deshalb steht hier ein fester Mittwoch und kein „vor drei Wochen". Ein Test,
 * der relativ zu heute rechnet, würde bei einem Lauf um Mitternacht kippen und wäre bei
 * Monats- und Jahresgrenzen ohnehin nicht mehr nachvollziehbar.
 *
 * Der Stichtag ist mit Bedacht gewählt: Mittwoch, 15.07.2026 — nicht der Wochenanfang
 * (sonst fiele nicht auf, wenn `eimerStart` den Montag verfehlt), nicht der Monatserste
 * und nicht der 1. Januar.
 */
class StatistikVerlaufTest {

    private val heute: LocalDate = LocalDate.of(2026, 7, 15)

    // --- Testdaten -----------------------------------------------------------

    // Zwei Systeme, damit die Trennung im Gradverlauf prüfbar ist. `order` zählt je System
    // ab 0 — genau darum sind die Werte zwischen den Systemen nicht vergleichbar.
    private val franzoesisch = listOf(
        GradeEntity(id = 10, systemId = 1, label = "6a", order = 0),
        GradeEntity(id = 11, systemId = 1, label = "6b", order = 1),
        GradeEntity(id = 12, systemId = 1, label = "6c", order = 2),
    )
    private val vScale = listOf(
        GradeEntity(id = 20, systemId = 2, label = "V3", order = 0),
        GradeEntity(id = 21, systemId = 2, label = "V4", order = 1),
    )
    private val gradesById = (franzoesisch + vScale).associateBy { it.id }

    private fun LocalDate.alsMillis(): Long =
        atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun session(id: Int, tag: LocalDate) =
        SessionEntity(id = id, gymId = 1, date = tag.alsMillis())

    private fun route(id: Int, sessionId: Int, gradeId: Int? = null, getoppt: Boolean = true) =
        RouteEntity(
            id = id,
            sessionId = sessionId,
            gradeId = gradeId,
            status = if (getoppt) RouteStatus.SENT else RouteStatus.OPEN,
            attempts = 1,
        )

    // --- routenVerlauf -------------------------------------------------------

    @Test
    fun `routenVerlauf zaehlt je Woche und laesst Luecken als Null stehen`() {
        // Diese Woche (Mo 13.07.): 2 Routen. Vor zwei Wochen (Mo 29.06.): 1 Route.
        // Dazwischen — die Woche ab 06.07. — nichts.
        val sessions = listOf(
            session(id = 1, tag = heute),
            session(id = 2, tag = heute.minusDays(1)),        // Di 14.07., dieselbe Woche
            session(id = 3, tag = heute.minusWeeks(2)),       // Mi 01.07.
        ).associateBy { it.id }
        val routes = listOf(
            route(id = 1, sessionId = 1),
            route(id = 2, sessionId = 2),
            route(id = 3, sessionId = 3),
        )

        val verlauf = routenVerlauf(routes, sessions, Zeitraum.Woche, heute)

        // Acht Wochen-Eimer, ältester zuerst — auch die sechs leeren stehen drin.
        assertThat(verlauf).hasSize(Zeitraum.Woche.eimer)
        assertThat(verlauf.map { it.value }).containsExactly(0f, 0f, 0f, 0f, 0f, 1f, 0f, 2f)
            .inOrder()

        // Beschriftung ist das Datum des Montags.
        assertThat(verlauf.last().label).isEqualTo("13.7.")
        assertThat(verlauf[5].label).isEqualTo("29.6.")
    }

    @Test
    fun `routenVerlauf ist unkumuliert - ein spaeterer Balken erbt nichts`() {
        // Alles in EINER alten Woche. Wäre der Verlauf kumuliert, stünden die folgenden
        // Wochen ebenfalls auf 3 statt auf 0.
        val sessions = listOf(session(id = 1, tag = heute.minusWeeks(5))).associateBy { it.id }
        val routes = (1..3).map { route(id = it, sessionId = 1) }

        val verlauf = routenVerlauf(routes, sessions, Zeitraum.Woche, heute)

        assertThat(verlauf.map { it.value }).containsExactly(0f, 0f, 3f, 0f, 0f, 0f, 0f, 0f)
            .inOrder()
    }

    @Test
    fun `routenVerlauf zaehlt jede Route, nicht jede Session`() {
        // Zwei Sessions in derselben Woche mit zusammen vier Routen → ein Balken mit 4.
        val sessions = listOf(
            session(id = 1, tag = heute),
            session(id = 2, tag = heute.minusDays(2)),
        ).associateBy { it.id }
        val routes = listOf(
            route(id = 1, sessionId = 1),
            route(id = 2, sessionId = 1),
            route(id = 3, sessionId = 2),
            // Offen — der Routenverlauf zählt alles Geklettertes, nicht nur Tops.
            route(id = 4, sessionId = 2, getoppt = false),
        )

        val verlauf = routenVerlauf(routes, sessions, Zeitraum.Woche, heute)

        assertThat(verlauf.last().value).isEqualTo(4f)
    }

    @Test
    fun `routenVerlauf uebergeht Routen ohne auffindbare Session`() {
        // Verwaiste Route (sessionId zeigt ins Leere) darf die Reihe nicht kippen.
        val sessions = listOf(session(id = 1, tag = heute)).associateBy { it.id }
        val routes = listOf(route(id = 1, sessionId = 1), route(id = 2, sessionId = 999))

        val verlauf = routenVerlauf(routes, sessions, Zeitraum.Woche, heute)

        assertThat(verlauf.sumOf { it.value.toDouble() }).isEqualTo(1.0)
    }

    @Test
    fun `routenVerlauf liefert je Koernung die vorgesehene Anzahl Abschnitte`() {
        val sessions = listOf(session(id = 1, tag = heute)).associateBy { it.id }
        val routes = listOf(route(id = 1, sessionId = 1))

        Zeitraum.entries.forEach { zeitraum ->
            val verlauf = routenVerlauf(routes, sessions, zeitraum, heute)
            assertThat(verlauf).hasSize(zeitraum.eimer)
            // Der jüngste Abschnitt ist immer der, in dem `heute` liegt.
            assertThat(verlauf.last().value).isEqualTo(1f)
        }
    }

    @Test
    fun `routenVerlauf faellt bei Monat und Jahr in den richtigen Abschnitt`() {
        val sessions = listOf(
            session(id = 1, tag = heute),                 // Juli 2026
            session(id = 2, tag = heute.minusMonths(3)),  // April 2026
            session(id = 3, tag = heute.minusYears(2)),   // Juli 2024
        ).associateBy { it.id }
        val routes = (1..3).map { route(id = it, sessionId = it) }

        val monate = routenVerlauf(routes, sessions, Zeitraum.Monat, heute)
        // 12 Monate, jüngster zuletzt: Juli 2026 = Index 11, April 2026 = Index 8.
        assertThat(monate.last().value).isEqualTo(1f)
        assertThat(monate.last().label).isEqualTo("Jul")
        assertThat(monate[8].value).isEqualTo(1f)

        val jahre = routenVerlauf(routes, sessions, Zeitraum.Jahr, heute)
        // 5 Jahre: 2022..2026. 2026 = Index 4 (zwei Routen: heute + April), 2024 = Index 2.
        assertThat(jahre.map { it.label }).containsExactly("2022", "2023", "2024", "2025", "2026")
            .inOrder()
        assertThat(jahre.map { it.value }).containsExactly(0f, 0f, 1f, 0f, 2f).inOrder()
    }

    @Test
    fun `routenVerlauf faerbt alle Balken gleich`() {
        val sessions = listOf(
            session(id = 1, tag = heute),
            session(id = 2, tag = heute.minusWeeks(3)),
        ).associateBy { it.id }
        val routes = listOf(route(id = 1, sessionId = 1), route(id = 2, sessionId = 2))

        val verlauf = routenVerlauf(routes, sessions, Zeitraum.Woche, heute)

        // Eine Farbe für alle: hier steht keine Farbe für eine Kategorie, es vergeht nur Zeit.
        assertThat(verlauf.map { it.color }.distinct()).hasSize(1)
    }

    // --- gradVerlauf ---------------------------------------------------------

    @Test
    fun `gradVerlauf nimmt das Maximum je Abschnitt, nicht den Durchschnitt`() {
        // Eine Woche mit viel Leichtem und einem schweren Top. Ein Mittelwert läge bei "6a",
        // gemeint ist aber: in dieser Woche war 6c drin.
        val sessions = listOf(session(id = 1, tag = heute)).associateBy { it.id }
        val topped = listOf(
            route(id = 1, sessionId = 1, gradeId = 10), // 6a, order 0
            route(id = 2, sessionId = 1, gradeId = 10), // 6a
            route(id = 3, sessionId = 1, gradeId = 10), // 6a
            route(id = 4, sessionId = 1, gradeId = 12), // 6c, order 2
        )

        val verlauf = gradVerlauf(topped, sessions, gradesById, Zeitraum.Woche, heute)

        val franzoesischeKurve = verlauf.getValue(1)
        assertThat(franzoesischeKurve.last().wert).isEqualTo(2f)
        assertThat(franzoesischeKurve.last().wertLabel).isEqualTo("6c")
    }

    @Test
    fun `gradVerlauf laesst Abschnitte ohne Top leer statt auf Null`() {
        // Der Unterschied ist inhaltlich: "nicht geklettert" ist nicht "auf dem tiefsten Grad
        // geklettert". Eine 0 würde die Kurve zum Boden ziehen und einen Einbruch behaupten.
        val sessions = listOf(session(id = 1, tag = heute)).associateBy { it.id }
        val topped = listOf(route(id = 1, sessionId = 1, gradeId = 11))

        val kurve = gradVerlauf(topped, sessions, gradesById, Zeitraum.Woche, heute).getValue(1)

        assertThat(kurve).hasSize(Zeitraum.Woche.eimer)
        assertThat(kurve.dropLast(1).map { it.wert }).containsExactly(null, null, null, null, null, null, null)
        assertThat(kurve.dropLast(1).map { it.wertLabel }).doesNotContain("6b")
        assertThat(kurve.last().wert).isEqualTo(1f)
        // Die Beschriftung steht auch an leeren Punkten — die x-Achse bleibt vollständig.
        assertThat(kurve.first().label).isNotEmpty()
    }

    @Test
    fun `gradVerlauf haelt Systeme getrennt`() {
        // Dieselbe Woche, zwei Systeme: V4 hat order 1, 6c hat order 2 — würde man beide in
        // eine Kurve werfen, „gewänne" 6c, obwohl die Zahlen nichts miteinander zu tun haben.
        val sessions = listOf(session(id = 1, tag = heute)).associateBy { it.id }
        val topped = listOf(
            route(id = 1, sessionId = 1, gradeId = 12), // 6c, System 1
            route(id = 2, sessionId = 1, gradeId = 21), // V4, System 2
        )

        val verlauf = gradVerlauf(topped, sessions, gradesById, Zeitraum.Woche, heute)

        assertThat(verlauf.keys).containsExactly(1, 2)
        assertThat(verlauf.getValue(1).last().wertLabel).isEqualTo("6c")
        assertThat(verlauf.getValue(2).last().wertLabel).isEqualTo("V4")
        // Beide Kurven haben dieselbe x-Achse, sonst wären sie nicht übereinanderlegbar.
        assertThat(verlauf.getValue(1).map { it.label })
            .isEqualTo(verlauf.getValue(2).map { it.label })
    }

    @Test
    fun `gradVerlauf kennt nur Systeme, in denen wirklich getoppt wurde`() {
        val sessions = listOf(session(id = 1, tag = heute)).associateBy { it.id }
        val topped = listOf(route(id = 1, sessionId = 1, gradeId = 10)) // nur System 1

        val verlauf = gradVerlauf(topped, sessions, gradesById, Zeitraum.Woche, heute)

        // Kein leerer Eintrag für System 2 — sonst stünde im Screen ein Umschalter für eine
        // Kurve ohne einen einzigen Punkt.
        assertThat(verlauf.keys).containsExactly(1)
    }

    @Test
    fun `gradVerlauf uebergeht Routen ohne Grad`() {
        val sessions = listOf(session(id = 1, tag = heute)).associateBy { it.id }
        val topped = listOf(
            route(id = 1, sessionId = 1, gradeId = null),
            route(id = 2, sessionId = 1, gradeId = 999), // Grad nicht auffindbar
        )

        val verlauf = gradVerlauf(topped, sessions, gradesById, Zeitraum.Woche, heute)

        assertThat(verlauf).isEmpty()
    }

    @Test
    fun `gradVerlauf trennt aufeinanderfolgende Abschnitte`() {
        // Steigerung über drei Wochen: 6a → nichts → 6c. Der mittlere Punkt bleibt leer,
        // die Steigerung ist trotzdem als Abstand sichtbar.
        val sessions = listOf(
            session(id = 1, tag = heute.minusWeeks(2)),
            session(id = 2, tag = heute),
        ).associateBy { it.id }
        val topped = listOf(
            route(id = 1, sessionId = 1, gradeId = 10), // 6a
            route(id = 2, sessionId = 2, gradeId = 12), // 6c
        )

        val kurve = gradVerlauf(topped, sessions, gradesById, Zeitraum.Woche, heute).getValue(1)

        assertThat(kurve[5].wertLabel).isEqualTo("6a")
        assertThat(kurve[6].wert).isNull()
        assertThat(kurve[7].wertLabel).isEqualTo("6c")
    }
}
