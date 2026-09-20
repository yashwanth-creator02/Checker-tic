package com.leo.checkertic.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.leo.checkertic.core.crypto.Vault
import com.leo.checkertic.data.AppDatabase
import com.leo.checkertic.data.entity.ReminderEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires a due reminder and re-arms the next occurrence.
 *
 * `goAsync()` because a broadcast receiver's `onReceive` runs on the main
 * thread and must return promptly; the database read that decides what the
 * notification says has to happen off it. The pending result is finished in
 * every path, including failure, or the system logs an ANR against the app.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_FIRE) return
        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L)
        if (reminderId <= 0) return

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                deliver(appContext, reminderId)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun deliver(context: Context, reminderId: Long) {
        Notifications.ensureChannels(context)
        val db = AppDatabase.getInstance(context)
        val reminder = db.reminderDao().getById(reminderId) ?: return
        if (!reminder.enabled) return

        val now = System.currentTimeMillis()
        val notificationId = (reminderId % Int.MAX_VALUE).toInt()

        when (reminder.ownerType) {
            ReminderEntity.OWNER_TASK -> {
                val task = db.taskDao().getById(reminder.ownerId)
                if (task != null && !task.completed) {
                    val category = db.categoryDao().getById(task.categoryId)
                    // A locked category must not put its task titles on the
                    // lock screen. The reminder still fires — suppressing it
                    // entirely would make locking silently break reminders —
                    // but it says only that something is due.
                    val title = if (task.encrypted) {
                        "Reminder in ${category?.name ?: "a locked list"}"
                    } else {
                        task.title
                    }
                    Notifications.postTaskReminder(
                        context = context,
                        notificationId = notificationId,
                        taskTitle = title,
                        categoryName = category?.name ?: "",
                        categoryId = task.categoryId,
                        taskId = task.id
                    )
                }
            }

            ReminderEntity.OWNER_CATEGORY -> {
                val category = db.categoryDao().getById(reminder.ownerId)
                if (category != null) {
                    val outstanding = db.taskDao()
                        .getTasksForCategoryOnce(category.id)
                        .filter { !it.completed }
                    // Tasks with their own reminder are excluded from the
                    // digest: the whole point of a task-level reminder is to
                    // be told about that task separately.
                    val ownReminders = db.reminderDao().getAllOnce()
                        .filter { it.ownerType == ReminderEntity.OWNER_TASK && it.enabled }
                        .map { it.ownerId }
                        .toSet()
                    val digestItems = outstanding.filter { it.id !in ownReminders }
                    val titles = if (category.locked && !Vault.unlocked.value) {
                        emptyList()
                    } else {
                        digestItems.map { task ->
                            if (task.encrypted) Vault.open(task.title) ?: "" else task.title
                        }.filter { it.isNotBlank() }
                    }
                    Notifications.postCategoryReminder(
                        context = context,
                        notificationId = notificationId,
                        categoryName = category.name,
                        categoryId = category.id,
                        outstandingTitles = titles,
                        outstandingCount = digestItems.size
                    )
                }
            }
        }

        val next = ReminderScheduler.nextOccurrence(reminder, now)
        if (next != null) {
            db.reminderDao().advance(reminder.id, next, now)
            ReminderScheduler.schedule(context, reminder.copy(triggerAt = next))
        } else {
            db.reminderDao().disable(reminder.id)
        }
    }
}
