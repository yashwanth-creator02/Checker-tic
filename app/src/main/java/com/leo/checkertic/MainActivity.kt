package com.leo.checkertic

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.leo.checkertic.core.audio.VoiceStore
import com.leo.checkertic.core.crypto.Vault
import com.leo.checkertic.core.share.ShareExport
import com.leo.checkertic.reminders.Notifications
import com.leo.checkertic.reminders.ReminderScheduler
import com.leo.checkertic.ui.screens.MainScreen
import com.leo.checkertic.ui.theme.CheckerTicTheme
import com.leo.checkertic.ui.theme.ThemeMode
import com.leo.checkertic.widget.WidgetUpdater
import com.leo.checkertic.work.WidgetRefreshWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The single Activity.
 *
 * Now a [FragmentActivity] rather than a `ComponentActivity`, because
 * `androidx.biometric`'s `BiometricPrompt` hosts itself in a fragment to
 * survive configuration changes. `FragmentActivity` extends
 * `ComponentActivity`, so `setContent` and the Compose integration are
 * unchanged.
 *
 * Startup work is deliberately split: anything that touches disk or the
 * database runs on `Dispatchers.IO` in `lifecycleScope`, so the first frame
 * is not waiting on a reminder sweep or a cache sweep.
 */
class MainActivity : FragmentActivity() {

    private var initialTabState by mutableStateOf<String?>(null)
    private var openNoteIdState by mutableStateOf<Long?>(null)
    private var openCategoryIdState by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        readIntent(intent)

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val initialTheme = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.DARK.name)!!)
        }.getOrDefault(ThemeMode.DARK)

        setContent {
            var themeMode by remember { mutableStateOf(initialTheme) }
            CheckerTicTheme(themeMode = themeMode) {
                MainScreen(
                    currentThemeMode = themeMode,
                    onThemeModeChange = { themeMode = it },
                    initialTab = initialTabState,
                    openNoteId = openNoteIdState,
                    openCategoryId = openCategoryIdState
                )
            }
        }

        // Everything below is off the critical path to first frame.
        lifecycleScope.launch(Dispatchers.IO) {
            Notifications.ensureChannels(applicationContext)
            initWorkManager(prefs.getBoolean(KEY_NIGHTLY_REFRESH, true))
            // Alarms are cleared by an app update as well as a reboot, so
            // re-arming here covers the update case that BootReceiver can't.
            ReminderScheduler.rescheduleAll(applicationContext)
            // Scratch files a process kill could have left behind: decrypted
            // audio for playback, and half-finished recordings.
            VoiceStore.clearPlaybackCache(applicationContext)
            ShareExport.clearExportCache(applicationContext)
            WidgetUpdater.update(applicationContext)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent) {
        initialTabState = intent.getStringExtra(Notifications.EXTRA_INITIAL_TAB)
        openNoteIdState = intent.getLongExtra("open_note_id", -1L).takeIf { it > 0 }
        openCategoryIdState = intent
            .getLongExtra(Notifications.EXTRA_CATEGORY_ID, -1L)
            .takeIf { it > 0 }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(Dispatchers.IO) { WidgetUpdater.update(applicationContext) }
    }

    /**
     * Drops the vault session when the app leaves the foreground.
     *
     * The Keystore's own auth window is the real boundary, but clearing the
     * session flag here means a locked note is not left rendered in the
     * recents screenshot after the user switches away.
     */
    override fun onStop() {
        super.onStop()
        Vault.lock()
    }

    private fun initWorkManager(enabled: Boolean) {
        if (!enabled) return
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "widget_nightly_refresh",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WidgetRefreshWorker>(12, TimeUnit.HOURS).build()
        )
    }

    private companion object {
        const val PREFS = "checker_tic_prefs"
        const val KEY_THEME = "theme_mode"
        const val KEY_NIGHTLY_REFRESH = "nightly_refresh_enabled"
    }
}
