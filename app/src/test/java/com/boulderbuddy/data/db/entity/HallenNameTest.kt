package com.boulderbuddy.data.db.entity

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Die Regel, welchen Hallennamen eine Session anzeigt.
 *
 * Sie steht an einer Stelle, wird aber an sieben gebraucht (Home, Sessions-Liste,
 * Session-Detail, Widget, Hangboard-Historie, CSV-Export). Ein Fehler hier zeigt sich also
 * nicht an einer Stelle, sondern überall — und im schlimmsten Fall als „Unbekannte Halle" auf
 * Sessions, deren Halle es sehr wohl noch gibt.
 */
class HallenNameTest {

    private fun session(gymId: Int?, gymName: String) =
        SessionEntity(id = 1, gymId = gymId, gymName = gymName, date = 0)

    @Test
    fun solange_die_halle_existiert_gewinnt_ihr_aktueller_name() {
        // Der Schnappschuss ist veraltet — die Halle wurde umbenannt.
        val s = session(gymId = 7, gymName = "Boulderwelt Alt")

        assertThat(s.hallenName { if (it == 7) "Boulderwelt Neu" else null })
            .isEqualTo("Boulderwelt Neu")
    }

    @Test
    fun ohne_halle_uebernimmt_der_schnappschuss() {
        // gymId == null heisst: die Halle wurde geloescht.
        val s = session(gymId = null, gymName = "Boulderwelt")

        assertThat(s.hallenName { "sollte nie gefragt werden" }).isEqualTo("Boulderwelt")
    }

    @Test
    fun eine_id_die_ins_leere_zeigt_faellt_ebenfalls_auf_den_schnappschuss() {
        // Kann nach einem Abgleich vorkommen: die Zeile ist weg, die ID stand noch da.
        val s = session(gymId = 7, gymName = "Boulderwelt")

        assertThat(s.hallenName { null }).isEqualTo("Boulderwelt")
    }

    @Test
    fun ohne_alles_gibt_es_null_und_der_aufrufer_entscheidet() {
        val s = session(gymId = null, gymName = "")

        assertThat(s.hallenName { null }).isNull()
    }

    @Test
    fun ein_leerer_schnappschuss_zaehlt_wie_keiner() {
        // Bestandssessions aus der Migration koennen einen leeren Namen haben, wenn ihre Halle
        // schon vorher fehlte. Leer ist dann kein gueltiger Anzeigename.
        val s = session(gymId = 7, gymName = "   ")

        assertThat(s.hallenName { null }).isNull()
    }
}
