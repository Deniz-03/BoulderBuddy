package com.boulderbuddy.widget

import android.content.Context
import android.content.Intent
import com.boulderbuddy.MainActivity

/**
 * Contract für den Sprung Widget → App (7.4c). Das Widget startet [MainActivity] mit einem
 * Ziel-Extra; die Activity reicht es an die Navigation weiter (siehe `AppNavigation`).
 */
object WidgetIntent {
    const val EXTRA_NAV_TARGET = "com.boulderbuddy.widget.NAV_TARGET"

    /** Session-ID für [TARGET_ACTIVE_SESSION]; fehlt sie, bleibt die App auf Home. */
    const val EXTRA_SESSION_ID = "com.boulderbuddy.widget.SESSION_ID"

    /** Direkt zum Hangboard-Timer. */
    const val TARGET_TIMER = "timer"

    /** Direkt in den „Session starten"-Flow. */
    const val TARGET_NEW_SESSION = "new_session"

    /** Direkt in die laufende Session (braucht [EXTRA_SESSION_ID]). */
    const val TARGET_ACTIVE_SESSION = "active_session"

    /**
     * Explizites [Intent] auf [MainActivity] mit optionalem Navigationsziel [target].
     * [sessionId] wird nur für [TARGET_ACTIVE_SESSION] gebraucht.
     */
    fun toApp(context: Context, target: String? = null, sessionId: Int? = null): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (target != null) putExtra(EXTRA_NAV_TARGET, target)
            if (sessionId != null) putExtra(EXTRA_SESSION_ID, sessionId)
        }
}
