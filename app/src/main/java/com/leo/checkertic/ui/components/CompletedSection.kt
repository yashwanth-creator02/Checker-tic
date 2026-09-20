package com.leo.checkertic.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.checkertic.ui.theme.AppTheme
import com.leo.checkertic.ui.theme.GlassBottomBoundary

/**
 * The completed-tasks reveal (feature 3).
 *
 * Completed work collapses into this bar, hidden by default, so the active
 * list stays the whole screen. Two ways to open it, because the brief asked
 * for the affordance and the gesture:
 *
 *  - Tap the chevron (or anywhere on the bar). Discoverable, and the only
 *    thing that works for a user navigating by accessibility services.
 *  - Swipe up on the bar. Faster once you know it's there.
 *
 * This is one of the three places translucency is allowed: it is a boundary
 * between the list and the history underneath it, which is exactly the
 * "glass at the edges" case. The rows it reveals are flat and opaque like
 * every other row.
 */
@Composable
fun CompletedRevealBar(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val spacing = AppTheme.spacing

    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = AppTheme.motion.normalSpec(),
        label = "reveal-chevron"
    )

    GlassBottomBoundary(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.radius.md))
            .clickable(onClick = onToggle)
            .pointerInput(expanded) {
                // A drag past the threshold toggles, then consumes the rest
                // of the gesture. Without the consumed flag a long swipe
                // would toggle repeatedly as the finger kept moving.
                var consumed = false
                detectVerticalDragGestures(
                    onDragEnd = { consumed = false },
                    onDragCancel = { consumed = false }
                ) { _, dragAmount ->
                    if (consumed) return@detectVerticalDragGestures
                    if (!expanded && dragAmount < -DRAG_THRESHOLD_PX) {
                        consumed = true
                        onToggle()
                    } else if (expanded && dragAmount > DRAG_THRESHOLD_PX) {
                        consumed = true
                        onToggle()
                    }
                }
            }
            .semantics {
                contentDescription = if (expanded) {
                    "Hide completed tasks"
                } else {
                    "Show $count completed tasks"
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm + 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (expanded) "Hide completed" else "Completed ($count)",
                color = colors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(spacing.xs))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp).rotate(rotation)
            )
        }
    }
}

/** ~6 dp at normal density. Low enough to feel responsive, high enough not to fire on a tap. */
private const val DRAG_THRESHOLD_PX = 18f
