package com.machandler.haptichabits

import com.machandler.haptichabits.data.AppSettings
import com.machandler.haptichabits.data.HabitButton
import com.machandler.haptichabits.data.TapEvent
import com.machandler.haptichabits.data.TapEventDao
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CounterViewModelTest {
    private lateinit var dao: FakeTapEventDao
    private val settings = MutableStateFlow(AppSettings())
    private val buttons = MutableStateFlow(listOf(PLUS_ONE, WALK))
    private lateinit var viewModel: CounterViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        dao = FakeTapEventDao()
        viewModel = CounterViewModel(dao, buttons, settings)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun nothingToUndoAtStartEvenWithEarlierTaps() = runTest {
        // Taps from a previous session are in the log, but this session hasn't changed anything.
        dao.insert(TapEvent(timestamp = 1_000, weight = 1))
        viewModel = CounterViewModel(dao, buttons, settings)

        assertFalse(viewModel.canUndo.value)
    }

    @Test
    fun tapMakesUndoAvailable() = runTest {
        viewModel.recordTap(PLUS_ONE)
        advanceUntilIdle()

        assertTrue(viewModel.canUndo.value)
        assertEquals(1L, dao.sum())
    }

    @Test
    fun tapRecordsItsWeight() = runTest {
        viewModel.recordTap(WALK)
        advanceUntilIdle()

        assertEquals(-3L, dao.sum())
    }

    @Test
    fun tapRecordsTheButtonAndTheValueItHasNow() = runTest {
        viewModel.recordTap(WALK)
        viewModel.recordTap(WALK.copy(weight = 7)) // value changed in Settings since
        advanceUntilIdle()

        assertEquals(listOf(WALK.id to -3, WALK.id to 7), dao.events().map { it.buttonId to it.weight })
    }

    @Test
    fun exposesTheActiveButtonsOnceLoaded() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.buttons.collect {} }
        assertEquals(listOf(PLUS_ONE, WALK), viewModel.buttons.value)

        buttons.value = emptyList()

        assertEquals(emptyList<HabitButton>(), viewModel.buttons.value)
    }

    @Test
    fun undoWorksAcrossDifferentButtons() = runTest {
        viewModel.recordTap(PLUS_ONE)
        viewModel.recordTap(WALK)
        viewModel.recordTap(PLUS_ONE)
        advanceUntilIdle()
        assertEquals(-1L, dao.sum())

        viewModel.undo()
        advanceUntilIdle()
        assertEquals(-2L, dao.sum())

        viewModel.undo()
        advanceUntilIdle()
        assertEquals(1L, dao.sum())
        assertEquals(listOf(PLUS_ONE.id), dao.events().map { it.buttonId })
    }

    @Test
    fun bothSignsVibrateByDefault() {
        assertTrue(viewModel.vibratesFor(1))
        assertTrue(viewModel.vibratesFor(-1))
    }

    @Test
    fun vibrationFollowsTheSwitchForTheSignAsItChanges() {
        settings.value = AppSettings(vibrateOnPositive = false, vibrateOnNegative = true)

        assertFalse(viewModel.vibratesFor(1))
        assertTrue(viewModel.vibratesFor(-1))

        settings.value = AppSettings(vibrateOnPositive = true, vibrateOnNegative = false)

        assertTrue(viewModel.vibratesFor(1))
        assertFalse(viewModel.vibratesFor(-1))
    }

    @Test
    fun colorsAreUnknownUntilTheSettingsLoad() = runTest {
        val notYetLoaded = MutableSharedFlow<AppSettings>()
        viewModel = CounterViewModel(dao, buttons, notYetLoaded)

        assertNull(viewModel.invertColors.value)
        // Vibration falls back to the defaults meanwhile.
        assertTrue(viewModel.vibratesFor(1))

        notYetLoaded.emit(AppSettings(invertColors = true))

        assertEquals(true, viewModel.invertColors.value)
    }

    @Test
    fun invertColorsFollowsTheSetting() {
        assertEquals(false, viewModel.invertColors.value)

        settings.value = AppSettings(invertColors = true)
        assertEquals(true, viewModel.invertColors.value)

        settings.value = AppSettings(invertColors = false)
        assertEquals(false, viewModel.invertColors.value)
    }

    @Test
    fun totalFollowsPressesAndUndoWithoutAnyCollector() = runTest {
        // Kept current while Settings is showing, so home never comes back to a stale total.
        assertEquals(0L, viewModel.total.value)

        viewModel.recordTap(PLUS_ONE)
        advanceUntilIdle()
        assertEquals(1L, viewModel.total.value)

        viewModel.recordTap(WALK)
        advanceUntilIdle()
        assertEquals(-2L, viewModel.total.value)

        viewModel.undo()
        advanceUntilIdle()
        assertEquals(1L, viewModel.total.value)
    }

    @Test
    fun undoRemovesMostRecentSessionTapFirst() = runTest {
        viewModel.recordTap(PLUS_ONE)
        viewModel.recordTap(PLUS_ONE)
        advanceUntilIdle()
        val (first, second) = dao.ids()

        viewModel.undo()
        advanceUntilIdle()

        assertEquals(listOf(first), dao.ids())
        assertTrue(viewModel.canUndo.value)

        viewModel.undo()
        advanceUntilIdle()

        assertEquals(emptyList<Long>(), dao.ids())
        assertFalse(viewModel.canUndo.value)
        assertEquals(listOf(second, first), dao.deletedIds)
    }

    @Test
    fun undoNeverTouchesTapsFromEarlierSessions() = runTest {
        val earlier = dao.insert(TapEvent(timestamp = 1_000, weight = 1))
        viewModel.recordTap(PLUS_ONE)
        advanceUntilIdle()

        viewModel.undo()
        viewModel.undo() // Nothing left this session; must not remove the earlier tap.
        advanceUntilIdle()

        assertEquals(listOf(earlier), dao.ids())
        assertFalse(viewModel.canUndo.value)
    }

    @Test
    fun undoSkipsSessionTapsThatWereAlreadyDeleted() = runTest {
        viewModel.recordTap(PLUS_ONE)
        viewModel.recordTap(PLUS_ONE)
        advanceUntilIdle()
        val (first, second) = dao.ids()
        dao.deleteById(second) // e.g. deleted from History in a later roadmap step

        viewModel.undo()
        advanceUntilIdle()

        assertEquals(emptyList<Long>(), dao.ids())
        assertFalse(viewModel.canUndo.value)
        assertEquals(listOf(second, first), dao.deletedIds)
    }

    @Test
    fun undoRightAfterTapWaitsForThatTapToBeSaved() = runTest {
        viewModel.recordTap(PLUS_ONE)
        advanceUntilIdle()
        val older = dao.ids().single()

        val gate = CompletableDeferred<Unit>()
        dao.insertGate = gate
        viewModel.recordTap(PLUS_ONE) // Insert is now held up.
        viewModel.undo()
        gate.complete(Unit)
        advanceUntilIdle()

        // The undo removed the newer tap, not the one that was already saved.
        assertEquals(listOf(older), dao.ids())
        assertTrue(viewModel.canUndo.value)
    }
}

private val PLUS_ONE = HabitButton(id = 1, name = "+1", weight = 1, position = 0)
private val WALK = HabitButton(id = 2, name = "Walk", weight = -3, position = 1)

/** In-memory stand-in for the Room DAO, so ViewModel logic can be tested without a database. */
private class FakeTapEventDao : TapEventDao {
    private val events = MutableStateFlow<List<TapEvent>>(emptyList())
    private var nextId = 1L
    val deletedIds = mutableListOf<Long>()

    /** When set, the next insert suspends until it completes. */
    var insertGate: CompletableDeferred<Unit>? = null

    override suspend fun insert(event: TapEvent): Long {
        insertGate?.also { insertGate = null }?.await()
        val id = nextId++
        events.update { it + event.copy(id = id) }
        return id
    }

    override suspend fun deleteById(id: Long): Int {
        val before = events.value.size
        events.update { list -> list.filterNot { it.id == id } }
        val deleted = before - events.value.size
        if (deleted > 0) deletedIds += id
        return deleted
    }

    override fun observeTotal(): Flow<Long> = events.map { list -> list.sumOf { it.weight.toLong() } }

    fun ids(): List<Long> = events.value.map { it.id }

    fun events(): List<TapEvent> = events.value

    fun sum(): Long = events.value.sumOf { it.weight.toLong() }
}
