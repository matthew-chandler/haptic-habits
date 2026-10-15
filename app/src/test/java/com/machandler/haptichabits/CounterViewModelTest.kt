package com.machandler.haptichabits

import com.machandler.haptichabits.data.AppSettings
import com.machandler.haptichabits.data.EntryType
import com.machandler.haptichabits.data.HabitButton
import com.machandler.haptichabits.data.TapEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
    fun deletingTheOnlySessionChangeHidesUndo() = runTest {
        viewModel.recordTap(PLUS_ONE)
        advanceUntilIdle()

        viewModel.deleteEntry(dao.ids().single()) // from History
        advanceUntilIdle()

        assertEquals(emptyList<Long>(), dao.ids())
        assertFalse(viewModel.canUndo.value)
    }

    @Test
    fun deletingEverySessionChangeHidesUndoAndLeavesEarlierTaps() = runTest {
        val earlier = dao.insert(TapEvent(timestamp = 1_000, weight = 1))
        viewModel.recordTap(PLUS_ONE)
        viewModel.recordTap(WALK)
        advanceUntilIdle()
        val (_, first, second) = dao.ids()

        viewModel.deleteEntry(second)
        advanceUntilIdle()
        assertTrue(viewModel.canUndo.value)

        viewModel.deleteEntry(first)
        advanceUntilIdle()
        assertFalse(viewModel.canUndo.value)

        // Nothing left to undo, so a stray press removes nothing.
        viewModel.undo()
        advanceUntilIdle()
        assertEquals(listOf(earlier), dao.ids())
    }

    @Test
    fun undoAfterDeletingTheNewestSessionChangeRemovesTheNewestRemainingOne() = runTest {
        viewModel.recordTap(PLUS_ONE)
        viewModel.recordTap(WALK)
        viewModel.recordTap(PLUS_ONE)
        advanceUntilIdle()
        val (first, second, third) = dao.ids()

        viewModel.deleteEntry(third)
        viewModel.undo()
        advanceUntilIdle()

        assertEquals(listOf(first), dao.ids())
        assertEquals(listOf(third, second), dao.deletedIds)
        assertTrue(viewModel.canUndo.value)
    }

    @Test
    fun undoAfterDeletingAMiddleSessionChangeSkipsIt() = runTest {
        viewModel.recordTap(PLUS_ONE)
        viewModel.recordTap(WALK)
        viewModel.recordTap(PLUS_ONE)
        advanceUntilIdle()
        val (first, second, third) = dao.ids()

        viewModel.deleteEntry(second)
        viewModel.undo()
        advanceUntilIdle()
        assertEquals(listOf(first), dao.ids())

        viewModel.undo()
        advanceUntilIdle()
        assertEquals(emptyList<Long>(), dao.ids())
        assertEquals(listOf(second, third, first), dao.deletedIds)
        assertFalse(viewModel.canUndo.value)
    }

    @Test
    fun deletingAnEarlierSessionsTapKeepsThisSessionsUndo() = runTest {
        val earlier = dao.insert(TapEvent(timestamp = 1_000, weight = 5))
        viewModel.recordTap(PLUS_ONE)
        advanceUntilIdle()

        viewModel.deleteEntry(earlier)
        advanceUntilIdle()

        assertTrue(viewModel.canUndo.value)
        assertEquals(1L, viewModel.total.value)
    }

    @Test
    fun deleteUpdatesTheTotal() = runTest {
        viewModel.recordTap(PLUS_ONE)
        viewModel.recordTap(WALK)
        advanceUntilIdle()
        assertEquals(-2L, viewModel.total.value)

        viewModel.deleteEntry(dao.ids().last())
        advanceUntilIdle()

        assertEquals(1L, viewModel.total.value)
    }

    @Test
    fun resetBringsTheTotalToZeroWithoutDeletingAnything() = runTest {
        viewModel.recordTap(PLUS_ONE)
        viewModel.recordTap(PLUS_ONE)
        advanceUntilIdle()
        val taps = dao.ids()

        viewModel.reset()
        advanceUntilIdle()

        assertEquals(0L, viewModel.total.value)
        assertEquals(3, dao.ids().size)
        assertEquals(taps, dao.ids().take(2))
        assertEquals(emptyList<Long>(), dao.deletedIds)
    }

    @Test
    fun resetRecordsAResetEntryWithNoButtonAndNoWeight() = runTest {
        viewModel.recordTap(WALK)
        advanceUntilIdle()

        viewModel.reset()
        advanceUntilIdle()

        val reset = dao.events().last()
        assertEquals(EntryType.RESET, reset.type)
        assertNull(reset.buttonId)
        assertEquals(0, reset.weight)
    }

    @Test
    fun resetCountsFromZeroAgainForLaterPresses() = runTest {
        viewModel.recordTap(PLUS_ONE)
        viewModel.reset()
        viewModel.recordTap(WALK)
        advanceUntilIdle()

        assertEquals(-3L, viewModel.total.value)
    }

    @Test
    fun resetOfANegativeTotalAlsoGoesToZero() = runTest {
        viewModel.recordTap(WALK)
        viewModel.reset()
        advanceUntilIdle()

        assertEquals(0L, viewModel.total.value)
    }

    @Test
    fun resetOfEarlierSessionsTotalIsUndoable() = runTest {
        dao.insert(TapEvent(timestamp = 1_000, weight = 4))
        viewModel = CounterViewModel(dao, buttons, settings)
        assertFalse(viewModel.canUndo.value)

        viewModel.reset()
        advanceUntilIdle()
        assertEquals(0L, viewModel.total.value)
        assertTrue(viewModel.canUndo.value)

        viewModel.undo()
        advanceUntilIdle()
        assertEquals(4L, viewModel.total.value)
        assertFalse(viewModel.canUndo.value)
        assertTrue(dao.events().none { it.type == EntryType.RESET })
    }

    @Test
    fun undoTakesBackPressesAfterTheResetThenTheResetItself() = runTest {
        viewModel.recordTap(PLUS_ONE)
        viewModel.recordTap(PLUS_ONE)
        viewModel.reset()
        viewModel.recordTap(WALK)
        advanceUntilIdle()
        assertEquals(-3L, viewModel.total.value)

        viewModel.undo()
        advanceUntilIdle()
        assertEquals(0L, viewModel.total.value)

        viewModel.undo()
        advanceUntilIdle()
        assertEquals(2L, viewModel.total.value)
        assertTrue(viewModel.canUndo.value)
    }

    @Test
    fun resetDoesNothingWhenTheTotalIsAlreadyZero() = runTest {
        viewModel.reset()
        advanceUntilIdle()
        assertEquals(emptyList<Long>(), dao.ids())
        assertFalse(viewModel.canUndo.value)

        // Entries that add up to 0 still leave nothing to reset.
        viewModel.recordTap(PLUS_ONE)
        viewModel.recordTap(PLUS_ONE.copy(weight = -1))
        viewModel.reset()
        advanceUntilIdle()
        assertTrue(dao.events().none { it.type == EntryType.RESET })
    }

    @Test
    fun aDoubleConfirmedResetRecordsOneReset() = runTest {
        viewModel.recordTap(PLUS_ONE)
        viewModel.reset()
        viewModel.reset()
        advanceUntilIdle()

        assertEquals(1, dao.events().count { it.type == EntryType.RESET })

        // So one Undo brings the total back.
        viewModel.undo()
        advanceUntilIdle()
        assertEquals(1L, viewModel.total.value)
    }

    @Test
    fun deletingTheResetFromHistoryRestoresTheTotalAndLeavesUndo() = runTest {
        viewModel.recordTap(PLUS_ONE)
        viewModel.reset()
        advanceUntilIdle()
        val reset = dao.ids().last()

        viewModel.deleteEntry(reset)
        advanceUntilIdle()

        assertEquals(1L, viewModel.total.value)
        // Undo now takes back the press, not the deleted reset.
        viewModel.undo()
        advanceUntilIdle()
        assertEquals(0L, viewModel.total.value)
        assertFalse(viewModel.canUndo.value)
        assertEquals(emptyList<Long>(), dao.ids())
    }

    @Test
    fun deletingAnEntryBeforeTheResetKeepsTheTotalAtZero() = runTest {
        viewModel.recordTap(PLUS_ONE)
        viewModel.recordTap(WALK)
        viewModel.reset()
        advanceUntilIdle()
        val (first, _, reset) = dao.ids()

        viewModel.deleteEntry(first)
        advanceUntilIdle()
        assertEquals(0L, viewModel.total.value)

        // Undoing the reset now brings back only what is left before it.
        viewModel.undo()
        advanceUntilIdle()
        assertEquals(-3L, viewModel.total.value)
        assertEquals(listOf(first, reset), dao.deletedIds)
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
