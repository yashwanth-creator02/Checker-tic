package com.leo.checkertic.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.checkertic.data.entity.TaskEntity
import com.leo.checkertic.ui.theme.CompletionGreen
import com.leo.checkertic.ui.theme.SubtleGrayLine
import com.leo.checkertic.ui.theme.TextCompletedDark
import com.leo.checkertic.ui.theme.TextPrimaryDark
import kotlinx.coroutines.launch

@Composable
fun TaskItem(
    task: TaskEntity,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isCompleting by remember(task.id) { mutableStateOf(false) }
    val lineProgress = remember(task.id) { Animatable(0f) }
    val itemAlpha = remember(task.id) { Animatable(1f) }

    val accentColor = if (task.completed || isCompleting) CompletionGreen else SubtleGrayLine
    val textColor = if (task.completed || isCompleting) TextCompletedDark else TextPrimaryDark

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer { alpha = itemAlpha.value }
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = !isCompleting) {
                if (!task.completed) {
                    isCompleting = true
                    coroutineScope.launch {
                        // Line passes across the task text
                        lineProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 200, easing = LinearEasing)
                        )
                        // Smoothly fade out the entire card
                        itemAlpha.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
                        )
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

        // Title with animated strikethrough line
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
                fontWeight = FontWeight.Normal,
                textDecoration = if (task.completed && !isCompleting) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.drawWithContent {
                    drawContent()
                    if (lineProgress.value > 0f) {
                        val strokeWidth = 2.dp.toPx()
                        val y = size.height / 2f
                        drawLine(
                            color = CompletionGreen,
                            start = Offset(0f, y),
                            end = Offset(size.width * lineProgress.value, y),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }
                }
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
