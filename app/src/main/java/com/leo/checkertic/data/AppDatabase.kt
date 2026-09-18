package com.leo.checkertic.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.leo.checkertic.data.dao.CategoryDao
import com.leo.checkertic.data.dao.NoteDao
import com.leo.checkertic.data.dao.TaskDao
import com.leo.checkertic.data.entity.CategoryEntity
import com.leo.checkertic.data.entity.NoteEntity
import com.leo.checkertic.data.entity.TaskCompletionEntity
import com.leo.checkertic.data.entity.TaskEntity

@Database(
    entities = [
        CategoryEntity::class,
        TaskEntity::class,
        TaskCompletionEntity::class,
        NoteEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun taskDao(): TaskDao
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "checker_tic.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
