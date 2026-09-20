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
import com.leo.checkertic.ui.theme.CopperContainer
import com.leo.checkertic.ui.theme.RadiantCopperText
import com.leo.checkertic.ui.theme.SatinCopper
import com.leo.checkertic.ui.theme.TextCompletedDark
import com.leo.checkertic.ui.theme.TextPrimaryDark
import com.leo.checkertic.ui.theme.TextSecondaryDark
import com.leo.checkertic.ui.trampoline.NotePopupActivity
import com.leo.checkertic.ui.trampoline.QuickAddActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Stand-in shown on the home screen for content the widget can't decrypt. */
private const val LOCKED_MASK = "\u2022\u2022\u2022\u2022\u2022\u2022"

class CheckerTicWidget : GlanceAppWidget() {

    companion object {
        val CURRENT_TAB_KEY = stringPreferencesKey("current_tab")       // "tasks" | "notes"
        val SELECTED_CATEGORY_KEY = longPreferencesKey("selected_category_id")
        val COMPLETING_TASK_ID_KEY = longPreferencesKey("completing_task_id")
        val COMPLETING_PHASE_KEY = intPreferencesKey("completing_phase")
        val UPDATE_TICK_KEY = longPreferencesKey("update_tick")
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
            val updateTick = prefs[UPDATE_TICK_KEY] ?: 0L

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

            // Fetch incomplete tasks for the active category so completed tasks disappear.
            //
            // A locked category's task titles are stored as ciphertext and the
            // vault key requires authentication the widget has no way to
            // prompt for, so they are masked here rather than rendered raw.
            // Counting them still works, which is what keeps the widget
            // honest about there being something there.
            val activeCategory = categories.firstOrNull { it.id == activeCategoryId }
            val rawTasks = if (currentTab == "tasks" && activeCategoryId > 0L) {
                runBlocking { db.taskDao().getIncompleteTasksForCategory(activeCategoryId).first() }
            } else emptyList()
            val tasks = if (activeCategory?.locked == true) {
                rawTasks.map { it.copy(title = LOCKED_MASK) }
            } else {
                rawTasks
            }

            // Fetch notes. Locked notes are masked for the same reason.
            val notes = if (currentTab == "notes") {
                runBlocking { db.noteDao().getAll().first() }
                    .map { note ->
                        if (note.encrypted) {
                            note.copy(title = LOCKED_MASK, content = "")
                        } else {
                            note
                        }
                    }
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
    ) {
        // Margin spacer from left edge
        Spacer(modifier = GlanceModifier.width(10.dp))

        // Left Sidebar Dock with vertical margin
        Box(
            modifier = GlanceModifier
                .fillMaxHeight()
                .padding(vertical = 10.dp)
        ) {
            Column(
                modifier = GlanceModifier
                    .fillMaxHeight()
                    .width(44.dp)
                    .background(ImageProvider(R.drawable.bg_widget_sidebar))
                    .padding(vertical = 8.dp),
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
                            ColorProvider(if (isTasksActive) RadiantCopperText else TextSecondaryDark)
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
                            ColorProvider(if (isNotesActive) RadiantCopperText else TextSecondaryDark)
                        ),
                        modifier = GlanceModifier.size(20.dp)
                    )
                }

                // Spacer to push jump-to-app icon to the bottom of the sidebar
                Spacer(modifier = GlanceModifier.defaultWeight())

                // Jump to app button in the bottom of the sidebar
                val mainActivityParams = if (currentTab == "notes") {
                    actionParametersOf(ActionParameters.Key<String>("initial_tab") to "notes")
                } else {
                    actionParametersOf(ActionParameters.Key<String>("initial_tab") to "tasks")
                }
                Box(
                    modifier = GlanceModifier
                        .size(36.dp)
                        .clickable(actionStartActivity<MainActivity>(mainActivityParams)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_open_app),
                        contentDescription = "Open App",
                        colorFilter = ColorFilter.tint(ColorProvider(TextSecondaryDark)),
                        modifier = GlanceModifier.size(18.dp)
                    )
                }
            }
        }

        // Margin spacer between sidebar and main content
        Spacer(modifier = GlanceModifier.width(10.dp))

        // Main Content Area with Bottom-Right FAB
        Box(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .padding(vertical = 10.dp)
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
                        colorFilter = ColorFilter.tint(ColorProvider(RadiantCopperText)),
                        modifier = GlanceModifier.size(20.dp)
                    )
                }
            }
        }

        // Margin spacer on right edge
        Spacer(modifier = GlanceModifier.width(10.dp))
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
                    .padding(bottom = 8.dp),
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
                                    if (isSelected) RadiantCopperText else TextSecondaryDark
                                ),
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                fontSize = 13.sp
                            ),
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = GlanceModifier.width(6.dp))
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
                        color = ColorProvider(TextSecondaryDark),
                        fontSize = 13.sp
                    )
                )
            }
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize().padding(top = 2.dp, end = 2.dp, bottom = 40.dp)) {
                items(tasks, itemId = { it.id }) { task ->
                    val isCompleting = task.id == completingTaskId
                    val phase = if (isCompleting) completingPhase else 0

                    val (leftTickerRes, rightTickerRes) = when (phase) {
                        1 -> R.drawable.bg_ticker_left_active to R.drawable.bg_ticker_right_active
                        2 -> R.drawable.bg_ticker_left_faded to R.drawable.bg_ticker_right_faded
                        else -> if (task.completed) {
                            R.drawable.bg_ticker_left_active to R.drawable.bg_ticker_right_active
                        } else {
                            R.drawable.bg_ticker_left_normal to R.drawable.bg_ticker_right_normal
                        }
                    }
                    val textColor = when (phase) {
                        1 -> Color(0xFF86EFAC)
                        2 -> Color(0x3386EFAC)
                        else -> if (task.completed) TextCompletedDark else TextPrimaryDark
                    }
                    val rowBackground = when (phase) {
                        1 -> R.drawable.bg_task_row_blinking
                        2 -> R.drawable.bg_task_row_faded
                        else -> R.drawable.bg_task_row
                    }

                    val baseModifier = GlanceModifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(ImageProvider(rowBackground))

                    val rowModifier = if (!isCompleting) {
                        baseModifier.clickable(actionRunCallback<CompleteTaskAction>(
                            actionParametersOf(CheckerTicWidget.TASK_ID_PARAM to task.id)
                        ))
                    } else {
                        baseModifier
                    }

                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = rowModifier,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left vertical ticker bar (14dp with rounded corners)
                            Box(
                                modifier = GlanceModifier
                                    .width(14.dp)
                                    .fillMaxHeight()
                                    .background(ImageProvider(leftTickerRes))
                            ) {}

                            // Task title (no strikethrough line)
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
                                        fontWeight = if (phase == 1) FontWeight.Medium else FontWeight.Normal,
                                        textDecoration = TextDecoration.None
                                    )
                                )
                            }

                            // Right vertical ticker bar (14dp with rounded corners)
                            Box(
                                modifier = GlanceModifier
                                    .width(14.dp)
                                    .fillMaxHeight()
                                    .background(ImageProvider(rightTickerRes))
                            ) {}
                        }
                    }
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
                    color = ColorProvider(TextSecondaryDark),
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
                        .padding(bottom = 8.dp)
                ) {
                    NoteWidgetCard(
                        note = pair[0],
                        dotRes = dotPalette[firstIndex % dotPalette.size],
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
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
            .clickable(
                actionStartActivity<NotePopupActivity>(
                    actionParametersOf(
                        ActionParameters.Key<Long>(NotePopupActivity.EXTRA_NOTE_ID) to note.id
                    )
                )
            )
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
                    color = ColorProvider(TextPrimaryDark),
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
                    color = ColorProvider(TextSecondaryDark),
                    fontSize = 11.sp
                )
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

private suspend fun completeTaskWithBlink(context: Context, glanceId: GlanceId, taskId: Long) {
    val db = AppDatabase.getInstance(context)
    val taskRepo = TaskRepository(db.taskDao(), db.categoryDao())
    try {
        // Phase 1: Blink ON (bright green card background, active green tickers & green text)
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[CheckerTicWidget.COMPLETING_TASK_ID_KEY] = taskId
            prefs[CheckerTicWidget.COMPLETING_PHASE_KEY] = 1
        }
        CheckerTicWidget().update(context, glanceId)
        kotlinx.coroutines.delay(160)

        // Phase 2: Blink OFF (dimmed background, faded tickers)
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[CheckerTicWidget.COMPLETING_PHASE_KEY] = 2
        }
        CheckerTicWidget().update(context, glanceId)
        kotlinx.coroutines.delay(140)
    } finally {
        // Phase 3: Mark complete in DB and disappear from the widget
        taskRepo.completeTask(taskId)
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs.remove(CheckerTicWidget.COMPLETING_TASK_ID_KEY)
            prefs.remove(CheckerTicWidget.COMPLETING_PHASE_KEY)
        }
        WidgetUpdater.update(context)
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
            completeTaskWithBlink(context, glanceId, taskId)
        }
    }
}

class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[CheckerTicWidget.TASK_ID_PARAM] ?: return
        completeTaskWithBlink(context, glanceId, taskId)
    }
}
