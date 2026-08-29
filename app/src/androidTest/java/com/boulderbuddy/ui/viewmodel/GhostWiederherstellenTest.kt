package com.boulderbuddy.ui.viewmodel

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.boulderbuddy.data.camera.EigeneAufnahmen
import com.boulderbuddy.data.db.BoulderBuddyDatabase
import com.boulderbuddy.data.db.createInMemoryDatabase
import com.boulderbuddy.data.db.entity.GhostAnalysisEntity
import com.boulderbuddy.data.repository.GhostAnalysisRepository
import com.boulderbuddy.data.repository.GhostAnalysisRepositoryImpl
import com.boulderbuddy.ghost.GhostAnalyseRunner
import com.boulderbuddy.ghost.GhostAnalyseStand
import com.boulderbuddy.ghost.GhostArtifactStore
import com.boulderbuddy.ghost.model.GhostLandmark
import com.boulderbuddy.ghost.model.GhostLandmarkTypes as T
import com.boulderbuddy.ghost.model.GhostPoint
import com.boulderbuddy.ghost.model.GhostPoseFrame
import com.boulderbuddy.ghost.model.GhostPoseTrack
import com.boulderbuddy.ghost.pose.PoseSpurQuelle
import com.boulderbuddy.ghost.video.GhostFrameDecoder
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Eine wiederhergestellte Analyse und ein Lauf im Hintergrund greifen nach denselben zwei
 * Video-Slots. Der Lauf darf nicht gewinnen.
 *
 * Der Schaden ist still und bleibt: der Bildschirm merkt sich, welche Zeile er gerade
 * bearbeitet, damit Nachbessern die vorhandene Analyse korrigiert statt eine zweite
 * anzulegen. Fährt ein fremder Lauf dazwischen, stehen plötzlich dessen Videos in den Slots —
 * die Kennung zeigt aber weiter auf die alte Zeile. Wer dann speichert, schreibt Homographie
 * und Routenpfad des fremden Paars in die gespeicherte Analyse. Die behält ihre eigenen
 * Videos, und der Geist darin ist von da an Unsinn. Keine Meldung, kein Hinweis.
 *
 * Nachgestellt werden beide Reihenfolgen: der Lauf ist schon fertig, wenn der Bildschirm
 * aufgebaut wird, und der Lauf wird fertig, während die Analyse offen ist.
 */
@RunWith(AndroidJUnit4::class)
class GhostWiederherstellenTest {

    private lateinit var context: Application
    private lateinit var db: BoulderBuddyDatabase
    private lateinit var store: GhostArtifactStore
    private lateinit var repository: GhostAnalysisRepository
    private lateinit var runner: GhostAnalyseRunner
    private var analyseId = 0

    private val json = Json

    // Die Videos der gespeicherten Analyse und die eines völlig anderen Laufs.
    private val gespeichertRef = "content://gespeichert/ref"
    private val gespeichertCmp = "content://gespeichert/cmp"
    private val fremdRef = "content://fremd/ref"
    private val fremdCmp = "content://fremd/cmp"

    private fun landmark(type: Int, x: Float, y: Float) =
        GhostLandmark(type = type, x = x, y = y, confidence = 0.9f, presence = 0.9f)

    /**
     * Eine Spur, die durch die Pipeline kommt: Schultern und Hüften in jedem Frame, dazu eine
     * Bewegung nach oben. Ohne Hüften gibt es kein Fortschrittssignal, und die
     * Wiederherstellung bräche mit „keine verwertbare Pose-Spur" ab — der Test liefe dann am
     * eigentlichen Fall vorbei.
     */
    private fun spur(uri: String) = GhostPoseTrack(
        videoUri = uri,
        frameWidth = 480,
        frameHeight = 640,
        durationMs = 1_000L,
        sampleFps = 12.0,
        frames = List(12) { i ->
            val hoehe = 400f - i * 20f
            GhostPoseFrame(
                timeMs = i * 83L,
                landmarks = listOf(
                    landmark(T.LEFT_SHOULDER, 150f, hoehe - 100f),
                    landmark(T.RIGHT_SHOULDER, 250f, hoehe - 100f),
                    landmark(T.LEFT_HIP, 150f, hoehe),
                    landmark(T.RIGHT_HIP, 250f, hoehe),
                ),
            )
        },
    )

    /** Attrappe der Extraktion — der Lauf soll fertig werden, nicht rechnen. */
    private inner class FakeQuelle : PoseSpurQuelle {
        override suspend fun spur(
            videoUri: String,
            onFortschritt: (fertig: Int, gesamt: Int) -> Unit,
        ): GhostPoseTrack = this@GhostWiederherstellenTest.spur(videoUri)
    }

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = createInMemoryDatabase()
        store = GhostArtifactStore(context)
        repository = GhostAnalysisRepositoryImpl(db.ghostAnalysisDao(), store)
        runner = GhostAnalyseRunner(FakeQuelle(), Dispatchers.Default)

        store.savePoseTrack(spur(gespeichertRef))
        store.savePoseTrack(spur(gespeichertCmp))
        analyseId = repository.create(
            GhostAnalysisEntity(
                sessionId = null,
                refMediaUri = gespeichertRef,
                cmpMediaUri = gespeichertCmp,
                refKeypointsPath = store.poseTrackPath(gespeichertRef),
                cmpKeypointsPath = store.poseTrackPath(gespeichertCmp),
                // Einheit: der Vergleich liegt schon im Referenzraum.
                homographyCmpJson = json.encodeToString(
                    doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0),
                ),
                routePathJson = json.encodeToString(
                    listOf(GhostPoint(200f, 400f), GhostPoint(200f, 180f)),
                ),
                suggestedMode = "OVERLAY",
                createdAt = 1_000L,
            ),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** Das ViewModel gehört dem Main-Thread — `viewModelScope` setzt dort auf. */
    private fun baueViewModel(argumente: Map<String, Any?>): GhostClimberViewModel {
        lateinit var viewModel: GhostClimberViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel = GhostClimberViewModel(
                application = context,
                runner = runner,
                artifactStore = store,
                frameDecoder = GhostFrameDecoder(context),
                analysisRepository = repository,
                eigeneAufnahmen = EigeneAufnahmen(context),
                savedStateHandle = SavedStateHandle(argumente),
            )
        }
        return viewModel
    }

    /** Lässt einen kompletten Lauf über zwei fremde Videos durchlaufen. */
    private suspend fun fremderLaufWirdFertig() {
        runner.starte(fremdRef, fremdCmp)
        runner.warteAufEnde()
        assertThat(runner.stand.value).isInstanceOf(GhostAnalyseStand.Fertig::class.java)
    }

    private suspend fun warteAufVorschau(viewModel: GhostClimberViewModel) =
        withTimeout(10_000) {
            viewModel.uiState.first { it.step == GhostStep.PREVIEW || it.error != null }
        }

    @Test
    fun ein_wartendes_ergebnis_verdraengt_die_geoeffnete_analyse_nicht() = runBlocking {
        // Ein Lauf ist zu Ende gegangen, während kein Bildschirm da war, der ihn abholt —
        // sein Ergebnis liegt im Runner und wartet.
        fremderLaufWirdFertig()

        // Jetzt tippt der Nutzer im Session-Block auf eine gespeicherte Analyse.
        val viewModel = baueViewModel(mapOf("analyseId" to analyseId))
        val state = warteAufVorschau(viewModel)

        assertThat(state.error).isNull()
        assertThat(state.step).isEqualTo(GhostStep.PREVIEW)
        assertThat(state.reference.uri).isEqualTo(gespeichertRef)
        assertThat(state.comparison.uri).isEqualTo(gespeichertCmp)

        // Und das Ergebnis des Laufs ist nicht nebenbei verbraucht worden: es wurde nicht
        // angezeigt, also darf es auch nicht als abgeholt gelten. Sieben Minuten Rechnung
        // still wegzuwerfen wäre die zweite Hälfte desselben Fehlers.
        assertThat(runner.stand.value).isInstanceOf(GhostAnalyseStand.Fertig::class.java)
    }

    @Test
    fun ein_lauf_der_waehrenddessen_fertig_wird_greift_nicht_hinein() = runBlocking {
        val viewModel = baueViewModel(emptyMap())
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.restoreAnalysis(analyseId)
        }
        val vorher = warteAufVorschau(viewModel)
        assertThat(vorher.error).isNull()
        assertThat(vorher.reference.uri).isEqualTo(gespeichertRef)

        // Der Nutzer sieht sich die Analyse an, im Hintergrund läuft ein anderer Vergleich
        // zu Ende.
        fremderLaufWirdFertig()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        val nachher = viewModel.uiState.value
        assertThat(nachher.step).isEqualTo(GhostStep.PREVIEW)
        assertThat(nachher.reference.uri).isEqualTo(gespeichertRef)
        assertThat(nachher.comparison.uri).isEqualTo(gespeichertCmp)
    }
}
