package com.leo.checkertic.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.leo.checkertic.data.entity.VoiceNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceNoteDao {

    @Query("SELECT * FROM voice_notes WHERE note_id = :noteId ORDER BY created_at ASC")
    fun observeForNote(noteId: Long): Flow<List<VoiceNoteEntity>>

    @Query("SELECT * FROM voice_notes WHERE note_id = :noteId ORDER BY created_at ASC")
    suspend fun forNote(noteId: Long): List<VoiceNoteEntity>

    @Query("SELECT * FROM voice_notes")
    suspend fun getAllOnce(): List<VoiceNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(voiceNote: VoiceNoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(voiceNotes: List<VoiceNoteEntity>)

    @Delete
    suspend fun delete(voiceNote: VoiceNoteEntity)

    @Query("UPDATE voice_notes SET file_name = :fileName, encrypted = :encrypted WHERE id = :id")
    suspend fun setFile(id: Long, fileName: String, encrypted: Boolean)

    @Query("DELETE FROM voice_notes")
    suspend fun deleteAll()
}
