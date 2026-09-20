package com.leo.checkertic.data.repository

import com.leo.checkertic.data.dao.CategoryDao
import com.leo.checkertic.data.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

class CategoryRepository(private val categoryDao: CategoryDao) {

    fun getAllOrdered(): Flow<List<CategoryEntity>> = categoryDao.getAllOrdered()

    suspend fun getAllOnce(): List<CategoryEntity> = categoryDao.getAllOnce()

    suspend fun getById(id: Long): CategoryEntity? = categoryDao.getById(id)

    suspend fun insert(category: CategoryEntity): Long = categoryDao.insert(category)

    suspend fun update(category: CategoryEntity) =
        categoryDao.update(category.copy(updatedAt = System.currentTimeMillis()))

    suspend fun delete(category: CategoryEntity) = categoryDao.delete(category)

    suspend fun updateRecurrence(id: Long, type: String, customDays: Int = 0) =
        categoryDao.updateRecurrence(id, type, customDays)

    suspend fun setPinned(id: Long, pinned: Boolean) = categoryDao.setPinned(id, pinned)

    suspend fun setLocked(id: Long, locked: Boolean) = categoryDao.setLocked(id, locked)

    suspend fun setBackground(id: Long, background: String?) =
        categoryDao.setBackground(id, background)

    suspend fun setSortMode(id: Long, sortMode: String) = categoryDao.setSortMode(id, sortMode)

    suspend fun updateOrderIndexes(updates: List<Pair<Long, Int>>) =
        categoryDao.updateOrderIndexes(updates)
}
