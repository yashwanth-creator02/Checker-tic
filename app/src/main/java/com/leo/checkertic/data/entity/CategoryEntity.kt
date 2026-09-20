package com.leo.checkertic.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A category owns a task list, a recurrence rule, and (new in schema v2)
 * presentation + security state.
 *
 * Note that [name] is deliberately NOT encrypted when [locked] is true: the
 * category tab has to render for the user to know what to unlock. Locking a
 * category encrypts what is *inside* it — its task titles — which is the same
 * model Google Keep and Notion use for locked containers.
 */
@Entity(
    tableName = "categories",
    indices = [Index("pinned"), Index("order_index")]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    @ColumnInfo(name = "order_index")
    val orderIndex: Int,

    @ColumnInfo(name = "recurrence_type")
    val recurrenceType: String = "once",

    @ColumnInfo(name = "recurrence_custom_days")
    val recurrenceCustomDays: Int = 0,

    @ColumnInfo(name = "last_period_key")
    val lastPeriodKey: String = "",

    // -- v2 ---------------------------------------------------------------

    /** Pinned categories float to the front of the tab strip. */
    val pinned: Boolean = false,

    /** When true, task titles in this category are stored encrypted. */
    val locked: Boolean = false,

    /** `asset:<id>` for a curated background, `file:<name>` for a user pick. */
    val background: String? = null,

    /** Task sort within this category. See `data.model.SortMode`. */
    @ColumnInfo(name = "sort_mode")
    val sortMode: String = "manual",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
