package com.boulderbuddy.ghost.pose

import com.boulderbuddy.ghost.GhostTuning
import com.boulderbuddy.ghost.analysis.qualityMetrics
import com.boulderbuddy.ghost.model.GhostPoseFrame
import com.boulderbuddy.ghost.model.GhostPoseTrack
import com.boulderbuddy.ghost.model.coreCentroid
import com.boulderbuddy.ghost.model.personScales
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import kotlin.math.hypot

/**
 * Filtereinstellungen gegen die Kennzahlen prüfen, nicht gegen das Gefühl (D3 Nr. 2).
 *
 * Der Grund für diesen Umweg: eine neue One-Euro-Einstellung ändert die Pose-Pipeline, also
 * den Cache-Schlüssel, also kostet jeder Versuch am Gerät einen Sieben-Minuten-Lauf. Dabei
 * hängt von der Einstellung nur die NACHbearbeitung ab — die teure Inferenz liefert die
 * Roh-Spur, und die liegt fertig in `GhostPoseTrack.rawFrames`. Auf ihr lässt sich die
 * gesamte Kette beliebig oft in Millisekunden wiederholen.
 *
 * Der Lauf braucht eine echte Spur vom Gerät und ist deshalb standardmäßig übersprungen —
 * eine 300-Frame-Kletterspur gehört nicht ins Repository, und eine kurze Fixture-Spur trüge
 * keinen belastbaren Median:
 *
 * ```bash
 * adb exec-out run-as com.boulderbuddy cat files/ghost/pose_<hash>.json > spur.json
 * ```
 *
 * ```bash
 * ./gradlew :app:testDebugUnitTest --tests "*OneEuroSweepTest" -Dghost.spur=C:/pfad/spur.json --info
 * ```
 *
 * Die Tabelle ist NICHT spaltenweise nach dem kleinsten Wert zu lesen. Unruhe und Morph
 * sinken beide monoton, je träger man filtert — am ruhigsten steht das Skelett, das der
 * Bewegung gar nicht mehr folgt. Genau diese Einseitigkeit macht die vorhandenen Kennzahlen
 * für diese eine Frage unbrauchbar: die früher „unnatürlich träge" verworfene Einstellung
 * (0.5/0.03) schneidet in allen fünf besser ab als die heute eingestellte.
 *
 * Deshalb steht hier eine sechste Zahl, die in die andere Richtung zieht: der **Nachlauf**.
 * Er misst, wie weit das gefilterte Rumpfzentrum entlang der Bewegungsrichtung hinter dem
 * rohen herläuft — in Millisekunden, also in der Einheit, in der „hinkt hinterher"
 * überhaupt eine Aussage ist. Erst mit ihr wird aus der Tabelle eine Abwägung statt einer
 * Bestenliste.
 */
class OneEuroSweepTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun kennzahlen_je_einstellung() {
        val pfad = System.getProperty("ghost.spur")
        assumeTrue("Ohne -Dghost.spur=<datei> übersprungen", pfad != null)
        val track = json.decodeFromString<GhostPoseTrack>(File(pfad!!).readText())
        val roh = track.rawFrames
        assumeTrue("Spur ohne rawFrames — nichts zu wiederholen", roh != null)

        println("Spur: ${track.frames.size} Frames, ${track.frameWidth}x${track.frameHeight}")
        println(
            "Rohspur ungefiltert: " + zeile(
                "roh", GhostTuning.ONE_EURO_MIN_CUTOFF_HZ, GhostTuning.ONE_EURO_BETA, roh!!, roh,
            ),
        )
        println("%-6s %-6s %s".format("cutoff", "beta", "Kennzahlen"))
        CUTOFFS.forEach { cutoff ->
            BETAS.forEach { beta ->
                val gefiltert = pipeline(roh, track.frameHeight, cutoff, beta)
                println(zeile("", cutoff, beta, gefiltert, roh))
            }
        }
    }

    /** Exakt die Kette aus [VideoPoseExtractor] — sie muss identisch bleiben, sonst misst
     *  der Sweep eine Pipeline, die es nicht gibt. */
    private fun pipeline(
        roh: List<GhostPoseFrame>,
        frameHeight: Int,
        cutoff: Double,
        beta: Double,
    ): List<GhostPoseFrame> =
        enforceRigidSkeleton(
            applyVisibilityHysteresis(
                smoothPoseFrames(
                    fillLandmarkGaps(cleanPoseFrames(roh, frameHeight)),
                    minCutoffHz = cutoff,
                    beta = beta,
                ),
            ),
        )

    private fun zeile(
        vorspann: String,
        cutoff: Double,
        beta: Double,
        frames: List<GhostPoseFrame>,
        roh: List<GhostPoseFrame>,
    ): String {
        val m = frames.qualityMetrics()
        return String.format(
            Locale.GERMANY,
            "%-6s %-6s Puls %.2f · Unruhe %.3f%% · Morph %.3f%% · Verkürzung %.1f%% · " +
                "Kollaps %.1f%% · Nachlauf %+.0f ms",
            if (vorspann.isEmpty()) "%.2f".format(Locale.GERMANY, cutoff) else vorspann,
            if (vorspann.isEmpty()) "%.3f".format(Locale.GERMANY, beta) else "",
            m.centroidPulse,
            m.centroidWobble * 100,
            m.boneLengthWobble * 100,
            m.boneLengthCv * 100,
            m.scaleCv * 100,
            nachlaufMs(roh, frames),
        )
    }

    /**
     * Nachlauf in Millisekunden: wie weit das gefilterte Rumpfzentrum ENTLANG DER
     * BEWEGUNGSRICHTUNG hinter dem rohen zurückbleibt.
     *
     * Die Roh-Spur ist verrauscht, aber sie ist zeitlich ehrlich — sie weiß in jedem Frame,
     * wo der Körper gerade ist. Der Versatz zwischen beiden lässt sich in zwei Anteile
     * zerlegen: quer zur Bewegung steht das Rauschen (mittelt sich weg), längs dazu die
     * Verzögerung. Geteilt durch die Geschwindigkeit wird daraus eine Zeit, und die ist die
     * Einheit, in der „das Skelett hinkt hinterher" eine prüfbare Aussage ist.
     *
     * Nur Frames mit erkennbarer Bewegung zählen: steht der Kletterer, ist der Nachlauf
     * nicht definiert (durch fast Null geteilt), und das Rauschen ergäbe beliebige Werte.
     */
    private fun nachlaufMs(roh: List<GhostPoseFrame>, gefiltert: List<GhostPoseFrame>): Double {
        val scales = personScales(roh)
        val werte = ArrayList<Double>(roh.size)
        for (i in 1 until minOf(roh.size, gefiltert.size) - 1) {
            val vorher = coreCentroid(roh[i - 1].landmarks) ?: continue
            val nachher = coreCentroid(roh[i + 1].landmarks) ?: continue
            val jetzt = coreCentroid(roh[i].landmarks) ?: continue
            val gefiltertJetzt = coreCentroid(gefiltert[i].landmarks) ?: continue
            val scale = scales[i]?.takeIf { it > 0.0 } ?: continue
            val dtS = (roh[i + 1].timeMs - roh[i - 1].timeMs) / 1000.0
            if (dtS <= 0.0) continue
            val vx = (nachher.first - vorher.first) / dtS
            val vy = (nachher.second - vorher.second) / dtS
            val tempo = hypot(vx, vy)
            // Schwelle an der Körpergröße statt in Pixeln: sonst hinge die Auswahl der
            // Frames an der Auflösung des Videos.
            if (tempo < MIN_TEMPO_KOERPER_PRO_S * scale) continue
            val dx = jetzt.first - gefiltertJetzt.first
            val dy = jetzt.second - gefiltertJetzt.second
            werte += (dx * vx + dy * vy) / (tempo * tempo) * 1000.0
        }
        if (werte.isEmpty()) return 0.0
        werte.sort()
        return werte[werte.size / 2]
    }

    private companion object {
        // Um die aktuelle Einstellung (1.5 / 0.015) herum, in beide Richtungen. 0.5 ist
        // bewusst dabei, obwohl früher als „unnatürlich träge" verworfen: die Kennzahlen
        // sollen zeigen, wie dieses Urteil aussieht, wenn man es misst statt es zu sehen.
        val CUTOFFS = listOf(0.5, 1.0, 1.5, 2.0, 3.0)
        val BETAS = listOf(0.005, 0.015, 0.03)

        /** Ab welcher Geschwindigkeit ein Frame für den Nachlauf zählt — ein Zehntel der
         *  Körperhöhe pro Sekunde, also erkennbare Bewegung statt Zittern im Griff. */
        const val MIN_TEMPO_KOERPER_PRO_S = 0.1
    }
}
