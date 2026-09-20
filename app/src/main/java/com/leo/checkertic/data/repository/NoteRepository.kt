package com.leo.checkertic.data.repository

import android.content.Context
import com.leo.checkertic.core.audio.VoiceStore
import com.leo.checkertic.core.crypto.Vault
import com.leo.checkertic.data.dao.NoteDao
import com.leo.checkertic.data.dao.VoiceNoteDao
import com.leo.checkertic.data.entity.NoteActivityEntity
import com.leo.checkertic.data.entity.NoteEntity
import com.leo.checkertic.data.entity.VoiceNoteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class NoteRepository(
    private val noteDao: NoteDao,
    private val voiceNoteDao: VoiceNoteDao
) {

    fun getAll(): Flow<List<NoteEntity>> = noteDao.getAll()

    suspend fun getById(id: Long): NoteEntity? = noteDao.getById(id)

    fun observeById(id: Long): Flow<NoteEntity?> = noteDao.observeById(id)

    suspend fun insert(note: NoteEntity): Long {
        val id = noteDao.insert(note)
        noteDao.insertActivity(
            NoteActivityEntity(noteId = id, kind = KIND_CREATED, at = note.createdAt)
        )
        return id
    }

    /**
     * Saves a note and records an edit event.
     *
     * The activity log is coalesced to at most one `edited` row per note per
     * [ACTIVITY_COALESCE_MS]. The editor autosaves as you type, so without
     * this the activity table would grow a row per keystroke — hundreds of
     * thousands of rows inside a month, all to draw a trend chart that only
     * has daily resolution.
     */
    suspend fun update(note: NoteEntity, recordActivity: Boolean = true) {
        val now = System.currentTimeMillis()
        noteDao.update(note.copy(updatedAt = now))
        if (!recordActivity) return
        val lastEdit = noteDao.lastEditActivityAt(note.id) ?: 0L
        if (now - lastEdit >= ACTIVITY_COALESCE_MS) {
            noteDao.insertActivity(
                NoteActivityEntity(noteId = note.id, kind = KIND_EDITED, at = now)
            )
        }
    }

    suspend fun delete(context: Context, note: NoteEntity) {
        // Room's CASCADE removes the voice_notes rows, but not the files they
        // point at — those have to go explicitly or they leak forever.
        voiceNoteDao.forNote(note.id).forEach { VoiceStore.delete(context, it.fileName) }
        noteDao.delete(note)
    }

    suspend fun setPinned(id: Long, pinned: Boolean) = noteDao.setPinned(id, pinned)

    suspend fun setBackground(id: Long, background: String?) =
        noteDao.setBackground(id, background)

    fun activitySince(from: Long): Flow<List<Long>> = noteDao.activitySince(from)

    // -- Locking -----------------------------------------------------------

    /**
     * Locks or unlocks a single note: title, content and every attached
     * recording move together.
     *
     * If any part fails to convert, nothing is written. A note with an
     * encrypted title and a plaintext body would be worse than either state.
     */
    suspend fun setNoteLocked(
        context: Context,
        note: NoteEntity,
        locked: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val plainTitle = displayTitle(note).takeIf { it != Vault.LOCKED_PLACEHOLDER }
            ?: return@withContext false
        val plainContent = displayContent(note).takeIf { it != Vault.LOCKED_PLACEHOLDER }
            ?: return@withContext false

        val recordings = voiceNoteDao.forNote(note.id)
        val retargeted = recordings.map { recording ->
            recording to (
                VoiceStore.retarget(context, recording.fileName, recording.encrypted, locked)
                    ?: return@withContext false
                )
        }

        noteDao.setLockState(
            id = note.id,
            title = if (locked) Vault.seal(plainTitle) else plainTitle,
            content = if (locked) Vault.seal(plainContent) else plainContent,
            locked = locked,
            encrypted = locked
        )
        retargeted.forEach { (recording, newName) ->
            voiceNoteDao.setFile(recording.id, newName, locked)
        }
        true
    }

    /** Display accessors. Every render path goes through these. */
    fun displayTitle(note: NoteEntity): String =
        if (!note.encrypted) note.title else Vault.open(note.title) ?: Vault.LOCKED_PLACEHOLDER

    fun displayContent(note: NoteEntity): String =
        if (!note.encrypted) note.content
        else Vault.open(note.content) ?: Vault.LOCKED_PLACEHOLDER

    fun isReadable(note: NoteEntity): Boolean = !note.encrypted || Vault.unlocked.value

    // -- Voice notes -------------------------------------------------------

    fun voiceNotesFor(noteId: Long): Flow<List<VoiceNoteEntity>> =
        voiceNoteDao.observeForNote(noteId)

    suspend fun addVoiceNote(noteId: Long, saved: VoiceStore.Saved): Long =
        voiceNoteDao.insert(
            VoiceNoteEntity(
                noteId = noteId,
                fileName = saved.fileName,
                durationMs = saved.durationMs,
                encrypted = saved.encrypted
            )
        )

    suspend fun deleteVoiceNote(context: Context, voiceNote: VoiceNoteEntity) {
        VoiceStore.delete(context, voiceNote.fileName)
        voiceNoteDao.delete(voiceNote)
    }

    // -- Search ------------------------------------------------------------

    /** Title + content, plaintext via SQL and locked notes in memory once open. */
    suspend fun search(query: String): List<NoteEntity> = withContext(Dispatchers.Default) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val plain = noteDao.search(trimmed.replace("%", "").replace("_", ""))
        if (!Vault.unlocked.value) return@withContext plain

        val encrypted = noteDao.getAllOnce()
            .filter { it.encrypted }
            .filter { note ->
                val title = Vault.open(note.title).orEmpty()
                val content = Vault.open(note.content).orEmpty()
                title.contains(trimmed, true) || content.contains(trimmed, true)
            }
        (plain + encrypted).take(SEARCH_LIMIT)
    }

    private companion object {
        const val KIND_CREATED = "created"
        const val KIND_EDITED = "edited"
        const val ACTIVITY_COALESCE_MS = 60 * 60 * 1000L
        const val SEARCH_LIMIT = 200
    }
}
