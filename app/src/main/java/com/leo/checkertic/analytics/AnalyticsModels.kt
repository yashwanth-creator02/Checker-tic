package com.leo.checkertic.analytics

import androidx.compose.runtime.Immutable
import java.time.LocalDate

/**
 * ============================================================================
 *  ANALYTICS MODELS
 * ============================================================================
 *
 * The shapes the charts draw from. Three properties they all share, and all
 * three are there for frame-time reasons:
 *
 *  1. **`@Immutable`.** Compose's strong skipping compares parameters by
 *     equality; marking these immutable lets it skip a chart whose data has
 *     not changed, even when the screen around it recomposed.
 *  2. **Primitive arrays, not `List<Int>`.** A year of heatmap data is 365
 *     values. As `List<Int>` that is 365 boxed `Integer`s plus list overhead,
 *     re-walked by the garbage collector on every aggregation. As `IntArray`
 *     it is one 1.4 KB allocation.
 *  3. **Precomputed on a background thread.** Nothing here is derived inside
 *     a composable. The draw code reads values and does nothing else, which
 *     is what keeps the analytics screen from dropping frames the moment a
 *     task is ticked.
 *
 * `IntArray` has reference equality, so anything holding one overrides
 * `equals`/`hashCode` to compare contents — otherwise every recomputation
 * would look like a change and skipping would never engage.
 */

/**
 * Density values for a contiguous run of days, oldest first.
 *
 * @param startDate the day [counts] index 0 refers to
 * @param counts completions per day
 * @param max largest value in [counts], precomputed so the draw pass doesn't
 *   scan the array on every frame to normalise colour
 */
@Immutable
class DaySeries(
    val startDate: LocalDate,
    val counts: IntArray,
    val max: Int
) {
    val size: Int get() = counts.size

    fun dateAt(index: Int): LocalDate = startDate.plusDays(index.toLong())

    /** Density 0f..1f for colour ramp lookup. */
    fun intensityAt(index: Int): Float =
        if (max <= 0) 0f else counts[index].toFloat() / max

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DaySeries) return false
        return startDate == other.startDate && max == other.max &&
            counts.contentEquals(other.counts)
    }

    override fun hashCode(): Int =
        31 * (31 * startDate.hashCode() + max) + counts.contentHashCode()

    companion object {
        val Empty = DaySeries(LocalDate.EPOCH, IntArray(0), 0)
    }
}

/**
 * Completions bucketed by day-of-week × hour-of-day.
 *
 * Flat `IntArray(7 * 24)` rather than `Array<IntArray>`: one allocation
 * instead of eight, and contiguous memory for the draw loop to walk.
 */
@Immutable
class TimeOfDayMatrix(
    val counts: IntArray,
    val max: Int
) {
    init {
        require(counts.size == CELLS) { "TimeOfDayMatrix must be 7 x 24" }
    }

    /** @param day 0 = Monday, to match ISO and the row order drawn. */
    fun at(day: Int, hour: Int): Int = counts[day * HOURS + hour]

    fun intensityAt(day: Int, hour: Int): Float =
        if (max <= 0) 0f else at(day, hour).toFloat() / max

    /** The single busiest cell, or null when there is no data at all. */
    fun peak(): Pair<Int, Int>? {
        if (max <= 0) return null
        val index = counts.indexOfFirst { it == max }
        return if (index < 0) null else (index / HOURS) to (index % HOURS)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TimeOfDayMatrix) return false
        return max == other.max && counts.contentEquals(other.counts)
    }

    override fun hashCode(): Int = 31 * max + counts.contentHashCode()

    companion object {
        const val DAYS = 7
        const val HOURS = 24
        const val CELLS = DAYS * HOURS
        val Empty = TimeOfDayMatrix(IntArray(CELLS), 0)
    }
}

/**
 * A streak, counted in the category's own recurrence periods.
 *
 * @param unit human label for the period ("days", "weeks", ...)
 * @param activeNow false when the streak is alive but the current period has
 *   nothing in it yet — the difference between "you're on 12 days" and
 *   "you were on 12 days, do something today"
 */
@Immutable
data class StreakInfo(
    val current: Int,
    val longest: Int,
    val unit: String,
    val activeNow: Boolean
) {
    companion object {
        val Empty = StreakInfo(0, 0, "days", false)
    }
}

@Immutable
data class CategoryStat(
    val categoryId: Long,
    val name: String,
    val recurrenceType: String,
    val recurrenceCustomDays: Int,
    val completionsInWindow: Int,
    val streak: StreakInfo,
    val locked: Boolean
)

/** Selectable analytics window. Kept small — every option is one tap. */
enum class AnalyticsWindow(val label: String, val days: Int) {
    WEEK("7d", 7),
    MONTH("30d", 30),
    QUARTER("90d", 90),
    YEAR("1y", 365);

    companion object {
        val Default = MONTH
    }
}

/** Per-category heatmap layout mode (feature 2). */
enum class HeatmapMode(val label: String) {
    /** A continuous rolling window ending today. */
    ROLLING("Rolling"),

    /** A real calendar month, aligned to weekday columns. */
    CALENDAR("Month")
}

@Immutable
data class GlobalAnalytics(
    val completedToday: Int,
    val bestCurrentStreak: Int,
    val bestCurrentStreakCategory: String?,
    val bestStreakUnit: String,
    /** Completions ÷ active days across the window, 0f..1f. */
    val completionRate: Float,
    val totalInWindow: Int,
    val calendar: DaySeries,
    val timeOfDay: TimeOfDayMatrix,
    val categories: List<CategoryStat>,
    val noteActivity: DaySeries,
    val loading: Boolean = false
) {
    companion object {
        val Empty = GlobalAnalytics(
            completedToday = 0,
            bestCurrentStreak = 0,
            bestCurrentStreakCategory = null,
            bestStreakUnit = "days",
            completionRate = 0f,
            totalInWindow = 0,
            calendar = DaySeries.Empty,
            timeOfDay = TimeOfDayMatrix.Empty,
            categories = emptyList(),
            noteActivity = DaySeries.Empty,
            loading = true
        )
    }
}

/**
 * The inline, per-category block on the Tasks screen.
 *
 * Deliberately the same [DaySeries] and [StreakInfo] the global screen uses,
 * produced by the same functions in [AnalyticsEngine] with a category filter
 * applied — scoped and re-laid-out, not a parallel implementation.
 */
@Immutable
data class CategoryAnalytics(
    val categoryId: Long,
    val rolling: DaySeries,
    val calendarMonth: DaySeries,
    /** First day of [calendarMonth], for the weekday-aligned layout. */
    val monthStart: LocalDate,
    val trend: DaySeries,
    val streak: StreakInfo,
    val totalInWindow: Int,
    val loading: Boolean = false
) {
    companion object {
        fun empty(categoryId: Long) = CategoryAnalytics(
            categoryId = categoryId,
            rolling = DaySeries.Empty,
            calendarMonth = DaySeries.Empty,
            monthStart = LocalDate.EPOCH,
            trend = DaySeries.Empty,
            streak = StreakInfo.Empty,
            totalInWindow = 0,
            loading = true
        )
    }
}
