package com.leo.checkertic.data.repository

import com.leo.checkertic.data.dao.AnalyticsDao
import com.leo.checkertic.data.dao.CompletionPoint
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

/**
 * Feeds the analytics engine.
 *
 * Exactly one Flow is exposed for the whole feature: the completion points
 * inside the widest window the UI can ask for. Every chart — global, per
 * category, rolling, calendar — is derived from that single list in memory.
 *
 * The alternative (a query per chart) would mean five or six concurrent Room
 * observers, each re-firing on every tick, each triggering its own
 * recomposition. One query, one aggregation pass, one state emission is both
 * simpler and dramatically cheaper.
 */
class AnalyticsRepository(private val analyticsDao: AnalyticsDao) {

    /**
     * Completion points from [windowDays] ago until now.
     *
     * A day of slack is added on the front so a completion logged just before
     * local midnight still lands in the window after a timezone shift.
     */
    fun points(windowDays: Int, zone: ZoneId): Flow<List<CompletionPoint>> {
        val from = LocalDate.now(zone)
            .minusDays(windowDays.toLong() + 1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        return analyticsDao.pointsSince(from)
    }

    /**
     * Streaks need history beyond the visible window — a 400-day streak is
     * still 400 days long when you are looking at the last 30. This asks for
     * enough past to compute one without loading the entire table.
     */
    fun streakPoints(zone: ZoneId): Flow<List<CompletionPoint>> {
        val from = LocalDate.now(zone)
            .minusDays(STREAK_HISTORY_DAYS)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        return analyticsDao.pointsSince(from)
    }

    suspend fun earliestCompletion(): Long? = analyticsDao.earliestCompletion()

    private companion object {
        /** ~5.5 years. Beyond any streak anyone will actually build. */
        const val STREAK_HISTORY_DAYS = 2000L
    }
}
