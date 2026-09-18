package com.leo.checkertic.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.leo.checkertic.data.AppDatabase
import com.leo.checkertic.data.entity.NoteEntity
import com.leo.checkertic.data.repository.NoteRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotesViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val noteRepo = NoteRepository(db.noteDao())

    val notes: StateFlow<List<NoteEntity>> = noteRepo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addNote(title: String, content: String = "", onCreated: ((Long) -> Unit)? = null) {
        viewModelScope.launch {
            val id = noteRepo.insert(
                NoteEntity(
                    title = title,
                    content = content,
                    updatedAt = System.currentTimeMillis()
                )
            )
            onCreated?.invoke(id)
        }
    }

    fun updateNote(note: NoteEntity) {
        viewModelScope.launch {
            noteRepo.update(note.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            noteRepo.delete(note)
        }
    }
}
