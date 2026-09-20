package com.leo.checkertic.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    // (category_id, completed) is the shape of every list query on this table,
    // so it gets a composite index rather than category_id alone.
    indices = [
        Index(value = ["category_id", "completed"]),
        Index("pinned")
    ]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * Plaintext title, or a `VaultCodec` envelope when [encrypted] is true.
     * Always read through `TaskRepository`, never rendered raw.
     */
    val title: String,

    @ColumnInfo(name = "category_id")
    val categoryId: Long,

    val completed: Boolean = false,

    // -- v2 ---------------------------------------------------------------

    val pinned: Boolean = false,

    /** True when [title] holds ciphertext because the category is locked. */
    val encrypted: Boolean = false,

    @ColumnInfo(name = "order_index")
    val orderIndex: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    /**
     * Denormalised copy of the most recent completion timestamp. The
     * `task_completions` table remains the source of truth for analytics;
     * this exists purely so the completed-tasks section can sort by
     * recency without a join on every list emission.
     */
    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null
)
