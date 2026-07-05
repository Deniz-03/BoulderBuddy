package com.boulderbuddy.ghost.geometry

import com.boulderbuddy.ghost.model.GhostPoint
import com.boulderbuddy.ghost.model.GhostPoseTrack

// P0 des Alignment-Docs: Posen beider Versuche müssen im SELBEN Wand-Referenzraum
// liegen, sonst sind die Trajektorien nicht vergleichbar. Als Referenzraum dient der
// Analyse-Frame-Raum des REFERENZ-Videos (dessen Homographie ist damit die Identität);
// nur das Vergleichs-Video wird transformiert.

/**
 * Transformiert alle Keypoints der Spur durch [homography] in den Frame-Raum von
 * [target] (= Referenzraum). Konfidenzen und Zeitstempel bleiben unberührt —
 * die Homographie ist eine rein räumliche Umrechnung.
 */
fun GhostPoseTrack.transformedBy(homography: Homography, target: GhostPoseTrack): GhostPoseTrack =
    copy(
        frameWidth = target.frameWidth,
        frameHeight = target.frameHeight,
        frames = frames.map { frame ->
            frame.copy(
                landmarks = frame.landmarks.map { landmark ->
                    val mapped = homography.map(landmark.x, landmark.y)
                    landmark.copy(x = mapped.x.toFloat(), y = mapped.y.toFloat())
                },
            )
        },
    )

fun GhostPoint.toVec2(): Vec2 = Vec2(x.toDouble(), y.toDouble())
