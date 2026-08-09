package com.boulderbuddy.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Die Datei-Hygiene des Abgleichs.
 *
 * Klein, aber nicht harmlos: eine liegengebliebene `-wal` neben einer neu geschriebenen `.db`
 * ist kein Müll, sondern ein alter Stand, den SQLite beim nächsten Öffnen darüberspielt. Der
 * Fehler zeigt sich erst zwei Abgleiche später und sieht dann nicht nach seiner Ursache aus.
 */
class AbgleichDateienTest {

    @get:Rule
    val ordner = TemporaryFolder()

    private fun mitBegleitern(name: String): Triple<File, File, File> {
        val db = File(ordner.root, name).apply { writeText("db") }
        val wal = File(ordner.root, "$name-wal").apply { writeText("wal") }
        val shm = File(ordner.root, "$name-shm").apply { writeText("shm") }
        return Triple(db, wal, shm)
    }

    @Test
    fun `loeschen nimmt wal und shm mit`() {
        val (db, wal, shm) = mitBegleitern("vorher.db")

        loescheMitBegleitern(db)

        assertThat(db.exists()).isFalse()
        assertThat(wal.exists()).isFalse()
        assertThat(shm.exists()).isFalse()
    }

    @Test
    fun `fehlende begleiter sind kein fehler`() {
        val db = File(ordner.root, "basis.db").apply { writeText("db") }

        loescheMitBegleitern(db)

        assertThat(db.exists()).isFalse()
    }

    @Test
    fun `eine gar nicht vorhandene datei ist kein fehler`() {
        loescheMitBegleitern(File(ordner.root, "gibt-es-nicht.db"))
    }

    /**
     * Der Fall, der am Gerät aufgetreten wäre: `vorher.db` ist weg, seine Begleiter nicht.
     * Wer danach nur die `.db` neu schreibt, erbt den fremden WAL.
     */
    @Test
    fun `verwaiste begleiter verschwinden auch ohne ihre datenbank`() {
        val (db, wal, shm) = mitBegleitern("vorher.db")
        db.delete()

        loescheMitBegleitern(db)

        assertThat(wal.exists()).isFalse()
        assertThat(shm.exists()).isFalse()
    }
}
