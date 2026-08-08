package com.boulderbuddy.ui.viewmodel

import com.boulderbuddy.data.db.entity.GymEntity
import com.boulderbuddy.data.db.entity.SessionEntity
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Die Reihenfolge der Hallen-Chips beim Session-Anlegen.
 *
 * Sie ist nicht Kosmetik: die erste Halle ist zugleich die Vorauswahl. Wer den Button drückt,
 * ohne etwas anzutippen, startet die Session dort — deshalb muss „zuletzt benutzt" stimmen.
 */
class HallenReihenfolgeTest {

    private fun halle(id: Int, name: String) = GymEntity(id = id, name = name)

    private fun session(gymId: Int, date: Long) =
        SessionEntity(gymId = gymId, date = date, endedAt = null)

    @Test
    fun zuletzt_benutzte_halle_steht_vorn() {
        val gyms = listOf(halle(1, "Alpha"), halle(2, "Beta"), halle(3, "Gamma"))
        val sessions = listOf(
            session(gymId = 1, date = 1_000),
            session(gymId = 3, date = 9_000),
            session(gymId = 2, date = 5_000),
        )

        val sortiert = sortiereNachLetzterNutzung(gyms, sessions)

        assertThat(sortiert.map { it.name }).containsExactly("Gamma", "Beta", "Alpha").inOrder()
    }

    @Test
    fun mehrere_sessions_zaehlen_mit_der_juengsten() {
        val gyms = listOf(halle(1, "Alpha"), halle(2, "Beta"))
        val sessions = listOf(
            // Alpha war oefter dran, Beta zuletzt — die Anzahl entscheidet nicht.
            session(gymId = 1, date = 1_000),
            session(gymId = 1, date = 2_000),
            session(gymId = 1, date = 3_000),
            session(gymId = 2, date = 4_000),
        )

        val sortiert = sortiereNachLetzterNutzung(gyms, sessions)

        assertThat(sortiert.map { it.name }).containsExactly("Beta", "Alpha").inOrder()
    }

    @Test
    fun hallen_ohne_session_landen_hinten_und_bleiben_alphabetisch() {
        // So kommen sie aus dem Repository: alphabetisch.
        val gyms = listOf(halle(1, "Alpha"), halle(2, "Beta"), halle(3, "Gamma"))
        val sessions = listOf(session(gymId = 2, date = 1_000))

        val sortiert = sortiereNachLetzterNutzung(gyms, sessions)

        // Beta war benutzt, der Rest behaelt seine Reihenfolge — eine frisch angelegte Halle
        // draengt sich nicht vor die, in der man tatsaechlich war.
        assertThat(sortiert.map { it.name }).containsExactly("Beta", "Alpha", "Gamma").inOrder()
    }

    @Test
    fun ohne_sessions_bleibt_alles_wie_es_kam() {
        val gyms = listOf(halle(1, "Alpha"), halle(2, "Beta"))

        val sortiert = sortiereNachLetzterNutzung(gyms, sessions = emptyList())

        assertThat(sortiert.map { it.name }).containsExactly("Alpha", "Beta").inOrder()
    }

    @Test
    fun eine_session_an_einer_geloeschten_halle_stoert_nicht() {
        val gyms = listOf(halle(1, "Alpha"))
        // gymId 99 gibt es nicht mehr; der Eintrag darf die Sortierung nicht sprengen.
        val sessions = listOf(session(gymId = 99, date = 9_000), session(gymId = 1, date = 1_000))

        val sortiert = sortiereNachLetzterNutzung(gyms, sessions)

        assertThat(sortiert.map { it.name }).containsExactly("Alpha")
    }
}
