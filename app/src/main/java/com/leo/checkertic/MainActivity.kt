package com.leo.checkertic

import android.content.Context
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.leo.checkertic.ui.screens.MainScreen
import com.leo.checkertic.ui.theme.CheckerTicTheme
import com.leo.checkertic.ui.theme.ThemeMode
import com.leo.checkertic.work.WidgetRefreshWorker
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private var initialTabState by mutableStateOf<String?>(null)
    private var openNoteIdState by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initWorkManager()

        initialTabState = intent.getStringExtra("initial_tab")
        openNoteIdState = intent.getLongExtra("open_note_id", -1L).takeIf { it > 0 }

        val prefs = getSharedPreferences("checker_tic_prefs", Context.MODE_PRIVATE)
        val savedThemeStr = prefs.getString("theme_mode", ThemeMode.DARK.name) ?: ThemeMode.DARK.name
        val initialTheme = try {
            ThemeMode.valueOf(savedThemeStr)
        } catch (e: Exception) {
            ThemeMode.DARK
        }

        setContent {
            var themeMode by remember { mutableStateOf(initialTheme) }

            CheckerTicTheme(themeMode = themeMode) {
                MainScreen(
                    currentThemeMode = themeMode,
                    onThemeModeChange = { themeMode = it },
                    initialTab = initialTabState,
                    openNoteId = openNoteIdState
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        initialTabState = intent.getStringExtra("initial_tab")
        openNoteIdState = intent.getLongExtra("open_note_id", -1L).takeIf { it > 0 }
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