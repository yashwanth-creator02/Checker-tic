package com.leo.checkertic.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields

/**
 * ============================================================================
 *  RECURRENCE PERIODS — one definition, two consumers
 * ============================================================================
 *
 * This logic used to live privately inside `TaskRepository`, where it decided
 * when a category's checkboxes reset. Streak analytics needs exactly the same
 * notion of "a period" — a daily category breaks on a missed *day*, a weekly
 * one on a missed *week* — so it was lifted out here rather than
 * reimplemented. Two copies of this would drift, and the symptom would be
 * streaks that disagree with the resets the user actually sees.
 *
 * Deliberately free of Android and Room imports so it is unit-testable as
 * plain Kotlin.
 */
object PeriodKeys {

    const val ONCE = "once"
    const val DAILY = "daily"
    const val WEEKLY = "weekly"
    const val MONTHLY = "monthly"
    const val CUSTOM = "custom"

    val ALL_TYPES = listOf(ONCE, DAILY, WEEKLY, MONTHLY, CUSTOM)

    private val dailyFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val monthlyFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

    /**
     * Derives the period key a [date] falls in for a given recurrence rule.
     *
     * - `once`    -> `""` (never resets, so it has no periods)
     * - `daily`   -> `2026-09-19`
     * - `weekly`  -> `2026-W38` (ISO week, Monday-based)
     * - `monthly` -> `2026-09`
     * - `custom`  -> `epoch/<N>/<bucket>` where bucket = daysSinceEpoch / N
     *
     * Weekly uses [WeekFields.ISO] explicitly rather than the `"YYYY-ww"`
     * pattern the original used. That pattern silently follows the device
     * locale's first-day-of-week, so the same completion history produced a
     * different streak in the US than in India. ISO weeks are stable
     * everywhere, which is what a streak needs to be.
     */
    fun keyFor(
        recurrenceType: String,
        customDays: Int,
        date: LocalDate
    ): String = when (recurrenceType) {
        DAILY -> date.format(dailyFormatter)
        WEEKLY -> {
            val wf = WeekFields.ISO
            val week = date.get(wf.weekOfWeekBasedYear())
            val year = date.get(wf.weekBasedYear())
            "%04d-W%02d".format(year, week)
        }
        MONTHLY -> date.format(monthlyFormatter)
        CUSTOM -> {
            if (customDays <= 0) "" else {
                val bucket = ChronoUnit.DAYS.between(LocalDate.EPOCH, date) / customDays
                "epoch/$customDays/$bucket"
            }
        }
        else -> ""
    }

    /**
     * Steps a period key back by [periods] whole periods.
     *
     * Streak walking needs "what would the key have been one period before
     * this one?", and deriving that from a date is far less error-prone than
     * parsing and decrementing the key string — especially across year
     * boundaries, where ISO week 1 can belong to the previous calendar year.
     */
    fun shift(
        recurrenceType: String,
        customDays: Int,
        from: LocalDate,
        periods: Long
    ): LocalDate = when (recurrenceType) {
        DAILY -> from.plusDays(periods)
        WEEKLY -> from.plusWeeks(periods)
        MONTHLY -> from.plusMonths(periods)
        CUSTOM -> from.plusDays(periods * customDays.coerceAtLeast(1))
        else -> from.plusDays(periods)
    }

    /** Human label for a recurrence rule, used in settings and analytics. */
    fun label(recurrenceType: String, customDays: Int): String = when (recurrenceType) {
        DAILY -> "Daily"
        WEEKLY -> "Weekly"
        MONTHLY -> "Monthly"
        CUSTOM -> if (customDays > 0) "Every ${customDays}d" else "Custom"
        else -> "Once"
    }

    /** The unit a streak for this rule is counted in. */
    fun streakUnit(recurrenceType: String, customDays: Int): String = when (recurrenceType) {
        WEEKLY -> "weeks"
        MONTHLY -> "months"
        CUSTOM -> if (customDays > 0) "× ${customDays}d" else "periods"
        else -> "days"
    }

    fun localDateOf(epochMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
}
