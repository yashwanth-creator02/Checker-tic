package com.leo.checkertic.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Creation / edit events, powering the notes activity trend.
 *
 * Writes are coalesced by `NoteRepository` to at most one `edited` row per
 * note per hour. Without that, autosave would append a row per keystroke and
 * this table would dwarf the notes themselves inside a week.
 */
@Entity(
    tableName = "note_activity",
    indices = [Index("at"), Index(value = ["note_id", "at"])]
)
data class NoteActivityEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "note_id")
    val noteId: Long,

    /** `created` or `edited`. */
    val kind: String,

    val at: Long
)
