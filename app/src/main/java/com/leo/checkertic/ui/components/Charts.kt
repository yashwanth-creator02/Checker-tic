package com.leo.checkertic.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.checkertic.analytics.CategoryStat
import com.leo.checkertic.analytics.DaySeries
import com.leo.checkertic.ui.theme.AppTheme
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * ============================================================================
 *  CHARTS
 * ============================================================================
 *
 * Hand-drawn on `Canvas` rather than pulled from Vico / MPAndroidChart /
 * Compose-Charts.
 *
 * Those libraries earn their weight when you need axes, legends, zoom,
 * multiple series types and interaction. What this app needs is a bar trend
 * and a horizontal comparison, both of which are a `for` loop over an
 * `IntArray`. A charting dependency here would add hundreds of KB, its own
 * animation loop and its own recomposition behaviour, in exchange for less
 * control over exactly the thing that matters most — how many nodes end up in
 * the layout tree of a scrolling screen.
 */

private val trendDateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")

/**
 * Completion trend as bars.
 *
 * Down-sampled to at most one bar per ~4 dp of width before drawing (see
 * `AnalyticsEngine.bucket`), so a year-long window doesn't try to draw 365
 * sub-pixel bars that alias into a grey block.
 */
@Composable
fun TrendChart(
    series: DaySeries,
    modifier: Modifier = Modifier,
    height: Dp = AppTheme.sizes.sparklineHeight,
    barColor: Color = AppTheme.colors.accent,
    animate: Boolean = true
) {
    val colors = AppTheme.colors
    val density = LocalDensity.current
    var selectedIndex by remember(series) { mutableStateOf<Int?>(null) }

    if (series.size == 0 || series.max == 0) {
        EmptyChartHint(text = "Nothing to chart yet", modifier = modifier)
        return
    }

    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = AppTheme.motion.normalSpec(),
        label = "trend-grow"
    )
    val grow = if (animate) progress else 1f

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(series) {
                    detectTapGestures { offset ->
                        val count = series.size
                        val gapPx = with(density) { 1.5.dp.toPx() }
                        val barWidth = ((size.width - gapPx * (count - 1)) / count).coerceAtLeast(1f)
                        val index = (offset.x / (barWidth + gapPx)).toInt().coerceIn(0, count - 1)
                        selectedIndex = if (selectedIndex == index) null else index
                    }
                }
        ) {
            val count = series.size
            val gapPx = with(density) { 1.5.dp.toPx() }
            val barWidth = ((size.width - gapPx * (count - 1)) / count).coerceAtLeast(1f)
            val radiusPx = with(density) { 2.dp.toPx() }
            val baseline = size.height

            // Top peak dashed ceiling guide
            val dashEffect = PathEffect.dashPathEffect(
                floatArrayOf(with(density) { 4.dp.toPx() }, with(density) { 4.dp.toPx() }),
                0f
            )
            drawLine(
                color = colors.hairline,
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = with(density) { 1.dp.toPx() },
                pathEffect = dashEffect
            )

            for (index in 0 until count) {
                val value = series.counts[index]
                val left = index * (barWidth + gapPx)
                val isSelected = selectedIndex == index

                if (value == 0) {
                    drawRoundRect(
                        color = colors.surfaceSunken,
                        topLeft = Offset(left, baseline - with(density) { 3.dp.toPx() }),
                        size = Size(barWidth, with(density) { 3.dp.toPx() }),
                        cornerRadius = CornerRadius(with(density) { 1.dp.toPx() }, with(density) { 1.dp.toPx() })
                    )
                    continue
                }

                val barHeight = (value.toFloat() / series.max) * (size.height - with(density) { 4.dp.toPx() }) * grow
                val top = baseline - barHeight

                val brush = Brush.verticalGradient(
                    colors = listOf(
                        barColor,
                        barColor.copy(alpha = if (isSelected) 1f else 0.72f)
                    ),
                    startY = top,
                    endY = baseline
                )

                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(radiusPx, radiusPx)
                )

                if (isSelected) {
                    drawRoundRect(
                        color = colors.textPrimary,
                        topLeft = Offset(left - 0.5f, top - 0.5f),
                        size = Size(barWidth + 1f, barHeight + 1f),
                        cornerRadius = CornerRadius(radiusPx, radiusPx),
                        style = Stroke(width = with(density) { 1.dp.toPx() })
                    )
                }
            }

            // Baseline anchor
            drawLine(
                color = colors.hairline,
                start = Offset(0f, baseline),
                end = Offset(size.width, baseline),
                strokeWidth = with(density) { 1.dp.toPx() }
            )
        }

        selectedIndex?.let { idx ->
            Spacer(Modifier.height(AppTheme.spacing.xs))
            val dateStr = series.dateAt(idx).format(trendDateFormatter)
            val c = series.counts[idx]
            val label = if (c == 1) "1 completion" else "$c completions"
            Text(
                text = "$dateStr · $label",
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * A smoothed spline curve variant, used for the notes activity trend where the
 * point is the shape of the curve rather than individual days.
 */
@Composable
fun SparklineChart(
    series: DaySeries,
    modifier: Modifier = Modifier,
    height: Dp = AppTheme.sizes.sparklineHeight,
    lineColor: Color = AppTheme.colors.accent
) {
    val colors = AppTheme.colors
    val density = LocalDensity.current

    if (series.size < 2 || series.max == 0) {
        EmptyChartHint(text = "Not enough activity yet", modifier = modifier)
        return
    }

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val topPaddingPx = with(density) { 8.dp.toPx() }
        val usableHeight = size.height - topPaddingPx
        val step = size.width / (series.size - 1)
        val strokePx = with(density) { 2.dp.toPx() }

        // Dashed ceiling line
        val dashEffect = PathEffect.dashPathEffect(
            floatArrayOf(with(density) { 4.dp.toPx() }, with(density) { 4.dp.toPx() }),
            0f
        )
        drawLine(
            color = colors.hairline,
            start = Offset(0f, topPaddingPx),
            end = Offset(size.width, topPaddingPx),
            strokeWidth = with(density) { 1.dp.toPx() },
            pathEffect = dashEffect
        )

        // Calculate smooth points
        val points = Array(series.size) { i ->
            val x = i * step
            val y = topPaddingPx + usableHeight - (series.counts[i].toFloat() / series.max) * usableHeight
            Offset(x, y)
        }

        // Smooth cubic Bézier spline
        val linePath = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]
                val midX = (prev.x + curr.x) / 2f
                cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
            }
        }

        // Gradient filled area
        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    lineColor.copy(alpha = 0.32f),
                    lineColor.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                startY = topPaddingPx,
                endY = size.height
            )
        )

        // Curve stroke
        drawPath(path = linePath, color = lineColor, style = Stroke(width = strokePx))

        // Peak point indicator dot
        val peakIdx = series.counts.indices.maxByOrNull { series.counts[it] } ?: 0
        val peakPoint = points[peakIdx]

        drawCircle(
            color = lineColor.copy(alpha = 0.25f),
            radius = with(density) { 6.dp.toPx() },
            center = peakPoint
        )
        drawCircle(
            color = lineColor,
            radius = with(density) { 3.5.dp.toPx() },
            center = peakPoint
        )
        drawCircle(
            color = colors.surface,
            radius = with(density) { 1.8.dp.toPx() },
            center = peakPoint
        )

        // Baseline hairline
        drawLine(
            color = colors.hairline,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = with(density) { 1.dp.toPx() }
        )
    }
}

/**
 * Category comparison: completions per category over the window.
 *
 * Horizontal bars rather than a pie or a donut. With five or six categories
 * of similar size, a pie is genuinely hard to read — angle comparison is a
 * well-documented weak point of human visual perception — whereas bars
 * sharing a baseline are read accurately at a glance, and the labels have
 * room to be full category names rather than a legend the eye has to
 * cross-reference.
 */
@Composable
fun CategoryComparisonChart(
    stats: List<CategoryStat>,
    modifier: Modifier = Modifier,
    onCategoryClick: ((Long) -> Unit)? = null
) {
    val colors = AppTheme.colors
    val spacing = AppTheme.spacing

    val ranked = remember(stats) {
        stats.sortedByDescending { it.completionsInWindow }
    }
    val max = remember(ranked) { ranked.maxOfOrNull { it.completionsInWindow } ?: 0 }
    val total = remember(ranked) { ranked.sumOf { it.completionsInWindow }.coerceAtLeast(1) }

    if (ranked.isEmpty() || max == 0) {
        EmptyChartHint(text = "No completions in this window", modifier = modifier)
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        ranked.forEach { stat ->
            val fraction = stat.completionsInWindow.toFloat() / max
            val percent = ((stat.completionsInWindow.toFloat() / total) * 100).roundToInt()
            val accent = colors.accentCycle[
                (stat.categoryId % colors.accentCycle.size).toInt().coerceAtLeast(0)
            ]
            Column(
                modifier = if (onCategoryClick == null) {
                    Modifier
                } else {
                    Modifier.clickable { onCategoryClick(stat.categoryId) }
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stat.name,
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(spacing.sm))
                    Text(
                        text = "${stat.completionsInWindow} · $percent%",
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(spacing.xs))
                Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
                    val radius = CornerRadius(size.height / 2f, size.height / 2f)
                    drawRoundRect(color = colors.surfaceSunken, cornerRadius = radius)
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(accent.copy(alpha = 0.82f), accent)
                        ),
                        size = Size((size.width * fraction).coerceAtLeast(size.height), size.height),
                        cornerRadius = radius
                    )
                }
            }
        }
    }
}

/** A single headline number with a caption. */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = AppTheme.colors.textPrimary,
    caption: String? = null
) {
    val colors = AppTheme.colors
    Column(modifier = modifier) {
        Text(
            text = value,
            color = accent,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Text(text = label, color = colors.textSecondary, fontSize = 12.sp, maxLines = 1)
        if (caption != null) {
            Text(
                text = caption,
                color = colors.textMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** A flat, opaque container. Card interiors never get glass — that is the rule. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    infoText: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = AppTheme.colors
    val spacing = AppTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.radius.lg))
            .background(colors.surface)
            .padding(spacing.lg)
    ) {
        if (title != null || infoText != null || trailing != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (title != null) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (infoText != null) {
                            Spacer(Modifier.width(spacing.xs))
                            InfoDropdown(infoText = infoText)
                        }
                    }
                } else {
                    if (infoText != null) {
                        InfoDropdown(infoText = infoText)
                    }
                    Spacer(Modifier.weight(1f))
                }
                trailing?.invoke()
            }
            Spacer(Modifier.height(spacing.md))
        }
        content()
    }
}

/**
 * Clean anchored information popup for explaining analytics cards and visualizations.
 */
@Composable
fun InfoDropdown(
    infoText: String,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val spacing = AppTheme.spacing
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "Show information",
                tint = if (expanded) colors.accent else colors.textMuted,
                modifier = Modifier.size(15.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(AppTheme.radius.md),
            containerColor = colors.surfaceRaised,
            border = BorderStroke(1.dp, colors.hairline),
            modifier = Modifier
                .widthIn(min = 220.dp, max = 290.dp)
                .padding(spacing.sm)
        ) {
            Text(
                text = infoText,
                color = colors.textPrimary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = spacing.xs, vertical = spacing.xs)
            )
        }
    }
}
