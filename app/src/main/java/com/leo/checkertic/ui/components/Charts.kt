package com.leo.checkertic.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.checkertic.analytics.CategoryStat
import com.leo.checkertic.analytics.DaySeries
import com.leo.checkertic.ui.theme.AppTheme

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

    if (series.size == 0 || series.max == 0) {
        EmptyChartHint(text = "Nothing to chart yet", modifier = modifier)
        return
    }

    // A single animated scalar drives the whole draw. Animating per-bar would
    // mean N animations running N recompositions; this is one value change
    // and one redraw of an otherwise static node.
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = AppTheme.motion.normalSpec(),
        label = "trend-grow"
    )
    val grow = if (animate) progress else 1f

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val count = series.size
        val gapPx = with(density) { 1.5.dp.toPx() }
        val barWidth = ((size.width - gapPx * (count - 1)) / count).coerceAtLeast(1f)
        val radiusPx = with(density) { 1.5.dp.toPx() }
        val baseline = size.height

        for (index in 0 until count) {
            val value = series.counts[index]
            if (value == 0) {
                // A hairline for empty days: without it a sparse chart reads
                // as "no data" rather than "nothing happened that day".
                drawRect(
                    color = colors.surfaceSunken,
                    topLeft = Offset(index * (barWidth + gapPx), baseline - gapPx),
                    size = Size(barWidth, gapPx)
                )
                continue
            }
            val barHeight = (value.toFloat() / series.max) * size.height * grow
            drawRoundRect(
                color = barColor,
                topLeft = Offset(index * (barWidth + gapPx), baseline - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(radiusPx, radiusPx)
            )
        }
    }
}

/**
 * A smoothed line variant, used for the notes activity trend where the point
 * is the shape of the curve rather than individual days.
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
        val step = size.width / (series.size - 1)
        val strokePx = with(density) { 1.5.dp.toPx() }

        // Paths are built once per draw and reused for both the fill and the
        // stroke, rather than built twice.
        val line = Path()
        val fill = Path()
        for (index in 0 until series.size) {
            val x = index * step
            val y = size.height - (series.counts[index].toFloat() / series.max) * size.height
            if (index == 0) {
                line.moveTo(x, y)
                fill.moveTo(x, size.height)
                fill.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(size.width, size.height)
        fill.close()

        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                listOf(lineColor.copy(alpha = 0.22f), Color.Transparent)
            )
        )
        drawPath(path = line, color = lineColor, style = Stroke(width = strokePx))
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
                        text = stat.completionsInWindow.toString(),
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
                        color = accent,
                        size = Size(size.width * fraction, size.height),
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
        if (title != null || trailing != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                trailing?.invoke()
            }
            Spacer(Modifier.height(spacing.md))
        }
        content()
    }
}
