package com.leo.checkertic.analytics

import com.leo.checkertic.core.time.PeriodKeys
import com.leo.checkertic.data.dao.CompletionPoint
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * ============================================================================
 *  ANALYTICS ENGINE
 * ============================================================================
 *
 * Every number on the analytics screen and every number in the per-category
 * inline block is produced here. One implementation, two callers — the
 * category block passes a filter, nothing else differs.
 *
 * Pure Kotlin: no Android, no Room, no coroutines. That makes it unit
 * testable without an emulator, and it makes it obvious that none of this
 * touches the main thread — the caller decides which dispatcher it runs on,
 * and `AnalyticsRepository` always picks `Dispatchers.Default`.
 *
 * ## Performance shape
 *
 * Input is a list of (categoryId, completedAt) pairs sorted ascending — the
 * database already returns them that way, so nothing re-sorts.
 *
 * Every aggregation is a single linear pass writing into a pre-sized
 * `IntArray`. A year of data for a heavy user is maybe 20k points; one pass
 * over that is a fraction of a millisecond, and building the calendar, the
 * time-of-day matrix and the per-category series in the same walk means the
 * list is touched once rather than four times.
 *
 * Timezone is passed in rather than read from the system inside the loop:
 * `ZoneId.systemDefault()` is a synchronized lookup, and calling it 20,000
 * times inside a hot loop is measurable.
 */
object AnalyticsEngine {

    /** Cheap epoch-millis → local epoch-day, avoiding a `LocalDate` per point. */
    private fun epochDayOf(millis: Long, zone: ZoneId): Long {
        val offsetSeconds = zone.rules.getOffset(Instant.ofEpochMilli(millis)).totalSeconds
        return Math.floorDiv(millis / 1000 + offsetSeconds, 86_400L)
    }

    private fun hourOf(millis: Long, zone: ZoneId): Int {
        val offsetSeconds = zone.rules.getOffset(Instant.ofEpochMilli(millis)).totalSeconds
        val secondsOfDay = Math.floorMod(millis / 1000 + offsetSeconds, 86_400L)
        return (secondsOfDay / 3600).toInt()
    }

    // ------------------------------------------------------------------
    // Day series
    // ------------------------------------------------------------------

    /**
     * Completions per day over `[startDate, startDate + days)`.
     *
     * @param categoryFilter null for every category; a specific id scopes the
     *   series without needing a separate query or a separate code path.
     */
    fun dailySeries(
        points: List<CompletionPoint>,
        startDate: LocalDate,
        days: Int,
        zone: ZoneId,
        categoryFilter: Long? = null
    ): DaySeries {
        if (days <= 0) return DaySeries.Empty
        val counts = IntArray(days)
        val startEpochDay = startDate.toEpochDay()
        var max = 0
        for (point in points) {
            if (categoryFilter != null && point.categoryId != categoryFilter) continue
            val index = (epochDayOf(point.completedAt, zone) - startEpochDay).toInt()
            if (index in 0 until days) {
                val next = counts[index] + 1
                counts[index] = next
                if (next > max) max = next
            }
        }
        return DaySeries(startDate, counts, max)
    }

    /** Same as [dailySeries] but over a bare list of timestamps (note activity). */
    fun dailySeriesOfTimestamps(
        timestamps: List<Long>,
        startDate: LocalDate,
        days: Int,
        zone: ZoneId
    ): DaySeries {
        if (days <= 0) return DaySeries.Empty
        val counts = IntArray(days)
        val startEpochDay = startDate.toEpochDay()
        var max = 0
        for (at in timestamps) {
            val index = (epochDayOf(at, zone) - startEpochDay).toInt()
            if (index in 0 until days) {
                val next = counts[index] + 1
                counts[index] = next
                if (next > max) max = next
            }
        }
        return DaySeries(startDate, counts, max)
    }

    /**
     * A whole calendar month, padded so index 0 is the 1st.
     *
     * The calendar-mode heatmap needs real month boundaries, not a rolling
     * window — that is the entire difference between the two modes the
     * per-category block offers.
     */
    fun calendarMonthSeries(
        points: List<CompletionPoint>,
        month: YearMonth,
        zone: ZoneId,
        categoryFilter: Long? = null
    ): DaySeries = dailySeries(
        points = points,
        startDate = month.atDay(1),
        days = month.lengthOfMonth(),
        zone = zone,
        categoryFilter = categoryFilter
    )

    // ------------------------------------------------------------------
    // Time of day × day of week
    // ------------------------------------------------------------------

    fun timeOfDayMatrix(
        points: List<CompletionPoint>,
        zone: ZoneId,
        from: Long,
        categoryFilter: Long? = null
    ): TimeOfDayMatrix {
        val counts = IntArray(TimeOfDayMatrix.CELLS)
        var max = 0
        for (point in points) {
            if (point.completedAt < from) continue
            if (categoryFilter != null && point.categoryId != categoryFilter) continue
            // Monday = 0. epochDay 0 (1970-01-01) was a Thursday, hence +3.
            val day = Math.floorMod(epochDayOf(point.completedAt, zone) + 3L, 7L).toInt()
            val hour = hourOf(point.completedAt, zone)
            val index = day * TimeOfDayMatrix.HOURS + hour
            val next = counts[index] + 1
            counts[index] = next
            if (next > max) max = next
        }
        return TimeOfDayMatrix(counts, max)
    }

    // ------------------------------------------------------------------
    // Streaks
    // ------------------------------------------------------------------

    /**
     * Recurrence-aware streak.
     *
     * The unit of a streak is the category's own recurrence period, so a
     * weekly category does not break because you skipped Tuesday. The period
     * boundaries come from [PeriodKeys] — the exact same function that
     * decides when the category's checkboxes reset — so the streak can never
     * disagree with what the user sees happen in the list.
     *
     * `once` categories have no periods at all. Rather than showing nothing,
     * they are measured in days, which is the only honest fallback: a
     * one-off list that gets touched daily *is* a daily habit, whatever its
     * recurrence setting says.
     *
     * The current streak tolerates an empty current period. Counting a streak
     * as broken at 00:01 because the day's work has not happened yet would
     * make the number useless before noon; [StreakInfo.activeNow] carries the
     * distinction instead.
     */
    fun streakFor(
        points: List<CompletionPoint>,
        categoryId: Long,
        recurrenceType: String,
        customDays: Int,
        zone: ZoneId,
        today: LocalDate
    ): StreakInfo {
        val effectiveType =
            if (recurrenceType == PeriodKeys.ONCE) PeriodKeys.DAILY else recurrenceType
        val unit = PeriodKeys.streakUnit(recurrenceType, customDays)

        // Distinct periods that contain at least one completion.
        val activePeriods = HashSet<String>()
        for (point in points) {
            if (point.categoryId != categoryId) continue
            val date = PeriodKeys.localDateOf(point.completedAt, zone)
            val key = PeriodKeys.keyFor(effectiveType, customDays, date)
            if (key.isNotEmpty()) activePeriods.add(key)
        }
        if (activePeriods.isEmpty()) return StreakInfo(0, 0, unit, false)

        val currentKey = PeriodKeys.keyFor(effectiveType, customDays, today)
        val activeNow = currentKey in activePeriods

        // Walk backwards period by period from the current one.
        var current = 0
        var cursor = if (activeNow) today else {
            PeriodKeys.shift(effectiveType, customDays, today, -1)
        }
        while (true) {
            val key = PeriodKeys.keyFor(effectiveType, customDays, cursor)
            if (key.isEmpty() || key !in activePeriods) break
            current++
            cursor = PeriodKeys.shift(effectiveType, customDays, cursor, -1)
            if (current > MAX_STREAK_WALK) break
        }

        // Longest: walk from the oldest active period forward. Bounded by the
        // number of distinct active periods, not by elapsed time, so a user
        // returning after a two-year gap doesn't cost two years of iterations.
        val longest = longestRun(activePeriods, effectiveType, customDays, points, zone)

        return StreakInfo(
            current = current,
            longest = maxOf(longest, current),
            unit = unit,
            activeNow = activeNow
        )
    }

    private fun longestRun(
        activePeriods: Set<String>,
        effectiveType: String,
        customDays: Int,
        points: List<CompletionPoint>,
        zone: ZoneId
    ): Int {
        // Anchor on the earliest completion date, then step forward one
        // period at a time. The set lookup is O(1), so this is linear in the
        // span covered, and the span is capped.
        var earliest: LocalDate? = null
        var latest: LocalDate? = null
        for (point in points) {
            val date = PeriodKeys.localDateOf(point.completedAt, zone)
            if (earliest == null || date.isBefore(earliest)) earliest = date
            if (latest == null || date.isAfter(latest)) latest = date
        }
        val start = earliest ?: return 0
        val end = latest ?: return 0

        var cursor = start
        var run = 0
        var best = 0
        var guard = 0
        while (!cursor.isAfter(end) && guard < MAX_STREAK_WALK) {
            val key = PeriodKeys.keyFor(effectiveType, customDays, cursor)
            if (key.isNotEmpty() && key in activePeriods) {
                run++
                if (run > best) best = run
            } else {
                run = 0
            }
            cursor = PeriodKeys.shift(effectiveType, customDays, cursor, 1)
            guard++
        }
        return best
    }

    // ------------------------------------------------------------------
    // Headline figures
    // ------------------------------------------------------------------

    fun countOnDay(points: List<CompletionPoint>, day: LocalDate, zone: ZoneId): Int {
        val target = day.toEpochDay()
        var count = 0
        for (point in points) if (epochDayOf(point.completedAt, zone) == target) count++
        return count
    }

    /**
     * Rolling completion rate: the share of days in the window with at least
     * one completion.
     *
     * "Completions ÷ tasks" was the obvious alternative and is the wrong
     * measure for this app — tasks are created and deleted continuously, so
     * the denominator moves under you and the rate jumps when you tidy up.
     * Days-with-activity is stable, comparable week to week, and is what a
     * habit tracker is actually asking about.
     */
    fun completionRate(series: DaySeries): Float {
        if (series.size == 0) return 0f
        var active = 0
        for (count in series.counts) if (count > 0) active++
        return active.toFloat() / series.size
    }

    /** Down-samples a long series so a narrow chart draws one bar per pixel column. */
    fun bucket(series: DaySeries, buckets: Int): DaySeries {
        if (buckets <= 0 || series.size <= buckets) return series
        val perBucket = series.size.toFloat() / buckets
        val counts = IntArray(buckets)
        var max = 0
        for (i in 0 until buckets) {
            val from = (i * perBucket).toInt()
            val to = (((i + 1) * perBucket).toInt()).coerceAtMost(series.size)
            var sum = 0
            for (j in from until to) sum += series.counts[j]
            counts[i] = sum
            if (sum > max) max = sum
        }
        return DaySeries(series.startDate, counts, max)
    }

    /**
     * Guard against pathological walks. 4000 periods is ~11 years of days or
     * ~76 years of weeks; nothing real reaches it, and a corrupt timestamp
     * from a restored backup can't spin the loop forever.
     */
    private const val MAX_STREAK_WALK = 4000
}
