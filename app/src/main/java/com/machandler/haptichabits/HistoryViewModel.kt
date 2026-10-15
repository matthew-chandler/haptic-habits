package com.machandler.haptichabits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.machandler.haptichabits.data.HistoryEntry
import com.machandler.haptichabits.data.TapEventDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Backs Settings -> History: every entry, newest first, with the total right after it. Deleting
 * goes through [CounterViewModel.deleteEntry] instead, which keeps the session's undo list in step.
 */
class HistoryViewModel(
    dao: TapEventDao,
    // Where the running sums are computed: off the main thread, as there can be many thousands.
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    /** All entries, newest first, or null until loaded. */
    val rows: StateFlow<List<HistoryRow>?> = dao.observeHistory()
        .map(::withRunningSums)
        .flowOn(computeDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * One History row: an entry, [sumBefore] (the total right before it) and [sumAfter] (the total
 * right after it, it included). For a tap, sumAfter = sumBefore + its weight; for a reset,
 * sumAfter is 0 and sumBefore is the amount it cleared.
 */
data class HistoryRow(val entry: HistoryEntry, val sumBefore: Long, val sumAfter: Long)

/**
 * Pairs each entry with the running total before and after it. [newestFirst] must be in the
 * canonical order (id descending, as [TapEventDao.observeHistory] returns it); the sums run from
 * the oldest entry and restart at 0 at each reset, so the newest row's [HistoryRow.sumAfter] is
 * always the home total. Done here rather than in SQL because window functions (`SUM() OVER`)
 * need SQLite 3.25 (Android 11+) and a correlated subquery would be quadratic.
 */
fun withRunningSums(newestFirst: List<HistoryEntry>): List<HistoryRow> {
    val before = LongArray(newestFirst.size)
    val after = LongArray(newestFirst.size)
    var sum = 0L
    for (i in newestFirst.indices.reversed()) {
        val entry = newestFirst[i]
        before[i] = sum
        sum = if (entry.isReset) 0 else sum + entry.weight
        after[i] = sum
    }
    return newestFirst.mapIndexed { i, entry -> HistoryRow(entry, before[i], after[i]) }
}
