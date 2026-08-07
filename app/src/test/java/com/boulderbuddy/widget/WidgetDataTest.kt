package com.boulderbuddy.widget

import com.boulderbuddy.data.db.entity.GymEntity
import com.boulderbuddy.data.db.entity.RouteEntity
import com.boulderbuddy.data.db.entity.SessionEntity
import com.boulderbuddy.data.model.RouteStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Sichert die Einstiegs-Logik des Homescreen-Widgets ab: läuft eine Session, springt das
 * Widget hinein; sonst bietet es den „Session starten"-Flow an.
 */
class WidgetDataTest {

    /**
     * Ohne gesetzten Schalter muss die **automatische** Palette gewählt werden.
     *
     * Der Unterschied ist nicht kosmetisch: nur die Auto-Palette besteht aus Farbressourcen,
     * die der Launcher live gegen sein `values-night` auflöst. Ein fester Farbwert bliebe
     * beim System-Theme-Wechsel stehen — genau der Fehler, der am Gerät sichtbar war, als
     * alles ringsum dunkel wurde und das Widget cremefarben blieb.
     */
    @Test
    fun `ohne gesetzten Schalter folgt das Widget dem System`() {
        assertThat(paletteFuer(null)).isEqualTo(paletteFuer(null))
        assertThat(paletteFuer(null)).isNotEqualTo(paletteFuer(true))
        assertThat(paletteFuer(null)).isNotEqualTo(paletteFuer(false))
    }

    @Test
    fun `der gesetzte Schalter waehlt einen festen, vom System unabhaengigen Satz`() {
        assertThat(paletteFuer(true)).isNotEqualTo(paletteFuer(false))
    }

    @Test
    fun `der Dark-Mode-Schalter steht in den Widget-Daten, auch ohne aktive Session`() {
        // Der Leerzustand ist ein eigener Rückgabepfad in buildWidgetData — er hat das
        // Theme-Feld beim ersten Anlauf schlicht fallen lassen.
        val leer = buildWidgetData(
            routes = emptyList(),
            active = null,
            gyms = emptyList(),
            darkModeOverride = true,
        )
        assertThat(leer.darkModeOverride).isTrue()

        val mitSession = buildWidgetData(
            routes = emptyList(),
            active = SessionEntity(id = 7, gymId = 1, date = 0L),
            gyms = emptyList(),
            darkModeOverride = true,
        )
        assertThat(mitSession.darkModeOverride).isTrue()
    }

    @Test
    fun `ohne aktive Session fuehrt das Widget in den Anlege-Flow`() {
        val data = WidgetData(hasActiveSession = false, totalTops = 12)

        assertThat(data.sessionNavTarget).isEqualTo(WidgetIntent.TARGET_NEW_SESSION)
    }

    @Test
    fun `mit aktiver Session fuehrt das Widget direkt in die Session`() {
        val data = WidgetData(
            hasActiveSession = true,
            gymName = "Boulderwelt",
            activeSessionId = 7,
        )

        assertThat(data.sessionNavTarget).isEqualTo(WidgetIntent.TARGET_ACTIVE_SESSION)
    }

    @Test
    fun `laufende Session liefert Halle, ID und Zaehler`() {
        val data = buildWidgetData(
            routes = listOf(
                RouteEntity(id = 1, sessionId = 7, status = RouteStatus.SENT),
                RouteEntity(id = 2, sessionId = 7, status = RouteStatus.PROJECT),
                // Top aus einer alten Session: zaehlt nur in die Gesamtsumme.
                RouteEntity(id = 3, sessionId = 6, status = RouteStatus.SENT),
            ),
            active = SessionEntity(id = 7, gymId = 3, date = 0L, endedAt = null),
            gyms = listOf(GymEntity(id = 3, name = "Boulderwelt")),
        )

        assertThat(data.hasActiveSession).isTrue()
        assertThat(data.activeSessionId).isEqualTo(7)
        assertThat(data.gymName).isEqualTo("Boulderwelt")
        assertThat(data.routeCount).isEqualTo(2)
        assertThat(data.sessionTops).isEqualTo(1)
        assertThat(data.totalTops).isEqualTo(2)
    }

    @Test
    fun `beendete Session verschwindet aus dem Widget`() {
        // observeActive liefert nach dem Beenden null — das Widget darf dann nicht mehr in die
        // alte Session springen, sondern bietet wieder "Session starten" an.
        val data = buildWidgetData(
            routes = listOf(RouteEntity(id = 1, sessionId = 7, status = RouteStatus.SENT)),
            active = null,
            gyms = listOf(GymEntity(id = 3, name = "Boulderwelt")),
        )

        assertThat(data.hasActiveSession).isFalse()
        assertThat(data.activeSessionId).isNull()
        assertThat(data.totalTops).isEqualTo(1)
        assertThat(data.sessionNavTarget).isEqualTo(WidgetIntent.TARGET_NEW_SESSION)
    }

    @Test
    fun `aktive Session ohne ID faellt auf den Anlege-Flow zurueck`() {
        // Defensive: ohne ID gibt es kein Sprungziel — dann lieber „Session starten" anbieten
        // als in der App auf einem toten Ziel zu landen.
        val data = WidgetData(hasActiveSession = true, activeSessionId = null)

        assertThat(data.sessionNavTarget).isEqualTo(WidgetIntent.TARGET_NEW_SESSION)
    }
}
