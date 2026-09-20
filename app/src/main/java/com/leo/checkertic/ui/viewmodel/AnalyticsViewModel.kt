package com.leo.checkertic.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.leo.checkertic.analytics.AnalyticsEngine
import com.leo.checkertic.analytics.AnalyticsWindow
import com.leo.checkertic.analytics.CategoryStat
import com.leo.checkertic.analytics.GlobalAnalytics
import com.leo.checkertic.data.AppDatabase
import com.leo.checkertic.data.repository.AnalyticsRepository
import com.leo.checkertic.data.repository.CategoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

/**
 * ============================================================================
 *  ANALYTICS VIEW MODEL
 * ============================================================================
 *
 * Drives the global analytics screen.
 *
 * ## One query, one pass, one emission
 *
 * There are six visualisations on this screen. A naive build would give each
 * its own Room query and its own state, meaning six observers all re-firing
 * on every completion and six separate recompositions.
 *
 * Instead: a single flow of completion points feeds one aggregation pass that
 * produces one [GlobalAnalytics]. The whole screen updates in one frame or
 * not at all.
 *
 * The aggregation runs on `Dispatchers.Default` — CPU work with no I/O, which
 * is exactly what the Default pool is sized for, and exactly what should
 * never touch the main thread. On a heavy dataset (several years of
 * completions) this is a handful of milliseconds; on the main thread it would
 * be several dropped frames every time a checkbox moved.
 */
class AnalyticsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val analyticsRepo = AnalyticsRepository(db.analyticsDao())
    private val categoryRepo = CategoryRepository(db.categoryDao())
    private val noteDao = db.noteDao()

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val _window = MutableStateFlow(AnalyticsWindow.Default)
    val window: StateFlow<AnalyticsWindow> = _window

    /** null = every category. Drives both heatmaps. */
    private val _categoryFilter = MutableStateFlow<Long?>(null)
    val categoryFilter: StateFlow<Long?> = _categoryFilter

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<GlobalAnalytics> = _window
        .flatMapLatest { window ->
            val noteFrom = LocalDate.now(zone)
                .minusDays(window.days.toLong())
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()

            combine(
                analyticsRepo.streakPoints(zone),
                categoryRepo.getAllOrdered(),
                noteDao.activitySince(noteFrom),
                _categoryFilter
            ) { points, categories, noteActivity, filter ->
                val today = LocalDate.now(zone)
                val windowStart = today.minusDays((window.days - 1).toLong())
                val windowStartMillis = windowStart.atStartOfDay(zone).toInstant().toEpochMilli()

                val calendar = AnalyticsEngine.dailySeries(
                    points = points,
                    startDate = windowStart,
                    days = window.days,
                    zone = zone,
                    categoryFilter = filter
                )

                val stats = categories.map { category ->
                    val streak = AnalyticsEngine.streakFor(
                        points = points,
                        categoryId = category.id,
                        recurrenceType = category.recurrenceType,
                        customDays = category.recurrenceCustomDays,
                        zone = zone,
                        today = today
                    )
                    CategoryStat(
                        categoryId = category.id,
                        name = category.name,
                        recurrenceType = category.recurrenceType,
                        recurrenceCustomDays = category.recurrenceCustomDays,
                        completionsInWindow = countInWindow(
                            points, category.id, windowStartMillis
                        ),
                        streak = streak,
                        locked = category.locked
                    )
                }

                // "Best current streak" means the longest one still running.
                // A dead 90-day streak is history, not a headline.
                val best = stats.filter { it.streak.current > 0 }
                    .maxByOrNull { it.streak.current }

                GlobalAnalytics(
                    completedToday = AnalyticsEngine.countOnDay(points, today, zone),
                    bestCurrentStreak = best?.streak?.current ?: 0,
                    bestCurrentStreakCategory = best?.name,
                    bestStreakUnit = best?.streak?.unit ?: "days",
                    completionRate = AnalyticsEngine.completionRate(calendar),
                    totalInWindow = calendar.counts.sum(),
                    calendar = calendar,
                    timeOfDay = AnalyticsEngine.timeOfDayMatrix(
                        points = points,
                        zone = zone,
                        from = windowStartMillis,
                        categoryFilter = filter
                    ),
                    categories = stats,
                    noteActivity = AnalyticsEngine.dailySeriesOfTimestamps(
                        timestamps = noteActivity,
                        startDate = windowStart,
                        days = window.days,
                        zone = zone
                    ),
                    loading = false
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GlobalAnalytics.Empty)

    private fun countInWindow(
        points: List<com.leo.checkertic.data.dao.CompletionPoint>,
        categoryId: Long,
        from: Long
    ): Int {
        var count = 0
        for (point in points) {
            if (point.categoryId == categoryId && point.completedAt >= from) count++
        }
        return count
    }

    fun setWindow(window: AnalyticsWindow) {
        _window.value = window
    }

    fun setCategoryFilter(categoryId: Long?) {
        _categoryFilter.value = categoryId
    }
}
