package com.leo.checkertic.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.checkertic.analytics.DaySeries
import com.leo.checkertic.analytics.TimeOfDayMatrix
import com.leo.checkertic.ui.theme.AppTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.floor

/**
 * ============================================================================
 *  HEATMAPS
 * ============================================================================
 *
 * Both heatmaps here are a **single `Canvas`** drawing rectangles in a loop,
 * not a grid of composables.
 *
 * That is the whole performance story of the analytics screen. A year-long
 * contribution graph is 365 cells; the time-of-day matrix is 168. Built the
 * obvious way — a `Row` of `Column`s of `Box`es — that is 500+ layout nodes
 * to measure, place and recompose, inside a screen the user scrolls. Drawn
 * this way it is one node, one measure pass, and a draw loop of `drawRect`
 * calls that allocates nothing per frame.
 *
 * Colour lookup is a precomputed ramp index rather than `lerp` per cell, and
 * the series carries its own `max` so normalising never rescans the array.
 */

private val monthLabelFormatter = DateTimeFormatter.ofPattern("MMM")
private val tooltipFormatter = DateTimeFormatter.ofPattern("d MMM")

/**
 * Maps a 0..1 density onto the theme's 5-step ramp.
 *
 * Discrete steps rather than a continuous gradient, deliberately: a GitHub
 * graph is readable because "two days" and "five days" are visibly different
 * swatches. A smooth gradient turns the whole thing into indistinguishable
 * mush at these cell sizes.
 */
private fun rampColor(ramp: List<Color>, count: Int, intensity: Float): Color {
    if (count <= 0) return ramp[0]
    val step = when {
        intensity <= 0.25f -> 1
        intensity <= 0.5f -> 2
        intensity <= 0.75f -> 3
        else -> 4
    }
    return ramp[step.coerceIn(0, ramp.lastIndex)]
}

/**
 * GitHub-style contribution grid: weeks run left to right, days top to
 * bottom within a column.
 *
 * The weekday column (M, T, W, T, F, S, S) remains pinned on the left while the
 * month labels and grid cells scroll horizontally.
 *
 * @param weekdayAligned when true the first column is padded so rows line up
 *   with real weekdays — what the calendar-month mode needs. When false the
 *   grid simply starts at [series]`.startDate`, which is what a rolling
 *   window wants.
 */
@Composable
fun CalendarHeatmap(
    series: DaySeries,
    modifier: Modifier = Modifier,
    weekdayAligned: Boolean = true,
    cellSize: Dp = AppTheme.sizes.heatCell,
    cellGap: Dp = AppTheme.sizes.heatCellGap,
    showMonthLabels: Boolean = true,
    scrollState: ScrollState? = null,
    scrollToEnd: Boolean = true,
    onDaySelected: ((LocalDate, Int) -> Unit)? = null
) {
    val colors = AppTheme.colors
    val density = LocalDensity.current
    var selected by remember(series) { mutableStateOf<Int?>(null) }

    if (series.size == 0) {
        EmptyChartHint(text = "No completions yet", modifier = modifier)
        return
    }

    // Monday = 0, matching the row order and the ISO weeks streaks use.
    val leadingBlanks = if (weekdayAligned) {
        (series.startDate.dayOfWeek.value - 1).coerceIn(0, 6)
    } else {
        0
    }
    val totalCells = leadingBlanks + series.size
    val columns = (totalCells + 6) / 7

    val cellPx = with(density) { cellSize.toPx() }
    val gapPx = with(density) { cellGap.toPx() }
    val radiusPx = with(density) { 2.dp.toPx() }
    val gridHeight = cellSize * 7 + cellGap * 6
    val gridWidth = cellSize * columns + cellGap * (columns - 1).coerceAtLeast(0)
    val gutterWidth = 14.dp
    val gutterSpacer = 6.dp

    val internalScrollState = rememberScrollState()
    val effectiveScrollState = scrollState ?: internalScrollState

    if (scrollToEnd) {
        LaunchedEffect(series, effectiveScrollState.maxValue) {
            if (effectiveScrollState.maxValue > 0) {
                effectiveScrollState.scrollTo(effectiveScrollState.maxValue)
            }
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = if (columns <= 6) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.wrapContentWidth()
        ) {
            // Pinned weekday gutter (stays fixed during horizontal scrolling)
            Column {
                if (showMonthLabels) {
                    Spacer(Modifier.height(18.dp + AppTheme.spacing.xs))
                }
                WeekdayGutter(
                    cellSize = cellSize,
                    cellGap = cellGap,
                    gutterWidth = gutterWidth
                )
            }

            Spacer(Modifier.width(gutterSpacer))

            // Horizontally scrolling grid + month labels
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .horizontalScroll(effectiveScrollState)
            ) {
                Column(modifier = Modifier.padding(end = AppTheme.spacing.sm)) {
                    if (showMonthLabels) {
                        MonthLabelRow(
                            series = series,
                            leadingBlanks = leadingBlanks,
                            columns = columns,
                            cellSize = cellSize,
                            cellGap = cellGap,
                            startPadding = 0.dp
                        )
                        Spacer(Modifier.height(AppTheme.spacing.xs))
                    }

                    Canvas(
                        modifier = Modifier
                            .width(gridWidth)
                            .height(gridHeight)
                            .then(
                                if (onDaySelected == null) Modifier else Modifier.pointerInput(series) {
                                    detectTapGestures { offset ->
                                        val column = floor(offset.x / (cellPx + gapPx)).toInt()
                                        val row = floor(offset.y / (cellPx + gapPx)).toInt()
                                        val index = column * 7 + row - leadingBlanks
                                        if (index in 0 until series.size) {
                                            selected = index
                                            onDaySelected(series.dateAt(index), series.counts[index])
                                        }
                                    }
                                }
                            )
                    ) {
                        // One pass, no allocation inside the loop. `ramp` and the
                        // pixel metrics are all hoisted above it.
                        val ramp = colors.heatRamp
                        for (index in 0 until series.size) {
                            val cellIndex = index + leadingBlanks
                            val column = cellIndex / 7
                            val row = cellIndex % 7
                            val count = series.counts[index]
                            val color = rampColor(ramp, count, series.intensityAt(index))
                            val topLeft = Offset(column * (cellPx + gapPx), row * (cellPx + gapPx))
                            val cellSizeObj = Size(cellPx, cellPx)
                            val cornerRadiusObj = CornerRadius(radiusPx, radiusPx)

                            drawRoundRect(
                                color = color,
                                topLeft = topLeft,
                                size = cellSizeObj,
                                cornerRadius = cornerRadiusObj
                            )

                            // Subtle hairline frame around empty cells for clean structure
                            if (count <= 0) {
                                drawRoundRect(
                                    color = colors.hairline.copy(alpha = 0.4f),
                                    topLeft = topLeft,
                                    size = cellSizeObj,
                                    cornerRadius = cornerRadiusObj,
                                    style = Stroke(
                                        width = with(density) { 0.6.dp.toPx() }
                                    )
                                )
                            }
                        }
                        selected?.let { index ->
                            val cellIndex = index + leadingBlanks
                            drawRoundRect(
                                color = colors.accent,
                                topLeft = Offset(
                                    (cellIndex / 7) * (cellPx + gapPx),
                                    (cellIndex % 7) * (cellPx + gapPx)
                                ),
                                size = Size(cellPx, cellPx),
                                cornerRadius = CornerRadius(radiusPx, radiusPx),
                                style = Stroke(
                                    width = with(density) { 1.5.dp.toPx() }
                                )
                            )
                        }
                    }
                }
            }
        }

        selected?.let { index ->
            Spacer(Modifier.height(AppTheme.spacing.sm))
            val count = series.counts[index]
            Text(
                text = "${series.dateAt(index).format(tooltipFormatter)} — " +
                    if (count == 1) "1 completion" else "$count completions",
                color = colors.textSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun WeekdayGutter(
    cellSize: Dp,
    cellGap: Dp,
    gutterWidth: Dp = 14.dp
) {
    val colors = AppTheme.colors
    // Mark every single row with weekday initial: Mon, Tue, Wed, Thu, Fri, Sat, Sun
    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
    Column(verticalArrangement = Arrangement.spacedBy(cellGap)) {
        dayLabels.forEach { label ->
            Box(
                modifier = Modifier
                    .height(cellSize)
                    .width(gutterWidth),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = label,
                    color = colors.textSecondary,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 9.sp,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(
                            includeFontPadding = false
                        )
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun MonthLabelRow(
    series: DaySeries,
    leadingBlanks: Int,
    columns: Int,
    cellSize: Dp,
    cellGap: Dp,
    startPadding: Dp
) {
    val colors = AppTheme.colors
    // A label is placed at the first column whose week contains a 1st of the
    // month, ensuring at least 3 columns clearance to prevent overlapping text.
    val labels = remember(series, columns, leadingBlanks) {
        val out = arrayOfNulls<String>(columns)
        var lastMonth = -1
        var lastPlacedCol = -4

        for (index in 0 until series.size) {
            val date = series.dateAt(index)
            if (date.monthValue != lastMonth) {
                val column = (index + leadingBlanks) / 7
                if (column < columns) {
                    var daysInThisMonth = 0
                    for (forward in index until series.size) {
                        if (series.dateAt(forward).monthValue == date.monthValue) {
                            daysInThisMonth++
                        } else {
                            break
                        }
                    }
                    // Only label if there are at least 14 days of this month in the series
                    // and it is at least 3 columns apart from the previous label.
                    if (daysInThisMonth >= 14 && (column - lastPlacedCol >= 3)) {
                        out[column] = date.format(monthLabelFormatter)
                        lastPlacedCol = column
                    }
                }
                lastMonth = date.monthValue
            }
        }
        // If no labels were placed due to a short window, place the starting month
        if (lastPlacedCol == -4 && series.size > 0) {
            val col = (leadingBlanks / 7).coerceIn(0, columns - 1)
            out[col] = series.startDate.format(monthLabelFormatter)
        }
        out
    }

    Row(
        modifier = Modifier
            .padding(start = startPadding)
            .height(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (column in 0 until columns) {
            Box(
                modifier = Modifier
                    .width(cellSize + if (column < columns - 1) cellGap else 0.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                labels[column]?.let {
                    Text(
                        text = it,
                        color = colors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.wrapContentSize(align = Alignment.CenterStart, unbounded = true)
                    )
                }
            }
        }
    }
}

/**
 * Day-of-week × hour-of-day density.
 *
 * Hours are drawn in 24 columns but labelled every six, because 24 labels at
 * this width overlap into a grey smear. The cell width is derived from the
 * available width rather than fixed, so this fits a phone and a foldable
 * without a second layout.
 */
@Composable
fun TimeOfDayHeatmap(
    matrix: TimeOfDayMatrix,
    modifier: Modifier = Modifier,
    rowHeight: Dp = 14.dp,
    gap: Dp = 2.dp
) {
    val colors = AppTheme.colors
    val density = LocalDensity.current

    if (matrix.max == 0) {
        EmptyChartHint(text = "No completions in this window", modifier = modifier)
        return
    }

    val dayLabels = remember { listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun") }
    val yAxisWidth = 32.dp

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                verticalArrangement = Arrangement.spacedBy(gap),
                modifier = Modifier.width(yAxisWidth)
            ) {
                dayLabels.forEach { label ->
                    Box(
                        modifier = Modifier.height(rowHeight),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = label,
                            color = colors.textSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 10.sp,
                            style = TextStyle(
                                platformStyle = PlatformTextStyle(
                                    includeFontPadding = false
                                )
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(Modifier.width(AppTheme.spacing.xs))

            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(rowHeight * 7 + gap * 6)
            ) {
                val gapPx = with(density) { gap.toPx() }
                val rowPx = with(density) { rowHeight.toPx() }
                val cellWidth = (size.width - gapPx * 23) / 24f
                val radiusPx = with(density) { 2.dp.toPx() }
                val ramp = colors.heatRamp

                val peak = matrix.peak()
                for (day in 0 until TimeOfDayMatrix.DAYS) {
                    for (hour in 0 until TimeOfDayMatrix.HOURS) {
                        val count = matrix.at(day, hour)
                        val color = rampColor(ramp, count, matrix.intensityAt(day, hour))
                        val topLeft = Offset(
                            hour * (cellWidth + gapPx),
                            day * (rowPx + gapPx)
                        )
                        val cellSizeObj = Size(cellWidth, rowPx)
                        val cornerRadiusObj = CornerRadius(radiusPx, radiusPx)

                        drawRoundRect(
                            color = color,
                            topLeft = topLeft,
                            size = cellSizeObj,
                            cornerRadius = cornerRadiusObj
                        )

                        if (count <= 0) {
                            drawRoundRect(
                                color = colors.hairline.copy(alpha = 0.35f),
                                topLeft = topLeft,
                                size = cellSizeObj,
                                cornerRadius = cornerRadiusObj,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = with(density) { 0.6.dp.toPx() }
                                )
                            )
                        } else if (peak != null && peak.first == day && peak.second == hour) {
                            drawRoundRect(
                                color = colors.accent,
                                topLeft = topLeft,
                                size = cellSizeObj,
                                cornerRadius = cornerRadiusObj,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = with(density) { 1.5.dp.toPx() }
                                )
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(AppTheme.spacing.xs))
        Row(
            modifier = Modifier
                .padding(start = yAxisWidth + AppTheme.spacing.xs)
                .fillMaxWidth()
        ) {
            Text(
                text = "12 AM",
                color = colors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(6f)
            )
            Text(
                text = "6 AM",
                color = colors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(6f)
            )
            Text(
                text = "12 PM",
                color = colors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(6f)
            )
            Text(
                text = "6 PM",
                color = colors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(5f)
            )
            Text(
                text = "11 PM",
                color = colors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End
            )
        }

        Spacer(Modifier.height(AppTheme.spacing.xs + 2.dp))
        Text(
            text = "Hours of the day (12 AM midnight to 11 PM)",
            color = colors.textMuted,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(start = yAxisWidth + AppTheme.spacing.xs)
        )

        matrix.peak()?.let { (day, hour) ->
            Spacer(Modifier.height(AppTheme.spacing.xs))
            Text(
                text = "Busiest: ${dayLabels[day]} around ${formatHour(hour)}",
                color = colors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 16.sp,
                modifier = Modifier.padding(start = yAxisWidth + AppTheme.spacing.xs)
            )
        }
    }
}

private fun formatHour(hour: Int): String = when {
    hour == 0 -> "12 AM (Midnight)"
    hour < 12 -> "$hour AM"
    hour == 12 -> "12 PM (Noon)"
    else -> "${hour - 12} PM"
}

/** The ramp legend. Shared by both heatmaps so they can never disagree. */
@Composable
fun HeatLegend(modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)
    ) {
        Text("Less", color = colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        colors.heatRamp.forEach { color ->
            Canvas(modifier = Modifier.size(9.dp)) {
                drawRoundRect(color = color, cornerRadius = CornerRadius(2.dp.toPx()))
            }
        }
        Text("More", color = colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun EmptyChartHint(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().height(56.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = AppTheme.colors.textMuted, fontSize = 12.sp)
    }
}
