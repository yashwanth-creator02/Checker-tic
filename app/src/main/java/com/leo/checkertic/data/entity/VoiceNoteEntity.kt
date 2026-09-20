package com.leo.checkertic.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single recording attached to a note. A note may hold several.
 *
 * Several rather than one: the storage model is identical either way (a row
 * plus a file), but "several" avoids the awkward question of what happens
 * when you record over an existing memo, and matches how people actually use
 * voice notes — a few short thoughts rather than one long take.
 *
 * [fileName] is an opaque generated name inside `filesDir/voice/`. It never
 * contains the note title, so a locked note leaks nothing through its
 * filenames.
 */
@Entity(
    tableName = "voice_notes",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["note_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("note_id")]
)
data class VoiceNoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "note_id")
    val noteId: Long,

    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    /** True when the audio bytes on disk are vault-encrypted. */
    val encrypted: Boolean = false
)
