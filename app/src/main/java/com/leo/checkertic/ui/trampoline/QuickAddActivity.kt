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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.leo.checkertic.data.AppDatabase
import com.leo.checkertic.data.entity.NoteEntity
import com.leo.checkertic.data.repository.NoteRepository
import com.leo.checkertic.data.repository.TaskRepository
import com.leo.checkertic.ui.theme.CheckerTicTheme
import com.leo.checkertic.widget.CheckerTicWidget
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

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
        val categoryId = intent.getLongExtra(EXTRA_CATEGORY_ID, -1L)

        val db = AppDatabase.getInstance(applicationContext)

        setContent {
            CheckerTicTheme {
                var text by remember { mutableStateOf("") }

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
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            TextButton(onClick = { finish() }) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = {
                                    if (text.isNotBlank()) {
                                        saveAndDismiss(type, text.trim(), categoryId, db)
                                    }
                                }
                            ) {
                                Text("Save")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun saveAndDismiss(type: String, text: String, categoryId: Long, db: AppDatabase) {
        val scope = MainScope()
        scope.launch {
            if (type == "task" && categoryId > 0) {
                val taskRepo = TaskRepository(db.taskDao(), db.categoryDao())
                taskRepo.addTask(text, categoryId)
            } else {
                val noteRepo = NoteRepository(db.noteDao())
                noteRepo.insert(NoteEntity(title = text, updatedAt = System.currentTimeMillis()))
            }
            // Dismiss immediately
            finish()
            // Refresh widget
            com.leo.checkertic.widget.WidgetUpdater.update(applicationContext)
        }
    }
}
