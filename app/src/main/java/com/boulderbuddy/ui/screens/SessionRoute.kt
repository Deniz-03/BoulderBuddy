package com.boulderbuddy.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.ui.theme.BoulderBuddyTheme

// Minimal-Infos einer Session, die für die UI-Entscheidung nötig sind.
// Kernregel: endedAt == null ⇒ die Session läuft noch ⇒ aktive Ansicht.
// (endedAt liefert später zugleich die Dauer: endedAt - date.)
private data class SessionMeta(
    val id: Int,
    val endedAt: Long?,
) {
    val istAktiv: Boolean get() = endedAt == null
}

// Platzhalter, bis ViewModel/Room existiert: simuliert das Laden einer Session per id.
// TODO: ersetzen durch ViewModel/Room — SELECT endedAt FROM Session WHERE id = :sessionId.
//  Hier nur zur Demonstration: id 0 = laufende Session, alles andere = abgeschlossen.
private fun ladeSessionMeta(sessionId: Int): SessionMeta =
    if (sessionId == 0) {
        SessionMeta(id = sessionId, endedAt = null)              // läuft noch
    } else {
        SessionMeta(id = sessionId, endedAt = 1_700_000_000_000L) // beendet (Beispiel-Timestamp)
    }

// Routing-/Dispatcher-Ebene für die beiden Session-Ansichten — KEIN sichtbarer Screen,
// rendert nichts Eigenes. Der Aufrufer (die Navigation) übergibt nur die sessionId und
// die Navigations-Callbacks; welches UI geladen wird, erkennt das System selbst am
// Aktiv-Marker. onAddRoute wird hier an die sessionId gebunden, bevor es (nur an die
// aktive Ansicht) weitergereicht wird.
@Composable
fun SessionRoute(
    sessionId: Int,
    onBack: () -> Unit = {},
    onOpenBoulder: (Int) -> Unit = {},
    onAddRoute: (Int) -> Unit = {},
) {
    val session = ladeSessionMeta(sessionId)
    if (session.istAktiv) {
        // aktiv: editierbar, Live-Timer, Add-Button
        SessionDetailScreen(
            onBack = onBack,
            onOpenBoulder = onOpenBoulder,
            onAddRoute = { onAddRoute(sessionId) },
        )
    } else {
        // abgeschlossen: read-only
        AlteSessionScreen(
            onBack = onBack,
            onOpenBoulder = onOpenBoulder,
        )
    }
}

@Preview(name = "Aktive Session → Detail", showBackground = true, backgroundColor = 0xFFF9F4E3)
@Composable
private fun SessionRouteAktivPreview() {
    BoulderBuddyTheme {
        SessionRoute(sessionId = 0) // 0 = laufende Beispiel-Session
    }
}

@Preview(name = "Abgeschlossene Session → Alte Ansicht", showBackground = true, backgroundColor = 0xFFF9F4E3)
@Composable
private fun SessionRouteAbgeschlossenPreview() {
    BoulderBuddyTheme {
        SessionRoute(sessionId = 1) // hat endedAt → read-only
    }
}
