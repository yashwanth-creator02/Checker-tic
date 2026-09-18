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

    @Query("SELECT * FROM categories ORDER BY order_index ASC")
    fun getAllOrdered(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("UPDATE categories SET last_period_key = :key WHERE id = :id")
    suspend fun updateLastPeriodKey(id: Long, key: String)

    @Query("UPDATE categories SET recurrence_type = :type, recurrence_custom_days = :customDays WHERE id = :id")
    suspend fun updateRecurrence(id: Long, type: String, customDays: Int = 0)

    @Transaction
    suspend fun updateOrderIndexes(updates: List<Pair<Long, Int>>) {
        for ((id, newIndex) in updates) {
            updateOrderIndex(id, newIndex)
        }
    }

    @Query("UPDATE categories SET order_index = :orderIndex WHERE id = :id")
    suspend fun updateOrderIndex(id: Long, orderIndex: Int)
}
