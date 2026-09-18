package com.leo.checkertic.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.leo.checkertic.MainActivity
import com.leo.checkertic.R
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
        val COMPLETING_TASK_ID_KEY = longPreferencesKey("completing_task_id")
        val COMPLETING_PHASE_KEY = intPreferencesKey("completing_phase")
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
            val completingTaskId = prefs[COMPLETING_TASK_ID_KEY] ?: -1L
            val completingPhase = prefs[COMPLETING_PHASE_KEY] ?: 0

            // Load categories
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

            // Fetch incomplete tasks for the active category so completed tasks disappear
            val tasks = if (currentTab == "tasks" && activeCategoryId > 0L) {
                runBlocking { db.taskDao().getIncompleteTasksForCategory(activeCategoryId).first() }
            } else emptyList()

            // Fetch notes
            val notes = if (currentTab == "notes") {
                runBlocking { db.noteDao().getAll().first() }
            } else emptyList()

            GlanceTheme {
                WidgetContent(
                    currentTab = currentTab,
                    categories = categories,
                    selectedCategoryId = activeCategoryId,
                    tasks = tasks,
                    notes = notes,
                    completingTaskId = completingTaskId,
                    completingPhase = completingPhase
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
    notes: List<NoteEntity>,
    completingTaskId: Long,
    completingPhase: Int
) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.bg_widget_root))
            .padding(8.dp)
    ) {
        // Left Sidebar
        Column(
            modifier = GlanceModifier
                .fillMaxHeight()
                .width(42.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Tasks tab button
            val isTasksActive = currentTab == "tasks"
            Box(
                modifier = (if (isTasksActive) {
                    GlanceModifier.background(ImageProvider(R.drawable.bg_tab_selected))
                } else {
                    GlanceModifier
                })
                    .size(36.dp)
                    .clickable(actionRunCallback<SwitchTabAction>(
                        actionParametersOf(CheckerTicWidget.TAB_PARAM to "tasks")
                    )),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_tasks),
                    contentDescription = "Tasks",
                    colorFilter = ColorFilter.tint(
                        ColorProvider(if (isTasksActive) Color(0xFF38BDF8) else Color(0xFF71717A))
                    ),
                    modifier = GlanceModifier.size(20.dp)
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Notes tab button
            val isNotesActive = currentTab == "notes"
            Box(
                modifier = (if (isNotesActive) {
                    GlanceModifier.background(ImageProvider(R.drawable.bg_tab_selected))
                } else {
                    GlanceModifier
                })
                    .size(36.dp)
                    .clickable(actionRunCallback<SwitchTabAction>(
                        actionParametersOf(CheckerTicWidget.TAB_PARAM to "notes")
                    )),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_notes),
                    contentDescription = "Notes",
                    colorFilter = ColorFilter.tint(
                        ColorProvider(if (isNotesActive) Color(0xFF38BDF8) else Color(0xFF71717A))
                    ),
                    modifier = GlanceModifier.size(20.dp)
                )
            }

            // Spacer to push jump-to-app icon to the bottom-left corner
            Spacer(modifier = GlanceModifier.defaultWeight())

            // Jump to app button in the bottom-left corner
            Box(
                modifier = GlanceModifier
                    .size(36.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_open_app),
                    contentDescription = "Open App",
                    colorFilter = ColorFilter.tint(ColorProvider(Color(0xFF71717A))),
                    modifier = GlanceModifier.size(18.dp)
                )
            }
        }

        // Main Content Area with Bottom-Right FAB
        Box(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .padding(start = 6.dp)
        ) {
            if (currentTab == "tasks") {
                TasksWidgetContent(
                    categories = categories,
                    selectedCategoryId = selectedCategoryId,
                    tasks = tasks,
                    completingTaskId = completingTaskId,
                    completingPhase = completingPhase
                )
            } else {
                NotesWidgetContent(notes = notes)
            }

            // Floating Action Button (FAB) at bottom-end
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.BottomEnd
            ) {
                val addType = if (currentTab == "tasks") "task" else "note"
                Box(
                    modifier = GlanceModifier
                        .size(38.dp)
                        .background(ImageProvider(R.drawable.bg_fab_circle))
                        .clickable(
                            actionStartActivity<QuickAddActivity>(
                                actionParametersOf(
                                    ActionParameters.Key<String>("quick_add_type") to addType,
                                    ActionParameters.Key<Long>("quick_add_category_id") to selectedCategoryId
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_add),
                        contentDescription = "Add",
                        colorFilter = ColorFilter.tint(ColorProvider(Color(0xFF60A5FA))),
                        modifier = GlanceModifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TasksWidgetContent(
    categories: List<CategoryEntity>,
    selectedCategoryId: Long,
    tasks: List<TaskEntity>,
    completingTaskId: Long,
    completingPhase: Int
) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        // Category tabs row
        if (categories.isNotEmpty()) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                categories.forEach { category ->
                    val isSelected = category.id == selectedCategoryId
                    Box(
                        modifier = (if (isSelected) {
                            GlanceModifier.background(ImageProvider(R.drawable.bg_category_selected))
                        } else {
                            GlanceModifier
                        })
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .clickable(actionRunCallback<SwitchCategoryAction>(
                                actionParametersOf(CheckerTicWidget.CATEGORY_ID_PARAM to category.id)
                            )),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = category.name,
                            style = TextStyle(
                                color = ColorProvider(
                                    if (isSelected) Color(0xFFBFDBFE) else Color(0xFF9CA3AF)
                                ),
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                fontSize = 13.sp
                            ),
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = GlanceModifier.width(4.dp))
                }
            }
        }

        // Task items list
        if (tasks.isEmpty()) {
            Box(
                modifier = GlanceModifier.fillMaxSize().padding(bottom = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (categories.isEmpty()) "Add a category in the app" else "All tasks done",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF71717A)),
                        fontSize = 13.sp
                    )
                )
            }
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize().padding(bottom = 40.dp)) {
                items(tasks, itemId = { it.id }) { task ->
                    val isCompleting = task.id == completingTaskId
                    val phase = if (isCompleting) completingPhase else 0

                    val accentColor = when (phase) {
                        1 -> Color(0xFF22C55E)
                        2 -> Color(0x4422C55E)
                        else -> if (task.completed) Color(0xFF22C55E) else Color(0xFF3F3F46)
                    }
                    val textColor = when (phase) {
                        1 -> Color(0xFF86EFAC)
                        2 -> Color(0x4486EFAC)
                        else -> if (task.completed) Color(0xFF6B7280) else Color(0xFFF3F4F6)
                    }
                    val rowBackground = if (phase == 2) R.drawable.bg_task_row_faded else R.drawable.bg_task_row
                    val showStrikeLine = phase > 0
                    val strikeLineColor = if (phase == 2) Color(0x4422C55E) else Color(0xFF22C55E)

                    val baseModifier = GlanceModifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .background(ImageProvider(rowBackground))

                    val rowModifier = if (!isCompleting) {
                        baseModifier.clickable(actionRunCallback<CompleteTaskAction>(
                            actionParametersOf(CheckerTicWidget.TASK_ID_PARAM to task.id)
                        ))
                    } else {
                        baseModifier
                    }

                    Row(
                        modifier = rowModifier,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left vertical ticker bar (20dp)
                        Box(
                            modifier = GlanceModifier
                                .width(20.dp)
                                .fillMaxHeight()
                                .background(ColorProvider(accentColor))
                        ) {}

                        // Task title with line passing animation
                        Box(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .fillMaxHeight()
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = task.title,
                                maxLines = 1,
                                style = TextStyle(
                                    color = ColorProvider(textColor),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal,
                                    textDecoration = if (showStrikeLine || task.completed) TextDecoration.LineThrough else TextDecoration.None
                                )
                            )

                            if (showStrikeLine) {
                                Box(
                                    modifier = GlanceModifier
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .background(ColorProvider(strikeLineColor))
                                ) {}
                            }
                        }

                        // Right vertical ticker bar (20dp)
                        Box(
                            modifier = GlanceModifier
                                .width(20.dp)
                                .fillMaxHeight()
                                .background(ColorProvider(accentColor))
                        ) {}
                    }
                    Spacer(modifier = GlanceModifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun NotesWidgetContent(notes: List<NoteEntity>) {
    val dotPalette = listOf(
        R.drawable.dot_blue,
        R.drawable.dot_green,
        R.drawable.dot_amber,
        R.drawable.dot_coral
    )

    if (notes.isEmpty()) {
        Box(
            modifier = GlanceModifier.fillMaxSize().padding(bottom = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No notes yet",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF71717A)),
                    fontSize = 13.sp
                )
            )
        }
    } else {
        val chunked = notes.chunked(2)
        LazyColumn(modifier = GlanceModifier.fillMaxSize().padding(bottom = 40.dp)) {
            items(chunked, itemId = { pair -> pair.first().id }) { pair ->
                val firstIndex = notes.indexOf(pair[0])
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    NoteWidgetCard(
                        note = pair[0],
                        dotRes = dotPalette[firstIndex % dotPalette.size],
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    if (pair.size > 1) {
                        val secondIndex = notes.indexOf(pair[1])
                        NoteWidgetCard(
                            note = pair[1],
                            dotRes = dotPalette[secondIndex % dotPalette.size],
                            modifier = GlanceModifier.defaultWeight()
                        )
                    } else {
                        Box(modifier = GlanceModifier.defaultWeight()) {}
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteWidgetCard(
    note: NoteEntity,
    dotRes: Int,
    modifier: GlanceModifier
) {
    Column(
        modifier = modifier
            .height(64.dp)
            .background(ImageProvider(R.drawable.bg_note_card))
            .padding(8.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(dotRes),
                contentDescription = null,
                modifier = GlanceModifier.size(8.dp)
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = note.title,
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            )
        }
        if (note.content.isNotBlank()) {
            Spacer(modifier = GlanceModifier.height(3.dp))
            Text(
                text = note.content,
                maxLines = 2,
                style = TextStyle(
                    color = ColorProvider(Color(0xFF9CA3AF)),
                    fontSize = 11.sp
                )
            )
        }
    }
}

// -- Action callbacks --

private suspend fun completeTaskWithAnimation(context: Context, glanceId: GlanceId, taskId: Long) {
    // Phase 1: Line passes through the task + bright green ticker borders
    updateAppWidgetState(context, glanceId) { prefs ->
        prefs[CheckerTicWidget.COMPLETING_TASK_ID_KEY] = taskId
        prefs[CheckerTicWidget.COMPLETING_PHASE_KEY] = 1
    }
    CheckerTicWidget().update(context, glanceId)

    // Give visual time for the line to pass across the task
    kotlinx.coroutines.delay(200)

    // Phase 2: Fade out card
    updateAppWidgetState(context, glanceId) { prefs ->
        prefs[CheckerTicWidget.COMPLETING_PHASE_KEY] = 2
    }
    CheckerTicWidget().update(context, glanceId)

    // Give visual time for fade out
    kotlinx.coroutines.delay(180)

    // Phase 3: Mark complete in DB and remove from active list
    val db = AppDatabase.getInstance(context)
    val taskRepo = TaskRepository(db.taskDao(), db.categoryDao())
    taskRepo.completeTask(taskId)

    updateAppWidgetState(context, glanceId) { prefs ->
        prefs.remove(CheckerTicWidget.COMPLETING_TASK_ID_KEY)
        prefs.remove(CheckerTicWidget.COMPLETING_PHASE_KEY)
    }
    WidgetUpdater.update(context)
}

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

class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[CheckerTicWidget.TASK_ID_PARAM] ?: return
        val db = AppDatabase.getInstance(context)
        val taskRepo = TaskRepository(db.taskDao(), db.categoryDao())
        val task = db.taskDao().getById(taskId) ?: return
        if (task.completed) {
            taskRepo.uncompleteTask(taskId)
            WidgetUpdater.update(context)
        } else {
            completeTaskWithAnimation(context, glanceId, taskId)
        }
    }
}

class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[CheckerTicWidget.TASK_ID_PARAM] ?: return
        completeTaskWithAnimation(context, glanceId, taskId)
    }
}
