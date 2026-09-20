package com.leo.checkertic.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * ============================================================================
 *  GLASS — applied at edges only
 * ============================================================================
 *
 * The design rule for this app is "glass at the edges, not everywhere": app
 * bar backdrops, sheet and dialog edges, and the completed-tasks reveal sheet
 * get translucency; card interiors and list rows stay flat and opaque.
 *
 * ## Why this is a tint-and-fade, not a real backdrop blur
 *
 * Real backdrop blur on Android means one of three things today:
 *
 *  - `Modifier.blur` / `RenderEffect` — blurs a composable's *own* content,
 *    not what is behind it. Wrong tool for a translucent app bar.
 *  - Haze — genuinely good, but 2.x is still beta, and it works by capturing
 *    the scrolling content into a graphics layer and re-rendering it through a
 *    two-pass shader every frame. On a screen that is already drawing a
 *    heatmap and a chart, that is real per-frame GPU cost for a decorative
 *    effect.
 *  - `RenderNode.setBackdropRenderEffect` — the actual platform answer, but it
 *    landed in Android 17 QPR2 (SDK 37.2), so nearly nobody has it.
 *
 * Given "performance and smoothness are the top priority, above any individual
 * feature", the boundary treatment here is a translucent tint plus a hairline
 * plus an optional gradient fade. It costs one extra draw op, allocates
 * nothing per frame, and reads as glass because the content genuinely shows
 * through it. If a real blur is wanted later it drops into exactly these two
 * functions and nowhere else in the codebase.
 */

/**
 * Top boundary: a translucent app-bar backdrop with a hairline on its lower
 * edge and a short fade so content dissolves into the bar as it scrolls under.
 */
@Composable
fun GlassTopBoundary(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = AppTheme.colors
    val hairline = AppTheme.sizes.hairline
    Box(
        modifier = modifier.drawWithCache {
            val tint = colors.glassTint
            val line = colors.glassHairline
            val hairlinePx = hairline.toPx()
            val fade = Brush.verticalGradient(
                colors = listOf(tint, tint.copy(alpha = tint.alpha * 0.86f)),
                startY = 0f,
                endY = size.height
            )
            onDrawBehind {
                drawRect(brush = fade)
                drawRect(
                    color = line,
                    topLeft = Offset(0f, size.height - hairlinePx),
                    size = Size(size.width, hairlinePx)
                )
            }
        },
        content = content
    )
}

/**
 * Bottom boundary: used by the nav bar and by the completed-tasks reveal
 * sheet. Hairline sits on the upper edge; the fade runs the other way.
 */
@Composable
fun GlassBottomBoundary(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = AppTheme.colors
    val hairline = AppTheme.sizes.hairline
    Box(
        modifier = modifier.drawWithCache {
            val tint = colors.glassTint
            val line = colors.glassHairline
            val hairlinePx = hairline.toPx()
            val fade = Brush.verticalGradient(
                colors = listOf(tint.copy(alpha = tint.alpha * 0.86f), tint),
                startY = 0f,
                endY = size.height
            )
            onDrawBehind {
                drawRect(brush = fade)
                drawRect(color = line, size = Size(size.width, hairlinePx))
            }
        },
        content = content
    )
}

/**
 * A short scrim strip drawn *above* a bottom boundary so list content fades
 * out rather than being sliced off by the bar's edge. Draw-only; no layout
 * cost, no recomposition when the list scrolls.
 */
fun Modifier.contentFadeOut(color: Color): Modifier = this.drawBehind {
    drawRect(
        brush = Brush.verticalGradient(
            listOf(color.copy(alpha = 0f), color)
        )
    )
}
