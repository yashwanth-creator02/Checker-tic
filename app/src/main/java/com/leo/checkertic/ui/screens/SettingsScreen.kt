package com.leo.checkertic.ui.screens

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.leo.checkertic.BuildConfig
import com.leo.checkertic.R
import com.leo.checkertic.core.backup.BackupManager
import com.leo.checkertic.core.crypto.BiometricGate
import com.leo.checkertic.core.crypto.Vault
import com.leo.checkertic.reminders.Notifications
import com.leo.checkertic.reminders.ReminderScheduler
import com.leo.checkertic.ui.components.SectionCard
import com.leo.checkertic.ui.components.WidgetPreview
import com.leo.checkertic.ui.theme.AppTheme
import com.leo.checkertic.ui.theme.GlassTopBoundary
import com.leo.checkertic.ui.theme.ThemeMode
import com.leo.checkertic.ui.viewmodel.TasksViewModel
import com.leo.checkertic.widget.CheckerTicWidgetReceiver
import com.leo.checkertic.work.WidgetRefreshWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

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
    val categories by viewModel.categories.collectAsState()
    val vaultUnlocked by Vault.unlocked.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = AppTheme.colors
    val spacing = AppTheme.spacing

    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }
    val canPinWidget = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appWidgetManager.isRequestPinAppWidgetSupported
    }

    val prefs = remember { context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) }
    var nightlyRefresh by remember { mutableStateOf(prefs.getBoolean(KEY_NIGHTLY_REFRESH, true)) }

    var showBackupSheet by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val completedCount = tasks.count { it.completed }
    val activeCount = tasks.count { !it.completed }

    var exactAlarms by remember { mutableStateOf(ReminderScheduler.canBeExact(context)) }
    var notificationsOn by remember { mutableStateOf(Notifications.canPost(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exactAlarms = ReminderScheduler.canBeExact(context)
                notificationsOn = Notifications.canPost(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        notificationsOn = Notifications.canPost(context)
    }

    Scaffold(
        topBar = {
            GlassTopBoundary {
                TopAppBar(
                    title = { Text("Settings", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = colors.textPrimary
                    )
                )
            }
        },
        containerColor = colors.root,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = spacing.screenGutter,
                end = spacing.screenGutter,
                top = spacing.sm,
                bottom = spacing.fabClearance
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {

            // -- Appearance ------------------------------------------------
            item(key = "appearance") {
                SectionCard(title = "Appearance") {
                    Text(
                        text = "Applies everywhere. Every colour in the app comes from one " +
                            "token set, so this switches the whole surface at once.",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(Modifier.height(spacing.sm))
                    listOf(
                        ThemeMode.DARK to "Dark (Obsidian)",
                        ThemeMode.LIGHT to "Light (Radiant)",
                        ThemeMode.SYSTEM to "Follow system"
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
                                    onThemeModeChange(mode)
                                }
                                .padding(vertical = spacing.xs + 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentThemeMode == mode,
                                onClick = {
                                    prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
                                    onThemeModeChange(mode)
                                }
                            )
                            Spacer(Modifier.width(spacing.sm + 2.dp))
                            Text(label, color = colors.textPrimary, fontSize = 14.sp)
                        }
                    }
                }
            }

            // -- Security --------------------------------------------------
            item(key = "security") {
                SectionCard(title = "Locking") {
                    val available = remember { BiometricGate.canAuthenticate(context) }
                    Text(
                        text = if (available) {
                            "Locked lists and notes are encrypted with a key held in this " +
                                "device's hardware keystore. The key is released only after " +
                                "biometric or device-credential authentication, and stays " +
                                "available for ${Vault.AUTH_VALIDITY_SECONDS / 60} minutes."
                        } else {
                            BiometricGate.unavailableReason(context)
                                ?: "Locking isn't available on this device."
                        },
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    if (Vault.hasKey() && Vault.isKeyInvalidated()) {
                        Spacer(Modifier.height(spacing.sm))
                        Text(
                            text = "A new biometric was enrolled since locking was set up, " +
                                "which permanently invalidates the old key. Previously " +
                                "locked content can no longer be decrypted.",
                            color = colors.danger,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                    if (vaultUnlocked) {
                        Spacer(Modifier.height(spacing.md))
                        OutlinedButton(onClick = { viewModel.lockVault() }) {
                            Text("Lock now")
                        }
                    }
                }
            }

            // -- Reminders -------------------------------------------------
            item(key = "reminders") {
                SectionCard(title = "Reminders") {
                    Text(
                        text = if (exactAlarms) {
                            "Reminders are delivered at the exact minute you set."
                        } else {
                            "Reminders are delivered within a few minutes of the time you " +
                                "set. Android grants to-the-minute delivery only when you " +
                                "allow \"Alarms & reminders\" for Flip."
                        },
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    if (!exactAlarms && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Spacer(Modifier.height(spacing.sm))
                        OutlinedButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                )
                            }
                        }) { Text("Allow exact alarms") }
                    }
                    if (!notificationsOn) {
                        Spacer(Modifier.height(spacing.sm))
                        Text(
                            text = "Notifications are turned off, so reminders won't appear.",
                            color = colors.danger,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(Modifier.height(spacing.xs))
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                OutlinedButton(onClick = {
                                    runCatching {
                                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }.onFailure {
                                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        }
                                        runCatching { context.startActivity(intent) }
                                    }
                                }) { Text("Enable notifications") }
                            }
                            OutlinedButton(onClick = {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                runCatching { context.startActivity(intent) }
                            }) { Text("Settings") }
                        }
                    }
                }
            }

            // -- Backup ----------------------------------------------------
            item(key = "backup") {
                SectionCard(title = "Local backup") {
                    Text(
                        text = "Exports everything — lists, tasks, completion history, notes, " +
                            "voice note records and reminders — as a single file to a " +
                            "location you choose.",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(Modifier.height(spacing.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        OutlinedButton(onClick = { showBackupSheet = true }) {
                            Text("Back up")
                        }
                        RestoreButton(onPicked = { pendingRestoreUri = it })
                    }
                    statusMessage?.let {
                        Spacer(Modifier.height(spacing.sm))
                        Text(text = it, color = colors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }

            // -- Widget ----------------------------------------------------
            item(key = "widget") {
                SectionCard(
                    title = "Home Screen Widget",
                    trailing = {
                        Text(
                            text = "PREVIEW",
                            color = colors.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                ) {
                    Text(
                        text = "Flip includes an interactive home screen widget. You can check off " +
                            "tasks, increment counters with the ticker controls, flip to notes, " +
                            "and quickly add items without opening the app.",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(Modifier.height(spacing.md))

                    // Authentic widget visual preview
                    WidgetPreview(tasks = tasks, categories = categories)

                    Spacer(Modifier.height(spacing.md))

                    if (canPinWidget) {
                        OutlinedButton(
                            onClick = {
                                val provider = ComponentName(context, CheckerTicWidgetReceiver::class.java)
                                val success = runCatching {
                                    appWidgetManager.requestPinAppWidget(provider, null, null)
                                }.getOrDefault(false)
                                if (!success) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Please touch and hold your home screen to add the Flip widget.",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Add widget to Home Screen")
                        }
                        Spacer(Modifier.height(spacing.xs))
                    }

                    Text(
                        text = "To add manually: Touch and hold an empty space on your home screen, " +
                            "select Widgets, locate Flip, and drag it to your screen.",
                        color = colors.textMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )

                    Spacer(Modifier.height(spacing.lg))

                    // Nightly widget refresh toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Nightly widget refresh",
                                color = colors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Refreshes the widget overnight so recurrence resets show " +
                                    "on your home screen without opening the app.",
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                        Spacer(Modifier.width(spacing.md))
                        Switch(
                            checked = nightlyRefresh,
                            onCheckedChange = { enabled ->
                                nightlyRefresh = enabled
                                prefs.edit().putBoolean(KEY_NIGHTLY_REFRESH, enabled).apply()
                                val workManager = WorkManager.getInstance(context)
                                if (enabled) {
                                    workManager.enqueueUniquePeriodicWork(
                                        WORK_TAG,
                                        ExistingPeriodicWorkPolicy.KEEP,
                                        PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
                                            12, TimeUnit.HOURS
                                        ).build()
                                    )
                                } else {
                                    workManager.cancelUniqueWork(WORK_TAG)
                                }
                            }
                        )
                    }
                }
            }

            // -- Maintenance -----------------------------------------------
            item(key = "maintenance") {
                SectionCard(title = "Maintenance") {
                    Text(
                        text = "Active: $activeCount   ·   Completed: $completedCount",
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = "Clearing completed tasks removes the task rows. Your " +
                            "completion history — and therefore your streaks and heatmaps — " +
                            "is kept.",
                        color = colors.textMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                    Spacer(Modifier.height(spacing.md))
                    OutlinedButton(
                        onClick = { viewModel.clearAllCompletedTasks() },
                        enabled = completedCount > 0,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Clear completed tasks") }
                }
            }

            // -- About -----------------------------------------------------
            item(key = "about") {
                SectionCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.lg)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_flip_logo),
                            contentDescription = null,
                            modifier = Modifier.size(52.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Flip v${BuildConfig.VERSION_NAME}",
                                color = colors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 20.sp
                            )
                            Spacer(Modifier.height(spacing.xs))
                            Text(
                                text = "Notes and tasks built around a Glance home-screen " +
                                    "widget, a Room database, and recurrence-aware analytics.",
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBackupSheet) {
        BackupDialog(
            onDismiss = { showBackupSheet = false },
            onExport = { uri, passphrase ->
                showBackupSheet = false
                scope.launch {
                    val outcome = BackupManager.export(context, uri, passphrase)
                    statusMessage = when (outcome) {
                        is BackupManager.Outcome.Success -> outcome.message
                        is BackupManager.Outcome.Failure -> outcome.message
                    }
                }
            }
        )
    }

    pendingRestoreUri?.let { uri ->
        RestoreDialog(
            uri = uri,
            onDismiss = { pendingRestoreUri = null },
            onConfirm = { passphrase ->
                pendingRestoreUri = null
                scope.launch {
                    val outcome = BackupManager.restore(context, uri, passphrase)
                    statusMessage = when (outcome) {
                        is BackupManager.Outcome.Success -> outcome.message
                        is BackupManager.Outcome.Failure -> outcome.message
                    }
                }
            }
        )
    }
}

/**
 * Export sheet.
 *
 * The encryption toggle is here, at backup time, rather than being a stored
 * preference — the brief was explicit that this is a per-backup decision, and
 * it genuinely is: a backup going to a USB stick in a drawer and one going to
 * a shared cloud folder want different answers.
 *
 * The consequence of each choice is written next to it, because "encrypt?" is
 * not a question most people can answer well without being told what happens
 * if they forget the passphrase.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupDialog(
    onDismiss: () -> Unit,
    onExport: (Uri, String?) -> Unit
) {
    val colors = AppTheme.colors
    var encrypt by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            onExport(uri, if (encrypt) passphrase.takeIf { it.isNotEmpty() } else null)
        } else {
            onDismiss()
        }
    }

    val suggestedName = remember {
        "flip-backup-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.json"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Back up") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = encrypt, onCheckedChange = { encrypt = it })
                    Spacer(Modifier.width(AppTheme.spacing.md))
                    Text("Encrypt this backup", fontSize = 14.sp)
                }
                Text(
                    text = if (encrypt) {
                        "Protected with a passphrase you choose. There is no recovery — " +
                            "if you forget it, the backup cannot be opened by anyone, " +
                            "including you."
                    } else {
                        "Readable by anything that can open the file. Fine for a folder " +
                            "only you can reach; not for shared or cloud storage."
                    },
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
                if (encrypt) {
                    TextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Passphrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    text = "Content inside locked lists and notes stays encrypted with this " +
                        "device's hardware key, so it can only be read again on this device.",
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { createDocument.launch(suggestedName) },
                enabled = !encrypt || passphrase.length >= 6
            ) { Text("Choose location") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RestoreButton(onPicked: (Uri) -> Unit) {
    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onPicked) }

    OutlinedButton(onClick = { openDocument.launch(arrayOf("application/json", "*/*")) }) {
        Text("Restore")
    }
}

/**
 * Restore confirmation.
 *
 * The file is inspected and summarised *before* anything is written, so the
 * user is told exactly what they are about to replace their data with. A
 * corrupt file, a wrong passphrase, or a file from another app is caught
 * here, while the live database is still untouched.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestoreDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    val context = LocalContext.current
    val colors = AppTheme.colors
    var passphrase by remember { mutableStateOf("") }
    var needsPassphrase by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<BackupManager.Preview?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(true) }

    androidx.compose.runtime.LaunchedEffect(uri) {
        needsPassphrase = BackupManager.isEncrypted(context, uri)
        if (!needsPassphrase) {
            BackupManager.inspect(context, uri, null)
                .onSuccess { preview = it; error = null }
                .onFailure { error = it.message }
        }
        checking = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)) {
                when {
                    checking -> Text("Checking the file...", fontSize = 13.sp)
                    error != null -> Text(
                        text = error!!,
                        color = colors.danger,
                        fontSize = 13.sp
                    )
                    preview != null -> {
                        val p = preview!!
                        Text(
                            text = "${p.tasks} tasks · ${p.notes} notes · " +
                                "${p.completions} completions · ${p.categories} lists",
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        Text(
                            text = "This replaces everything currently in Flip. It cannot " +
                                "be undone.",
                            color = colors.danger,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                if (needsPassphrase && preview == null) {
                    Text(
                        text = "This backup is encrypted.",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    TextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Passphrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            val scope = rememberCoroutineScope()
            TextButton(
                enabled = !checking && (preview != null || (needsPassphrase && passphrase.isNotEmpty())),
                onClick = {
                    if (preview != null) {
                        onConfirm(if (needsPassphrase) passphrase else null)
                    } else {
                        scope.launch {
                            BackupManager.inspect(context, uri, passphrase)
                                .onSuccess { preview = it; error = null }
                                .onFailure { error = it.message }
                        }
                    }
                }
            ) { Text(if (preview != null) "Replace my data" else "Check") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
