package com.boulderbuddy.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boulderbuddy.data.db.BoulderBuddyDatabase
import com.boulderbuddy.data.db.createInMemoryDatabase
import com.boulderbuddy.data.db.entity.StandMetaEntity
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Der Abgleich gegen eine echte SQLite-Datenbank (Sync-Plan S6, S9).
 *
 * Die Vergleichslogik selbst ist in `AbgleichTest` auf der JVM abgedeckt. Hier geht es um
 * das, was nur eine echte Datenbank zeigt: dass gelesene und geschriebene Zeilen zueinander
 * passen, dass `AUTOINCREMENT` nach dem Abgleich wieder im eigenen Band zählt, und dass ein
 * **zweiter** Durchlauf nichts mehr zu tun findet.
 */
@RunWith(AndroidJUnit4::class)
class StandZugriffTest {

    private lateinit var db: BoulderBuddyDatabase

    @Before
    fun aufbauen() {
        db = createInMemoryDatabase()
    }

    @After
    fun abbauen() {
        db.close()
    }

    private val sqlite get() = db.openHelper.writableDatabase

    private fun meta(generation: Long) = StandMetaEntity(
        generation = generation,
        erzeugtVon = "test-geraet",
        basiertAuf = generation - 1,
    )

    /**
     * Eine Gym-Zeile mit **allen** Spalten, die `STAND_TABELLEN` für `gym` führt.
     *
     * Die drei letzten sind nicht schmückendes Beiwerk: `geofenceRadiusMeters` und
     * `proximityAlertsEnabled` kamen mit v8 dazu und sind `NOT NULL`, ihren Standardwert haben
     * sie aber **nur in Kotlin**, nicht im SQL-Schema (Begründung in MIGRATION_7_8). Eine
     * Fixture ohne sie scheitert am `NOT NULL constraint` — dieselbe Falle, die zuvor schon das
     * Seed erwischt hat. Wer hier eine Spalte ergänzt, ergänzt sie in `Standtabellen.kt` mit.
     */
    private fun gym(name: String): Zeile = mapOf(
        "name" to Feld.Text(name),
        "location" to Feld.Leer,
        "latitude" to Feld.Leer,
        "longitude" to Feld.Leer,
        "geofenceRadiusMeters" to Feld.Zahl(150),
        "proximityAlertsEnabled" to Feld.Zahl(1),
        "defaultGradeSystemId" to Feld.Leer,
    )

    // `gymName` ist seit v10 NOT NULL und muss deshalb auch hier stehen — leer ist erlaubt,
    // fehlend nicht.
    private fun session(gymId: Int, datum: Long): Zeile = mapOf(
        "gymId" to Feld.Zahl(gymId.toLong()),
        "gymName" to Feld.Text(""),
        "gradeSystemId" to Feld.Leer,
        "date" to Feld.Zahl(datum),
        "durationMin" to Feld.Leer,
        "notes" to Feld.Leer,
        "endedAt" to Feld.Leer,
    )

    /** Eine leere DB — die Beispieldaten von `SeedData` stören den Vergleich sonst. */
    private fun leere() {
        for (tabelle in STAND_TABELLEN.reversed()) {
            sqlite.delete(tabelle.name, null, null)
        }
    }

    @Test
    fun geschriebene_zeilen_kommen_unveraendert_zurueck() {
        leere()
        wendeAn(
            sqlite,
            listOf(
                Operation.Einfuegen("gym", 1, gym("Halle Nord")),
                Operation.Einfuegen("session", 2, session(1, 100)),
            ),
            meta(1),
            band = 0,
        )

        val stand = liesStand(sqlite)
        assertThat(stand.zeile("gym", 1)).isEqualTo(gym("Halle Nord"))
        assertThat(stand.zeile("session", 2)).isEqualTo(session(1, 100))
        assertThat(liesStandMeta(sqlite))
            .isEqualTo(StandMeta(generation = 1, erzeugtVon = "test-geraet", basiertAuf = 0))
    }

    /**
     * Legt eine Halle direkt über SQLite an — [ziel] ist standardmäßig die eigene Datenbank,
     * für den Zwei-Geräte-Fall auch die fremde.
     *
     * Wie [gym]: die NOT-NULL-Spalten aus v8 haben ihren Standardwert nur in Kotlin, ein
     * `ContentValues` ohne sie scheitert am Constraint. Deshalb steht das Einfügen an genau
     * einer Stelle und nicht dreimal ausgeschrieben.
     */
    private fun neueHalle(
        name: String,
        ziel: androidx.sqlite.db.SupportSQLiteDatabase = sqlite,
    ): Long = ziel.insert(
        "gym",
        5, // CONFLICT_REPLACE
        android.content.ContentValues().apply {
            put("name", name)
            put("geofenceRadiusMeters", 150)
            put("proximityAlertsEnabled", 1)
        },
    )

    @Test
    fun beide_geraete_vergeben_nach_dem_abgleich_verschiedene_nummern() {
        // Ablauf 7, an einer echten Datenbank: die übernommenen Zeilen liegen physisch in
        // der Tabelle. Genau daran scheiterte die ursprüngliche Idee, `sqlite_sequence` auf
        // einen festen Bandanfang zurückzusetzen — SQLite nimmt
        // `max(sequenz, größte id) + 1`, die Rückstellung nach unten war wirkungslos.
        val gemeinsameZeilen = listOf(
            Operation.Einfuegen("gym", 1, gym("Halle Nord")),
            Operation.Einfuegen("gym", 1_000_000, gym("Kletterwerk")),
        )

        leere()
        wendeAn(sqlite, gemeinsameZeilen, meta(1), band = 0)
        val untenErste = neueHalle("neu unten")
        val untenZweite = neueHalle("noch eine unten")

        val andere = createInMemoryDatabase()
        val obenErste: Long
        val obenZweite: Long
        try {
            val fremd = andere.openHelper.writableDatabase
            for (t in STAND_TABELLEN.reversed()) fremd.delete(t.name, null, null)
            wendeAn(fremd, gemeinsameZeilen, meta(1), band = 1)
            obenErste = neueHalle("neu oben", fremd)
            obenZweite = neueHalle("noch eine oben", fremd)
        } finally {
            andere.close()
        }

        // Beide zählen über dem gemeinsamen Höchstwert weiter …
        assertThat(untenErste).isGreaterThan(1_000_000L)
        assertThat(obenErste).isGreaterThan(1_000_000L)
        // … aber in getrennten Fenstern, also ohne je dieselbe Nummer zu vergeben.
        assertThat(listOf(untenErste, untenZweite))
            .containsNoneIn(listOf(obenErste, obenZweite))
        assertThat(obenErste - untenErste).isEqualTo(NummernBand.FENSTER.toLong())
    }

    @Test
    fun eine_leere_tabelle_beginnt_im_eigenen_fenster() {
        leere()
        setzeSequenzenZurueck(sqlite, band = 1)

        assertThat(neueHalle("erste Halle")).isEqualTo(NummernBand.FENSTER.toLong() + 1)
    }

    @Test
    fun zwei_abgleiche_hintereinander_gegen_echte_datenbanken() {
        // Die Regel aus Runde 4/5: jeder Test läuft über ZWEI Abgleiche. Beim ersten sieht
        // vieles gut aus, was beim zweiten auseinanderfällt.
        leere()

        // Gemeinsame Ausgangslage, die beide Seiten und die Basis kennen.
        val basisOps = listOf(
            Operation.Einfuegen("gym", 1, gym("Halle Nord")),
            Operation.Einfuegen("session", 2, session(1, 100)),
        )
        wendeAn(sqlite, basisOps, meta(1), band = 0)
        val basis = liesStand(sqlite)

        // Dieses Gerät ergänzt eine Session, das andere eine Halle (Ablauf 4).
        wendeAn(
            sqlite,
            listOf(Operation.Einfuegen("session", 3, session(1, 200))),
            meta(1),
            band = 0,
        )
        val meine = liesStand(sqlite)

        val fremdeOps = basisOps + Operation.Einfuegen(
            "gym",
            1_000_000 + 1,
            gym("Kletterwerk"),
        )
        val fremde = standAus(fremdeOps)

        val ersterPlan = abgleich(basis, meine, fremde)
        assertThat(ersterPlan.konflikte).isEmpty()
        val erste = anwenden(ersterPlan, Seite.MEINS)

        wendeAn(sqlite, erste.fuerMich, meta(2), band = 0)
        val meineNachher = liesStand(sqlite)
        val fremdeNachher = standAus(fremdeOps + erste.fuerDieGegenseite)

        // Beide stehen gleich da …
        for (tabelle in STAND_TABELLEN) {
            assertThat(meineNachher.zeilen(tabelle.name))
                .isEqualTo(fremdeNachher.zeilen(tabelle.name))
        }

        // … und der zweite Abgleich gegen die frische Basis findet nichts mehr.
        val zweiterPlan = abgleich(meineNachher, meineNachher, fremdeNachher)
        assertThat(zweiterPlan.konflikte).isEmpty()
        assertThat(anwenden(zweiterPlan, Seite.MEINS).bilanz.nichtsZuTun).isTrue()
    }

    /** Baut den Stand der Gegenseite in einer zweiten Datenbank auf. */
    private fun standAus(operationen: List<Operation>): Stand {
        val andere = createInMemoryDatabase()
        try {
            val sql = andere.openHelper.writableDatabase
            for (tabelle in STAND_TABELLEN.reversed()) sql.delete(tabelle.name, null, null)
            wendeAn(sql, operationen, meta(1), band = 1)
            return liesStand(sql)
        } finally {
            andere.close()
        }
    }
}
