package com.leo.checkertic.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.leo.checkertic.data.entity.TaskCompletionEntity
import com.leo.checkertic.data.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    /**
     * Every task in a category, active and completed, in one emission.
     *
     * The screen needs both lists simultaneously (active list + collapsed
     * completed section), and two separate Flows would mean two separate
     * recompositions per tick. One query, split in the ViewModel, means one.
     */
    @Query("SELECT * FROM tasks WHERE category_id = :categoryId")
    fun getTasksForCategory(categoryId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE category_id = :categoryId AND completed = 0 ORDER BY pinned DESC, order_index ASC, id DESC")
    fun getIncompleteTasksForCategory(categoryId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasksOnce(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT COUNT(*) FROM tasks WHERE category_id = :categoryId AND completed = 0")
    suspend fun countIncomplete(categoryId: Long): Int

    /**
     * Plaintext search. Encrypted rows can't match a SQL LIKE — they are
     * ciphertext — so they are excluded here and handled separately in
     * `TaskRepository`, which decrypts in memory only while the vault is open.
     */
    @Query(
        """
        SELECT * FROM tasks
        WHERE encrypted = 0
          AND title LIKE '%' || :query || '%'
          AND (:categoryId IS NULL OR category_id = :categoryId)
        ORDER BY completed ASC, pinned DESC, updated_at DESC
        LIMIT 200
        """
    )
    suspend fun search(query: String, categoryId: Long?): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE category_id = :categoryId")
    suspend fun getTasksForCategoryOnce(categoryId: Long): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Update
    suspend fun update(task: TaskEntity)

    @Update
    suspend fun updateAll(tasks: List<TaskEntity>)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    @Query("UPDATE tasks SET pinned = :pinned, updated_at = :now WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE tasks SET completed = :completed, completed_at = :at, updated_at = :at WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean, at: Long)

    @Query("UPDATE tasks SET order_index = :orderIndex WHERE id = :id")
    suspend fun updateOrderIndex(id: Long, orderIndex: Int)

    @Insert
    suspend fun insertCompletion(completion: TaskCompletionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletions(completions: List<TaskCompletionEntity>)

    /**
     * Flip a task to done and log the event atomically. One transaction, so
     * the completion log can never disagree with the task row.
     */
    @Transaction
    suspend fun completeTask(id: Long, categoryId: Long, completedAt: Long) {
        setCompleted(id, true, completedAt)
        insertCompletion(
            TaskCompletionEntity(
                taskId = id,
                completedAt = completedAt,
                categoryId = categoryId
            )
        )
    }

    /**
     * Undo. The logged completion is removed too, otherwise an accidental
     * tap would permanently inflate the streak and heatmap.
     */
    @Transaction
    suspend fun uncompleteTask(id: Long, now: Long) {
        setCompleted(id, false, 0L)
        clearCompletedAt(id)
        deleteLatestCompletion(id)
    }

    @Query("UPDATE tasks SET completed_at = NULL WHERE id = :id")
    suspend fun clearCompletedAt(id: Long)

    @Query("DELETE FROM task_completions WHERE id = (SELECT id FROM task_completions WHERE task_id = :taskId ORDER BY completed_at DESC LIMIT 1)")
    suspend fun deleteLatestCompletion(taskId: Long)

    @Query("UPDATE tasks SET completed = 0, completed_at = NULL WHERE category_id = :categoryId")
    suspend fun resetCompletionsForCategory(categoryId: Long)

    // -- Encryption transitions -------------------------------------------

    @Query("SELECT * FROM tasks WHERE category_id = :categoryId AND encrypted = :encrypted")
    suspend fun getByEncryptionState(categoryId: Long, encrypted: Boolean): List<TaskEntity>

    @Query("UPDATE tasks SET title = :title, encrypted = :encrypted WHERE id = :id")
    suspend fun setTitleAndEncryption(id: Long, title: String, encrypted: Boolean)
}
