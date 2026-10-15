package com.machandler.haptichabits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.machandler.haptichabits.data.TapEvent
import com.machandler.haptichabits.data.TapEventDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CounterViewModel(private val dao: TapEventDao) : ViewModel() {

    /** The running total, or null until the first value has loaded from the database. */
    val total: StateFlow<Long?> = dao.observeTotal()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun recordTap() {
        viewModelScope.launch {
            dao.insert(TapEvent(timestamp = System.currentTimeMillis(), weight = 1))
        }
    }
}
