package com.leo.checkertic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.checkertic.R
import com.leo.checkertic.data.entity.CategoryEntity
import com.leo.checkertic.data.entity.TaskEntity
import com.leo.checkertic.ui.theme.CopperContainer
import com.leo.checkertic.ui.theme.RadiantCopperText
import com.leo.checkertic.ui.theme.SatinCopper
import com.leo.checkertic.ui.theme.TextPrimaryDark
import com.leo.checkertic.ui.theme.TextSecondaryDark

/**
 * Visual in-app preview of the Flip home screen Glance widget.
 *
 * Renders the authentic Obsidian Copper theme, sidebar dock, category chips,
 * interactive ticker task rows, and floating quick-add button.
 */
@Composable
fun WidgetPreview(
    tasks: List<TaskEntity>,
    categories: List<CategoryEntity>,
    modifier: Modifier = Modifier
) {
    val activeTasks = tasks.filter { !it.completed }
    val firstTaskTitle = activeTasks.firstOrNull()?.title ?: "Review daily priorities"
    val secondTaskTitle = activeTasks.drop(1).firstOrNull()?.title ?: "Daily check-in"

    val displayCategories = if (categories.isNotEmpty()) {
        categories.take(3).map { it.name }
    } else {
        listOf("All", "Tasks", "Personal")
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(164.dp)
            .background(Color(0xFF141416), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF27272A), RoundedCornerShape(16.dp))
            .padding(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Sidebar Dock
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(36.dp)
                    .background(Color(0xFF101012), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF27272A), RoundedCornerShape(10.dp))
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Active Tasks Tab
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(CopperContainer, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_widget_tasks),
                        contentDescription = null,
                        tint = RadiantCopperText,
                        modifier = Modifier.size(15.dp)
                    )
                }

                Spacer(Modifier.height(6.dp))

                // Inactive Notes Tab
                Box(
                    modifier = Modifier.size(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_widget_notes),
                        contentDescription = null,
                        tint = TextSecondaryDark,
                        modifier = Modifier.size(15.dp)
                    )
                }

                Spacer(Modifier.weight(1f))

                // Open App shortcut
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_open_app),
                        contentDescription = null,
                        tint = TextSecondaryDark,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Main Widget Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Category Chips Strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        displayCategories.forEachIndexed { index, name ->
                            val isSelected = index == 0
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) CopperContainer else Color.Transparent)
                                    .then(
                                        if (isSelected) Modifier.border(1.dp, Color(0xFF4A2818), RoundedCornerShape(6.dp))
                                        else Modifier
                                    )
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = name,
                                    color = if (isSelected) RadiantCopperText else TextSecondaryDark,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Task Row 1
                    WidgetPreviewTaskRow(title = firstTaskTitle)

                    Spacer(Modifier.height(5.dp))

                    // Task Row 2
                    WidgetPreviewTaskRow(title = secondTaskTitle)
                }

                // Quick-Add Floating Action Button
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(28.dp)
                        .background(CopperContainer, CircleShape)
                        .border(1.dp, SatinCopper, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_widget_add),
                        contentDescription = null,
                        tint = RadiantCopperText,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetPreviewTaskRow(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(Color(0xFF1E1E22), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF2C2C32), RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left ticker
        Box(
            modifier = Modifier
                .width(16.dp)
                .fillMaxHeight()
                .background(Color(0xFF27272A), RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "<",
                color = TextSecondaryDark,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Title
        Text(
            text = title,
            color = TextPrimaryDark,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
        )

        // Right ticker
        Box(
            modifier = Modifier
                .width(16.dp)
                .fillMaxHeight()
                .background(Color(0xFF27272A), RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = ">",
                color = TextSecondaryDark,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
