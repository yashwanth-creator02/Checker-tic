package com.leo.checkertic.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.checkertic.R
import com.leo.checkertic.core.crypto.Vault
import com.leo.checkertic.core.share.ShareExport
import com.leo.checkertic.data.entity.ReminderEntity
import com.leo.checkertic.ui.components.BackgroundSurface
import com.leo.checkertic.ui.components.CategoryAnalyticsBlock
import com.leo.checkertic.ui.components.CompletedRevealBar
import com.leo.checkertic.ui.components.InlineSearchBar
import com.leo.checkertic.ui.components.LockedPanel
import com.leo.checkertic.ui.components.SortMenuButton
import com.leo.checkertic.ui.components.TaskItem
import com.leo.checkertic.ui.components.rememberVaultUnlock
import com.leo.checkertic.ui.theme.AppTheme
import com.leo.checkertic.ui.theme.GlassTopBoundary
import com.leo.checkertic.ui.viewmodel.CategoryUi
import com.leo.checkertic.ui.viewmodel.TaskUi
import com.leo.checkertic.ui.viewmodel.TasksViewModel
import kotlinx.coroutines.launch

/**
 * ============================================================================
 *  TASKS SCREEN
 * ============================================================================
 *
 * Carries features 2, 3, 4, 5, 6, 7, 8 and 10 on one surface.
 *
 * ## The single-scroll structure
 *
 * Everything below the category strip lives in one `LazyColumn`: active
 * tasks, the completed reveal, the revealed history, and the inline analytics
 * block. Nesting a scrollable analytics section inside a scrollable task list
 * would be both a gesture conflict and a measurement error (Compose cannot
 * measure an unbounded-height child inside an unbounded-height parent), so
 * the sections are items in the same list instead. That is also why
 * analytics disposes naturally when it scrolls away.
 *
 * ## Why state here is minimal
 *
 * The only things this file remembers are genuinely ephemeral UI state:
 * whether the completed section is open, which sheet is showing, what is
 * typed into a dialog. Every piece of data, including the decrypted titles
 * and the sorted order, arrives pre-computed from the ViewModel. No
 * filtering, sorting or decryption happens during composition.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    viewModel: TasksViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val analytics by viewModel.categoryAnalytics.collectAsState()
    val inlineMonth by viewModel.inlineMonth.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchScope by viewModel.searchScope.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()

    val colors = AppTheme.colors
    val spacing = AppTheme.spacing
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val unlock = rememberVaultUnlock()

    var searching by remember { mutableStateOf(false) }
    var completedExpanded by remember(state.selected?.id) { mutableStateOf(false) }
    var showAddTask by remember { mutableStateOf(false) }
    var taskSheet by remember { mutableStateOf<TaskUi?>(null) }
    var categorySheet by remember { mutableStateOf<CategoryUi?>(null) }
    var renameTarget by remember { mutableStateOf<TaskUi?>(null) }

    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            GlassTopBoundary {
                Column {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.sm + 2.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_flip_logo),
                                    contentDescription = null,
                                    modifier = Modifier.size(26.dp)
                                )
                                Text("Flip", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            }
                        },
                        actions = {
                            IconButton(onClick = { searching = !searching }) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = "Search",
                                    tint = colors.textPrimary
                                )
                            }
                            state.selected?.let { category ->
                                SortMenuButton(
                                    current = category.sortMode,
                                    onSelect = { viewModel.setCategorySort(category.id, it) }
                                )
                            }
                            IconButton(onClick = onOpenSettings) {
                                Icon(
                                    imageVector = Icons.Outlined.Tune,
                                    contentDescription = "Category settings",
                                    tint = colors.textPrimary
                                )
                            }
                        },
                        // Transparent so the glass boundary behind it shows
                        // through; the bar itself must not paint a second
                        // opaque layer on top of it.
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = colors.textPrimary,
                            actionIconContentColor = colors.textPrimary
                        )
                    )

                    AnimatedVisibility(
                        visible = searching,
                        enter = fadeIn(AppTheme.motion.fastSpec()) +
                            expandVertically(AppTheme.motion.normalSpec()),
                        exit = fadeOut(AppTheme.motion.fastSpec()) +
                            shrinkVertically(AppTheme.motion.fastSpec())
                    ) {
                        InlineSearchBar(
                            query = searchQuery,
                            onQueryChange = viewModel::setSearchQuery,
                            onClose = {
                                searching = false
                                viewModel.clearSearch()
                            },
                            scope = searchScope,
                            onToggleScope = viewModel::toggleSearchScope,
                            placeholder = "Search tasks & notes"
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!searching && !state.needsUnlock) {
                FloatingActionButton(
                    onClick = { showAddTask = true },
                    shape = CircleShape,
                    containerColor = colors.accentContainer,
                    contentColor = colors.onAccentContainer,
                    modifier = Modifier
                        .padding(end = spacing.sm, bottom = spacing.sm)
                        .size(AppTheme.sizes.fab)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add task",
                        modifier = Modifier.size(AppTheme.sizes.iconLg)
                    )
                }
            }
        },
        containerColor = colors.root,
        modifier = modifier
    ) { innerPadding ->

        BackgroundSurface(
            reference = state.selected?.background,
            target = com.leo.checkertic.core.image.ImageStore.Target.FULL,
            fallback = colors.root,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

                CategoryStrip(
                    categories = state.categories,
                    selectedId = state.selected?.id,
                    onSelect = viewModel::selectCategory,
                    onLongPress = { categorySheet = it }
                )

                when {
                    searching && searchQuery.isNotBlank() -> {
                        SearchResultsList(
                            results = searchResults,
                            onTaskClick = { hit ->
                                viewModel.selectCategory(hit.task.categoryId)
                                searching = false
                                viewModel.clearSearch()
                            }
                        )
                    }

                    state.needsUnlock -> {
                        LockedPanel(
                            title = "${state.selected?.name.orEmpty()} is locked",
                            subtitle = "Everything in this list is encrypted on this device. " +
                                "Unlock to read or change it.",
                            actionLabel = "Unlock",
                            onAction = { unlock("Unlock this list") { } },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = spacing.screenGutter,
                                end = spacing.screenGutter,
                                top = spacing.sm,
                                bottom = spacing.fabClearance
                            ),
                            verticalArrangement = Arrangement.spacedBy(spacing.md)
                        ) {

                            if (state.active.isEmpty() && !state.hasCompleted) {
                                item(key = "empty") {
                                    EmptyState(hasCategories = state.categories.isNotEmpty())
                                }
                            }

                            // Stable keys plus a contentType, so Compose reuses
                            // a task row for a task row and never tries to
                            // reuse one for the analytics block.
                            items(
                                items = state.active,
                                key = { it.id },
                                contentType = { "task" }
                            ) { task ->
                                TaskItem(
                                    task = task,
                                    onToggle = { viewModel.toggleTask(task.id, task.completed) },
                                    onLongPress = { taskSheet = task },
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = null,
                                        placementSpec = AppTheme.motion.placementSpec(),
                                        fadeOutSpec = AppTheme.motion.fastSpec()
                                    )
                                )
                            }

                            if (state.hasCompleted) {
                                item(key = "completed-bar", contentType = "reveal") {
                                    CompletedRevealBar(
                                        count = state.completed.size,
                                        expanded = completedExpanded,
                                        onToggle = { completedExpanded = !completedExpanded },
                                        modifier = Modifier.animateItem(
                                            placementSpec = AppTheme.motion.placementSpec()
                                        )
                                    )
                                }

                                if (completedExpanded) {
                                    items(
                                        items = state.completed,
                                        key = { "done-${it.id}" },
                                        contentType = { "task" }
                                    ) { task ->
                                        TaskItem(
                                            task = task,
                                            onToggle = {
                                                viewModel.toggleTask(task.id, task.completed)
                                            },
                                            onLongPress = { taskSheet = task },
                                            modifier = Modifier.animateItem(
                                                placementSpec = AppTheme.motion.placementSpec()
                                            )
                                        )
                                    }
                                    item(key = "clear-completed") {
                                        TextButton(
                                            onClick = viewModel::clearCompletedTasks,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Clear completed", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            item(key = "analytics", contentType = "analytics") {
                                Spacer(Modifier.height(spacing.sm))
                                CategoryAnalyticsBlock(
                                    analytics = analytics,
                                    month = inlineMonth,
                                    onStepMonth = viewModel::stepInlineMonth
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Dialogs and sheets
    // ------------------------------------------------------------------

    if (showAddTask) {
        AddTaskDialog(
            onDismiss = { showAddTask = false },
            onAdd = { title ->
                viewModel.addTask(title)
                showAddTask = false
            }
        )
    }

    taskSheet?.let { task ->
        TaskActionsSheet(
            task = task,
            onDismiss = { taskSheet = null },
            onTogglePin = {
                viewModel.toggleTaskPinned(task.id)
                taskSheet = null
            },
            onRename = {
                renameTarget = task
                taskSheet = null
            },
            onShare = {
                ShareExport.shareText(context, "${if (task.completed) "[x]" else "[ ]"} ${task.title}")
                taskSheet = null
            },
            onCopy = {
                ShareExport.copyToClipboard(context, "Task", task.title)
                taskSheet = null
            },
            onDelete = {
                viewModel.deleteTask(task.id)
                taskSheet = null
            }
        )
    }

    categorySheet?.let { category ->
        CategoryActionsSheet(
            category = category,
            onDismiss = { categorySheet = null },
            onTogglePin = {
                viewModel.toggleCategoryPinned(category.id)
                categorySheet = null
            },
            onToggleLock = {
                val target = !category.locked
                unlock(
                    if (target) "Confirm to lock this list" else "Unlock this list"
                ) { authenticated ->
                    if (authenticated) {
                        viewModel.setCategoryLocked(category.id, target) { }
                    }
                }
                categorySheet = null
            },
            onShare = {
                scope.launch {
                    val text = buildString {
                        appendLine("# ${category.name}")
                        appendLine()
                        state.active.forEach { appendLine("- [ ] ${it.title}") }
                        state.completed.forEach { appendLine("- [x] ${it.title}") }
                    }.trimEnd()
                    ShareExport.shareText(context, text, category.name)
                }
                categorySheet = null
            },
            onSetBackground = { reference ->
                viewModel.setCategoryBackground(category.id, reference)
            },
            onPickBackground = { uri ->
                scope.launch {
                    com.leo.checkertic.core.image.ImageStore
                        .importFromPicker(context, uri)
                        ?.let { viewModel.setCategoryBackground(category.id, it) }
                }
            }
        )
    }

    renameTarget?.let { task ->
        RenameDialog(
            initial = task.title,
            title = "Rename task",
            onDismiss = { renameTarget = null },
            onConfirm = { newTitle ->
                viewModel.renameTask(task.id, newTitle)
                renameTarget = null
            }
        )
    }
}

// ----------------------------------------------------------------------
// Pieces
// ----------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryStrip(
    categories: List<CategoryUi>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onLongPress: (CategoryUi) -> Unit
) {
    val colors = AppTheme.colors
    val spacing = AppTheme.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = spacing.screenGutter, vertical = spacing.xs + 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        categories.forEach { category ->
            val selected = category.id == selectedId
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppTheme.radius.lg - 2.dp))
                    .background(if (selected) colors.accentContainer else Color.Transparent)
                    .combinedClickable(
                        onClick = { onSelect(category.id) },
                        onLongClick = { onLongPress(category) }
                    )
                    .padding(horizontal = spacing.md + 2.dp, vertical = spacing.sm - 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (category.locked) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "Locked",
                        tint = if (selected) colors.onAccentContainer else colors.textSecondary,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(spacing.xs))
                }
                Text(
                    text = category.name,
                    color = if (selected) colors.onAccentContainer else colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                )
            }
            Spacer(Modifier.width(spacing.xs + 2.dp))
        }
    }
}

@Composable
private fun EmptyState(hasCategories: Boolean) {
    Box(
        modifier = Modifier.fillMaxWidth().height(160.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (hasCategories) "All clear" else "Add a category to get started",
            color = AppTheme.colors.textMuted,
            fontSize = 14.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTaskDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val submit = {
        val trimmed = title.trim()
        if (trimmed.isNotEmpty()) onAdd(trimmed) else onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New task") },
        text = {
            TextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("Task title") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
            )
        },
        confirmButton = { TextButton(onClick = submit) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenameDialog(
    initial: String,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
