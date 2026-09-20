package com.leo.checkertic.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * ============================================================================
 *  THE TOKEN LAYER
 * ============================================================================
 *
 * Every colour, spacing step, corner radius, motion duration and fixed size
 * used anywhere in the app is defined here and *only* here. Screens and
 * components read them through [AppTheme]; they never hardcode a literal.
 *
 * Re-skinning the app = editing [darkColors] / [lightColors] below.
 * Retuning the feel of the app = editing [AppMotion].
 *
 * The one deliberate exception is the Glance widget, which runs outside
 * Compose composition and therefore cannot read a CompositionLocal. It pulls
 * from the raw palette in `Color.kt` directly — the same constants these
 * token sets are built from, so the two stay in sync by construction.
 */

// ---------------------------------------------------------------------------
// Colour
// ---------------------------------------------------------------------------

@Immutable
data class AppColors(
    /** Window background, behind everything. */
    val root: Color,
    /** Default card / row fill. Flat and opaque by design rule. */
    val surface: Color,
    /** Slightly lifted surface, for nested content inside a card. */
    val surfaceRaised: Color,
    /** Recessed surface, for wells and empty heatmap cells. */
    val surfaceSunken: Color,

    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,

    val accent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,

    val success: Color,
    val warning: Color,
    val danger: Color,

    /** 1px separators and card outlines. */
    val hairline: Color,
    /** Full-bleed dim behind modals. */
    val scrim: Color,

    // -- Glass (used only at boundaries: app bars, sheet edges, reveal sheet) --
    val glassTint: Color,
    val glassHairline: Color,

    /** 5-step completion-density ramp for heatmaps, coldest → hottest. */
    val heatRamp: List<Color>,

    /** Stable accent cycle for notes / categories. */
    val accentCycle: List<Color>,

    val isDark: Boolean
)

private val darkColors = AppColors(
    root = ObsidianRoot,
    surface = DarkSurfaceCard,
    surfaceRaised = Color(0xFF26262B),
    surfaceSunken = Color(0xFF1A1A1D),
    textPrimary = TextPrimaryDark,
    textSecondary = TextSecondaryDark,
    textMuted = TextCompletedDark,
    accent = SatinCopper,
    accentContainer = CopperContainer,
    onAccentContainer = RadiantCopperText,
    success = CompletionGreen,
    warning = DotAmber,
    danger = AccentRed,
    hairline = Color(0x1FFFFFFF),
    scrim = Color(0x99000000),
    glassTint = Color(0xC7161618),
    glassHairline = Color(0x14FFFFFF),
    heatRamp = listOf(
        Color(0xFF1E1E22),
        Color(0xFF452418),
        Color(0xFF7C3E24),
        Color(0xFFC86D44),
        Color(0xFFFB923C)
    ),
    accentCycle = listOf(DotBlue, DotGreen, DotAmber, DotCoral),
    isDark = true
)

private val lightColors = AppColors(
    root = LightRoot,
    surface = LightSurfaceCard,
    surfaceRaised = Color(0xFFF8FAFC),
    surfaceSunken = Color(0xFFE9EDF2),
    textPrimary = TextPrimaryLight,
    textSecondary = TextSecondaryLight,
    textMuted = TextCompletedLight,
    accent = LightSatinCopper,
    accentContainer = LightCopperContainer,
    onAccentContainer = LightCopperText,
    success = Color(0xFF16A34A),
    warning = Color(0xFFD97706),
    danger = AccentRed,
    hairline = Color(0x14000000),
    scrim = Color(0x66000000),
    glassTint = Color(0xC7F3F4F6),
    glassHairline = Color(0x0F000000),
    heatRamp = listOf(
        Color(0xFFECE6DF),
        Color(0xFFFBD7C0),
        Color(0xFFF8A87C),
        Color(0xFFDD6B35),
        Color(0xFF9A3412)
    ),
    accentCycle = listOf(DotBlue, Color(0xFF16A34A), Color(0xFFD97706), DotCoral),
    isDark = false
)

internal fun appColorsFor(dark: Boolean): AppColors = if (dark) darkColors else lightColors

// ---------------------------------------------------------------------------
// Spacing
// ---------------------------------------------------------------------------

@Immutable
data class AppSpacing(
    val hair: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    /** Horizontal gutter every screen aligns to. */
    val screenGutter: Dp = 16.dp,
    /** Bottom padding so the last list item clears the FAB. */
    val fabClearance: Dp = 76.dp
)

// ---------------------------------------------------------------------------
// Radius
// ---------------------------------------------------------------------------

@Immutable
data class AppRadius(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val pill: Dp = 999.dp
)

// ---------------------------------------------------------------------------
// Sizing
// ---------------------------------------------------------------------------

@Immutable
data class AppSizes(
    val taskRowHeight: Dp = 46.dp,
    val tickerWidth: Dp = 14.dp,
    val fab: Dp = 48.dp,
    val iconSm: Dp = 16.dp,
    val iconMd: Dp = 20.dp,
    val iconLg: Dp = 24.dp,
    val touchTarget: Dp = 40.dp,
    val hairline: Dp = 1.dp,
    /** Edge of one calendar-heatmap cell. */
    val heatCell: Dp = 11.dp,
    val heatCellGap: Dp = 3.dp,
    val chartHeight: Dp = 128.dp,
    val sparklineHeight: Dp = 64.dp,
    val noteBackgroundHeight: Dp = 84.dp
)

// ---------------------------------------------------------------------------
// Motion
// ---------------------------------------------------------------------------

@Immutable
data class AppMotion(
    /** Feedback that must read as "already happened". */
    val instant: Int = 90,
    /** Row state changes, ticks, ripples. */
    val fast: Int = 140,
    /** The default. Screen fades, expands, list placement. */
    val normal: Int = 200,
    /** Sheets, reveals, anything travelling a long distance. */
    val slow: Int = 300,

    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
    val decelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f),
    val accelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)
) {
    fun <T> fastSpec(): FiniteAnimationSpec<T> = tween(fast, easing = standard)
    fun <T> normalSpec(): FiniteAnimationSpec<T> = tween(normal, easing = standard)
    fun <T> slowSpec(): FiniteAnimationSpec<T> = tween(slow, easing = decelerate)

    /**
     * Placement animation for lazy lists. A spring (not a tween) so that a
     * reorder interrupted mid-flight retargets from its current velocity
     * instead of snapping — interruption is where list animations usually
     * look broken.
     */
    fun <T> placementSpec(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}

// ---------------------------------------------------------------------------
// Access
// ---------------------------------------------------------------------------

internal val LocalAppColors = staticCompositionLocalOf { darkColors }
internal val LocalAppSpacing = staticCompositionLocalOf { AppSpacing() }
internal val LocalAppRadius = staticCompositionLocalOf { AppRadius() }
internal val LocalAppSizes = staticCompositionLocalOf { AppSizes() }
internal val LocalAppMotion = staticCompositionLocalOf { AppMotion() }

/**
 * Token accessor, mirroring the shape of `MaterialTheme`.
 *
 * All five locals are `staticCompositionLocalOf`: their values never change
 * for the lifetime of a theme, so Compose can skip tracking reads of them and
 * a token lookup costs nothing at recomposition time.
 */
object AppTheme {
    val colors: AppColors
        @androidx.compose.runtime.Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalAppColors.current

    val spacing: AppSpacing
        @androidx.compose.runtime.Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalAppSpacing.current

    val radius: AppRadius
        @androidx.compose.runtime.Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalAppRadius.current

    val sizes: AppSizes
        @androidx.compose.runtime.Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalAppSizes.current

    val motion: AppMotion
        @androidx.compose.runtime.Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalAppMotion.current
}
