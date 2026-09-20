package com.leo.checkertic.reminders

import android.content.Context
import com.leo.checkertic.data.dao.ReminderDao
import com.leo.checkertic.data.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalTime

/**
 * Create / update / clear reminders, keeping the database row and the system
 * alarm in step.
 *
 * Every mutation goes through here rather than touching the DAO and
 * [ReminderScheduler] separately — the single most likely bug in a reminder
 * feature is a stored row with no alarm behind it, or an alarm with no row.
 */
class ReminderRepository(
    private val reminderDao: ReminderDao,
    private val context: Context
) {

    fun observeAll(): Flow<List<ReminderEntity>> = reminderDao.observeAll()

    suspend fun forTask(taskId: Long): ReminderEntity? =
        reminderDao.forOwner(ReminderEntity.OWNER_TASK, taskId)

    suspend fun forCategory(categoryId: Long): ReminderEntity? =
        reminderDao.forOwner(ReminderEntity.OWNER_CATEGORY, categoryId)

    suspend fun set(
        ownerType: String,
        ownerId: Long,
        time: LocalTime,
        repeatMode: String,
        intervalDays: Int = 0
    ): ReminderEntity {
        val existing = reminderDao.forOwner(ownerType, ownerId)
        existing?.let { ReminderScheduler.cancel(context, it.id) }

        val triggerAt = ReminderScheduler.firstOccurrence(time, repeatMode, intervalDays)
        val row = ReminderEntity(
            id = existing?.id ?: 0L,
            ownerType = ownerType,
            ownerId = ownerId,
            triggerAt = triggerAt,
            repeatMode = repeatMode,
            repeatIntervalDays = intervalDays,
            enabled = true
        )
        val id = reminderDao.upsert(row)
        val saved = row.copy(id = if (row.id == 0L) id else row.id)
        ReminderScheduler.schedule(context, saved)
        return saved
    }

    suspend fun clear(ownerType: String, ownerId: Long) {
        reminderDao.forOwner(ownerType, ownerId)?.let {
            ReminderScheduler.cancel(context, it.id)
            reminderDao.delete(it)
        }
    }

    /** Called when a task or category is deleted, so no orphan alarm survives. */
    suspend fun clearForDeleted(ownerType: String, ownerId: Long) = clear(ownerType, ownerId)
}
