package com.machandler.haptichabits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.machandler.haptichabits.data.TapEvent
import com.machandler.haptichabits.data.TapEventDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CounterViewModel(private val dao: TapEventDao) : ViewModel() {

    /** The running total, or null until the first value has loaded from the database. */
    val total: StateFlow<Long?> = dao.observeTotal()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Ids of the event rows added this session, oldest first; undo removes from the end.
     * Held in memory only: it survives rotation along with this ViewModel, and a fresh launch
     * of the app starts with an empty list (so no undo button).
     */
    private val sessionChangeIds = MutableStateFlow<List<Long>>(emptyList())

    /** Whether there is a change from this session that undo can remove. */
    val canUndo: StateFlow<Boolean> = sessionChangeIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Runs writes one at a time, in the order they were requested, so an undo pressed right
    // after +1 waits for that tap's insert (and its id) instead of undoing an older change.
    private val writeLock = Mutex()

    fun recordTap() {
        viewModelScope.launch {
            writeLock.withLock {
                val id = dao.insert(TapEvent(timestamp = System.currentTimeMillis(), weight = 1))
                sessionChangeIds.update { it + id }
            }
        }
    }

    /**
     * Removes the most recent change made this session. A row that is already gone (for example,
     * deleted elsewhere) is skipped and the next most recent one is removed instead.
     */
    fun undo() {
        viewModelScope.launch {
            writeLock.withLock {
                while (sessionChangeIds.value.isNotEmpty()) {
                    val id = sessionChangeIds.value.last()
                    sessionChangeIds.update { it.dropLast(1) }
                    if (dao.deleteById(id) > 0) break
                }
            }
        }
    }
}
