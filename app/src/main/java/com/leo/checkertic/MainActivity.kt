package com.leo.checkertic

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.leo.checkertic.ui.screens.MainScreen
import com.leo.checkertic.ui.theme.CheckerTicTheme
import com.leo.checkertic.work.WidgetRefreshWorker
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initWorkManager()
        setContent {
            CheckerTicTheme {
                MainScreen()
            }
        }
    }

    private fun initWorkManager() {
        val prefs = getSharedPreferences("checker_tic_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("nightly_refresh_enabled", true)
        val workManager = WorkManager.getInstance(this)
        if (enabled) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
                12, TimeUnit.HOURS
            ).build()
            workManager.enqueueUniquePeriodicWork(
                "widget_nightly_refresh",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}