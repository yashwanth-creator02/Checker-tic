package com.leo.checkertic.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.checkertic.analytics.CategoryAnalytics
import com.leo.checkertic.analytics.HeatmapMode
import com.leo.checkertic.ui.theme.AppTheme
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * ============================================================================
 *  PER-CATEGORY INLINE ANALYTICS (feature 2)
 * ============================================================================
 *
 * Stacked beneath the task list in the same scroll — no navigation away.
 *
 * This renders the *same* [CategoryAnalytics] types the global screen renders,
 * produced by the same `AnalyticsEngine` functions with a category filter
 * applied. It is the same data, scoped and re-laid-out: there is no second
 * aggregation implementation anywhere in the codebase, which is why a change
 * to how streaks are counted lands on both screens at once.
 *
 * Two heatmap modes, per the brief:
 *  - **Rolling** — a continuous window ending today. Good for "am I keeping
 *    this up", because today is always in the same place.
 *  - **Month** — a real calendar month, weekday-aligned and steppable. Good
 *    for "what did February look like", because the shape matches a calendar
 *    the user already has a mental model of.
 *
 * Because this lives inside the tasks `LazyColumn`, it is disposed when it
 * scrolls out of view, and the ViewModel's aggregation flow (subscribed with
 * no grace period) stops with it. Scrolling past analytics costs nothing once
 * it is gone.
 */
private val monthTitleFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

@Composable
fun CategoryAnalyticsBlock(
    analytics: CategoryAnalytics,
    month: YearMonth,
    onStepMonth: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val spacing = AppTheme.spacing
    var mode by remember { mutableStateOf(HeatmapMode.ROLLING) }
    var expanded by remember { mutableStateOf(true) }

    Column(modifier = modifier.fillMaxWidth()) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppTheme.radius.sm))
                .clickable { expanded = !expanded }
                .padding(vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Activity",
                color = colors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (analytics.streak.current > 0) {
                    "${analytics.streak.current} ${analytics.streak.unit}"
                } else {
                    "no streak"
                },
                color = if (analytics.streak.activeNow) colors.success else colors.textMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(AppTheme.motion.normalSpec()) +
                expandVertically(AppTheme.motion.normalSpec()),
            exit = fadeOut(AppTheme.motion.fastSpec()) +
                shrinkVertically(AppTheme.motion.fastSpec())
        ) {
            Column {
                SectionCard(
                    title = "Task activity",
                    infoText = "Rolling completion activity and calendar view specifically for tasks in this category.",
                    trailing = {
                        ModeToggle(mode = mode, onSelect = { mode = it })
                    }
                ) {
                    when (mode) {
                        HeatmapMode.ROLLING -> {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                CalendarHeatmap(
                                    series = analytics.rolling,
                                    weekdayAligned = true,
                                    showMonthLabels = true,
                                    scrollToEnd = true,
                                    onDaySelected = { _, _ -> }
                                )
                            }
                        }

                        HeatmapMode.CALENDAR -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                MonthStepper(
                                    month = month,
                                    onStep = onStepMonth
                                )
                                Spacer(Modifier.height(spacing.sm))
                                CalendarHeatmap(
                                    series = analytics.calendarMonth,
                                    weekdayAligned = true,
                                    showMonthLabels = false,
                                    scrollToEnd = false,
                                    onDaySelected = { _, _ -> }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(spacing.md))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        HeatLegend()
                    }
                }

                Spacer(Modifier.height(spacing.md))

                SectionCard(
                    title = "Last 30 days",
                    infoText = "Daily completion volume over the last 30 days for this category, along with your streak records.",
                    trailing = {
                        Text(
                            text = "${analytics.trend.counts.sum()} done",
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                ) {
                    TrendChart(series = analytics.trend)
                    Spacer(Modifier.height(spacing.sm))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Longest run: ${analytics.streak.longest} ${analytics.streak.unit}",
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${analytics.totalInWindow} all-time in view",
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeToggle(mode: HeatmapMode, onSelect: (HeatmapMode) -> Unit) {
    val colors = AppTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeatmapMode.entries.forEach { option ->
            val selected = option == mode
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppTheme.radius.xs))
                    .background(if (selected) colors.accentContainer else colors.surfaceSunken)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = option.label,
                    color = if (selected) colors.onAccentContainer else colors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun MonthStepper(month: YearMonth, onStep: (Long) -> Unit) {
    val colors = AppTheme.colors
    val isCurrentMonth = remember(month) { month >= YearMonth.now() }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onStep(-1L) }, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous month",
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(AppTheme.spacing.sm))
        Text(
            text = month.format(monthTitleFormatter),
            color = colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.width(AppTheme.spacing.sm))
        IconButton(
            onClick = { onStep(1L) },
            enabled = !isCurrentMonth,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next month",
                // Stepping past the current month would show an empty grid of
                // days that haven't happened, which reads as data loss.
                tint = if (isCurrentMonth) colors.textMuted else colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
