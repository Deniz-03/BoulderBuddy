package com.boulderbuddy.ui.viewmodel

import com.boulderbuddy.data.db.entity.GradeEntity
import com.boulderbuddy.data.db.entity.RouteEntity
import com.boulderbuddy.data.model.RouteStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Der Verlauf eines Klettertages — reine Aggregation über Routen, deshalb ohne Gerät prüfbar.
 *
 * Geprüft wird vor allem das, was man beim Lesen der Kurve glaubt: dass die Reihenfolge die
 * des Kletterns bleibt, dass ein Flash etwas anderes ist als ein Top nach acht Versuchen, und
 * dass zwei Gradsysteme nicht in einer Kurve landen — `order` ist nur innerhalb eines Systems
 * eine Reihenfolge.
 */
class TagesstatistikTest {

    // V-Scale (System 1) und Französisch (System 2). Die Ordnungszahlen überschneiden sich
    // absichtlich: genau daran müsste eine System-übergreifende Kurve scheitern.
    private val grade = listOf(
        GradeEntity(id = 1, systemId = 1, label = "V2", order = 2),
        GradeEntity(id = 2, systemId = 1, label = "V4", order = 4),
        GradeEntity(id = 3, systemId = 1, label = "V6", order = 6),
        GradeEntity(id = 4, systemId = 2, label = "6a", order = 2),
        GradeEntity(id = 5, systemId = 2, label = "6c", order = 4),
    ).associateBy { it.id }

    private fun route(
        id: Int,
        gradeId: Int?,
        status: RouteStatus = RouteStatus.SENT,
        attempts: Int = 1,
    ) = RouteEntity(
        id = id,
        sessionId = 1,
        gradeId = gradeId,
        attempts = attempts,
        status = status,
    )

    @Test
    fun behaeltDieReihenfolgeUndZaehltPositionenAbEins() {
        val routen = listOf(route(1, 2), route(2, 1), route(3, 3))

        val verlauf = tagesstatistikJeSystem(routen, grade).getValue(1).boulder

        assertThat(verlauf.map { it.position }).containsExactly(1, 2, 3).inOrder()
        assertThat(verlauf.map { it.gradLabel }).containsExactly("V4", "V2", "V6").inOrder()
    }

    @Test
    fun flashIstTopImErstenVersuch() {
        val routen = listOf(
            route(1, 1, attempts = 1),
            route(2, 2, attempts = 5),
            // Ein Projekt mit einem Versuch ist KEIN Flash — nur ein einziger Versuch.
            route(3, 3, status = RouteStatus.PROJECT, attempts = 1),
        )

        val verlauf = tagesstatistikJeSystem(routen, grade).getValue(1).boulder

        assertThat(verlauf.map { it.flash }).containsExactly(true, false, false).inOrder()
        assertThat(verlauf.map { it.getoppt }).containsExactly(true, true, false).inOrder()
    }

    @Test
    fun trenntGradsysteme() {
        val routen = listOf(route(1, 1), route(2, 4), route(3, 2), route(4, 5))

        val jeSystem = tagesstatistikJeSystem(routen, grade)

        assertThat(jeSystem.keys).containsExactly(1, 2)
        assertThat(jeSystem.getValue(1).boulder.map { it.gradLabel })
            .containsExactly("V2", "V4").inOrder()
        assertThat(jeSystem.getValue(2).boulder.map { it.gradLabel })
            .containsExactly("6a", "6c").inOrder()
        // Die Positionen zählen JE SYSTEM ab eins — sonst begänne die zweite Kurve bei 2.
        assertThat(jeSystem.getValue(2).boulder.map { it.position })
            .containsExactly(1, 2).inOrder()
    }

    @Test
    fun routenOhneGradFallenHeraus() {
        // Ohne Grad gibt es weder y-Position noch Systemzugehörigkeit. Der Boulder gehört
        // nirgendwohin — und seine Versuche dürfen die Zahlen der Kurve nicht aufblähen.
        val routen = listOf(route(1, 1, attempts = 2), route(2, null, attempts = 7))

        val statistik = tagesstatistikJeSystem(routen, grade).getValue(1)

        assertThat(statistik.boulder).hasSize(1)
        assertThat(statistik.kennzahlen.versuche).isEqualTo("2")
    }

    @Test
    fun kennzahlenBeschreibenDenTag() {
        val routen = listOf(
            route(1, 1, attempts = 1),                                  // V2, Flash
            route(2, 2, attempts = 3),                                  // V4, Top
            route(3, 3, status = RouteStatus.PROJECT, attempts = 9),    // V6, gescheitert
        )

        val k = tagesstatistikJeSystem(routen, grade).getValue(1).kennzahlen

        assertThat(k.tops).isEqualTo("2")
        assertThat(k.versuche).isEqualTo("13")
        // Der höchste GETOPPTE Grad — das gescheiterte V6 zählt hier nicht.
        assertThat(k.topGrad).isEqualTo("V4")
        // Ein Verhaeltnis, kein Prozentwert — bei zwei Tops waere "50 %" eine Behauptung
        // von Genauigkeit, die es nicht gibt.
        assertThat(k.flash).isEqualTo("1/2")
    }

    @Test
    fun ohneTopGibtEsKeineQuoteUndKeinenTopGrad() {
        val routen = listOf(route(1, 3, status = RouteStatus.PROJECT, attempts = 6))

        val k = tagesstatistikJeSystem(routen, grade).getValue(1).kennzahlen

        assertThat(k.tops).isEqualTo("0")
        // Gedankenstrich statt "0/0": ein Verhältnis ohne Grundgesamtheit gibt es nicht.
        assertThat(k.flash).isEqualTo("–")
        assertThat(k.topGrad).isEqualTo("–")
    }
}
