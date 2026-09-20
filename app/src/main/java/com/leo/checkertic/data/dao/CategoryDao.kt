package com.leo.checkertic.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.leo.checkertic.data.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    /** Pinned first, then manual order. Sorted in SQL so the UI never has to. */
    @Query("SELECT * FROM categories ORDER BY pinned DESC, order_index ASC")
    fun getAllOrdered(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY pinned DESC, order_index ASC")
    suspend fun getAllOnce(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    @Query("UPDATE categories SET last_period_key = :key WHERE id = :id")
    suspend fun updateLastPeriodKey(id: Long, key: String)

    @Query("UPDATE categories SET recurrence_type = :type, recurrence_custom_days = :customDays, updated_at = :now WHERE id = :id")
    suspend fun updateRecurrence(id: Long, type: String, customDays: Int = 0, now: Long = System.currentTimeMillis())

    @Query("UPDATE categories SET pinned = :pinned, updated_at = :now WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE categories SET locked = :locked, updated_at = :now WHERE id = :id")
    suspend fun setLocked(id: Long, locked: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE categories SET background = :background, updated_at = :now WHERE id = :id")
    suspend fun setBackground(id: Long, background: String?, now: Long = System.currentTimeMillis())

    @Query("UPDATE categories SET sort_mode = :sortMode, updated_at = :now WHERE id = :id")
    suspend fun setSortMode(id: Long, sortMode: String, now: Long = System.currentTimeMillis())

    @Transaction
    suspend fun updateOrderIndexes(updates: List<Pair<Long, Int>>) {
        for ((id, newIndex) in updates) {
            updateOrderIndex(id, newIndex)
        }
    }

    @Query("UPDATE categories SET order_index = :orderIndex WHERE id = :id")
    suspend fun updateOrderIndex(id: Long, orderIndex: Int)
}
