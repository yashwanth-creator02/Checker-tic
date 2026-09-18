package com.leo.checkertic.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WidgetUpdater {
    suspend fun update(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val manager = GlanceAppWidgetManager(context)
                val glanceIds = manager.getGlanceIds(CheckerTicWidget::class.java)
                glanceIds.forEach { glanceId ->
                    CheckerTicWidget().update(context, glanceId)
                }
            } catch (_: Exception) {
                // Ignore if Glance is unavailable
            }
        }
    }
}
