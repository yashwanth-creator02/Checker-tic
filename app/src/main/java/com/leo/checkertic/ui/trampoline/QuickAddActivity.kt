package com.leo.checkertic.ui.trampoline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.leo.checkertic.data.AppDatabase
import com.leo.checkertic.data.entity.CategoryEntity
import com.leo.checkertic.data.entity.NoteEntity
import com.leo.checkertic.data.repository.NoteRepository
import com.leo.checkertic.data.repository.TaskRepository
import com.leo.checkertic.ui.theme.CheckerTicTheme
import com.leo.checkertic.widget.CheckerTicWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * A transparent, dialog-themed Activity launched from the widget FAB.
 * Shows a single text field + save button, then dismisses back to the home screen.
 *
 * Expects extras:
 * - EXTRA_TYPE: "task" or "note"
 * - EXTRA_CATEGORY_ID: Long (required when type == "task")
 */
class QuickAddActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TYPE = "quick_add_type"
        const val EXTRA_CATEGORY_ID = "quick_add_category_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val type = intent.getStringExtra(EXTRA_TYPE) ?: "task"
        val rawCatId = intent.extras?.get(EXTRA_CATEGORY_ID)
        val categoryId = when (rawCatId) {
            is Long -> rawCatId
            is Int -> rawCatId.toLong()
            is String -> rawCatId.toLongOrNull() ?: -1L
            else -> intent.getLongExtra(EXTRA_CATEGORY_ID, -1L)
        }

        val db = AppDatabase.getInstance(applicationContext)

        setContent {
            CheckerTicTheme {
                var text by remember { mutableStateOf("") }
                var contentText by remember { mutableStateOf("") }
                val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                val performSave = {
                    val trimmedTitle = text.trim()
                    val trimmedContent = contentText.trim()
                    if (type == "task") {
                        if (trimmedTitle.isNotBlank()) {
                            saveAndDismiss(type, trimmedTitle, "", categoryId, db)
                        }
                    } else {
                        if (trimmedTitle.isNotBlank() || trimmedContent.isNotBlank()) {
                            val finalTitle = trimmedTitle.ifEmpty { "Untitled" }
                            saveAndDismiss(type, finalTitle, trimmedContent, categoryId, db)
                        }
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = if (type == "task") "New Task" else "New Note",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextField(
                            value = text,
                            onValueChange = { text = it },
                            placeholder = {
                                Text(if (type == "task") "Task title" else "Note title")
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                imeAction = if (type == "task") ImeAction.Done else ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { performSave() }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                        if (type == "note") {
                            Spacer(modifier = Modifier.height(10.dp))
                            TextField(
                                value = contentText,
                                onValueChange = { contentText = it },
                                placeholder = { Text("Note content (optional)") },
                                minLines = 3,
                                maxLines = 5,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            TextButton(onClick = { finish() }) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = performSave) {
                                Text("Save")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun saveAndDismiss(type: String, text: String, content: String, categoryId: Long, db: AppDatabase) {
        runBlocking(Dispatchers.IO) {
            if (type == "task") {
                val resolvedCatId = if (categoryId > 0 && db.categoryDao().getById(categoryId) != null) {
                    categoryId
                } else {
                    val existing = db.categoryDao().getAllOnce()
                    existing.firstOrNull()?.id ?: run {
                        val now = System.currentTimeMillis()
                        db.categoryDao().insert(
                            CategoryEntity(
                                name = "General",
                                orderIndex = 0,
                                createdAt = now,
                                updatedAt = now
                            )
                        )
                    }
                }
                val taskRepo = TaskRepository(db.taskDao(), db.categoryDao())
                taskRepo.addTask(text, resolvedCatId)
            } else {
                val noteRepo = NoteRepository(db.noteDao(), db.voiceNoteDao())
                noteRepo.insert(
                    NoteEntity(
                        title = text,
                        content = content,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            com.leo.checkertic.widget.WidgetUpdater.update(applicationContext)
        }
        finish()
    }
}
