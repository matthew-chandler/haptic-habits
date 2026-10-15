package com.machandler.haptichabits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.machandler.haptichabits.data.TapEventDao
import com.machandler.haptichabits.data.TrendButton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import java.util.TimeZone

/**
 * Backs Settings -> Trends. The log is loaded with one query and condensed into a [TrendLog] in
 * one pass (off the main thread) each time it changes; switching the granularity or the button
 * only re-cuts the charts from that, with no further queries.
 *
 * Takes the buttons as a Flow (`HabitButtonDao.observeTrendButtons`), like [CounterViewModel]
 * takes the active buttons, so tests need no button DAO. [now], [timeZone] and [firstDayOfWeek]
 * are injectable for tests; by default they follow the phone.
 */
class TrendsViewModel(
    tapDao: TapEventDao,
    trendButtons: Flow<List<TrendButton>>,
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val now: () -> Long = System::currentTimeMillis,
    private val timeZone: () -> TimeZone = TimeZone::getDefault,
    private val firstDayOfWeek: () -> Int = { Calendar.getInstance().firstDayOfWeek },
) : ViewModel() {

    private val granularity = MutableStateFlow(Granularity.Day)

    // Null picks the first button offered.
    private val selectedButtonId = MutableStateFlow<Long?>(null)

    /** Everything the screen shows, or null until loaded. */
    val state: StateFlow<TrendsState?> = combine(
        tapDao.observeTrendEntries().map { buildTrendLog(it, timeZone()) },
        trendButtons,
        granularity,
        selectedButtonId,
    ) { log, buttons, granularity, selectedId ->
        trendsState(log, buttons, granularity, selectedId, today = localDay(now(), log.zone), firstDayOfWeek())
    }
        .flowOn(computeDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setGranularity(granularity: Granularity) {
        this.granularity.value = granularity
    }

    fun selectButton(id: Long) {
        selectedButtonId.value = id
    }
}

/**
 * The Trends screen's content. [totals] and [presses] share [granularity] and the same buckets;
 * [calendar] always covers about the last year by day. [selected] is null only when there is no
 * button to show (then [presses] and [calendar] are null too).
 */
data class TrendsState(
    /** False when the log is empty (no presses or resets at all). */
    val hasEntries: Boolean,
    val granularity: Granularity,
    val totals: TrendSeries,
    val buttons: List<TrendButton>,
    val selected: TrendButton?,
    val presses: TrendSeries?,
    val calendar: ContributionCalendar?,
)

/**
 * Cuts the screen's charts from [log]: the window of buckets ending [today], the total at the end
 * of each, and for the selected button (the one with [selectedId], or else the first of
 * [buttons]) its presses per bucket and its calendar.
 */
fun trendsState(
    log: TrendLog,
    buttons: List<TrendButton>,
    granularity: Granularity,
    selectedId: Long?,
    today: Long,
    firstDayOfWeek: Int,
): TrendsState {
    val buckets = windowBuckets(today, granularity, firstDayOfWeek, log.firstDay)
    val selected = buttons.firstOrNull { it.id == selectedId } ?: buttons.firstOrNull()
    val daily = selected?.let { log.dailyPresses[it.id] }
    return TrendsState(
        hasEntries = !log.isEmpty,
        granularity = granularity,
        totals = totalSeries(log, buckets, granularity, firstDayOfWeek),
        buttons = buttons,
        selected = selected,
        presses = selected?.let { pressSeries(daily, buckets, granularity, firstDayOfWeek) },
        calendar = selected?.let { contributionCalendar(today, firstDayOfWeek, daily) },
    )
}
