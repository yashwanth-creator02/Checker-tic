package com.leo.checkertic.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.leo.checkertic.data.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders WHERE enabled = 1 ORDER BY trigger_at ASC")
    suspend fun allEnabled(): List<ReminderEntity>

    @Query("SELECT * FROM reminders")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders")
    suspend fun getAllOnce(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE owner_type = :ownerType AND owner_id = :ownerId LIMIT 1")
    suspend fun forOwner(ownerType: String, ownerId: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: ReminderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reminders: List<ReminderEntity>)

    @Delete
    suspend fun delete(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE owner_type = :ownerType AND owner_id = :ownerId")
    suspend fun deleteForOwner(ownerType: String, ownerId: Long)

    @Query("UPDATE reminders SET trigger_at = :triggerAt, last_fired_at = :firedAt WHERE id = :id")
    suspend fun advance(id: Long, triggerAt: Long, firedAt: Long)

    @Query("UPDATE reminders SET enabled = 0 WHERE id = :id")
    suspend fun disable(id: Long)

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()
}
