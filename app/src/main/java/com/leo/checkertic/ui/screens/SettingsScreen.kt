package com.leo.checkertic.ui.screens

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import com.leo.checkertic.R
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.leo.checkertic.ui.theme.SubtleGrayLine
import com.leo.checkertic.ui.theme.ThemeMode
import com.leo.checkertic.ui.viewmodel.TasksViewModel
import com.leo.checkertic.work.WidgetRefreshWorker
import java.util.concurrent.TimeUnit

private const val PREF_NAME = "checker_tic_prefs"
private const val KEY_NIGHTLY_REFRESH = "nightly_refresh_enabled"
private const val KEY_THEME_MODE = "theme_mode"
private const val WORK_TAG = "widget_nightly_refresh"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: TasksViewModel,
    currentThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.allTasks.collectAsState()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) }
    var nightlyRefreshEnabled by remember {
        mutableStateOf(prefs.getBoolean(KEY_NIGHTLY_REFRESH, true))
    }

    val completedCount = tasks.count { it.completed }
    val activeCount = tasks.count { !it.completed }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "App Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // -- Appearance (Theme Mode) --
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Appearance",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Choose your preferred color theme across all screens.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val themeOptions = listOf(
                            ThemeMode.DARK to "Dark (Obsidian)",
                            ThemeMode.LIGHT to "Light (Radiant)",
                            ThemeMode.SYSTEM to "System default"
                        )

                        themeOptions.forEach { (mode, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
                                        onThemeModeChange(mode)
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = currentThemeMode == mode,
                                    onClick = {
                                        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
                                        onThemeModeChange(mode)
                                    }
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = label,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // -- Nightly widget refresh toggle --
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
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Periodically refreshes the widget overnight so recurrence resets appear on your home screen without opening the app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = nightlyRefreshEnabled,
                            onCheckedChange = { enabled ->
                                nightlyRefreshEnabled = enabled
                                prefs.edit().putBoolean(KEY_NIGHTLY_REFRESH, enabled).apply()
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
                        )
                    }
                }
            }

            // -- Task Maintenance / Summary --
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Task Maintenance",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Active tasks: $activeCount  |  Completed tasks: $completedCount",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = { viewModel.clearCompletedTasks() },
                            enabled = completedCount > 0,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Clear Completed Tasks")
                        }
                    }
                }
            }

            // -- About / Version --
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_flip_logo),
                            contentDescription = "Flip App Logo",
                            modifier = Modifier.size(52.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Flip v1.1.0",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Notes & Tasks control surface built around a native Jetpack Glance home-screen widget, Room database, and WorkManager.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
