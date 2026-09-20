package com.leo.checkertic.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.leo.checkertic.data.entity.NoteActivityEntity
import com.leo.checkertic.data.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes ORDER BY pinned DESC, updated_at DESC")
    fun getAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY pinned DESC, updated_at DESC")
    suspend fun getAllOnce(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: Long): Flow<NoteEntity?>

    /**
     * Title + content search over unencrypted notes. Locked notes are
     * ciphertext and are matched separately in `NoteRepository` once the
     * vault is open.
     *
     * LIKE with a leading wildcard can't use an index, so this is a table
     * scan — deliberately. For a personal notes corpus (hundreds, not
     * millions) a scan off the main thread returns in well under a frame,
     * and it avoids an FTS4 shadow table plus the three sync triggers that
     * would have to be kept correct through every future migration. If the
     * corpus ever grows past a few thousand notes, FTS is the upgrade and
     * it slots in behind this same function.
     */
    @Query(
        """
        SELECT * FROM notes
        WHERE encrypted = 0
          AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%')
        ORDER BY pinned DESC, updated_at DESC
        LIMIT 200
        """
    )
    suspend fun search(query: String): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<NoteEntity>)

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("DELETE FROM notes")
    suspend fun deleteAll()

    @Query("UPDATE notes SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE notes SET background = :background WHERE id = :id")
    suspend fun setBackground(id: Long, background: String?)

    @Query("UPDATE notes SET title = :title, content = :content, locked = :locked, encrypted = :encrypted WHERE id = :id")
    suspend fun setLockState(id: Long, title: String, content: String, locked: Boolean, encrypted: Boolean)

    // -- Activity log ------------------------------------------------------

    @Insert
    suspend fun insertActivity(activity: NoteActivityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivities(activities: List<NoteActivityEntity>)

    @Query("SELECT at FROM note_activity WHERE at >= :from ORDER BY at ASC")
    fun activitySince(from: Long): Flow<List<Long>>

    @Query("SELECT * FROM note_activity")
    suspend fun allActivityOnce(): List<NoteActivityEntity>

    @Query("SELECT MAX(at) FROM note_activity WHERE note_id = :noteId AND kind = 'edited'")
    suspend fun lastEditActivityAt(noteId: Long): Long?

    @Query("DELETE FROM note_activity")
    suspend fun deleteAllActivity()
}
