package com.leo.checkertic.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.leo.checkertic.core.audio.VoiceStore
import com.leo.checkertic.core.image.ImageStore
import com.leo.checkertic.core.share.ShareExport
import com.leo.checkertic.ui.components.BackgroundPicker
import com.leo.checkertic.ui.components.BackgroundSurface
import com.leo.checkertic.ui.components.LockedPanel
import com.leo.checkertic.ui.components.VoiceNoteRow
import com.leo.checkertic.ui.components.VoiceRecorderBar
import com.leo.checkertic.ui.components.rememberVaultUnlock
import com.leo.checkertic.ui.theme.AppTheme
import com.leo.checkertic.ui.theme.GlassTopBoundary
import com.leo.checkertic.ui.viewmodel.NotesViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * ============================================================================
 *  NOTE EDITOR
 * ============================================================================
 *
 * ## The autosave fix
 *
 * The previous version had `LaunchedEffect(title, content) { updateNote(...) }`,
 * which wrote to Room **on every keystroke**. Typing a paragraph was a few
 * hundred database transactions, each one waking the widget updater across a
 * process boundary. That is the single most expensive thing the old app did.
 *
 * Now the field state is funnelled through `snapshotFlow` and debounced: a
 * write lands [AUTOSAVE_DEBOUNCE_MS] after the user stops typing, and once
 * more on dispose so nothing is lost when they navigate back mid-sentence.
 * Same guarantee, roughly two orders of magnitude fewer writes.
 *
 * Locking, voice notes, backgrounds, sharing and export all live in the
 * overflow menu here rather than as separate screens.
 */
private const val AUTOSAVE_DEBOUNCE_MS = 600L

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun NoteEditScreen(
    noteId: Long,
    viewModel: NotesViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val notes by viewModel.notes.collectAsState()
    val note = remember(notes, noteId) { notes.find { it.id == noteId } }

    val colors = AppTheme.colors
    val spacing = AppTheme.spacing
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val unlock = rememberVaultUnlock()

    val voiceNotes by remember(noteId) { viewModel.voiceNotesFor(noteId) }.collectAsState()
    val player = remember { VoiceStore.Player() }

    var title by remember(noteId) { mutableStateOf("") }
    var content by remember(noteId) { mutableStateOf("") }
    var initialised by remember(noteId) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var showBackgroundPicker by remember { mutableStateOf(false) }

    val readable = note != null && viewModel.displayTitle(note) != com.leo.checkertic.core.crypto.Vault.LOCKED_PLACEHOLDER

    // Seed the fields once the note (and, for a locked note, the vault) is
    // available. Keyed on readability so unlocking mid-screen fills them in.
    LaunchedEffect(note, readable) {
        if (note != null && readable && !initialised) {
            title = viewModel.displayTitle(note)
            content = viewModel.displayContent(note)
            initialised = true
        }
    }

    // Debounced autosave. `snapshotFlow` turns the two mutable states into a
    // single flow, so one debounce covers edits to either field.
    LaunchedEffect(noteId, initialised) {
        if (!initialised) return@LaunchedEffect
        snapshotFlow { title to content }
            .distinctUntilChanged()
            .debounce(AUTOSAVE_DEBOUNCE_MS)
            .collect { (currentTitle, currentContent) ->
                val current = notes.find { it.id == noteId } ?: return@collect
                viewModel.updateNote(current, currentTitle, currentContent)
            }
    }

    // Final flush on leaving, so the last few characters are never lost.
    androidx.compose.runtime.DisposableEffect(noteId, initialised) {
        onDispose {
            player.stop()
            if (initialised) {
                notes.find { it.id == noteId }?.let {
                    viewModel.updateNote(it, title, content)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            GlassTopBoundary {
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = colors.textPrimary
                            )
                        }
                    },
                    actions = {
                        if (note != null) {
                            IconButton(onClick = { viewModel.togglePinned(note.id) }) {
                                Icon(
                                    imageVector = Icons.Filled.PushPin,
                                    contentDescription = if (note.pinned) "Unpin" else "Pin",
                                    tint = if (note.pinned) colors.accent else colors.textSecondary
                                )
                            }
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "More",
                                    tint = colors.textPrimary
                                )
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Outlined.Send, null)
                                    },
                                    onClick = {
                                        menuOpen = false
                                        ShareExport.shareText(
                                            context,
                                            if (content.isBlank()) title else "$title\n\n$content",
                                            title
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy") },
                                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                                    onClick = {
                                        menuOpen = false
                                        ShareExport.copyToClipboard(
                                            context,
                                            title,
                                            if (content.isBlank()) title else "$title\n\n$content"
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export as file") },
                                    leadingIcon = { Icon(Icons.Outlined.FileDownload, null) },
                                    onClick = {
                                        menuOpen = false
                                        scope.launch {
                                            ShareExport.exportNoteToFile(
                                                context,
                                                note.copy(title = title, content = content)
                                            )
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Background") },
                                    onClick = {
                                        menuOpen = false
                                        showBackgroundPicker = !showBackgroundPicker
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(if (note.locked) "Remove lock" else "Lock note")
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (note.locked) Icons.Outlined.LockOpen
                                            else Icons.Outlined.Lock,
                                            null
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        val target = !note.locked
                                        unlock(
                                            if (target) "Confirm to lock this note"
                                            else "Unlock this note"
                                        ) { authenticated ->
                                            if (authenticated) {
                                                viewModel.setNoteLocked(note.id, target) { }
                                            }
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Delete",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        viewModel.deleteNote(note)
                                        onBack()
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        navigationIconContentColor = colors.textPrimary
                    )
                )
            }
        },
        containerColor = colors.root,
        modifier = modifier
    ) { innerPadding ->

        if (note == null) return@Scaffold

        if (!readable) {
            LockedPanel(
                title = "This note is locked",
                subtitle = "Its text and any voice recordings are encrypted on this device.",
                actionLabel = "Unlock",
                onAction = { unlock("Unlock this note") { } },
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            )
            return@Scaffold
        }

        BackgroundSurface(
            reference = note.background,
            target = ImageStore.Target.FULL,
            fallback = colors.root,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                val transparentFields = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )

                TextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Title") },
                    textStyle = MaterialTheme.typography.titleLarge,
                    singleLine = true,
                    colors = transparentFields,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.sm)
                )

                TextField(
                    value = content,
                    onValueChange = { content = it },
                    placeholder = { Text("Start writing...") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    colors = transparentFields,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp)
                        .padding(horizontal = spacing.sm)
                )

                if (showBackgroundPicker) {
                    Spacer(Modifier.height(spacing.md))
                    BackgroundPicker(
                        current = note.background,
                        onSelect = { viewModel.setBackground(note.id, it) },
                        onPickFromGallery = { viewModel.importBackground(note.id, it) },
                        modifier = Modifier.padding(horizontal = spacing.screenGutter)
                    )
                }

                Spacer(Modifier.height(spacing.lg))

                Column(
                    modifier = Modifier.padding(horizontal = spacing.screenGutter),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    voiceNotes.forEach { recording ->
                        VoiceNoteRow(
                            voiceNote = recording,
                            player = player,
                            onDelete = { viewModel.deleteVoiceNote(recording) }
                        )
                    }
                    VoiceRecorderBar(
                        encryptRecordings = note.encrypted,
                        onSaved = { saved -> viewModel.addVoiceNote(note.id, saved) }
                    )
                }

                Spacer(Modifier.height(spacing.xxl))
            }
        }
    }
}
