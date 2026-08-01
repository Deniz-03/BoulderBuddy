package com.boulderbuddy.ghost.geometry

import com.boulderbuddy.ghost.model.GhostPoint
import com.boulderbuddy.ghost.model.GhostPoseFrame
import com.boulderbuddy.ghost.model.GhostPoseTrack
import com.boulderbuddy.ghost.pose.enforceRigidSkeleton

// P0 des Alignment-Docs: Posen beider Versuche müssen im SELBEN Wand-Referenzraum
// liegen, sonst sind die Trajektorien nicht vergleichbar. Als Referenzraum dient der
// Analyse-Frame-Raum des REFERENZ-Videos (dessen Homographie ist damit die Identität);
// nur das Vergleichs-Video wird transformiert.

/**
 * Transformiert alle Keypoints der Spur durch [homography] in den Frame-Raum von
 * [target] (= Referenzraum). Konfidenzen und Zeitstempel bleiben unberührt —
 * die Homographie ist eine rein räumliche Umrechnung.
 *
 * **S4e:** danach läuft [enforceRigidSkeleton] ein zweites Mal. Die Homographie ist aus
 * WAND-Ankern geschätzt, wird aber auf den KÖRPER angewendet — der liegt nicht in der
 * Wandebene, sondern steht davor. Eine Ebenen-Homographie erhält für solche Punkte
 * weder Längen noch deren Verhältnisse: die bei der Extraktion mühsam hergestellten
 * Proportionen werden hier wieder verzogen (gemessen: 8,7 % Überlängen beim Geist,
 * während die untransformierte Referenz bei 0,0 % lag). Die Rekonstruktion stellt sie
 * wieder her.
 *
 * Nicht behoben wird damit der POSITIONS-Fehler derselben Ursache — wie weit der Geist
 * neben seiner wahren Lage sitzt, hängt davon ab, wie weit der Körper aus der Wandebene
 * ragt, und das ist aus einer Ebenen-Homographie prinzipiell nicht rekonstruierbar.
 *
 * Die Roh-Spur ([GhostPoseTrack.rawFrames]) wird MITtransformiert, aber ohne die
 * Rekonstruktion — roh muss roh bleiben. Vorher blieb sie ganz unberührt und lag damit
 * weiter im Koordinatenraum des Vergleichs-Videos, während [GhostPoseTrack.frameWidth]
 * schon der Referenzraum war: das Debug-Overlay zeichnete die Roh-Keypoints des Geists
 * an falscher Stelle, und die „roh"-Kennzahlen im HUD verglichen zwei verschiedene Räume.
 */
fun GhostPoseTrack.transformedBy(homography: Homography, target: GhostPoseTrack): GhostPoseTrack {
    fun List<GhostPoseFrame>.mapped(): List<GhostPoseFrame> = map { frame ->
        frame.copy(
            landmarks = frame.landmarks.map { landmark ->
                val mapped = homography.map(landmark.x, landmark.y)
                landmark.copy(x = mapped.x.toFloat(), y = mapped.y.toFloat())
            },
        )
    }
    return copy(
        frameWidth = target.frameWidth,
        frameHeight = target.frameHeight,
        frames = enforceRigidSkeleton(frames.mapped()),
        rawFrames = rawFrames?.mapped(),
    )
}

fun GhostPoint.toVec2(): Vec2 = Vec2(x.toDouble(), y.toDouble())
