package com.leo.checkertic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.checkertic.data.entity.TaskEntity
import com.leo.checkertic.ui.theme.CompletionGreen
import com.leo.checkertic.ui.theme.SubtleGrayLine
import com.leo.checkertic.ui.theme.TextCompletedDark
import com.leo.checkertic.ui.theme.TextPrimaryDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TaskItem(
    task: TaskEntity,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var blinkPhase by remember(task.id) { mutableIntStateOf(0) } // 0: idle, 1: blink ON, 2: dim

    val backgroundColor = when (blinkPhase) {
        1 -> Color(0xFF162E1E) // Blinking green background matching widget
        2 -> Color(0x33162E1E) // Dimmed
        else -> MaterialTheme.colorScheme.surface
    }

    val accentColor = when (blinkPhase) {
        1 -> CompletionGreen
        2 -> CompletionGreen.copy(alpha = 0.3f)
        else -> if (task.completed) CompletionGreen else SubtleGrayLine
    }

    val textColor = when (blinkPhase) {
        1 -> Color(0xFF86EFAC)
        2 -> Color(0x4486EFAC)
        else -> if (task.completed) TextCompletedDark else TextPrimaryDark
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .clickable(enabled = blinkPhase == 0) {
                if (!task.completed) {
                    coroutineScope.launch {
                        blinkPhase = 1 // Flash green
                        delay(140)
                        blinkPhase = 2 // Dim
                        delay(120)
                        onToggle()
                    }
                } else {
                    onToggle()
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left vertical ticker bar (20dp)
        Box(
            modifier = Modifier
                .width(20.dp)
                .fillMaxHeight()
                .background(accentColor)
        )

        // Title with blinking green highlight (no strikethrough line)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = task.title,
                color = textColor,
                fontSize = 14.sp,
                fontWeight = if (blinkPhase == 1) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Right vertical ticker bar (20dp)
        Box(
            modifier = Modifier
                .width(20.dp)
                .fillMaxHeight()
                .background(accentColor)
        )
    }
}
