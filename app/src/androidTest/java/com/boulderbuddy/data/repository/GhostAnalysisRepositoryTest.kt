package com.boulderbuddy.data.repository

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boulderbuddy.data.db.BoulderBuddyDatabase
import com.boulderbuddy.data.db.createInMemoryDatabase
import com.boulderbuddy.data.db.entity.GhostAnalysisEntity
import com.boulderbuddy.data.db.entity.SessionEntity
import com.boulderbuddy.ghost.GhostArtifactStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Dass eine gelöschte Analyse ihre Pose-Spuren mitnimmt — und dass sie das **nicht** tut,
 * wenn eine andere Analyse dieselbe Spur noch braucht.
 *
 * Der Anlass war ein Befund aus der Kommentarpflege: Löschen entfernte nur die Zeile, die
 * JSON-Dateien blieben liegen. Je nach Videolänge sind das einige hundert kB pro Spur, und
 * es gab nirgends ein Aufräumen — also unbegrenztes Wachstum ohne Verfallsdatum.
 *
 * Der zweite Fall ist der, der die naive Lösung kaputtmacht: derselbe Versuch kann in einer
 * Analyse die Referenz und in einer anderen der Vergleich sein. Wer beim Löschen einfach
 * beide Pfade der eigenen Zeile wegräumt, nimmt der anderen Analyse ihre Daten weg — und die
 * ist danach nicht mehr zu öffnen.
 */
@RunWith(AndroidJUnit4::class)
class GhostAnalysisRepositoryTest {

    private lateinit var db: BoulderBuddyDatabase
    private lateinit var store: GhostArtifactStore
    private lateinit var repository: GhostAnalysisRepository
    private lateinit var filesDir: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = createInMemoryDatabase()
        store = GhostArtifactStore(context)
        repository = GhostAnalysisRepositoryImpl(db.ghostAnalysisDao(), store)
        filesDir = context.filesDir
    }

    @After
    fun tearDown() {
        db.close()
        File(filesDir, GhostArtifactStore.ORDNER).deleteRecursively()
    }

    /** Legt eine Spur-Datei an und gibt ihren DB-Pfad (relativ zu `filesDir`) zurück. */
    private fun spurAnlegen(name: String): String {
        val ordner = File(filesDir, GhostArtifactStore.ORDNER).apply { mkdirs() }
        File(ordner, name).writeText("{}")
        return "${GhostArtifactStore.ORDNER}/$name"
    }

    private fun spurExistiert(pfad: String) = File(filesDir, pfad).exists()

    private fun analyse(ref: String, cmp: String) = GhostAnalysisEntity(
        refMediaUri = "content://ref",
        cmpMediaUri = "content://cmp",
        refKeypointsPath = ref,
        cmpKeypointsPath = cmp,
        homographyCmpJson = "[]",
        routePathJson = "[]",
        suggestedMode = "OVERLAY",
        createdAt = 1_000L,
    )

    @Test
    fun update_aendert_die_auswertung_und_laesst_die_zuordnung_in_ruhe() = runTest {
        // Der Fall dahinter: eine Analyse aus dem Session-Block öffnen, den Routenpfad
        // korrigieren, speichern. Vorher entstand dabei eine ZWEITE Zeile, und die trug die
        // Session der aufrufenden Route — also keine. Die Korrektur verschwand damit aus der
        // Session, aus der man sie geöffnet hatte.
        // Die Session muss es geben — `ghost_analysis.sessionId` ist ein echter
        // Fremdschlüssel, und ein Test, der ihn umgeht, prüfte etwas anderes als die App tut.
        val sessionId = db.sessionDao()
            .insert(SessionEntity(gymName = "Testhalle", date = 1_000L))
            .toInt()
        val id = repository.create(
            analyse(ref = spurAnlegen("pose_a.json"), cmp = spurAnlegen("pose_b.json"))
                .copy(sessionId = sessionId, createdAt = 1_000L),
        )

        repository.update(
            id = id,
            homographieJson = "[9]",
            routenpfadJson = "[7]",
            modus = "SIDE_BY_SIDE",
        )

        val danach = repository.getById(id)!!
        assertThat(danach.homographyCmpJson).isEqualTo("[9]")
        assertThat(danach.routePathJson).isEqualTo("[7]")
        assertThat(danach.suggestedMode).isEqualTo("SIDE_BY_SIDE")
        // Das Eigentliche: Zuordnung und Entstehungszeit bleiben unangetastet.
        assertThat(danach.sessionId).isEqualTo(sessionId)
        assertThat(danach.createdAt).isEqualTo(1_000L)
        // Und es bleibt bei EINER Zeile.
        assertThat(repository.observeAll().first()).hasSize(1)
    }

    @Test
    fun delete_raeumt_die_spuren_der_geloeschten_analyse_ab() = runTest {
        val ref = spurAnlegen("pose_ref.json")
        val cmp = spurAnlegen("pose_cmp.json")
        val id = repository.create(analyse(ref, cmp))

        repository.delete(id)

        assertThat(repository.getById(id)).isNull()
        assertThat(spurExistiert(ref)).isFalse()
        assertThat(spurExistiert(cmp)).isFalse()
    }

    @Test
    fun delete_ruehrt_nichts_ausserhalb_des_ghost_ordners_an() = runTest {
        // Der Pfad in der Zeile ist keine vertrauenswürdige Angabe: er wird beim
        // Geräte-Abgleich mit einem fremden Stand verglichen und übernommen. Ein `..` darin
        // darf nicht dazu führen, dass „Analyse löschen" die Datenbank mitnimmt.
        val fremd = File(filesDir, "nicht_anfassen.txt").apply { writeText("wichtig") }
        val eigen = spurAnlegen("pose_eigen.json")
        val id = repository.create(
            analyse(ref = GhostArtifactStore.ORDNER + "/../nicht_anfassen.txt", cmp = eigen),
        )

        repository.delete(id)

        assertThat(fremd.exists()).isTrue()
        // Die Spur im eigenen Ordner verschwindet trotzdem — die Schranke blockiert nicht
        // pauschal, sie prüft nur, wohin der Pfad zeigt.
        assertThat(spurExistiert(eigen)).isFalse()

        fremd.delete()
    }

    @Test
    fun delete_laesst_spuren_stehen_die_eine_andere_analyse_noch_braucht() = runTest {
        val geteilt = spurAnlegen("pose_geteilt.json")
        val nurHier = spurAnlegen("pose_nur_hier.json")
        val andere = spurAnlegen("pose_andere.json")

        val id = repository.create(analyse(ref = geteilt, cmp = nurHier))
        // Dieselbe Spur, diesmal als Vergleich — genau der Fall, der die naive Lösung bricht.
        repository.create(analyse(ref = andere, cmp = geteilt))

        repository.delete(id)

        assertThat(spurExistiert(geteilt)).isTrue()
        assertThat(spurExistiert(andere)).isTrue()
        assertThat(spurExistiert(nurHier)).isFalse()
    }
}
