package com.boulderbuddy.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Manifest-Empfänger, der das [BoulderWidget] mit dem AppWidget-Framework verbindet (7.4c). */
class BoulderWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BoulderWidget()
}
