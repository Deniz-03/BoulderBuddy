package com.boulderbuddy.sync.nearby

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Das Übernehmen einer empfangenen Datei über den Dateideskriptor.
 *
 * Der Funkweg selbst braucht zwei Geräte, dieser Teil nicht: sobald Nearby ab Android 11 kein
 * `java.io.File` mehr herausrückt, entsteht die Datei aus einem Strom — und ein Strom kann
 * mittendrin enden. Eine abgeschnittene Datenbank ist der teuerste denkbare Fehler des
 * Abgleichs, weil sie sich öffnen lässt und trotzdem falsch ist.
 */
class EmpfangeneDateiTest {

    @get:Rule
    val ordner = TemporaryFolder()

    @Test
    fun `kopiert den Strom byte-genau`() {
        val inhalt = ByteArray(200_000) { (it % 251).toByte() }
        val ziel = java.io.File(ordner.newFolder(), "payload-1")

        val geschrieben = kopiereGepruft(ByteArrayInputStream(inhalt), ziel, inhalt.size.toLong())

        assertThat(geschrieben).isEqualTo(inhalt.size.toLong())
        assertThat(ziel.readBytes()).isEqualTo(inhalt)
    }

    @Test
    fun `legt fehlende Ordner selbst an`() {
        val ziel = java.io.File(ordner.newFolder(), "gibt/es/noch/nicht/payload-2")

        kopiereGepruft(ByteArrayInputStream(byteArrayOf(1, 2, 3)), ziel, 3)

        assertThat(ziel.exists()).isTrue()
    }

    @Test
    fun `meldet einen zu frueh endenden Strom, statt die halbe Datei durchzureichen`() {
        val ziel = java.io.File(ordner.newFolder(), "payload-3")

        val fehler = runCatching {
            kopiereGepruft(ByteArrayInputStream(ByteArray(10)), ziel, erwartet = 4096)
        }.exceptionOrNull()

        assertThat(fehler).isInstanceOf(IOException::class.java)
        assertThat(fehler).hasMessageThat().contains("10 von 4096")
    }

    @Test
    fun `ohne bekannte Sollgroesse wird nicht geprueft`() {
        val ziel = java.io.File(ordner.newFolder(), "payload-4")

        val geschrieben = kopiereGepruft(ByteArrayInputStream(ByteArray(7)), ziel, erwartet = 0)

        assertThat(geschrieben).isEqualTo(7)
    }

    @Test
    fun `ein mitten im Lesen abbrechender Strom kommt als Fehler an`() {
        val ziel = java.io.File(ordner.newFolder(), "payload-5")

        val fehler = runCatching {
            kopiereGepruft(AbbrechenderStrom(nachBytes = 64), ziel, erwartet = 4096)
        }.exceptionOrNull()

        assertThat(fehler).isInstanceOf(IOException::class.java)
    }

    /** Bricht mitten in der Übertragung ab — der Fall „Gerät geht aus der Reichweite". */
    private class AbbrechenderStrom(private val nachBytes: Int) : InputStream() {
        private var gelesen = 0

        override fun read(): Int {
            if (gelesen >= nachBytes) throw IOException("Verbindung weg")
            gelesen++
            return 0
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (gelesen >= nachBytes) throw IOException("Verbindung weg")
            val menge = minOf(len, nachBytes - gelesen)
            gelesen += menge
            return menge
        }
    }
}
