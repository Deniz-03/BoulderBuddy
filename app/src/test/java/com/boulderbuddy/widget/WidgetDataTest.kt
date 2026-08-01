package com.boulderbuddy.widget

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Sichert die Einstiegs-Logik des Homescreen-Widgets ab: läuft eine Session, springt das
 * Widget hinein; sonst bietet es den „Session starten"-Flow an.
 */
class WidgetDataTest {

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
    fun `aktive Session ohne ID faellt auf den Anlege-Flow zurueck`() {
        // Defensive: ohne ID gibt es kein Sprungziel — dann lieber „Session starten" anbieten
        // als in der App auf einem toten Ziel zu landen.
        val data = WidgetData(hasActiveSession = true, activeSessionId = null)

        assertThat(data.sessionNavTarget).isEqualTo(WidgetIntent.TARGET_NEW_SESSION)
    }
}
