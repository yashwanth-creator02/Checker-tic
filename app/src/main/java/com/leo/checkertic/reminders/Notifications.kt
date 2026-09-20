package com.leo.checkertic.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.leo.checkertic.MainActivity
import com.leo.checkertic.R

/**
 * Notification channel setup and posting.
 *
 * Two channels rather than one, because they carry genuinely different
 * urgency: a task reminder is something the user asked to be interrupted
 * about, while a category digest is a nudge. Splitting them means the user
 * can silence the nudges without losing the ones that matter, in the system
 * settings they already know how to use.
 */
object Notifications {

    const val CHANNEL_TASK = "reminders_task"
    const val CHANNEL_CATEGORY = "reminders_category"

    const val EXTRA_INITIAL_TAB = "initial_tab"
    const val EXTRA_CATEGORY_ID = "open_category_id"
    const val EXTRA_TASK_ID = "open_task_id"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TASK,
                "Task reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders you set on individual tasks."
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CATEGORY,
                "List reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Recurring nudges for a whole list."
            }
        )
    }

    fun canPost(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * Deep link back into the app.
     *
     * `SINGLE_TOP` plus `CLEAR_TOP` so tapping a reminder while the app is
     * already open routes through `onNewIntent` to the right screen instead
     * of stacking a second copy of MainActivity behind the first.
     */
    private fun contentIntent(
        context: Context,
        requestCode: Int,
        tab: String,
        categoryId: Long?,
        taskId: Long?
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_INITIAL_TAB, tab)
            categoryId?.let { putExtra(EXTRA_CATEGORY_ID, it) }
            taskId?.let { putExtra(EXTRA_TASK_ID, it) }
            data = android.net.Uri.parse("flip://open/$tab/${categoryId ?: 0}/${taskId ?: 0}")
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun postTaskReminder(
        context: Context,
        notificationId: Int,
        taskTitle: String,
        categoryName: String,
        categoryId: Long,
        taskId: Long
    ) {
        if (!canPost(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_TASK)
            .setSmallIcon(R.drawable.ic_widget_tasks)
            .setContentTitle(taskTitle)
            .setContentText(categoryName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(
                contentIntent(context, notificationId, "tasks", categoryId, taskId)
            )
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notificationId, notification) }
    }

    fun postCategoryReminder(
        context: Context,
        notificationId: Int,
        categoryName: String,
        categoryId: Long,
        outstandingTitles: List<String>,
        outstandingCount: Int
    ) {
        if (!canPost(context)) return
        val text = when (outstandingCount) {
            0 -> "Nothing outstanding. Nice."
            1 -> "1 thing left"
            else -> "$outstandingCount things left"
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_CATEGORY)
            .setSmallIcon(R.drawable.ic_widget_tasks)
            .setContentTitle(categoryName)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(
                contentIntent(context, notificationId, "tasks", categoryId, null)
            )

        if (outstandingTitles.isNotEmpty()) {
            val style = NotificationCompat.InboxStyle().setBigContentTitle(categoryName)
            outstandingTitles.take(MAX_INBOX_LINES).forEach { style.addLine(it) }
            if (outstandingCount > MAX_INBOX_LINES) {
                style.setSummaryText("+${outstandingCount - MAX_INBOX_LINES} more")
            }
            builder.setStyle(style)
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        }
    }

    private const val MAX_INBOX_LINES = 5
}
