package com.leo.checkertic.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One table for both task-level and category-level reminders.
 *
 * A category reminder acts as the recurring default for everything in that
 * category: when it fires it summarises that category's outstanding tasks. A
 * task with its own reminder fires independently and takes precedence, so a
 * user does not get told twice about the same thing.
 */
@Entity(
    tableName = "reminders",
    indices = [
        Index(value = ["owner_type", "owner_id"], unique = true),
        Index("trigger_at")
    ]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** [OWNER_TASK] or [OWNER_CATEGORY]. */
    @ColumnInfo(name = "owner_type")
    val ownerType: String,

    @ColumnInfo(name = "owner_id")
    val ownerId: Long,

    /** Absolute epoch-millis of the next fire. */
    @ColumnInfo(name = "trigger_at")
    val triggerAt: Long,

    /** `none`, `daily`, `weekly`, `monthly` or `custom`. */
    @ColumnInfo(name = "repeat_mode")
    val repeatMode: String = REPEAT_NONE,

    @ColumnInfo(name = "repeat_interval_days")
    val repeatIntervalDays: Int = 0,

    val enabled: Boolean = true,

    @ColumnInfo(name = "last_fired_at")
    val lastFiredAt: Long? = null
) {
    companion object {
        const val OWNER_TASK = "task"
        const val OWNER_CATEGORY = "category"

        const val REPEAT_NONE = "none"
        const val REPEAT_DAILY = "daily"
        const val REPEAT_WEEKLY = "weekly"
        const val REPEAT_MONTHLY = "monthly"
        const val REPEAT_CUSTOM = "custom"
    }
}
