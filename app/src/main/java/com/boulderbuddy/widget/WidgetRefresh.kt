package com.boulderbuddy.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/**
 * Stößt ein Neuzeichnen aller platzierten [BoulderWidget]-Instanzen an.
 *
 * Nötig, weil das Widget nur einen Room-Snapshot zeigt und das Framework sonst erst nach
 * `updatePeriodMillis` (30 min) neu lädt. Damit „Session öffnen" vs. „Session starten" nicht
 * an veralteten Daten hängt, rufen die ViewModels das nach Session-Start und -Ende auf.
 * Ohne platziertes Widget ist der Aufruf ein No-Op.
 */
suspend fun refreshBoulderWidget(context: Context) {
    BoulderWidget().updateAll(context.applicationContext)
}
