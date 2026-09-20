package com.leo.checkertic.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.checkertic.R
import com.leo.checkertic.ui.components.InlineSearchBar
import com.leo.checkertic.ui.components.NoteCard
import com.leo.checkertic.ui.components.SortMenuButton
import com.leo.checkertic.ui.theme.AppTheme
import com.leo.checkertic.ui.theme.GlassTopBoundary
import com.leo.checkertic.ui.viewmodel.NotesViewModel

/**
 * The notes grid.
 *
 * Search, sort and pinning all resolve in the ViewModel, so this file
 * renders a list that is already in its final order — a staggered grid is the
 * worst place to be doing work during composition, because every item's
 * measured height feeds back into the layout of every item after it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    viewModel: NotesViewModel,
    onNoteClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val notes by viewModel.gridNotes.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val query by viewModel.searchQuery.collectAsState()

    val colors = AppTheme.colors
    val spacing = AppTheme.spacing

    var searching by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }

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
                                Text("Notes", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            }
                        },
                        actions = {
                            IconButton(onClick = { searching = !searching }) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = "Search notes",
                                    tint = colors.textPrimary
                                )
                            }
                            SortMenuButton(current = sortMode, onSelect = viewModel::setSortMode)
                        },
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
                            query = query,
                            onQueryChange = viewModel::setSearchQuery,
                            onClose = {
                                searching = false
                                viewModel.clearSearch()
                            },
                            placeholder = "Search titles and content"
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                shape = CircleShape,
                containerColor = colors.accentContainer,
                contentColor = colors.onAccentContainer,
                modifier = Modifier
                    .padding(end = spacing.sm, bottom = spacing.sm)
                    .size(AppTheme.sizes.fab)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add note",
                    modifier = Modifier.size(AppTheme.sizes.iconLg)
                )
            }
        },
        containerColor = colors.root,
        modifier = modifier
    ) { innerPadding ->
        if (notes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(bottom = spacing.xxl),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (query.isNotBlank()) "Nothing matches that" else "No notes yet",
                    color = colors.textMuted,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                contentPadding = PaddingValues(
                    start = spacing.screenGutter,
                    end = spacing.screenGutter,
                    top = spacing.md,
                    bottom = spacing.fabClearance
                ),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm + 2.dp),
                verticalItemSpacing = spacing.sm + 2.dp,
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            ) {
                items(notes, key = { it.id }, contentType = { "note" }) { note ->
                    NoteCard(
                        note = note,
                        onClick = { onNoteClick(note.id) },
                        onLongPress = { viewModel.togglePinned(note.id) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = AppTheme.motion.normalSpec(),
                            placementSpec = AppTheme.motion.placementSpec(),
                            fadeOutSpec = AppTheme.motion.fastSpec()
                        )
                    )
                }
            }
        }
    }

    if (showAdd) {
        var title by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("New note") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm + 2.dp)) {
                    TextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("Note title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextField(
                        value = content,
                        onValueChange = { content = it },
                        placeholder = { Text("Note content (optional)") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmedTitle = title.trim()
                    val trimmedContent = content.trim()
                    if (trimmedTitle.isNotEmpty() || trimmedContent.isNotEmpty()) {
                        viewModel.addNote(
                            trimmedTitle.ifEmpty { "Untitled" },
                            trimmedContent
                        ) { id ->
                            showAdd = false
                            onNoteClick(id)
                        }
                    } else {
                        showAdd = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("Cancel") }
            }
        )
    }
}
