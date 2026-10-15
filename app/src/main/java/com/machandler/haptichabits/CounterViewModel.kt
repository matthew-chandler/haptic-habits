package com.machandler.haptichabits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.machandler.haptichabits.data.AppSettings
import com.machandler.haptichabits.data.HabitButton
import com.machandler.haptichabits.data.TapEvent
import com.machandler.haptichabits.data.TapEventDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CounterViewModel(
    private val dao: TapEventDao,
    buttons: Flow<List<HabitButton>>,
    settings: Flow<AppSettings>,
) : ViewModel() {

    /**
     * The running total, or null until the first value has loaded from the database. Kept up to
     * date even while Settings is showing, so the home screen comes back with the current total
     * rather than a stale one that then changes (which would flash).
     */
    val total: StateFlow<Long?> = dao.observeTotal()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The active buttons in display order, or null until loaded (so the empty hint never flashes). */
    val buttons: StateFlow<List<HabitButton>?> = buttons
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

    // Collected from creation so the switches are known by the first press. Null until the
    // stored values load (a few ms after launch).
    private val settings: StateFlow<AppSettings?> = settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Whether value colors are swapped (positive red, negative green), or null until the settings
     * have loaded, so the buttons can wait instead of showing the wrong colors first.
     */
    val invertColors: StateFlow<Boolean?> = this.settings
        .map { it?.invertColors }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Whether a press worth [weight] should vibrate, per the Settings switch for its sign (the
     * defaults until the settings have loaded). The caller plays the vibration, which keeps this
     * class free of Android services.
     */
    fun vibratesFor(weight: Int): Boolean = (settings.value ?: AppSettings()).vibratesFor(weight)

    // Runs writes one at a time, in the order they were requested, so an undo pressed right
    // after +1 waits for that tap's insert (and its id) instead of undoing an older change.
    private val writeLock = Mutex()

    /** Records a press of [button], with the value it has right now. */
    fun recordTap(button: HabitButton) {
        viewModelScope.launch {
            writeLock.withLock {
                val event = TapEvent(timestamp = System.currentTimeMillis(), weight = button.weight, buttonId = button.id)
                val id = dao.insert(event)
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
