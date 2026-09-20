package com.leo.checkertic.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import com.leo.checkertic.data.AppDatabase
import com.leo.checkertic.data.entity.ReminderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * ============================================================================
 *  REMINDER SCHEDULING
 * ============================================================================
 *
 * ## Why AlarmManager and not WorkManager
 *
 * WorkManager's minimum periodic interval is 15 minutes and it explicitly
 * makes no promise about *when* inside a flex window a job runs. That is
 * correct for the existing nightly widget refresh, which nobody notices, and
 * wrong for a reminder: "remind me at 9:00" arriving at 9:23 is a broken
 * feature. Reminders are user-visible, time-anchored notifications, which is
 * exactly the case `AlarmManager` exists for.
 *
 * ## Exact alarms, and the permission reality
 *
 * Since Android 14, `SCHEDULE_EXACT_ALARM` is **denied by default** for new
 * installs targeting API 33+. The permission that is granted at install,
 * `USE_EXACT_ALARM`, is Play-restricted to apps whose core function is an
 * alarm clock, a timer, or a calendar showing event notifications. Flip is a
 * task app, so declaring it would risk the listing.
 *
 * So the app declares `SCHEDULE_EXACT_ALARM`, checks
 * `canScheduleExactAlarms()` before every schedule, and falls back to
 * `setAndAllowWhileIdle` when it is not granted. Inexact-while-idle still
 * fires during Doze; it just lands within a maintenance window rather than on
 * the second. A reminder a few minutes late is a working reminder — an app
 * that crashes with `SecurityException`, or that silently schedules nothing,
 * is not. [canBeExact] lets settings tell the user which of the two they are
 * currently getting, with a route to the system screen if they want precision.
 */
object ReminderScheduler {

    const val ACTION_FIRE = "com.leo.checkertic.action.REMINDER_FIRE"
    const val EXTRA_REMINDER_ID = "reminder_id"

    fun canBeExact(context: Context): Boolean {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun pendingIntent(context: Context, reminderId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_REMINDER_ID, reminderId)
            // The id must be part of the intent's identity, not just its
            // extras: PendingIntent equality ignores extras, so without this
            // every reminder would overwrite the previous one's alarm.
            data = android.net.Uri.parse("flip://reminder/$reminderId")
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Schedules (or reschedules) one reminder. No-op when disabled or past. */
    fun schedule(context: Context, reminder: ReminderEntity) {
        if (!reminder.enabled) return
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        val intent = pendingIntent(context, reminder.id)
        val triggerAt = reminder.triggerAt
        if (triggerAt <= System.currentTimeMillis()) return

        runCatching {
            if (canBeExact(context)) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    intent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    intent
                )
            }
        }.onFailure {
            // The permission can be revoked between the check above and the
            // call, in which case the system kills the app. Catching it and
            // degrading to inexact is strictly better than crashing.
            runCatching {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
            }
        }
    }

    fun cancel(context: Context, reminderId: Long) {
        context.getSystemService<AlarmManager>()?.cancel(pendingIntent(context, reminderId))
    }

    /**
     * Re-arms every stored reminder.
     *
     * Alarms do not survive a reboot or an app update, so this runs from
     * [BootReceiver] and from app start. Reminders already in the past are
     * rolled forward to their next occurrence rather than fired late — being
     * told at 3pm about a 9am reminder from two days ago is noise.
     */
    suspend fun rescheduleAll(context: Context) = withContext(Dispatchers.IO) {
        val dao = AppDatabase.getInstance(context).reminderDao()
        val now = System.currentTimeMillis()
        dao.allEnabled().forEach { reminder ->
            if (reminder.triggerAt > now) {
                schedule(context, reminder)
            } else {
                val next = nextOccurrence(reminder, now)
                if (next != null) {
                    dao.advance(reminder.id, next, reminder.lastFiredAt ?: now)
                    schedule(context, reminder.copy(triggerAt = next))
                } else {
                    dao.disable(reminder.id)
                }
            }
        }
    }

    /**
     * The next fire time strictly after [after], or null for a one-shot that
     * has already passed.
     *
     * Repeats are recomputed from the *calendar*, not by adding a fixed
     * millisecond interval. Adding 24h across a DST boundary drifts a 9:00
     * reminder to 8:00 or 10:00 and it never comes back.
     */
    fun nextOccurrence(
        reminder: ReminderEntity,
        after: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long? {
        if (reminder.repeatMode == ReminderEntity.REPEAT_NONE) return null

        val original = java.time.Instant.ofEpochMilli(reminder.triggerAt).atZone(zone)
        val timeOfDay = original.toLocalTime()
        var date = original.toLocalDate()
        val afterInstant = java.time.Instant.ofEpochMilli(after)

        var guard = 0
        while (guard++ < MAX_ROLL_FORWARD) {
            date = when (reminder.repeatMode) {
                ReminderEntity.REPEAT_DAILY -> date.plusDays(1)
                ReminderEntity.REPEAT_WEEKLY -> date.plusWeeks(1)
                ReminderEntity.REPEAT_MONTHLY -> date.plusMonths(1)
                ReminderEntity.REPEAT_CUSTOM ->
                    date.plusDays(reminder.repeatIntervalDays.coerceAtLeast(1).toLong())
                else -> return null
            }
            val candidate = LocalDateTime.of(date, timeOfDay).atZone(zone).toInstant()
            if (candidate.isAfter(afterInstant)) return candidate.toEpochMilli()
        }
        return null
    }

    /**
     * First fire time for a newly created reminder at [time] on a [repeat]
     * cadence — today if that moment hasn't passed, otherwise the next one.
     */
    fun firstOccurrence(
        time: LocalTime,
        repeat: String,
        intervalDays: Int,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        val today = LocalDate.now(zone)
        val todayAt = LocalDateTime.of(today, time).atZone(zone).toInstant()
        if (todayAt.toEpochMilli() > System.currentTimeMillis()) return todayAt.toEpochMilli()

        val nextDate = when (repeat) {
            ReminderEntity.REPEAT_WEEKLY -> today.plusWeeks(1)
            ReminderEntity.REPEAT_MONTHLY -> today.plusMonths(1)
            ReminderEntity.REPEAT_CUSTOM -> today.plusDays(intervalDays.coerceAtLeast(1).toLong())
            else -> today.plusDays(1)
        }
        return LocalDateTime.of(nextDate, time).atZone(zone).toInstant().toEpochMilli()
    }

    private const val MAX_ROLL_FORWARD = 400
}
