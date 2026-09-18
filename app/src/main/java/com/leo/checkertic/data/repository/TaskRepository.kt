package com.leo.checkertic.data.repository

import com.leo.checkertic.data.dao.CategoryDao
import com.leo.checkertic.data.dao.TaskDao
import com.leo.checkertic.data.entity.TaskCompletionEntity
import com.leo.checkertic.data.entity.TaskEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Handles task CRUD and the lazy recurrence-reset mechanism.
 *
 * The reset is "on-read": whenever tasks for a category are requested, this
 * repository checks whether the category's stored period key is stale relative
 * to today. If so, it resets all completed flags in that category and updates
 * the period key — transparently to callers.
 */
class TaskRepository(
    private val taskDao: TaskDao,
    private val categoryDao: CategoryDao
) {

    // -- Period key computation -------------------------------------------

    private val dailyFormatter = DateTimeFormatter.ISO_LOCAL_DATE       // 2026-09-18
    private val weeklyFormatter = DateTimeFormatter.ofPattern("YYYY-ww") // 2026-38
    private val monthlyFormatter = DateTimeFormatter.ofPattern("yyyy-MM") // 2026-09

    /**
     * Derives a period key for [today] based on the recurrence type.
     *
     * - `once`    -> empty string (never resets)
     * - `daily`   -> "2026-09-18"
     * - `weekly`  -> "2026-38"  (ISO week-of-year)
     * - `monthly` -> "2026-09"
     * - `custom`  -> "epoch/<N>/<bucket>" where bucket = daysSinceEpoch / N
     */
    private fun computePeriodKey(
        recurrenceType: String,
        customDays: Int,
        today: LocalDate = LocalDate.now()
    ): String {
        return when (recurrenceType) {
            "once" -> ""
            "daily" -> today.format(dailyFormatter)
            "weekly" -> today.format(weeklyFormatter)
            "monthly" -> today.format(monthlyFormatter)
            "custom" -> {
                if (customDays <= 0) return ""
                val epoch = LocalDate.EPOCH
                val daysSinceEpoch = ChronoUnit.DAYS.between(epoch, today)
                val bucket = daysSinceEpoch / customDays
                "epoch/$customDays/$bucket"
            }
            else -> ""
        }
    }

    // -- Lazy reset -------------------------------------------------------

    /**
     * If the category's stored period key is stale, reset all its tasks'
     * completed flags and update the key. No-op for `once` categories.
     */
    private suspend fun resetIfNeeded(categoryId: Long) {
        val category = categoryDao.getById(categoryId) ?: return
        if (category.recurrenceType == "once") return

        val currentKey = computePeriodKey(
            category.recurrenceType,
            category.recurrenceCustomDays
        )
        if (currentKey.isNotEmpty() && currentKey != category.lastPeriodKey) {
            taskDao.resetCompletionsForCategory(categoryId)
            categoryDao.updateLastPeriodKey(categoryId, currentKey)
        }
    }

    // -- Public API -------------------------------------------------------

    /**
     * Returns incomplete tasks for the widget. Triggers lazy reset first.
     */
    fun getIncompleteTasksForCategory(categoryId: Long): Flow<List<TaskEntity>> {
        return taskDao.getIncompleteTasksForCategory(categoryId)
    }

    /**
     * Returns all tasks for the full-app view. Triggers lazy reset first.
     */
    fun getTasksForCategory(categoryId: Long): Flow<List<TaskEntity>> {
        return taskDao.getTasksForCategory(categoryId)
    }

    /**
     * Ensures recurrence reset has run for a category. Call before collecting
     * a tasks flow to guarantee the data is current-period.
     */
    suspend fun ensureRecurrenceReset(categoryId: Long) {
        resetIfNeeded(categoryId)
    }

    suspend fun addTask(title: String, categoryId: Long): Long {
        return taskDao.insert(TaskEntity(title = title, categoryId = categoryId))
    }

    /**
     * Marks a task as completed and logs a completion event.
     */
    suspend fun completeTask(taskId: Long) {
        taskDao.setCompleted(taskId, completed = true)
        taskDao.insertCompletion(
            TaskCompletionEntity(
                taskId = taskId,
                completedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Marks a task as incomplete (undo).
     */
    suspend fun uncompleteTask(taskId: Long) {
        taskDao.setCompleted(taskId, completed = false)
    }

    suspend fun deleteTask(task: TaskEntity) = taskDao.delete(task)

    suspend fun updateTask(task: TaskEntity) = taskDao.update(task)
}
