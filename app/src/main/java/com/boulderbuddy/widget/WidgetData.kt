package com.boulderbuddy.widget

import android.content.Context
import com.boulderbuddy.data.db.entity.GymEntity
import com.boulderbuddy.data.db.entity.RouteEntity
import com.boulderbuddy.data.db.entity.SessionEntity
import com.boulderbuddy.data.repository.GymRepository
import com.boulderbuddy.data.repository.RouteRepository
import com.boulderbuddy.data.repository.SessionRepository
import com.boulderbuddy.ui.model.istGetoppt
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

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

/**
 * Beobachtet die Widget-Daten in Room.
 *
 * Bewusst ein Flow und KEIN einmaliger Snapshot: Glance führt alles vor `provideContent` nur
 * einmal pro Composition-Session aus. `update()`/`updateAll()` rekomponiert eine laufende
 * Session bloß (es liest dabei nur die GlanceStateDefinition neu), lädt aber keine Repositories
 * nach — ein gecachter Snapshot bliebe dabei stehen. Deshalb sammelt [BoulderWidget] diesen
 * Flow INNERHALB der Composition; Room schiebt Änderungen dann von selbst nach.
 */
fun observeWidgetData(context: Context): Flow<WidgetData> {
    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        WidgetEntryPoint::class.java,
    )
    return combine(
        entryPoint.routeRepository().observeAll(),
        entryPoint.sessionRepository().observeActive(),
        entryPoint.gymRepository().observeAll(),
        ::buildWidgetData,
    )
}

/** Reine Zuordnung Room-Daten → [WidgetData]; ohne Context, damit testbar. */
internal fun buildWidgetData(
    routes: List<RouteEntity>,
    active: SessionEntity?,
    gyms: List<GymEntity>,
): WidgetData {
    val totalTops = routes.count { it.status.istGetoppt }
    if (active == null) return WidgetData(hasActiveSession = false, totalTops = totalTops)

    val sessionRoutes = routes.filter { it.sessionId == active.id }
    return WidgetData(
        hasActiveSession = true,
        gymName = gyms.firstOrNull { it.id == active.gymId }?.name ?: "Aktive Session",
        activeSessionId = active.id,
        routeCount = sessionRoutes.size,
        sessionTops = sessionRoutes.count { it.status.istGetoppt },
        totalTops = totalTops,
    )
}
