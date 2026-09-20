package com.leo.checkertic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.checkertic.analytics.AnalyticsEngine
import com.leo.checkertic.analytics.AnalyticsWindow
import com.leo.checkertic.analytics.CategoryStat
import com.leo.checkertic.core.time.PeriodKeys
import com.leo.checkertic.ui.components.CalendarHeatmap
import com.leo.checkertic.ui.components.CategoryComparisonChart
import com.leo.checkertic.ui.components.HeatLegend
import com.leo.checkertic.ui.components.SectionCard
import com.leo.checkertic.ui.components.SparklineChart
import com.leo.checkertic.ui.components.StatTile
import com.leo.checkertic.ui.components.TimeOfDayHeatmap
import com.leo.checkertic.ui.theme.AppTheme
import com.leo.checkertic.ui.viewmodel.AnalyticsViewModel
import kotlin.math.roundToInt

/**
 * ============================================================================
 *  GLOBAL ANALYTICS (feature 1)
 * ============================================================================
 *
 * A peer to Tasks / Notes / Settings, covering the app as a whole.
 *
 * ## Why this is a LazyColumn with one item per card
 *
 * A `Column` inside a `verticalScroll` would compose and measure every chart
 * on screen entry, including the ones below the fold, and keep them composed
 * forever. With a `LazyColumn` the time-of-day matrix and the category
 * comparison do not exist until they scroll into view, and their aggregation
 * subscriptions stop when they scroll out. On a mid-range phone that is the
 * difference between this screen opening in one frame and opening in four.
 *
 * Every number rendered here was computed on `Dispatchers.Default` in
 * [AnalyticsViewModel] and arrives as one immutable snapshot, so this file
 * contains no arithmetic beyond formatting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val window by viewModel.window.collectAsState()
    val filter by viewModel.categoryFilter.collectAsState()

    val colors = AppTheme.colors
    val spacing = AppTheme.spacing

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Analytics", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = colors.textPrimary
                )
            )
        },
        containerColor = colors.root,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(
                start = spacing.screenGutter,
                end = spacing.screenGutter,
                top = spacing.sm,
                bottom = spacing.fabClearance
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {

            // -- Window selector -------------------------------------------
            item(key = "window") {
                WindowSelector(
                    current = window,
                    onSelect = viewModel::setWindow,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // -- Headline stats --------------------------------------------
            item(key = "headline") {
                SectionCard(
                    title = "Overview",
                    infoText = "Key metrics for your selected timeframe: tasks completed today, your longest active streak, and overall completion rate."
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatTile(
                            value = state.completedToday.toString(),
                            label = "Done today",
                            accent = colors.success,
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            value = if (state.bestCurrentStreak > 0) {
                                state.bestCurrentStreak.toString()
                            } else {
                                "—"
                            },
                            label = "Best streak",
                            accent = colors.accent,
                            caption = state.bestCurrentStreakCategory
                                ?.let { "$it · ${state.bestStreakUnit}" },
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            value = "${(state.completionRate * 100).roundToInt()}%",
                            label = "Active days",
                            caption = "${state.totalInWindow} in ${window.label}",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // -- Category filter -------------------------------------------
            if (state.categories.size > 1) {
                item(key = "filter") {
                    CategoryFilterRow(
                        categories = state.categories,
                        selected = filter,
                        onSelect = viewModel::setCategoryFilter
                    )
                }
            }

            // -- Calendar heatmap ------------------------------------------
            item(key = "calendar") {
                SectionCard(
                    title = "Completions",
                    infoText = "Daily task completion activity. Darker copper squares indicate higher task volume. Tap any square to view the date and count.",
                    trailing = { HeatLegend() }
                ) {
                    CalendarHeatmap(
                        modifier = Modifier.fillMaxWidth(),
                        series = state.calendar,
                        weekdayAligned = true,
                        scrollToEnd = true,
                        onDaySelected = { _, _ -> }
                    )
                }
            }

            // -- Time of day x day of week ---------------------------------
            item(key = "timeofday") {
                SectionCard(
                    title = "When you finish things",
                    infoText = "Productivity distribution across hours of the day (12 AM midnight to 11 PM) and days of the week. Darker squares highlight your peak productivity times."
                ) {
                    TimeOfDayHeatmap(matrix = state.timeOfDay)
                }
            }

            // -- Per-category streaks --------------------------------------
            item(key = "streaks") {
                SectionCard(
                    title = "Streaks",
                    infoText = "Consecutive completion tracking per category, respecting each category's recurrence rule (daily, weekly, or custom intervals)."
                ) {
                    if (state.categories.isEmpty()) {
                        Text(
                            text = "No categories yet.",
                            color = colors.textMuted,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            state.categories.forEach { StreakRow(it) }
                        }
                    }
                }
            }

            // -- Category comparison ---------------------------------------
            item(key = "comparison") {
                SectionCard(
                    title = "By category · ${window.label}",
                    infoText = "Breakdown of completed tasks across categories in the selected window. Tap any category bar to filter the whole screen."
                ) {
                    CategoryComparisonChart(
                        stats = state.categories,
                        onCategoryClick = { id ->
                            viewModel.setCategoryFilter(if (filter == id) null else id)
                        }
                    )
                }
            }

            // -- Notes activity --------------------------------------------
            item(key = "notes") {
                SectionCard(
                    title = "Notes activity",
                    infoText = "Timeline of notes created or edited over the selected timeframe, visualizing your documentation momentum."
                ) {
                    SparklineChart(series = state.noteActivity)
                    Spacer(Modifier.height(spacing.sm))
                    Text(
                        text = "Notes created or edited over the last ${window.days} days.",
                        color = colors.textMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun WindowSelector(
    current: AnalyticsWindow,
    onSelect: (AnalyticsWindow) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)
    ) {
        AnalyticsWindow.entries.forEach { window ->
            val selected = window == current
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(AppTheme.radius.md))
                    .background(if (selected) colors.accentContainer else colors.surface)
                    .clickable { onSelect(window) }
                    .padding(vertical = AppTheme.spacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = window.label,
                    color = if (selected) colors.onAccentContainer else colors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    categories: List<CategoryStat>,
    selected: Long?,
    onSelect: (Long?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)
    ) {
        FilterChip(label = "All", selected = selected == null) { onSelect(null) }
        categories.forEach { stat ->
            FilterChip(label = stat.name, selected = selected == stat.categoryId) {
                onSelect(stat.categoryId)
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(AppTheme.radius.md))
            .background(if (selected) colors.accentContainer else colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = AppTheme.spacing.md, vertical = AppTheme.spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) colors.onAccentContainer else colors.textSecondary,
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}

/**
 * One category's streak.
 *
 * Shows the recurrence label next to the number because "12" means something
 * different for a daily list than a weekly one, and the unit is the only
 * thing that makes a streak comparable across categories.
 */
@Composable
private fun StreakRow(stat: CategoryStat) {
    val colors = AppTheme.colors
    val recurrenceLabel = remember(stat.recurrenceType, stat.recurrenceCustomDays) {
        PeriodKeys.label(stat.recurrenceType, stat.recurrenceCustomDays)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stat.name,
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = recurrenceLabel,
                color = colors.textMuted,
                fontSize = 10.sp
            )
        }
        Spacer(Modifier.width(AppTheme.spacing.sm))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${stat.streak.current} ${stat.streak.unit}",
                // Dimmed while the current period is still empty: the streak
                // is alive but hasn't been fed today, and that distinction is
                // the whole motivational point of showing it.
                color = if (stat.streak.activeNow) colors.success else colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "best ${stat.streak.longest}",
                color = colors.textMuted,
                fontSize = 10.sp
            )
        }
    }
}
