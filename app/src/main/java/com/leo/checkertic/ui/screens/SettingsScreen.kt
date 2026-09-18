package com.leo.checkertic.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.leo.checkertic.data.entity.CategoryEntity
import com.leo.checkertic.ui.viewmodel.TasksViewModel
import com.leo.checkertic.work.WidgetRefreshWorker
import java.util.concurrent.TimeUnit

private const val PREF_NAME = "checker_tic_prefs"
private const val KEY_NIGHTLY_REFRESH = "nightly_refresh_enabled"
private const val WORK_TAG = "widget_nightly_refresh"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: TasksViewModel,
    modifier: Modifier = Modifier
) {
    val categories by viewModel.categories.collectAsState()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) }
    var nightlyRefreshEnabled by remember {
        mutableStateOf(prefs.getBoolean(KEY_NIGHTLY_REFRESH, true))
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Settings") })

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // -- Nightly refresh toggle --
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Nightly widget refresh",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Periodically refreshes the widget overnight so recurrence resets appear without opening the app.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = nightlyRefreshEnabled,
                            onCheckedChange = { enabled ->
                                nightlyRefreshEnabled = enabled
                                prefs.edit().putBoolean(KEY_NIGHTLY_REFRESH, enabled).apply()
                                toggleWorkManager(context, enabled)
                            }
                        )
                    }
                }
            }

            // -- Per-category recurrence --
            item {
                Text(
                    text = "Category Recurrence",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(categories, key = { it.id }) { category ->
                CategoryRecurrenceCard(
                    category = category,
                    onRecurrenceChange = { type, customDays ->
                        viewModel.updateRecurrence(category.id, type, customDays)
                    }
                )
            }

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun CategoryRecurrenceCard(
    category: CategoryEntity,
    onRecurrenceChange: (type: String, customDays: Int) -> Unit
) {
    var showCustomDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleMedium
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            val options = listOf("once", "daily", "weekly", "monthly", "custom")
            options.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    RadioButton(
                        selected = category.recurrenceType == option ||
                            (option == "custom" && category.recurrenceType == "custom"),
                        onClick = {
                            if (option == "custom") {
                                showCustomDialog = true
                            } else {
                                onRecurrenceChange(option, 0)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (option) {
                            "once" -> "Once (no reset)"
                            "daily" -> "Daily"
                            "weekly" -> "Weekly"
                            "monthly" -> "Monthly"
                            "custom" -> {
                                if (category.recurrenceType == "custom" && category.recurrenceCustomDays > 0) {
                                    "Custom (every ${category.recurrenceCustomDays} days)"
                                } else {
                                    "Custom..."
                                }
                            }
                            else -> option
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }

    if (showCustomDialog) {
        var daysText by remember { mutableStateOf(
            if (category.recurrenceCustomDays > 0) category.recurrenceCustomDays.toString() else ""
        ) }

        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text("Custom Recurrence") },
            text = {
                Column {
                    Text(
                        text = "Reset tasks every N days:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = daysText,
                        onValueChange = { daysText = it.filter { c -> c.isDigit() } },
                        placeholder = { Text("Number of days") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val days = daysText.toIntOrNull()
                        if (days != null && days > 0) {
                            onRecurrenceChange("custom", days)
                            showCustomDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun toggleWorkManager(context: Context, enabled: Boolean) {
    val workManager = WorkManager.getInstance(context)
    if (enabled) {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
            12, TimeUnit.HOURS
        ).build()
        workManager.enqueueUniquePeriodicWork(
            WORK_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    } else {
        workManager.cancelUniqueWork(WORK_TAG)
    }
}
