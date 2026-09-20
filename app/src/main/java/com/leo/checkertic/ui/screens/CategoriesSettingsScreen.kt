package com.leo.checkertic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.checkertic.core.time.PeriodKeys
import com.leo.checkertic.data.entity.CategoryEntity
import com.leo.checkertic.data.entity.ReminderEntity
import com.leo.checkertic.reminders.ReminderScheduler
import com.leo.checkertic.ui.components.ReminderDialog
import com.leo.checkertic.ui.theme.AppTheme
import com.leo.checkertic.ui.theme.GlassTopBoundary
import com.leo.checkertic.ui.viewmodel.TasksViewModel

/**
 * Category management: order, recurrence, pinning, reminders, deletion.
 *
 * The category-level reminder lives here rather than on the Tasks screen
 * because it is a property of the list, not of today's view of it — and this
 * is where the recurrence rule it pairs with already lives. A category
 * reminder is the recurring default for everything inside: when it fires it
 * summarises what is still outstanding, skipping any task that has its own
 * reminder so nothing is announced twice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesSettingsScreen(
    viewModel: TasksViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categories by viewModel.categories.collectAsState()
    val colors = AppTheme.colors
    val spacing = AppTheme.spacing
    val context = LocalContext.current

    var showAdd by remember { mutableStateOf(false) }
    var recurrenceTarget by remember { mutableStateOf<CategoryEntity?>(null) }
    var reminderTarget by remember { mutableStateOf<CategoryEntity?>(null) }
    var reminderExisting by remember { mutableStateOf<ReminderEntity?>(null) }
    var renameTarget by remember { mutableStateOf<CategoryEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<CategoryEntity?>(null) }

    LaunchedEffect(reminderTarget) {
        reminderExisting = reminderTarget?.let { viewModel.reminderForCategory(it.id) }
    }

    Scaffold(
        topBar = {
            GlassTopBoundary {
                TopAppBar(
                    title = { Text("Categories", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = colors.textPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = colors.textPrimary,
                        navigationIconContentColor = colors.textPrimary
                    )
                )
            }
        },
        containerColor = colors.root,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(
                horizontal = spacing.screenGutter,
                vertical = spacing.sm
            )
        ) {
            itemsIndexed(categories, key = { _, category -> category.id }) { index, category ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        IconButton(
                            onClick = { viewModel.moveCategory(category, -1) },
                            enabled = index > 0,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowUp,
                                contentDescription = "Move up",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.moveCategory(category, 1) },
                            enabled = index < categories.size - 1,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Move down",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(spacing.sm))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (category.locked) {
                                Icon(
                                    Icons.Outlined.Lock,
                                    contentDescription = "Locked",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(spacing.xs))
                            }
                            Text(
                                text = category.name,
                                color = colors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.toggleCategoryPinned(category.id) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = if (category.pinned) "Unpin" else "Pin",
                            tint = if (category.pinned) colors.accent else colors.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { reminderTarget = category },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Notifications,
                            contentDescription = "Reminder",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { renameTarget = category },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "Rename",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { deleteTarget = category },
                        enabled = categories.size > 1,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete",
                            tint = if (categories.size > 1) {
                                MaterialTheme.colorScheme.error
                            } else {
                                colors.textMuted
                            },
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(Modifier.width(spacing.xs))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AppTheme.radius.lg - 2.dp))
                            .background(colors.accentContainer)
                            .clickable { recurrenceTarget = category }
                            .padding(horizontal = spacing.md, vertical = spacing.xs + 2.dp)
                    ) {
                        Text(
                            text = PeriodKeys.label(
                                category.recurrenceType,
                                category.recurrenceCustomDays
                            ),
                            color = colors.onAccentContainer,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                HorizontalDivider(color = colors.hairline, thickness = 0.5.dp)
            }

            item(key = "add") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable { showAdd = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(spacing.md + 2.dp))
                    Text(
                        text = "Add category",
                        color = colors.accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("New category") },
            text = {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Category name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        name.trim().takeIf { it.isNotEmpty() }?.let(viewModel::addCategory)
                        showAdd = false
                    }
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }

    renameTarget?.let { category ->
        var name by remember(category.id) { mutableStateOf(category.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename category") },
            text = {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        viewModel.renameCategory(category, name.trim())
                        renameTarget = null
                    }
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }

    deleteTarget?.let { category ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete category?") },
            text = {
                Text(
                    "\u201C${category.name}\u201D and all its tasks will be removed. " +
                        "Its completion history is removed with them, so any streak it " +
                        "contributed to will change."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCategory(category)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }

    reminderTarget?.let { category ->
        ReminderDialog(
            title = "Reminder for \u201C${category.name}\u201D",
            existing = reminderExisting,
            exactAvailable = ReminderScheduler.canBeExact(context),
            onDismiss = { reminderTarget = null },
            onClear = {
                viewModel.clearCategoryReminder(category.id)
                reminderTarget = null
            },
            onSave = { time, repeat, interval ->
                viewModel.setCategoryReminder(category.id, time, repeat, interval)
                reminderTarget = null
            }
        )
    }

    recurrenceTarget?.let { category ->
        var selectedType by remember(category.id) { mutableStateOf(category.recurrenceType) }
        var customDays by remember(category.id) {
            mutableIntStateOf(category.recurrenceCustomDays.takeIf { it > 0 } ?: 3)
        }

        AlertDialog(
            onDismissRequest = { recurrenceTarget = null },
            title = { Text("Recurrence for \u201C${category.name}\u201D") },
            text = {
                Column {
                    Text(
                        text = "This also sets the unit its streak is counted in — a weekly " +
                            "list breaks on a missed week, not a missed day.",
                        color = colors.textMuted,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(spacing.sm))
                    listOf(
                        PeriodKeys.ONCE to "Once (never resets)",
                        PeriodKeys.DAILY to "Daily",
                        PeriodKeys.WEEKLY to "Weekly",
                        PeriodKeys.MONTHLY to "Monthly",
                        PeriodKeys.CUSTOM to "Custom interval"
                    ).forEach { (type, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedType = type }
                                .padding(vertical = spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedType == type,
                                onClick = { selectedType = type }
                            )
                            Spacer(Modifier.width(spacing.sm))
                            Text(label, fontSize = 14.sp)
                        }
                    }
                    if (selectedType == PeriodKeys.CUSTOM) {
                        Spacer(Modifier.height(spacing.sm))
                        TextField(
                            value = if (customDays > 0) customDays.toString() else "",
                            onValueChange = { customDays = it.toIntOrNull() ?: 0 },
                            label = { Text("Days between resets") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateRecurrence(
                        category.id,
                        selectedType,
                        if (selectedType == PeriodKeys.CUSTOM) customDays else 0
                    )
                    recurrenceTarget = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { recurrenceTarget = null }) { Text("Cancel") }
            }
        )
    }
}
