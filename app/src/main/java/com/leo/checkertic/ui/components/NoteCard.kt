package com.leo.checkertic.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.checkertic.ui.theme.AppTheme
import com.leo.checkertic.ui.viewmodel.NoteUi

/**
 * A note card in the staggered grid.
 *
 * Interior stays flat and opaque per the design rule — the only translucency
 * on a card is the scrim over a background image, and that exists for
 * legibility rather than decoration.
 *
 * The snippet was already truncated in the ViewModel, so a long note costs
 * the same to measure as a short one.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: NoteUi,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val spacing = AppTheme.spacing
    val hasBackground = note.background != null
    val light = isLightBackground(note.background)

    val titleColor = when {
        !hasBackground -> colors.textPrimary
        light -> Color(0xFF111827)
        else -> Color(0xFFF3F4F6)
    }
    val bodyColor = when {
        !hasBackground -> colors.textSecondary
        light -> Color(0xFF374151)
        else -> Color(0xFFD1D5DB)
    }

    val dotColor = colors.accentCycle[
        (note.id % colors.accentCycle.size).toInt().coerceAtLeast(0)
    ]

    BackgroundSurface(
        reference = note.background,
        target = com.leo.checkertic.core.image.ImageStore.Target.THUMB,
        fallback = colors.surface,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.radius.md))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (hasBackground) AppTheme.sizes.noteBackgroundHeight else 0.dp)
                .padding(spacing.md + 2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(Modifier.width(spacing.sm))
                Text(
                    text = note.title,
                    color = titleColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (note.pinned) {
                    Spacer(Modifier.width(spacing.xs))
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = "Pinned",
                        tint = bodyColor,
                        modifier = Modifier.size(12.dp)
                    )
                }
                if (note.locked) {
                    Spacer(Modifier.width(spacing.xs))
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "Locked",
                        tint = bodyColor,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            if (note.obscured) {
                Spacer(Modifier.height(spacing.sm))
                Text(
                    text = "Locked — unlock to read",
                    color = bodyColor,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            } else if (note.snippet.isNotBlank()) {
                Spacer(Modifier.height(spacing.xs + 2.dp))
                Text(
                    text = note.snippet,
                    color = bodyColor,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
