package com.leo.checkertic.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.leo.checkertic.ui.components.TaskItem
import com.leo.checkertic.ui.viewmodel.TasksViewModel

@Composable
fun TasksScreen(
    viewModel: TasksViewModel,
    modifier: Modifier = Modifier
) {
    val categories by viewModel.categories.collectAsState()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()
    val tasks by viewModel.tasks.collectAsState()

    var showAddTaskDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }

    // Auto-select first category when categories load
    LaunchedEffect(categories) {
        if (selectedCategoryId == null && categories.isNotEmpty()) {
            viewModel.selectCategory(categories.first().id)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Category tabs
            if (categories.isNotEmpty()) {
                val selectedIndex = categories.indexOfFirst { it.id == selectedCategoryId }
                    .coerceAtLeast(0)

                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedIndex,
                    modifier = Modifier.fillMaxWidth(),
                    edgePadding = 16.dp
                ) {
                    categories.forEachIndexed { index, category ->
                        Tab(
                            selected = index == selectedIndex,
                            onClick = { viewModel.selectCategory(category.id) },
                            text = {
                                Text(
                                    text = category.name,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        )
                    }
                    // "+" tab to add category
                    Tab(
                        selected = false,
                        onClick = { showAddCategoryDialog = true },
                        text = {
                            Text(
                                text = "+",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    )
                }
            } else {
                // No categories — prompt to create one
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(onClick = { showAddCategoryDialog = true }) {
                        Text("Create your first category")
                    }
                }
            }

            // Task list
            if (categories.isNotEmpty()) {
                if (tasks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No tasks yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(tasks, key = { it.id }) { task ->
                            TaskItem(
                                task = task,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        viewModel.completeTask(task.id)
                                    } else {
                                        viewModel.uncompleteTask(task.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // FAB
        if (categories.isNotEmpty()) {
            FloatingActionButton(
                onClick = { showAddTaskDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add task",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }

    // Add task dialog
    if (showAddTaskDialog) {
        var taskTitle by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddTaskDialog = false },
            title = { Text("New Task") },
            text = {
                TextField(
                    value = taskTitle,
                    onValueChange = { taskTitle = it },
                    placeholder = { Text("Task title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (taskTitle.isNotBlank()) {
                            viewModel.addTask(taskTitle.trim())
                            showAddTaskDialog = false
                        }
                    }
                ) {
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

    // Add category dialog
    if (showAddCategoryDialog) {
        var categoryName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("New Category") },
            text = {
                TextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    placeholder = { Text("Category name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (categoryName.isNotBlank()) {
                            viewModel.addCategory(categoryName.trim())
                            showAddCategoryDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
