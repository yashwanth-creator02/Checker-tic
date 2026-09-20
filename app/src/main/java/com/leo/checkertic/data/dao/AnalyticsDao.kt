package com.leo.checkertic.data.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import com.leo.checkertic.data.entity.TaskCompletionEntity
import kotlinx.coroutines.flow.Flow

/**
 * A completion reduced to the only two fields any chart needs.
 *
 * Analytics reads tens of thousands of rows; pulling full `TaskCompletionEntity`
 * objects would allocate three times the memory for data nothing draws.
 */
data class CompletionPoint(
    @ColumnInfo(name = "category_id") val categoryId: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long
)

@Dao
interface AnalyticsDao {

    /**
     * All completions inside the analytics window, oldest first.
     *
     * Ordered in SQL because the index on `completed_at` makes it free here
     * and every downstream aggregation (heatmap, streaks, trend) wants
     * chronological order anyway.
     */
    @Query(
        """
        SELECT category_id, completed_at FROM task_completions
        WHERE completed_at >= :from
        ORDER BY completed_at ASC
        """
    )
    fun pointsSince(from: Long): Flow<List<CompletionPoint>>

    @Query(
        """
        SELECT category_id, completed_at FROM task_completions
        WHERE category_id = :categoryId AND completed_at >= :from
        ORDER BY completed_at ASC
        """
    )
    fun pointsForCategorySince(categoryId: Long, from: Long): Flow<List<CompletionPoint>>

    @Query("SELECT COUNT(*) FROM task_completions WHERE completed_at >= :from AND completed_at < :to")
    fun countBetween(from: Long, to: Long): Flow<Int>

    @Query("SELECT * FROM task_completions")
    suspend fun getAllOnce(): List<TaskCompletionEntity>

    @Query("SELECT MIN(completed_at) FROM task_completions")
    suspend fun earliestCompletion(): Long?

    @Query("DELETE FROM task_completions")
    suspend fun deleteAll()
}
