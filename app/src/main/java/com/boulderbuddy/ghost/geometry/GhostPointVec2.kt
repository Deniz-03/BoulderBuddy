package com.boulderbuddy.ghost.geometry

import com.boulderbuddy.ghost.model.GhostPoint

/**
 * Übergang vom Modell-Punkt zum Rechenpunkt der Geometrie — gebraucht für die Anker, aus
 * denen die Homographie geschätzt wird.
 *
 * Bleibt bei der Geometrie: mit Posen hat die Umrechnung nichts zu tun, sie führt nur einen
 * Bildpunkt in die Ebene, in der [Homography] rechnet.
 */
fun GhostPoint.toVec2(): Vec2 = Vec2(x.toDouble(), y.toDouble())
