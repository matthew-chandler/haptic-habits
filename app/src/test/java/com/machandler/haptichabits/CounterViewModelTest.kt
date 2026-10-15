package com.machandler.haptichabits

import com.machandler.haptichabits.data.AppSettings
import com.machandler.haptichabits.data.TapEvent
import com.machandler.haptichabits.data.TapEventDao
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CounterViewModelTest {
    private lateinit var dao: FakeTapEventDao
    private val settings = MutableStateFlow(AppSettings())
    private lateinit var viewModel: CounterViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        dao = FakeTapEventDao()
        viewModel = CounterViewModel(dao, settings)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun nothingToUndoAtStartEvenWithEarlierTaps() = runTest {
        // Taps from a previous session are in the log, but this session hasn't changed anything.
        dao.insert(TapEvent(timestamp = 1_000, weight = 1))
        viewModel = CounterViewModel(dao, settings)

        assertFalse(viewModel.canUndo.value)
    }

    @Test
    fun tapMakesUndoAvailable() = runTest {
        viewModel.recordTap(1)
        advanceUntilIdle()

        assertTrue(viewModel.canUndo.value)
        assertEquals(1L, dao.sum())
    }

    @Test
    fun tapRecordsItsWeight() = runTest {
        viewModel.recordTap(-3)
        advanceUntilIdle()

        assertEquals(-3L, dao.sum())
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
    fun undoRemovesMostRecentSessionTapFirst() = runTest {
        viewModel.recordTap(1)
        viewModel.recordTap(1)
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
        viewModel.recordTap(1)
        advanceUntilIdle()

        viewModel.undo()
        viewModel.undo() // Nothing left this session; must not remove the earlier tap.
        advanceUntilIdle()

        assertEquals(listOf(earlier), dao.ids())
        assertFalse(viewModel.canUndo.value)
    }

    @Test
    fun undoSkipsSessionTapsThatWereAlreadyDeleted() = runTest {
        viewModel.recordTap(1)
        viewModel.recordTap(1)
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
        viewModel.recordTap(1)
        advanceUntilIdle()
        val older = dao.ids().single()

        val gate = CompletableDeferred<Unit>()
        dao.insertGate = gate
        viewModel.recordTap(1) // Insert is now held up.
        viewModel.undo()
        gate.complete(Unit)
        advanceUntilIdle()

        // The undo removed the newer tap, not the one that was already saved.
        assertEquals(listOf(older), dao.ids())
        assertTrue(viewModel.canUndo.value)
    }
}

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

    fun sum(): Long = events.value.sumOf { it.weight.toLong() }
}
