package com.leo.checkertic.data.repository

import com.leo.checkertic.data.dao.NoteDao
import com.leo.checkertic.data.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

class NoteRepository(private val noteDao: NoteDao) {

    fun getAll(): Flow<List<NoteEntity>> = noteDao.getAll()

    suspend fun getById(id: Long): NoteEntity? = noteDao.getById(id)

    suspend fun insert(note: NoteEntity): Long = noteDao.insert(note)

    suspend fun update(note: NoteEntity) = noteDao.update(note)

    suspend fun delete(note: NoteEntity) = noteDao.delete(note)
}
