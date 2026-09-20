package com.leo.checkertic.data.repository

import com.leo.checkertic.core.crypto.Vault
import com.leo.checkertic.core.time.PeriodKeys
import com.leo.checkertic.data.dao.CategoryDao
import com.leo.checkertic.data.dao.TaskDao
import com.leo.checkertic.data.entity.CategoryEntity
import com.leo.checkertic.data.entity.TaskEntity
import com.leo.checkertic.data.model.SearchScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Task CRUD, the lazy recurrence reset, and the encrypt/decrypt boundary for
 * locked categories.
 *
 * The period-key logic that used to live here privately now lives in
 * [PeriodKeys], because streak analytics needs precisely the same definition
 * of a period. Two copies would drift, and the user-visible symptom would be
 * a streak that disagrees with the resets they can see happening.
 */
class TaskRepository(
    private val taskDao: TaskDao,
    private val categoryDao: CategoryDao
) {

    // -- Lazy reset -------------------------------------------------------

    /**
     * If the category's stored period key is stale, reset its completed flags
     * and store the new key.
     *
     * Note this only clears the *flags* — `task_completions` is untouched, so
     * resetting a daily list never erases the history the heatmap and streaks
     * are drawn from.
     */
    private suspend fun resetIfNeeded(categoryId: Long) {
        val category = categoryDao.getById(categoryId) ?: return
        if (category.recurrenceType == PeriodKeys.ONCE) return

        val currentKey = PeriodKeys.keyFor(
            category.recurrenceType,
            category.recurrenceCustomDays,
            LocalDate.now()
        )
        if (currentKey.isNotEmpty() && currentKey != category.lastPeriodKey) {
            taskDao.resetCompletionsForCategory(categoryId)
            categoryDao.updateLastPeriodKey(categoryId, currentKey)
        }
    }

    suspend fun ensureRecurrenceReset(categoryId: Long) = resetIfNeeded(categoryId)

    /** Runs the reset check for every category. Called once on app start. */
    suspend fun ensureAllRecurrenceResets() {
        categoryDao.getAllOnce().forEach { resetIfNeeded(it.id) }
    }

    // -- Reads -------------------------------------------------------------

    fun getTasksForCategory(categoryId: Long): Flow<List<TaskEntity>> =
        taskDao.getTasksForCategory(categoryId)

    fun getIncompleteTasksForCategory(categoryId: Long): Flow<List<TaskEntity>> =
        taskDao.getIncompleteTasksForCategory(categoryId)

    fun getAllTasks(): Flow<List<TaskEntity>> = taskDao.getAllTasks()

    suspend fun getById(id: Long): TaskEntity? = taskDao.getById(id)

    suspend fun getTasksForCategoryOnce(categoryId: Long): List<TaskEntity> =
        taskDao.getTasksForCategoryOnce(categoryId)

    suspend fun countIncomplete(categoryId: Long): Int = taskDao.countIncomplete(categoryId)

    // -- Writes ------------------------------------------------------------

    /**
     * Adds a task, encrypting the title if the category is locked.
     *
     * The encryption decision is made here rather than at the call site so a
     * task added from the widget trampoline can never accidentally land in a
     * locked category as plaintext.
     */
    suspend fun addTask(title: String, categoryId: Long): Long {
        var targetCatId = categoryId
        var category = categoryDao.getById(targetCatId)
        if (category == null) {
            val fallback = categoryDao.getAllOnce().firstOrNull() ?: run {
                val now = System.currentTimeMillis()
                val newCat = CategoryEntity(name = "General", orderIndex = 0, createdAt = now, updatedAt = now)
                val newId = categoryDao.insert(newCat)
                newCat.copy(id = newId)
            }
            targetCatId = fallback.id
            category = fallback
        }
        val locked = category.locked
        val storedTitle = if (locked) Vault.seal(title) else title
        val nextOrder = (taskDao.getTasksForCategoryOnce(targetCatId)
            .maxOfOrNull { it.orderIndex } ?: -1) + 1
        val now = System.currentTimeMillis()
        return taskDao.insert(
            TaskEntity(
                title = storedTitle,
                categoryId = targetCatId,
                encrypted = locked,
                orderIndex = nextOrder,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun completeTask(taskId: Long) {
        val task = taskDao.getById(taskId) ?: return
        taskDao.completeTask(taskId, task.categoryId, System.currentTimeMillis())
    }

    suspend fun uncompleteTask(taskId: Long) =
        taskDao.uncompleteTask(taskId, System.currentTimeMillis())

    suspend fun setPinned(taskId: Long, pinned: Boolean) = taskDao.setPinned(taskId, pinned)

    suspend fun deleteTask(task: TaskEntity) = taskDao.delete(task)

    suspend fun deleteTasks(ids: List<Long>) = taskDao.deleteByIds(ids)

    /** Renames a task, re-sealing if it lives in a locked category. */
    suspend fun renameTask(task: TaskEntity, newTitle: String) {
        val stored = if (task.encrypted) Vault.seal(newTitle) else newTitle
        taskDao.update(task.copy(title = stored, updatedAt = System.currentTimeMillis()))
    }

    suspend fun persistManualOrder(tasks: List<TaskEntity>) {
        tasks.forEachIndexed { index, task -> taskDao.updateOrderIndex(task.id, index) }
    }

    // -- Locking -----------------------------------------------------------

    /**
     * Seals or opens every task in a category.
     *
     * Runs on the IO dispatcher because a large locked category means a few
     * hundred AES operations, and the Keystore is not fast enough to do that
     * on a frame budget.
     *
     * Returns false without writing anything if the vault can't open —
     * partially converting a category would leave rows nothing can read.
     */
    suspend fun setCategoryEncryption(categoryId: Long, encrypt: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val tasks = taskDao.getByEncryptionState(categoryId, !encrypt)
            if (tasks.isEmpty()) return@withContext true

            val converted = ArrayList<Pair<Long, String>>(tasks.size)
            for (task in tasks) {
                val plain = if (task.encrypted) {
                    Vault.open(task.title) ?: return@withContext false
                } else {
                    task.title
                }
                converted += task.id to if (encrypt) Vault.seal(plain) else plain
            }
            converted.forEach { (id, title) ->
                taskDao.setTitleAndEncryption(id, title, encrypt)
            }
            true
        }

    /**
     * The display title for a task.
     *
     * Every render path goes through here, so an encrypted row can never be
     * drawn raw. When the vault is shut, the caller gets a dot placeholder
     * rather than base64.
     */
    fun displayTitle(task: TaskEntity): String =
        if (!task.encrypted) task.title
        else Vault.open(task.title) ?: Vault.LOCKED_PLACEHOLDER

    // -- Search ------------------------------------------------------------

    /**
     * Search.
     *
     * Two passes: SQL `LIKE` over plaintext rows, then an in-memory pass over
     * encrypted rows *only* while the vault is open. Locked content is
     * therefore searchable once you have authenticated and invisible before
     * that — a search that silently skipped unlocked-but-encrypted notes
     * would look broken, and one that matched them while locked would leak
     * their contents through the result count.
     */
    suspend fun search(query: String, scope: SearchScope, categoryId: Long?): List<TaskEntity> =
        withContext(Dispatchers.Default) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext emptyList()
            val scopeId = if (scope == SearchScope.CATEGORY) categoryId else null

            val plain = taskDao.search(escapeLike(trimmed), scopeId)
            if (!Vault.unlocked.value) return@withContext plain

            val encrypted = taskDao.getAllTasksOnce()
                .filter { it.encrypted && (scopeId == null || it.categoryId == scopeId) }
                .filter { task ->
                    Vault.open(task.title)?.contains(trimmed, ignoreCase = true) == true
                }
            (plain + encrypted).take(SEARCH_LIMIT)
        }

    /**
     * `LIKE` treats `%` and `_` as wildcards, so a user searching for "50%"
     * would otherwise match everything. Room's query has no ESCAPE clause, so
     * the safe move is to strip them from the needle.
     */
    private fun escapeLike(query: String): String = query.replace("%", "").replace("_", "")

    private companion object {
        const val SEARCH_LIMIT = 200
    }
}
