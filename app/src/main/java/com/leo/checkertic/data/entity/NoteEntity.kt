package com.leo.checkertic.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    indices = [Index("pinned"), Index("updated_at")]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Plaintext, or a `VaultCodec` envelope when [encrypted] is true. */
    val title: String,

    /** Plaintext, or a `VaultCodec` envelope when [encrypted] is true. */
    val content: String = "",

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    // -- v2 ---------------------------------------------------------------

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    val pinned: Boolean = false,

    val locked: Boolean = false,

    val encrypted: Boolean = false,

    /** `asset:<id>` for a curated background, `file:<name>` for a user pick. */
    val background: String? = null,

    @ColumnInfo(name = "order_index")
    val orderIndex: Int = 0
)
