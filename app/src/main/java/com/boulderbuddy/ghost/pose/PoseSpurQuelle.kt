package com.boulderbuddy.ghost.pose

import com.boulderbuddy.ghost.GhostArtifactStore
import com.boulderbuddy.ghost.model.GhostPoseTrack
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Liefert die Pose-Spur zu einem Video — gecacht oder frisch gerechnet.
 *
 * Die Unterscheidung interessiert keinen Aufrufer: für ihn gibt es genau eine Frage
 * („Spur zu dieser URI") und genau eine Antwort. Vorher stand das Paar aus Cache-Blick und
 * Extraktion im ViewModel, also ausgerechnet in der Schicht, die den Bildschirm überleben
 * sollte und es nicht konnte.
 */
interface PoseSpurQuelle {

    /**
     * [onFortschritt] meldet (fertig, gesamt) je Frame — nur bei einer echten Extraktion.
     * Kommt die Spur aus dem Cache, gibt es nichts zu melden und der Aufruf kehrt sofort
     * zurück. Der Empfänger muss thread-sicher sein (Aufruf vom Rechen-Dispatcher).
     */
    suspend fun spur(
        videoUri: String,
        onFortschritt: (fertig: Int, gesamt: Int) -> Unit = { _, _ -> },
    ): GhostPoseTrack
}

/**
 * Der reale Weg: erst in `files/ghost/` nachsehen, sonst [VideoPoseExtractor] laufen lassen
 * und das Ergebnis dort ablegen.
 *
 * Die Extraktion ist der teuerste Schritt der Pipeline (Minuten pro Video) — der Cache ist
 * kein Feinschliff, sondern der Grund, warum eine gespeicherte Analyse überhaupt in
 * Sekunden wieder aufgeht. Sein Schlüssel steckt im [GhostArtifactStore] und ändert sich
 * mit der Pipeline-Semantik, nicht hier.
 */
@Singleton
class GecachtePoseSpurQuelle @Inject constructor(
    private val extractor: VideoPoseExtractor,
    private val artifactStore: GhostArtifactStore,
) : PoseSpurQuelle {

    override suspend fun spur(
        videoUri: String,
        onFortschritt: (fertig: Int, gesamt: Int) -> Unit,
    ): GhostPoseTrack =
        artifactStore.loadPoseTrack(videoUri)
            ?: extractor.extract(videoUri, onFortschritt)
                .also { artifactStore.savePoseTrack(it) }
}
