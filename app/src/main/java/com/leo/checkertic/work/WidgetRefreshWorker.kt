package com.leo.checkertic.work

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.leo.checkertic.widget.CheckerTicWidget

class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val manager = GlanceAppWidgetManager(applicationContext)
        val glanceIds = manager.getGlanceIds(CheckerTicWidget::class.java)
        glanceIds.forEach { glanceId ->
            CheckerTicWidget().update(applicationContext, glanceId)
        }
        return Result.success()
    }
}
