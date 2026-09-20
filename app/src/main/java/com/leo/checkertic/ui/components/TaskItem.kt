package com.leo.checkertic.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.checkertic.ui.theme.AppTheme
import com.leo.checkertic.ui.viewmodel.TaskUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A task row.
 *
 * Keeps the original two-ticker visual language and the green flash on
 * completion — that is the app's signature interaction, and the brief said
 * settled things stay settled. What changed:
 *
 *  - Every colour and size now comes from [AppTheme] instead of literals, so
 *    the row re-skins with the rest of the app. The flash used two hardcoded
 *    dark-mode hex values that looked wrong in light mode; it is now derived
 *    from the theme's success colour.
 *  - The three flash colours are animated rather than snapped. Stepping
 *    between hard values reads as a stutter on a 120 Hz display.
 *  - Long-press opens the actions sheet instead of doing nothing.
 *  - Pin and reminder state are shown inline.
 *
 * The parameter is a [TaskUi], not an entity: the title arrives already
 * decrypted, so no cryptography ever happens during composition.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskItem(
    task: TaskUi,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val sizes = AppTheme.sizes
    val motion = AppTheme.motion
    val scope = rememberCoroutineScope()

    // 0 idle, 1 flash, 2 dim. Keyed by id so recycling a row in the lazy list
    // can never leave a new task showing the previous one's flash.
    var blinkPhase by remember(task.id) { mutableIntStateOf(0) }

    val flashSurface = colors.success.copy(alpha = 0.16f)

    val targetBackground = when (blinkPhase) {
        1 -> flashSurface
        2 -> flashSurface.copy(alpha = 0.06f)
        else -> colors.surface
    }
    val targetAccent = when (blinkPhase) {
        1 -> colors.success
        2 -> colors.success.copy(alpha = 0.3f)
        else -> if (task.completed) colors.success else colors.surfaceRaised
    }
    val targetText = when (blinkPhase) {
        1 -> colors.success
        2 -> colors.success.copy(alpha = 0.35f)
        else -> if (task.completed) colors.textMuted else colors.textPrimary
    }

    val background by animateColorAsState(targetBackground, motion.fastSpec(), label = "task-bg")
    val accent by animateColorAsState(targetAccent, motion.fastSpec(), label = "task-accent")
    val textColor by animateColorAsState(targetText, motion.fastSpec(), label = "task-text")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(sizes.taskRowHeight)
            .clip(RoundedCornerShape(AppTheme.radius.md))
            .background(background)
            .combinedClickable(
                enabled = blinkPhase == 0,
                onClick = {
                    if (!task.completed) {
                        scope.launch {
                            blinkPhase = 1
                            delay(motion.fast.toLong())
                            blinkPhase = 2
                            delay(motion.instant.toLong())
                            onToggle()
                        }
                    } else {
                        onToggle()
                    }
                },
                onLongClick = onLongPress
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(sizes.tickerWidth)
                .fillMaxHeight()
                .background(accent)
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = AppTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (task.pinned) {
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = "Pinned",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(AppTheme.spacing.sm))
            }
            Text(
                text = task.title,
                color = textColor,
                fontSize = 14.sp,
                fontWeight = if (blinkPhase == 1 || task.pinned) {
                    FontWeight.Medium
                } else {
                    FontWeight.Normal
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (task.hasReminder) {
                Spacer(Modifier.width(AppTheme.spacing.sm))
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = "Has a reminder",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .width(sizes.tickerWidth)
                .fillMaxHeight()
                .background(accent)
        )
    }
}
