package com.leo.checkertic.ui.screens

import android.net.Uri
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.checkertic.data.model.SearchResults
import com.leo.checkertic.data.model.TaskSearchHit
import com.leo.checkertic.ui.components.BackgroundPicker
import com.leo.checkertic.ui.theme.AppTheme
import com.leo.checkertic.ui.viewmodel.CategoryUi
import com.leo.checkertic.ui.viewmodel.TaskUi

/**
 * Bottom sheets and the search results list.
 *
 * A bottom sheet rather than a context menu for long-press actions: there are
 * six of them, several need an icon to be scannable, and the background
 * picker inside the category sheet needs real horizontal room. A dropdown
 * sized for that stops being a dropdown.
 *
 * The sheet edge is the second of the three sanctioned glass surfaces —
 * Material's own `ModalBottomSheet` already draws a translucent scrim and a
 * tonal edge, so it is left to do that rather than being wrapped in a second
 * custom one.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskActionsSheet(
    task: TaskUi,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = AppTheme.colors
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface
    ) {
        Column(modifier = Modifier.padding(bottom = AppTheme.spacing.xl)) {
            SheetHeader(task.title)
            HorizontalDivider(color = colors.hairline)

            SheetAction(
                icon = Icons.Filled.PushPin,
                label = if (task.pinned) "Unpin" else "Pin to top",
                onClick = onTogglePin
            )
            SheetAction(icon = Icons.Outlined.Edit, label = "Rename", onClick = onRename)
            SheetAction(
                icon = Icons.AutoMirrored.Outlined.Send,
                label = "Share",
                onClick = onShare
            )
            SheetAction(
                icon = Icons.Outlined.ContentCopy,
                label = "Copy to clipboard",
                onClick = onCopy
            )
            SheetAction(
                icon = Icons.Outlined.Delete,
                label = "Delete",
                tint = MaterialTheme.colorScheme.error,
                onClick = onDelete
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryActionsSheet(
    category: CategoryUi,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleLock: () -> Unit,
    onShare: () -> Unit,
    onSetBackground: (String?) -> Unit,
    onPickBackground: (Uri) -> Unit
) {
    val colors = AppTheme.colors
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface
    ) {
        Column(modifier = Modifier.padding(bottom = AppTheme.spacing.xl)) {
            SheetHeader(category.name)
            HorizontalDivider(color = colors.hairline)

            SheetAction(
                icon = Icons.Filled.PushPin,
                label = if (category.pinned) "Unpin list" else "Pin list",
                onClick = onTogglePin
            )
            SheetAction(
                icon = if (category.locked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                label = if (category.locked) "Remove lock" else "Lock this list",
                onClick = onToggleLock
            )
            SheetAction(
                icon = Icons.AutoMirrored.Outlined.Send,
                label = "Share as checklist",
                onClick = onShare
            )

            HorizontalDivider(
                color = colors.hairline,
                modifier = Modifier.padding(vertical = AppTheme.spacing.sm)
            )

            BackgroundPicker(
                current = category.background,
                onSelect = onSetBackground,
                onPickFromGallery = onPickBackground,
                modifier = Modifier.padding(horizontal = AppTheme.spacing.lg)
            )
        }
    }
}

@Composable
private fun SheetHeader(title: String) {
    Text(
        text = title,
        color = AppTheme.colors.textPrimary,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(
            horizontal = AppTheme.spacing.lg,
            vertical = AppTheme.spacing.md
        )
    )
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = AppTheme.colors.textPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AppTheme.spacing.lg, vertical = AppTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(AppTheme.sizes.iconMd)
        )
        Spacer(Modifier.width(AppTheme.spacing.md + 2.dp))
        Text(text = label, color = tint, fontSize = 14.sp)
    }
}

/**
 * Search results (feature 4).
 *
 * Tasks and notes in one list with section headers, rather than tabs. With a
 * debounced query the result set is usually small, and a tab bar would hide
 * half the answer behind a tap — the point of searching is to find the thing,
 * not to first decide what kind of thing it was.
 */
@Composable
fun SearchResultsList(
    results: SearchResults,
    onTaskClick: (TaskSearchHit) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val spacing = AppTheme.spacing

    if (results.isEmpty) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Nothing matches \u201C${results.query}\u201D",
                color = colors.textMuted,
                fontSize = 13.sp
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = spacing.screenGutter,
            vertical = spacing.sm
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        if (results.tasks.isNotEmpty()) {
            item(key = "tasks-header") {
                ResultHeader("Tasks · ${results.tasks.size}")
            }
            items(results.tasks, key = { "t-${it.task.id}" }) { hit ->
                ResultRow(
                    title = hit.task.title,
                    subtitle = hit.categoryName,
                    struck = hit.task.completed,
                    onClick = { onTaskClick(hit) }
                )
            }
        }
        if (results.notes.isNotEmpty()) {
            item(key = "notes-header") {
                ResultHeader("Notes · ${results.notes.size}")
            }
            items(results.notes, key = { "n-${it.id}" }) { note ->
                ResultRow(
                    title = note.title,
                    subtitle = note.content.take(80),
                    struck = false,
                    onClick = { }
                )
            }
        }
    }
}

@Composable
private fun ResultHeader(text: String) {
    Text(
        text = text,
        color = AppTheme.colors.textMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = AppTheme.spacing.sm)
    )
}

@Composable
private fun ResultRow(
    title: String,
    subtitle: String,
    struck: Boolean,
    onClick: () -> Unit
) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = AppTheme.spacing.sm)
    ) {
        Text(
            text = title,
            color = if (struck) colors.textMuted else colors.textPrimary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = colors.textMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
