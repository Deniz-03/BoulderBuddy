package com.boulderbuddy.ghost.model

import kotlinx.serialization.Serializable

/**
 * Darstellungsmodus des Vergleichs (P7): Overlay nur, wenn die Versuche derselben
 * Linie folgen — bei fundamental anderer Beta ist Side-by-Side die ehrlichere
 * Darstellung (P6). Die App schlägt vor, der Nutzer überstimmt.
 */
@Serializable
enum class GhostViewMode { OVERLAY, SIDE_BY_SIDE }
