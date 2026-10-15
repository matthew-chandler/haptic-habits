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

/** One History row: an entry and [sumAfter], the total right after it (it included). */
data class HistoryRow(val entry: HistoryEntry, val sumAfter: Long)

/**
 * Pairs each entry with the running total up to and including it. [newestFirst] must be in the
 * canonical order (id descending, as [TapEventDao.observeHistory] returns it); the sums run from
 * the oldest entry. Done here rather than in SQL because window functions (`SUM() OVER`) need
 * SQLite 3.25 (Android 11+) and a correlated subquery would be quadratic.
 */
fun withRunningSums(newestFirst: List<HistoryEntry>): List<HistoryRow> {
    val sums = LongArray(newestFirst.size)
    var sum = 0L
    for (i in newestFirst.indices.reversed()) {
        sum += newestFirst[i].weight
        sums[i] = sum
    }
    return newestFirst.mapIndexed { i, entry -> HistoryRow(entry, sums[i]) }
}
