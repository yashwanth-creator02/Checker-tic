package com.leo.checkertic.ui.screens

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.leo.checkertic.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.checkertic.ui.components.NoteCard
import com.leo.checkertic.ui.theme.FabBackgroundDark
import com.leo.checkertic.ui.theme.FabIconBlue
import com.leo.checkertic.ui.viewmodel.NotesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    viewModel: NotesViewModel,
    onNoteClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val notes by viewModel.notes.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                shape = CircleShape,
                containerColor = FabBackgroundDark,
                contentColor = FabIconBlue,
                modifier = Modifier
                    .padding(end = 8.dp, bottom = 8.dp)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add note",
                    tint = FabIconBlue,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        if (notes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No notes yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalItemSpacing = 10.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        onClick = { onNoteClick(note.id) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                            placementSpec = androidx.compose.animation.core.tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                            fadeOutSpec = androidx.compose.animation.core.tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                        )
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }

    if (showAddDialog) {
        var title by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New Note") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                TextButton(
                    onClick = {
                        val trimmedTitle = title.trim()
                        val trimmedContent = content.trim()
                        if (trimmedTitle.isNotEmpty() || trimmedContent.isNotEmpty()) {
                            val finalTitle = trimmedTitle.ifEmpty { "Untitled" }
                            viewModel.addNote(finalTitle, trimmedContent) { id ->
                                showAddDialog = false
                                onNoteClick(id)
                            }
                        } else {
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
