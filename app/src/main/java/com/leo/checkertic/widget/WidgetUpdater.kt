package com.leo.checkertic.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WidgetUpdater {
    suspend fun update(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val manager = GlanceAppWidgetManager(context)
                val glanceIds = manager.getGlanceIds(CheckerTicWidget::class.java)
                val tick = System.currentTimeMillis()
                glanceIds.forEach { glanceId ->
                    updateAppWidgetState(context, glanceId) { prefs ->
                        prefs[CheckerTicWidget.UPDATE_TICK_KEY] = tick
                    }
                    CheckerTicWidget().update(context, glanceId)
                }
            } catch (_: Exception) {
                // Ignore if Glance is unavailable
            }
        }
    }
}
