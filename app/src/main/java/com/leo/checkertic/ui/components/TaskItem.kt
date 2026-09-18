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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@Composable
fun TaskItem(
    task: TaskEntity,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isCompleting by remember(task.id) { mutableStateOf(false) }

    val accentColor = if (task.completed || isCompleting) CompletionGreen else SubtleGrayLine
    val textColor = if (task.completed || isCompleting) TextCompletedDark else TextPrimaryDark

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = !isCompleting) {
                isCompleting = true
                onToggle()
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

        // Title with instant strikethrough if completed
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
                textDecoration = if (task.completed || isCompleting) TextDecoration.LineThrough else TextDecoration.None,
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
