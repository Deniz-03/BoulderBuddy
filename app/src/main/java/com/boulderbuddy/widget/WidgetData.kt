package com.boulderbuddy.widget

import android.content.Context
import com.boulderbuddy.data.repository.GymRepository
import com.boulderbuddy.data.repository.RouteRepository
import com.boulderbuddy.data.repository.SessionRepository
import com.boulderbuddy.ui.model.istGetoppt
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/** Momentaufnahme für das Homescreen-Widget (7.4c). Bewusst flach & primitiv (Glance-State). */
data class WidgetData(
    val hasActiveSession: Boolean = false,
    val gymName: String = "",
    /** ID der laufenden Session — Sprungziel des Widgets, wenn eine Session aktiv ist. */
    val activeSessionId: Int? = null,
    /** Boulder in der aktiven Session. */
    val routeCount: Int = 0,
    /** Tops in der aktiven Session. */
    val sessionTops: Int = 0,
    /** Tops über alle Sessions (Motivations-Zahl, auch ohne aktive Session sinnvoll). */
    val totalTops: Int = 0,
)

/**
 * Navigationsziel des Session-Knopfs (und des Widget-Taps): läuft eine Session, geht es direkt
 * hinein, sonst in den „Session starten"-Flow. Bewusst pur (nur Konstanten, kein Context),
 * damit die Entscheidung ohne Android-Laufzeit testbar ist.
 */
val WidgetData.sessionNavTarget: String
    get() = if (hasActiveSession && activeSessionId != null) {
        WidgetIntent.TARGET_ACTIVE_SESSION
    } else {
        WidgetIntent.TARGET_NEW_SESSION
    }

/**
 * Hilt-Zugang für das Widget. Der [BoulderWidgetReceiver] ist kein Hilt-Android-Entrypoint,
 * darum ziehen wir die (Singleton-)Repositories über [EntryPointAccessors] aus der Application.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun sessionRepository(): SessionRepository
    fun routeRepository(): RouteRepository
    fun gymRepository(): GymRepository
}

/** Lädt einen einmaligen Snapshot der Widget-Daten aus Room (im Glance-Coroutine-Scope). */
suspend fun loadWidgetData(context: Context): WidgetData {
    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        WidgetEntryPoint::class.java,
    )
    val routes = entryPoint.routeRepository().observeAll().first()
    val totalTops = routes.count { it.status.istGetoppt }

    val active = entryPoint.sessionRepository().observeActive().first()
        ?: return WidgetData(hasActiveSession = false, totalTops = totalTops)

    val sessionRoutes = routes.filter { it.sessionId == active.id }
    val gymName = entryPoint.gymRepository().getById(active.gymId)?.name ?: "Aktive Session"
    return WidgetData(
        hasActiveSession = true,
        gymName = gymName,
        activeSessionId = active.id,
        routeCount = sessionRoutes.size,
        sessionTops = sessionRoutes.count { it.status.istGetoppt },
        totalTops = totalTops,
    )
}
