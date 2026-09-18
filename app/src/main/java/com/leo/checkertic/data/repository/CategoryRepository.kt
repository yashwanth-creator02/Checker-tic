package com.leo.checkertic.data.repository

import com.leo.checkertic.data.dao.CategoryDao
import com.leo.checkertic.data.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

class CategoryRepository(private val categoryDao: CategoryDao) {

    fun getAllOrdered(): Flow<List<CategoryEntity>> = categoryDao.getAllOrdered()

    suspend fun getById(id: Long): CategoryEntity? = categoryDao.getById(id)

    suspend fun insert(category: CategoryEntity): Long = categoryDao.insert(category)

    suspend fun update(category: CategoryEntity) = categoryDao.update(category)

    suspend fun delete(category: CategoryEntity) = categoryDao.delete(category)

    suspend fun updateRecurrence(id: Long, type: String, customDays: Int = 0) =
        categoryDao.updateRecurrence(id, type, customDays)

    suspend fun updateOrderIndexes(updates: List<Pair<Long, Int>>) =
        categoryDao.updateOrderIndexes(updates)
}
