package com.leo.checkertic.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Append-only completion log. This is the substrate the whole analytics
 * feature sits on, so it carries [categoryId] denormalised: every analytics
 * query filters or groups by category, and copying 8 bytes per completion is
 * cheaper than joining `tasks` on every read — and it survives the task row
 * being deleted, which a join would not.
 */
@Entity(
    tableName = "task_completions",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("task_id"),
        Index("completed_at"),
        Index(value = ["category_id", "completed_at"])
    ]
)
data class TaskCompletionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "task_id")
    val taskId: Long,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long,

    /** v2: denormalised owning category. -1 for rows migrated from v1. */
    @ColumnInfo(name = "category_id")
    val categoryId: Long = -1L
)
