package com.leo.checkertic.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.leo.checkertic.core.audio.VoiceStore
import com.leo.checkertic.core.crypto.Vault
import com.leo.checkertic.core.image.ImageStore
import com.leo.checkertic.data.AppDatabase
import com.leo.checkertic.data.entity.NoteEntity
import com.leo.checkertic.data.entity.VoiceNoteEntity
import com.leo.checkertic.data.model.SortMode
import com.leo.checkertic.data.model.Sorting
import com.leo.checkertic.data.repository.NoteRepository
import com.leo.checkertic.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A note as the grid renders it: decrypted once, off the main thread. */
@Immutable
data class NoteUi(
    val id: Long,
    val title: String,
    val snippet: String,
    val pinned: Boolean,
    val locked: Boolean,
    val obscured: Boolean,
    val background: String?,
    val updatedAt: Long
)

class NotesViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val noteRepo = NoteRepository(db.noteDao(), db.voiceNoteDao())

    private val _sortMode = MutableStateFlow(SortMode.MODIFIED)
    val sortMode: StateFlow<SortMode> = _sortMode

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    /** Raw entities, kept for the editor and for share/export. */
    val notes: StateFlow<List<NoteEntity>> = noteRepo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * What the grid draws.
     *
     * Search filtering, sorting and decryption all happen here on
     * `Dispatchers.Default`. The snippet is truncated at the model boundary
     * rather than relying on `maxLines` — a 40 KB note would otherwise have
     * its entire body measured by the text layout pass just to render two
     * visible lines, which is a real source of jank in a staggered grid.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val gridNotes: StateFlow<List<NoteUi>> = combine(
        notes,
        _sortMode,
        _searchQuery.debounce(180L).distinctUntilChanged(),
        Vault.unlocked
    ) { all, sort, query, _ -> Quad(all, sort, query, Unit) }
        .flatMapLatest { (all, sort, query, _) ->
            flowOf(
                (if (query.isBlank()) all else noteRepo.search(query))
                    .sortedWith(Sorting.notes(sort))
                    .map { note ->
                        val title = noteRepo.displayTitle(note)
                        val content = noteRepo.displayContent(note)
                        NoteUi(
                            id = note.id,
                            title = title,
                            snippet = content.take(SNIPPET_CHARS),
                            pinned = note.pinned,
                            locked = note.locked,
                            obscured = note.encrypted && title == Vault.LOCKED_PLACEHOLDER,
                            background = note.background,
                            updatedAt = note.updatedAt
                        )
                    }
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** `combine` has no 4-arity destructurable result; a data class gives us one. */
    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    // ------------------------------------------------------------------

    fun addNote(title: String, content: String = "", onCreated: ((Long) -> Unit)? = null) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = noteRepo.insert(
                NoteEntity(title = title, content = content, createdAt = now, updatedAt = now)
            )
            WidgetUpdater.update(getApplication())
            onCreated?.invoke(id)
        }
    }

    /**
     * Saves an edit.
     *
     * @param recordActivity false for keystroke-level autosaves that have
     *   already been logged inside the coalescing window, so the activity
     *   trend stays cheap.
     */
    fun updateNote(note: NoteEntity, plainTitle: String, plainContent: String) {
        viewModelScope.launch {
            val stored = if (note.encrypted) {
                note.copy(title = Vault.seal(plainTitle), content = Vault.seal(plainContent))
            } else {
                note.copy(title = plainTitle, content = plainContent)
            }
            noteRepo.update(stored)
            WidgetUpdater.update(getApplication())
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            noteRepo.delete(getApplication(), note)
            WidgetUpdater.update(getApplication())
        }
    }

    fun togglePinned(noteId: Long) {
        viewModelScope.launch {
            val note = noteRepo.getById(noteId) ?: return@launch
            noteRepo.setPinned(noteId, !note.pinned)
            WidgetUpdater.update(getApplication())
        }
    }

    fun setBackground(noteId: Long, background: String?) {
        viewModelScope.launch { noteRepo.setBackground(noteId, background) }
    }

    fun importBackground(noteId: Long, uri: android.net.Uri) {
        viewModelScope.launch {
            ImageStore.importFromPicker(getApplication(), uri)?.let { ref ->
                noteRepo.setBackground(noteId, ref)
            }
        }
    }

    fun setNoteLocked(noteId: Long, locked: Boolean, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val note = noteRepo.getById(noteId) ?: return@launch onResult(false)
            onResult(noteRepo.setNoteLocked(getApplication(), note, locked))
        }
    }

    fun displayTitle(note: NoteEntity) = noteRepo.displayTitle(note)

    fun displayContent(note: NoteEntity) = noteRepo.displayContent(note)

    // -- Voice notes -------------------------------------------------------

    fun voiceNotesFor(noteId: Long): StateFlow<List<VoiceNoteEntity>> =
        noteRepo.voiceNotesFor(noteId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addVoiceNote(noteId: Long, saved: VoiceStore.Saved) {
        viewModelScope.launch { noteRepo.addVoiceNote(noteId, saved) }
    }

    fun deleteVoiceNote(voiceNote: VoiceNoteEntity) {
        viewModelScope.launch { noteRepo.deleteVoiceNote(getApplication(), voiceNote) }
    }

    private companion object {
        /**
         * Two lines of a card at the smallest text size is well under 200
         * characters; anything beyond that is measured and discarded.
         */
        const val SNIPPET_CHARS = 200
    }
}
