package com.leo.checkertic.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.leo.checkertic.analytics.AnalyticsEngine
import com.leo.checkertic.analytics.CategoryAnalytics
import com.leo.checkertic.core.crypto.Vault
import com.leo.checkertic.data.AppDatabase
import com.leo.checkertic.data.entity.CategoryEntity
import com.leo.checkertic.data.entity.ReminderEntity
import com.leo.checkertic.data.entity.TaskEntity
import com.leo.checkertic.data.model.SearchScope
import com.leo.checkertic.data.model.SearchResults
import com.leo.checkertic.data.model.SortMode
import com.leo.checkertic.data.model.Sorting
import com.leo.checkertic.data.model.TaskSearchHit
import com.leo.checkertic.data.repository.AnalyticsRepository
import com.leo.checkertic.data.repository.CategoryRepository
import com.leo.checkertic.data.repository.NoteRepository
import com.leo.checkertic.data.repository.TaskRepository
import com.leo.checkertic.reminders.ReminderRepository
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * ============================================================================
 *  TASKS VIEW MODEL
 * ============================================================================
 *
 * Owns the Tasks screen: the category strip, the active list, the collapsed
 * completed section, search, sorting, pinning, locking, backgrounds,
 * reminders, and the inline per-category analytics block.
 *
 * ## The performance shape of this file
 *
 * Every derived value is produced in a flow pipeline with `.flowOn(
 * Dispatchers.Default)` and collapsed into a single `StateFlow`. Two
 * consequences, both deliberate:
 *
 *  - Sorting, splitting and AES decryption never run on the main thread.
 *  - Ticking one task produces **one** state emission, hence one
 *    recomposition pass, rather than one per observed flow.
 *
 * `SharingStarted.WhileSubscribed(5_000)` rather than `Eagerly`: the previous
 * version kept three database observers alive for the whole process lifetime,
 * including while the user was on Notes or Settings. The five-second grace
 * keeps state across a configuration change without re-querying.
 */
class TasksViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val categoryRepo = CategoryRepository(db.categoryDao())
    private val taskRepo = TaskRepository(db.taskDao(), db.categoryDao())
    private val noteRepo = NoteRepository(db.noteDao(), db.voiceNoteDao())
    private val analyticsRepo = AnalyticsRepository(db.analyticsDao())
    private val reminderRepo = ReminderRepository(db.reminderDao(), application)

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    val selectedCategoryId: StateFlow<Long?> = _selectedCategoryId

    private val categoryEntities: StateFlow<List<CategoryEntity>> =
        categoryRepo.getAllOrdered()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_GRACE_MS), emptyList())

    /** Kept for the existing Settings/Categories screens, which take entities. */
    val categories: StateFlow<List<CategoryEntity>> = categoryEntities

    private val reminderOwners: StateFlow<Set<Pair<String, Long>>> =
        reminderRepo.observeAll()
            .map { list -> list.filter { it.enabled }.map { it.ownerType to it.ownerId }.toSet() }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_GRACE_MS), emptySet())

    init {
        viewModelScope.launch {
            // One reset sweep at start: the old code only reset the category
            // you happened to select, so a widget-driven tick on an unvisited
            // list could act on last week's checkboxes.
            taskRepo.ensureAllRecurrenceResets()
        }
        viewModelScope.launch {
            categoryEntities.collect { list ->
                if (_selectedCategoryId.value == null && list.isNotEmpty()) {
                    selectCategory(list.first().id)
                } else if (list.none { it.id == _selectedCategoryId.value }) {
                    _selectedCategoryId.value = list.firstOrNull()?.id
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Main list state
    // ------------------------------------------------------------------

    @OptIn(ExperimentalCoroutinesApi::class)
    private val selectedTasks: StateFlow<List<TaskEntity>> = _selectedCategoryId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else taskRepo.getTasksForCategory(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_GRACE_MS), emptyList())

    val uiState: StateFlow<TasksUiState> = combine(
        categoryEntities,
        _selectedCategoryId,
        selectedTasks,
        reminderOwners,
        Vault.unlocked
    ) { categories, selectedId, tasks, reminders, unlocked ->
        val selectedEntity = categories.firstOrNull { it.id == selectedId }
        val categoryUis = categories.map { entity ->
            CategoryUi.from(
                entity,
                hasReminder = (ReminderEntity.OWNER_CATEGORY to entity.id) in reminders
            )
        }
        val sortMode = selectedEntity?.let { SortMode.from(it.sortMode) } ?: SortMode.Default
        val needsUnlock = selectedEntity?.locked == true && !unlocked

        if (needsUnlock) {
            TasksUiState(
                categories = categoryUis,
                selected = selectedEntity?.let {
                    CategoryUi.from(it, (ReminderEntity.OWNER_CATEGORY to it.id) in reminders)
                },
                active = emptyList(),
                completed = emptyList(),
                needsUnlock = true,
                loading = false
            )
        } else {
            // Partition first, then sort each half. Sorting the whole list and
            // filtering twice would walk it three times instead of twice.
            val active = ArrayList<TaskEntity>(tasks.size)
            val done = ArrayList<TaskEntity>()
            for (task in tasks) if (task.completed) done += task else active += task

            TasksUiState(
                categories = categoryUis,
                selected = selectedEntity?.let {
                    CategoryUi.from(it, (ReminderEntity.OWNER_CATEGORY to it.id) in reminders)
                },
                active = active
                    .sortedWith(Sorting.tasks(sortMode))
                    .map { it.toUi(taskRepo.displayTitle(it), hasReminderFor(it.id, reminders)) },
                completed = done
                    .sortedWith(Sorting.completedTasks)
                    .map { it.toUi(taskRepo.displayTitle(it), hasReminderFor(it.id, reminders)) },
                needsUnlock = false,
                loading = false
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_GRACE_MS), TasksUiState())

    private fun hasReminderFor(taskId: Long, reminders: Set<Pair<String, Long>>) =
        (ReminderEntity.OWNER_TASK to taskId) in reminders

    /** Retained for SettingsScreen's maintenance counters. */
    val allTasks: StateFlow<List<TaskEntity>> = taskRepo.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_GRACE_MS), emptyList())

    // ------------------------------------------------------------------
    // Inline per-category analytics (feature 2)
    // ------------------------------------------------------------------

    private val _inlineMonth = MutableStateFlow(YearMonth.now())
    val inlineMonth: StateFlow<YearMonth> = _inlineMonth

    /**
     * Analytics for the selected category, computed from the *same* engine
     * functions the global screen uses with a category filter applied.
     *
     * `WhileSubscribed(0)` with no grace on purpose: the block lives inside
     * the tasks LazyColumn, so it is disposed the moment it scrolls off
     * screen, and this stops aggregating with it. Scrolling past analytics
     * should cost nothing once it is gone.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val categoryAnalytics: StateFlow<CategoryAnalytics> =
        combine(_selectedCategoryId, _inlineMonth) { id, month -> id to month }
            .flatMapLatest { (id, month) ->
                if (id == null) {
                    flowOf(CategoryAnalytics.empty(-1L))
                } else {
                    analyticsRepo.streakPoints(zone).map { points ->
                        val today = LocalDate.now(zone)
                        val category = categoryEntities.value.firstOrNull { it.id == id }
                        val rollingDays = INLINE_ROLLING_DAYS
                        val rolling = AnalyticsEngine.dailySeries(
                            points = points,
                            startDate = today.minusDays((rollingDays - 1).toLong()),
                            days = rollingDays,
                            zone = zone,
                            categoryFilter = id
                        )
                        CategoryAnalytics(
                            categoryId = id,
                            rolling = rolling,
                            calendarMonth = AnalyticsEngine.calendarMonthSeries(
                                points, month, zone, id
                            ),
                            monthStart = month.atDay(1),
                            trend = AnalyticsEngine.dailySeries(
                                points = points,
                                startDate = today.minusDays((INLINE_TREND_DAYS - 1).toLong()),
                                days = INLINE_TREND_DAYS,
                                zone = zone,
                                categoryFilter = id
                            ),
                            streak = AnalyticsEngine.streakFor(
                                points = points,
                                categoryId = id,
                                recurrenceType = category?.recurrenceType ?: "once",
                                customDays = category?.recurrenceCustomDays ?: 0,
                                zone = zone,
                                today = today
                            ),
                            totalInWindow = rolling.counts.sum(),
                            loading = false
                        )
                    }
                }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), CategoryAnalytics.empty(-1L))

    fun stepInlineMonth(delta: Long) {
        _inlineMonth.value = _inlineMonth.value.plusMonths(delta)
    }

    // ------------------------------------------------------------------
    // Search (feature 4)
    // ------------------------------------------------------------------

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchScope = MutableStateFlow(SearchScope.CATEGORY)
    val searchScope: StateFlow<SearchScope> = _searchScope

    /**
     * Debounced search.
     *
     * 180 ms is deliberately below the ~250 ms at which a delay starts to
     * feel like lag, and high enough that typing "groceries" runs one query
     * instead of nine. Without it, every keystroke would fire two table scans
     * plus a decrypt pass over every locked row.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<SearchResults> =
        combine(
            _searchQuery.debounce(SEARCH_DEBOUNCE_MS).distinctUntilChanged(),
            _searchScope,
            _selectedCategoryId,
            Vault.unlocked
        ) { query, scope, categoryId, _ -> Triple(query, scope, categoryId) }
            .flatMapLatest { (query, scope, categoryId) ->
                flowOf(
                    if (query.isBlank()) {
                        SearchResults()
                    } else {
                        val names = categoryEntities.value.associate { it.id to it.name }
                        val tasks = taskRepo.search(query, scope, categoryId).map { task ->
                            TaskSearchHit(
                                task = task.copy(title = taskRepo.displayTitle(task)),
                                categoryName = names[task.categoryId].orEmpty()
                            )
                        }
                        val notes = noteRepo.search(query).map { note ->
                            note.copy(
                                title = noteRepo.displayTitle(note),
                                content = noteRepo.displayContent(note)
                            )
                        }
                        SearchResults(query = query, tasks = tasks, notes = notes)
                    }
                )
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_GRACE_MS), SearchResults())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleSearchScope() {
        _searchScope.value =
            if (_searchScope.value == SearchScope.CATEGORY) SearchScope.ALL else SearchScope.CATEGORY
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    // ------------------------------------------------------------------
    // Category actions
    // ------------------------------------------------------------------

    fun selectCategory(id: Long) {
        if (_selectedCategoryId.value == id) return
        _selectedCategoryId.value = id
        viewModelScope.launch { taskRepo.ensureRecurrenceReset(id) }
    }

    fun addCategory(name: String) = launchAndRefresh {
        val existing = categoryEntities.value
        val nextIndex = (existing.maxOfOrNull { it.orderIndex } ?: -1) + 1
        val id = categoryRepo.insert(CategoryEntity(name = name, orderIndex = nextIndex))
        _selectedCategoryId.value = id
    }

    fun deleteCategory(category: CategoryEntity) = launchAndRefresh {
        reminderRepo.clearForDeleted(ReminderEntity.OWNER_CATEGORY, category.id)
        categoryRepo.delete(category)
        if (_selectedCategoryId.value == category.id) {
            _selectedCategoryId.value =
                categoryEntities.value.firstOrNull { it.id != category.id }?.id
        }
    }

    fun renameCategory(category: CategoryEntity, newName: String) = launchAndRefresh {
        categoryRepo.update(category.copy(name = newName))
    }

    fun reorderCategories(reordered: List<CategoryEntity>) = launchAndRefresh {
        categoryRepo.updateOrderIndexes(reordered.mapIndexed { index, cat -> cat.id to index })
    }

    fun moveCategory(category: CategoryEntity, direction: Int) {
        val current = categoryEntities.value.toMutableList()
        val index = current.indexOfFirst { it.id == category.id }
        if (index == -1) return
        val target = index + direction
        if (target in current.indices) {
            current.add(target, current.removeAt(index))
            reorderCategories(current)
        }
    }

    fun toggleCategoryPinned(categoryId: Long) = launchAndRefresh {
        val category = categoryRepo.getById(categoryId) ?: return@launchAndRefresh
        categoryRepo.setPinned(categoryId, !category.pinned)
    }

    fun setCategorySort(categoryId: Long, mode: SortMode) = launchAndRefresh {
        categoryRepo.setSortMode(categoryId, mode.id)
    }

    fun setCategoryBackground(categoryId: Long, background: String?) = launchAndRefresh {
        categoryRepo.setBackground(categoryId, background)
    }

    fun updateRecurrence(categoryId: Long, type: String, customDays: Int = 0) = launchAndRefresh {
        categoryRepo.updateRecurrence(categoryId, type, customDays)
    }

    // ------------------------------------------------------------------
    // Locking (feature 8)
    // ------------------------------------------------------------------

    /**
     * Locks or unlocks a category, converting every task inside it.
     *
     * The caller is responsible for having authenticated first. If the
     * conversion fails part-way — an invalidated key, say — the category flag
     * is left untouched, so it never claims to be locked while its contents
     * are readable, or vice versa.
     */
    fun setCategoryLocked(categoryId: Long, locked: Boolean, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = taskRepo.setCategoryEncryption(categoryId, locked)
            if (ok) {
                categoryRepo.setLocked(categoryId, locked)
                WidgetUpdater.update(getApplication())
            }
            onResult(ok)
        }
    }

    fun lockVault() = Vault.lock()

    // ------------------------------------------------------------------
    // Task actions
    // ------------------------------------------------------------------

    fun addTask(title: String) {
        val categoryId = _selectedCategoryId.value
            ?: categoryEntities.value.firstOrNull()?.id
            ?: return
        launchAndRefresh { taskRepo.addTask(title, categoryId) }
    }

    fun toggleTask(taskId: Long, currentlyCompleted: Boolean) = launchAndRefresh {
        if (currentlyCompleted) taskRepo.uncompleteTask(taskId) else taskRepo.completeTask(taskId)
    }

    fun toggleTaskPinned(taskId: Long) = launchAndRefresh {
        val task = taskRepo.getById(taskId) ?: return@launchAndRefresh
        taskRepo.setPinned(taskId, !task.pinned)
    }

    fun renameTask(taskId: Long, newTitle: String) = launchAndRefresh {
        val task = taskRepo.getById(taskId) ?: return@launchAndRefresh
        taskRepo.renameTask(task, newTitle)
    }

    fun deleteTask(taskId: Long) = launchAndRefresh {
        val task = taskRepo.getById(taskId) ?: return@launchAndRefresh
        reminderRepo.clearForDeleted(ReminderEntity.OWNER_TASK, taskId)
        taskRepo.deleteTask(task)
    }

    fun clearCompletedTasks() = launchAndRefresh {
        val categoryId = _selectedCategoryId.value ?: return@launchAndRefresh
        val ids = taskRepo.getTasksForCategoryOnce(categoryId)
            .filter { it.completed }
            .map { it.id }
        if (ids.isNotEmpty()) taskRepo.deleteTasks(ids)
    }

    /** Clears completed tasks everywhere. Used by the Settings maintenance card. */
    fun clearAllCompletedTasks() = launchAndRefresh {
        val ids = allTasks.value.filter { it.completed }.map { it.id }
        if (ids.isNotEmpty()) taskRepo.deleteTasks(ids)
    }

    fun persistManualOrder(orderedIds: List<Long>) = launchAndRefresh {
        orderedIds.forEachIndexed { index, id ->
            db.taskDao().updateOrderIndex(id, index)
        }
    }

    // ------------------------------------------------------------------
    // Reminders (feature 10)
    // ------------------------------------------------------------------

    fun setTaskReminder(taskId: Long, time: LocalTime, repeat: String, intervalDays: Int = 0) {
        viewModelScope.launch {
            reminderRepo.set(ReminderEntity.OWNER_TASK, taskId, time, repeat, intervalDays)
        }
    }

    fun setCategoryReminder(
        categoryId: Long,
        time: LocalTime,
        repeat: String,
        intervalDays: Int = 0
    ) {
        viewModelScope.launch {
            reminderRepo.set(ReminderEntity.OWNER_CATEGORY, categoryId, time, repeat, intervalDays)
        }
    }

    fun clearTaskReminder(taskId: Long) {
        viewModelScope.launch { reminderRepo.clear(ReminderEntity.OWNER_TASK, taskId) }
    }

    fun clearCategoryReminder(categoryId: Long) {
        viewModelScope.launch { reminderRepo.clear(ReminderEntity.OWNER_CATEGORY, categoryId) }
    }

    suspend fun reminderForTask(taskId: Long) = reminderRepo.forTask(taskId)

    suspend fun reminderForCategory(categoryId: Long) = reminderRepo.forCategory(categoryId)

    // ------------------------------------------------------------------

    /**
     * Runs a mutation, then pushes the widget.
     *
     * The widget update is the expensive half — it crosses a process boundary
     * — so it happens once after the write rather than being scattered across
     * every call site, where it used to be easy to forget.
     */
    private inline fun launchAndRefresh(crossinline block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            WidgetUpdater.update(getApplication())
        }
    }

    private companion object {
        const val SUBSCRIBE_GRACE_MS = 5_000L
        const val SEARCH_DEBOUNCE_MS = 180L
        const val INLINE_ROLLING_DAYS = 119
        const val INLINE_TREND_DAYS = 30
    }
}
