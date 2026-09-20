package com.leo.checkertic.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-arms alarms after a reboot or an app update.
 *
 * Both matter: `BOOT_COMPLETED` covers restarts, and `MY_PACKAGE_REPLACED`
 * covers updates, which also clear pending alarms. Without the second one,
 * every user silently loses their reminders the next time the app updates —
 * a failure mode that is invisible until somebody misses something.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val relevant = intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        if (!relevant) return

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Notifications.ensureChannels(appContext)
                ReminderScheduler.rescheduleAll(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}
