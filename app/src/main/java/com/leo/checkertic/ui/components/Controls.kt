package com.leo.checkertic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.checkertic.data.entity.ReminderEntity
import com.leo.checkertic.data.model.SearchScope
import com.leo.checkertic.data.model.SortMode
import com.leo.checkertic.ui.theme.AppTheme
import java.time.LocalTime

/**
 * Inline search field (feature 4).
 *
 * Scope is a toggle chip rather than a setting, because which scope you want
 * depends on the search, not on a preference: "where did I put that" is
 * global, "is this already on today's list" is local. One tap switches, and
 * the current state is always visible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InlineSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    scope: SearchScope? = null,
    onToggleScope: (() -> Unit)? = null,
    placeholder: String = "Search"
) {
    val colors = AppTheme.colors
    val spacing = AppTheme.spacing
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenGutter, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(placeholder, fontSize = 14.sp) },
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(AppTheme.sizes.iconMd)
                )
            },
            trailingIcon = {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close search",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(AppTheme.sizes.iconMd)
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(AppTheme.radius.md),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
        )

        if (scope != null && onToggleScope != null) {
            Spacer(Modifier.width(spacing.sm))
            ScopeChip(scope = scope, onClick = onToggleScope)
        }
    }
}

@Composable
private fun ScopeChip(scope: SearchScope, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(AppTheme.radius.md))
            .background(colors.accentContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = AppTheme.spacing.md, vertical = AppTheme.spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = scope.label,
            color = colors.onAccentContainer,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Sort picker (feature 6). Pinning is deliberately absent — it composes with every mode. */
@Composable
fun SortMenuButton(
    current: SortMode,
    onSelect: (SortMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    var open by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { open = true }) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Sort,
                contentDescription = "Sort",
                tint = colors.textSecondary,
                modifier = Modifier.size(AppTheme.sizes.iconMd)
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = mode.label,
                            fontWeight = if (mode == current) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onSelect(mode)
                        open = false
                    }
                )
            }
        }
    }
}

/**
 * Shown in place of a locked category's list, or a locked note's body.
 *
 * Says what is hidden and offers the one action that helps. Deliberately not
 * a dialog: a modal on entry would fire every time the user tapped that
 * category tab, including by accident.
 */
@Composable
fun LockedPanel(
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val spacing = AppTheme.spacing

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.height(spacing.md))
        Text(text = title, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = subtitle,
            color = colors.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = spacing.lg)
        )
        Spacer(Modifier.height(spacing.lg))
        Button(onClick = onAction) { Text(actionLabel) }
    }
}

/**
 * Reminder editor (feature 10).
 *
 * A plain hour/minute entry rather than the Material time picker, because
 * this dialog also has to carry the repeat cadence and the exact-alarm
 * caveat, and stacking a full clock face on top of that makes a dialog that
 * doesn't fit on a small phone.
 *
 * [exactAvailable] surfaces the Android 14+ reality: without the "Alarms &
 * reminders" special access, reminders still arrive, just not to the second.
 * Saying so is better than a reminder that quietly drifts.
 */
@Composable
fun ReminderDialog(
    title: String,
    existing: ReminderEntity?,
    exactAvailable: Boolean,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onSave: (LocalTime, String, Int) -> Unit,
    onRequestExact: (() -> Unit)? = null
) {
    val colors = AppTheme.colors
    val spacing = AppTheme.spacing

    val initial = remember(existing) {
        existing?.let {
            java.time.Instant.ofEpochMilli(it.triggerAt)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalTime()
        } ?: LocalTime.of(9, 0)
    }

    var hour by remember { mutableIntStateOf(initial.hour) }
    var minute by remember { mutableIntStateOf(initial.minute) }
    var repeat by remember {
        mutableStateOf(existing?.repeatMode ?: ReminderEntity.REPEAT_DAILY)
    }
    var interval by remember {
        mutableIntStateOf(existing?.repeatIntervalDays?.takeIf { it > 0 } ?: 3)
    }

    val repeatOptions = remember {
        listOf(
            ReminderEntity.REPEAT_NONE to "Once",
            ReminderEntity.REPEAT_DAILY to "Every day",
            ReminderEntity.REPEAT_WEEKLY to "Every week",
            ReminderEntity.REPEAT_MONTHLY to "Every month",
            ReminderEntity.REPEAT_CUSTOM to "Every N days"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumberField(
                        value = hour,
                        onValueChange = { hour = it.coerceIn(0, 23) },
                        label = "Hour",
                        modifier = Modifier.width(88.dp)
                    )
                    Spacer(Modifier.width(spacing.sm))
                    Text(":", color = colors.textSecondary)
                    Spacer(Modifier.width(spacing.sm))
                    NumberField(
                        value = minute,
                        onValueChange = { minute = it.coerceIn(0, 59) },
                        label = "Minute",
                        modifier = Modifier.width(88.dp)
                    )
                }

                Spacer(Modifier.height(spacing.xs))

                repeatOptions.forEach { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { repeat = mode }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = repeat == mode, onClick = { repeat = mode })
                        Spacer(Modifier.width(spacing.xs))
                        Text(label, fontSize = 14.sp)
                    }
                }

                if (repeat == ReminderEntity.REPEAT_CUSTOM) {
                    NumberField(
                        value = interval,
                        onValueChange = { interval = it.coerceIn(1, 365) },
                        label = "Days between",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (!exactAvailable) {
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = "Reminders will arrive within a few minutes of this time. " +
                            "Grant \"Alarms & reminders\" for to-the-minute delivery.",
                        color = colors.textMuted,
                        fontSize = 11.sp
                    )
                    if (onRequestExact != null) {
                        TextButton(onClick = onRequestExact) { Text("Open settings", fontSize = 12.sp) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(LocalTime.of(hour, minute), repeat, interval) }
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(
                        onClick = onClear,
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Remove") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumberField(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    TextField(
        value = value.toString().padStart(2, '0'),
        onValueChange = { text -> text.filter { it.isDigit() }.toIntOrNull()?.let(onValueChange) },
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        keyboardActions = KeyboardActions(),
        modifier = modifier
    )
}
