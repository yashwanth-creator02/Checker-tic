package com.leo.checkertic.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.leo.checkertic.data.AppDatabase
import com.leo.checkertic.data.entity.CategoryEntity
import com.leo.checkertic.data.entity.NoteEntity
import com.leo.checkertic.data.entity.TaskEntity
import com.leo.checkertic.data.repository.TaskRepository
import com.leo.checkertic.ui.trampoline.QuickAddActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class CheckerTicWidget : GlanceAppWidget() {

    companion object {
        val CURRENT_TAB_KEY = stringPreferencesKey("current_tab")       // "tasks" | "notes"
        val SELECTED_CATEGORY_KEY = longPreferencesKey("selected_category_id")
        val TASK_ID_PARAM = ActionParameters.Key<Long>("task_id")
        val CATEGORY_ID_PARAM = ActionParameters.Key<Long>("category_id")
        val TAB_PARAM = ActionParameters.Key<String>("tab")
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.getInstance(context)

        provideContent {
            val prefs = currentState<androidx.datastore.preferences.core.Preferences>()
            val currentTab = prefs[CURRENT_TAB_KEY] ?: "tasks"
            val selectedCategoryId = prefs[SELECTED_CATEGORY_KEY] ?: 0L

            // Load data synchronously for widget (Glance runs in a coroutine already)
            val categories = runBlocking { db.categoryDao().getAllOrdered().first() }
            val activeCategoryId = if (selectedCategoryId > 0L && categories.any { it.id == selectedCategoryId }) {
                selectedCategoryId
            } else {
                categories.firstOrNull()?.id ?: 0L
            }

            // Trigger lazy recurrence reset
            if (activeCategoryId > 0L) {
                val taskRepo = TaskRepository(db.taskDao(), db.categoryDao())
                runBlocking { taskRepo.ensureRecurrenceReset(activeCategoryId) }
            }

            val tasks = if (currentTab == "tasks" && activeCategoryId > 0L) {
                runBlocking { db.taskDao().getIncompleteTasksForCategory(activeCategoryId).first() }
            } else emptyList()

            val notes = if (currentTab == "notes") {
                runBlocking { db.noteDao().getAll().first() }
            } else emptyList()

            GlanceTheme {
                WidgetContent(
                    currentTab = currentTab,
                    categories = categories,
                    selectedCategoryId = activeCategoryId,
                    tasks = tasks,
                    notes = notes
                )
            }
        }
    }
}

@Composable
private fun WidgetContent(
    currentTab: String,
    categories: List<CategoryEntity>,
    selectedCategoryId: Long,
    tasks: List<TaskEntity>,
    notes: List<NoteEntity>
) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(androidx.compose.ui.graphics.Color.White))
            .padding(4.dp)
    ) {
        // Sidebar
        Column(
            modifier = GlanceModifier
                .fillMaxHeight()
                .width(44.dp)
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Tasks tab button
            Box(
                modifier = GlanceModifier
                    .padding(6.dp)
                    .clickable(actionRunCallback<SwitchTabAction>(
                        actionParametersOf(CheckerTicWidget.TAB_PARAM to "tasks")
                    )),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "T",
                    style = TextStyle(
                        fontWeight = if (currentTab == "tasks") FontWeight.Bold else FontWeight.Normal,
                        fontSize = 18.sp
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(12.dp))

            // Notes tab button
            Box(
                modifier = GlanceModifier
                    .padding(6.dp)
                    .clickable(actionRunCallback<SwitchTabAction>(
                        actionParametersOf(CheckerTicWidget.TAB_PARAM to "notes")
                    )),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "N",
                    style = TextStyle(
                        fontWeight = if (currentTab == "notes") FontWeight.Bold else FontWeight.Normal,
                        fontSize = 18.sp
                    )
                )
            }
        }

        // Main content area
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .padding(4.dp)
        ) {
            if (currentTab == "tasks") {
                TasksWidgetContent(
                    categories = categories,
                    selectedCategoryId = selectedCategoryId,
                    tasks = tasks
                )
            } else {
                NotesWidgetContent(notes = notes)
            }
        }
    }
}

@Composable
private fun TasksWidgetContent(
    categories: List<CategoryEntity>,
    selectedCategoryId: Long,
    tasks: List<TaskEntity>
) {
    // Category tabs
    if (categories.isNotEmpty()) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)
        ) {
            categories.forEach { category ->
                Box(
                    modifier = GlanceModifier
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .clickable(actionRunCallback<SwitchCategoryAction>(
                            actionParametersOf(CheckerTicWidget.CATEGORY_ID_PARAM to category.id)
                        ))
                ) {
                    Text(
                        text = category.name,
                        style = TextStyle(
                            fontWeight = if (category.id == selectedCategoryId) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }

    // Task list
    if (tasks.isEmpty()) {
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (categories.isEmpty()) "Add a category first" else "All done",
                style = TextStyle(fontSize = 13.sp)
            )
        }
    } else {
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            items(tasks, itemId = { it.id }) { task ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Native checkbox on API 31+
                    CheckBox(
                        checked = false,
                        onCheckedChange = actionRunCallback<CompleteTaskAction>(
                            actionParametersOf(CheckerTicWidget.TASK_ID_PARAM to task.id)
                        ),
                        text = task.title,
                        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)
                    )
                } else {
                    // Fallback: tappable row for pre-31
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable(actionRunCallback<CompleteTaskAction>(
                                actionParametersOf(CheckerTicWidget.TASK_ID_PARAM to task.id)
                            )),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "[ ]",
                            style = TextStyle(fontSize = 14.sp)
                        )
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Text(
                            text = task.title,
                            style = TextStyle(fontSize = 14.sp),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }

    // FAB area
    Box(
        modifier = GlanceModifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomEnd
    ) {
        Box(
            modifier = GlanceModifier
                .padding(8.dp)
                .clickable(
                    actionStartActivity<QuickAddActivity>(
                        actionParametersOf(
                            ActionParameters.Key<String>("quick_add_type") to "task",
                            ActionParameters.Key<Long>("quick_add_category_id") to selectedCategoryId
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)
            )
        }
    }
}

@Composable
private fun NotesWidgetContent(notes: List<NoteEntity>) {
    if (notes.isEmpty()) {
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No notes yet",
                style = TextStyle(fontSize = 13.sp)
            )
        }
    } else {
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            items(notes, itemId = { it.id }) { note ->
                Column(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = note.title,
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                        maxLines = 1
                    )
                    if (note.content.isNotBlank()) {
                        Text(
                            text = note.content,
                            style = TextStyle(fontSize = 12.sp),
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }

    // FAB area
    Box(
        modifier = GlanceModifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomEnd
    ) {
        Box(
            modifier = GlanceModifier
                .padding(8.dp)
                .clickable(
                    actionStartActivity<QuickAddActivity>(
                        actionParametersOf(
                            ActionParameters.Key<String>("quick_add_type") to "note"
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)
            )
        }
    }
}

// -- Action callbacks --

class SwitchTabAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val tab = parameters[CheckerTicWidget.TAB_PARAM] ?: return
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[CheckerTicWidget.CURRENT_TAB_KEY] = tab
        }
        CheckerTicWidget().update(context, glanceId)
    }
}

class SwitchCategoryAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val categoryId = parameters[CheckerTicWidget.CATEGORY_ID_PARAM] ?: return
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[CheckerTicWidget.SELECTED_CATEGORY_KEY] = categoryId
        }
        CheckerTicWidget().update(context, glanceId)
    }
}

class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[CheckerTicWidget.TASK_ID_PARAM] ?: return
        val db = AppDatabase.getInstance(context)
        val taskRepo = TaskRepository(db.taskDao(), db.categoryDao())
        taskRepo.completeTask(taskId)
        CheckerTicWidget().update(context, glanceId)
    }
}
