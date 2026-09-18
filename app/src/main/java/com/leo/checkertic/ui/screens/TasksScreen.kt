package com.leo.checkertic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.leo.checkertic.R
import com.leo.checkertic.ui.components.TaskItem
import com.leo.checkertic.ui.theme.FabBackgroundDark
import com.leo.checkertic.ui.theme.FabIconBlue
import com.leo.checkertic.ui.theme.NavyContainer
import com.leo.checkertic.ui.theme.SkyBlueText
import com.leo.checkertic.ui.viewmodel.TasksViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    viewModel: TasksViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categories by viewModel.categories.collectAsState()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()
    val tasks by viewModel.tasks.collectAsState()

    var showAddTaskDialog by remember { mutableStateOf(false) }
    var showCategoryManagerDialog by remember { mutableStateOf(false) }
    var categoryToRename by remember { mutableStateOf<com.leo.checkertic.data.entity.CategoryEntity?>(null) }
    var categoryToDelete by remember { mutableStateOf<com.leo.checkertic.data.entity.CategoryEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var newCategoryName by remember { mutableStateOf("") }

    // Auto-select first category if none selected
    LaunchedEffect(categories) {
        if (selectedCategoryId == null && categories.isNotEmpty()) {
            viewModel.selectCategory(categories.first().id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_flip_logo),
                            contentDescription = "Flip Logo",
                            modifier = Modifier.size(26.dp)
                        )
                        Text(
                            text = "Flip",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = "Screen Settings",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddTaskDialog = true },
                shape = CircleShape,
                containerColor = FabBackgroundDark,
                contentColor = FabIconBlue,
                modifier = Modifier
                    .padding(end = 8.dp, bottom = 8.dp)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add task",
                    tint = FabIconBlue,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Category Filter Pills Row + Manage Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    categories.forEach { category ->
                        val isSelected = category.id == selectedCategoryId
                        Box(
                            modifier = (if (isSelected) {
                                Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(NavyContainer)
                            } else {
                                Modifier.clip(RoundedCornerShape(14.dp))
                            })
                                .combinedClickable(
                                    onClick = { viewModel.selectCategory(category.id) },
                                    onLongClick = {
                                        categoryToRename = category
                                        renameText = category.name
                                    }
                                )
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = category.name,
                                color = if (isSelected) SkyBlueText else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                }

                IconButton(
                    onClick = { showCategoryManagerDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Manage Categories",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Task items list
            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (categories.isEmpty()) "Add a category via settings" else "All tasks done",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(tasks, key = { it.id }) { task ->
                        TaskItem(
                            task = task,
                            onToggle = { viewModel.toggleTask(task) },
                            modifier = Modifier.animateItem(
                                fadeInSpec = null,
                                placementSpec = androidx.compose.animation.core.tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                fadeOutSpec = androidx.compose.animation.core.tween(180, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                            )
                        )
                    }
                    // Bottom spacer for FAB clearance
                    item {
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            }
        }
    }

    // Add Task Dialog
    if (showAddTaskDialog) {
        var title by remember { mutableStateOf("") }
        val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
        val submitTask = {
            val trimmed = title.trim()
            if (trimmed.isNotEmpty()) {
                viewModel.addTask(trimmed)
            }
            showAddTaskDialog = false
        }

        AlertDialog(
            onDismissRequest = { showAddTaskDialog = false },
            title = { Text("New Task") },
            text = {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Task title") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitTask() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            },
            confirmButton = {
                TextButton(onClick = submitTask) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTaskDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Manage Categories Dialog
    if (showCategoryManagerDialog) {
        AlertDialog(
            onDismissRequest = { showCategoryManagerDialog = false },
            title = {
                Text(text = "Manage Categories", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Add new category row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = newCategoryName,
                            onValueChange = { newCategoryName = it },
                            placeholder = { Text("New category...", fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (newCategoryName.isNotBlank()) {
                                    viewModel.addCategory(newCategoryName.trim())
                                    newCategoryName = ""
                                }
                            },
                            enabled = newCategoryName.isNotBlank()
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Category")
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Reorder, Rename, Delete List
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(categories, key = { _, cat -> cat.id }) { index, category ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Move Up
                                IconButton(
                                    onClick = { viewModel.moveCategory(category, -1) },
                                    enabled = index > 0,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Move Up",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Move Down
                                IconButton(
                                    onClick = { viewModel.moveCategory(category, 1) },
                                    enabled = index < categories.size - 1,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Move Down",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Text(
                                    text = category.name,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 6.dp),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Rename
                                IconButton(
                                    onClick = {
                                        categoryToRename = category
                                        renameText = category.name
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = "Rename",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // Delete (only if > 1 category remains)
                                IconButton(
                                    onClick = { categoryToDelete = category },
                                    enabled = categories.size > 1,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "Delete",
                                        tint = if (categories.size > 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCategoryManagerDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    // Rename Category Dialog
    if (categoryToRename != null) {
        AlertDialog(
            onDismissRequest = { categoryToRename = null },
            title = { Text("Rename Category") },
            text = {
                TextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = renameText.trim()
                        if (trimmed.isNotBlank()) {
                            categoryToRename?.let { viewModel.renameCategory(it, trimmed) }
                            categoryToRename = null
                        }
                    },
                    enabled = renameText.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Category Confirmation Dialog
    if (categoryToDelete != null) {
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Delete Category?") },
            text = {
                Text("Are you sure you want to delete '${categoryToDelete?.name}' and all its tasks?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        categoryToDelete?.let { viewModel.deleteCategory(it) }
                        categoryToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
