package com.boulderbuddy.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/**
 * Stößt ein Neuzeichnen aller platzierten [BoulderWidget]-Instanzen an.
 *
 * Läuft gerade eine Glance-Composition-Session, holt sich das Widget Änderungen ohnehin selbst
 * (es sammelt [observeWidgetData] in der Composition). Diese Sessions sind aber kurzlebig — sie
 * laufen als WorkManager-Job und enden ~45 s nach der letzten Composition. Danach bleibt auf dem
 * Homescreen das zuletzt gezeichnete Bild stehen, bis ein Update-Event eine neue Session startet;
 * genau das lösen die ViewModels hiermit nach Session-Start und -Ende aus. Ohne das würde erst
 * `updatePeriodMillis` (30 min) greifen.
 *
 * Ohne platziertes Widget ist der Aufruf ein No-Op.
 */
suspend fun refreshBoulderWidget(context: Context) {
    BoulderWidget().updateAll(context.applicationContext)
}
