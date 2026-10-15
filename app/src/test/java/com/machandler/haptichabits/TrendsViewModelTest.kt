package com.machandler.haptichabits

import com.machandler.haptichabits.data.TapEvent
import com.machandler.haptichabits.data.TrendButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class TrendsViewModelTest {
    private val dao = FakeTapEventDao()
    private val buttons = MutableStateFlow(
        listOf(
            TrendButton(1, "+1", 1, archived = false, presses = 0),
            TrendButton(2, "Walk", -2, archived = false, presses = 0),
            TrendButton(3, "Old habit", 4, archived = true, presses = 1),
        ),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun emptyLogSaysSo() = runTest {
        val viewModel = collected(viewModel())

        val state = viewModel.state.value!!
        assertFalse(state.hasEntries)
        assertEquals(1L, state.selected?.id)
    }

    @Test
    fun totalsAreTheEndOfDayTotalsWithResets() = runTest {
        press(TODAY - 2, weight = 5)
        press(TODAY - 2, weight = 1)
        dao.insert(TapEvent.reset(timestamp = at(TODAY - 1)))
        press(TODAY, weight = 2, button = 2)
        val viewModel = collected(viewModel())

        val totals = viewModel.state.value!!.totals
        assertEquals(listOf(TODAY - 2, TODAY - 1, TODAY), totals.buckets)
        assertEquals(listOf(6L, 0L, 2L), totals.values)
        assertEquals(setOf(1), totals.markers)
        // The last point is the home total.
        assertEquals(dao.sum(), totals.values.last())
    }

    @Test
    fun pressesAreForTheFirstButtonUntilAnotherIsPicked() = runTest {
        press(TODAY - 1, button = 1)
        press(TODAY, button = 1)
        press(TODAY, button = 1)
        press(TODAY, weight = -2, button = 2)
        val viewModel = collected(viewModel())
        assertEquals(listOf(1L, 2L), viewModel.state.value!!.presses?.values)

        viewModel.selectButton(2)
        advanceUntilIdle()

        val state = viewModel.state.value!!
        assertEquals("Walk", state.selected?.name)
        assertEquals(listOf(0L, 1L), state.presses?.values)
        assertEquals(1, state.calendar?.count(TODAY))
        assertEquals(1, state.calendar?.totalPresses)
    }

    @Test
    fun aDeletedButtonsHistoryCanBePicked() = runTest {
        press(TODAY - 3, weight = 4, button = 3)
        val viewModel = collected(viewModel())

        viewModel.selectButton(3)
        advanceUntilIdle()

        val state = viewModel.state.value!!
        assertTrue(state.selected!!.archived)
        assertEquals(1, state.calendar?.count(TODAY - 3))
    }

    @Test
    fun resetsAreNotPresses() = runTest {
        press(TODAY, button = 1)
        dao.insert(TapEvent.reset(timestamp = at(TODAY)))
        press(TODAY, button = 1)
        val viewModel = collected(viewModel())

        assertEquals(listOf(2L), viewModel.state.value!!.presses?.values)
        assertEquals(2, viewModel.state.value!!.calendar?.count(TODAY))
    }

    @Test
    fun switchingGranularityRebucketsBothCharts() = runTest {
        // Today is Thu Oct 15, 2026; weeks start on Sunday here.
        press(TODAY - 40, weight = 3)
        press(TODAY - 5, weight = 2) // Sat Oct 10, the week before
        press(TODAY, weight = 1)
        val viewModel = collected(viewModel())
        assertEquals(30, viewModel.state.value!!.totals.buckets.size)

        viewModel.setGranularity(Granularity.Week)
        advanceUntilIdle()

        val weekly = viewModel.state.value!!
        assertEquals(Granularity.Week, weekly.granularity)
        assertEquals(weekStart(TODAY, Calendar.SUNDAY), weekly.totals.buckets.last())
        assertEquals(weekStart(TODAY - 40, Calendar.SUNDAY), weekly.totals.buckets.first())
        assertEquals(6L, weekly.totals.values.last())
        assertEquals(1L, weekly.presses!!.values.last()) // Sun Oct 11 - Thu Oct 15: one press
        assertEquals(1L, weekly.presses!!.values[weekly.presses!!.values.size - 2])

        viewModel.setGranularity(Granularity.Month)
        advanceUntilIdle()

        val monthly = viewModel.state.value!!
        assertEquals(listOf(monthStart(TODAY - 40), monthStart(TODAY)), monthly.totals.buckets)
        assertEquals(listOf(3L, 6L), monthly.totals.values)
        assertEquals(listOf(1L, 2L), monthly.presses!!.values)
    }

    @Test
    fun theDayWindowIsTheLast30Days() = runTest {
        press(TODAY - 100, weight = 3)
        press(TODAY, weight = 1)
        val viewModel = collected(viewModel())

        val totals = viewModel.state.value!!.totals
        assertEquals(30, totals.buckets.size)
        // Carried over from before the window.
        assertEquals(3L, totals.values.first())
        assertEquals(4L, totals.values.last())
    }

    @Test
    fun aVanishedSelectionFallsBackToTheFirstButton() = runTest {
        val viewModel = collected(viewModel())
        viewModel.selectButton(2)
        advanceUntilIdle()

        buttons.value = buttons.value.filterNot { it.id == 2L }
        advanceUntilIdle()

        assertEquals(1L, viewModel.state.value!!.selected?.id)
    }

    @Test
    fun noButtonsLeavesOnlyTheTotal() = runTest {
        press(TODAY, weight = 1)
        buttons.value = emptyList()
        val viewModel = collected(viewModel())

        val state = viewModel.state.value!!
        assertTrue(state.hasEntries)
        assertNull(state.selected)
        assertNull(state.presses)
        assertNull(state.calendar)
        assertEquals(listOf(1L), state.totals.values)
    }

    @Test
    fun aNewPressUpdatesTheCharts() = runTest {
        press(TODAY, button = 1)
        val viewModel = collected(viewModel())
        assertEquals(listOf(1L), viewModel.state.value!!.presses?.values)

        press(TODAY, button = 1)
        advanceUntilIdle()

        assertEquals(listOf(2L), viewModel.state.value!!.presses?.values)
        assertEquals(listOf(2L), viewModel.state.value!!.totals.values)
    }

    @Test
    fun daysAreInThePhonesTimeZone() = runTest {
        // 03:00 UTC on Oct 15 is still Oct 14 in Los Angeles.
        dao.insert(TapEvent(timestamp = TODAY * DAY + 3 * HOUR, weight = 1, buttonId = 1))
        dao.insert(TapEvent(timestamp = TODAY * DAY + 20 * HOUR, weight = 1, buttonId = 1))
        val viewModel = collected(viewModel(zone = TimeZone.getTimeZone("America/Los_Angeles")))

        val state = viewModel.state.value!!
        assertEquals(listOf(TODAY - 1, TODAY), state.totals.buckets)
        assertEquals(1, state.calendar?.count(TODAY - 1))
        assertEquals(1, state.calendar?.count(TODAY))
    }

    private fun TestScope.viewModel(zone: TimeZone = UTC) = TrendsViewModel(
        dao,
        buttons,
        UnconfinedTestDispatcher(testScheduler),
        now = { TODAY * DAY + 22 * HOUR },
        timeZone = { zone },
        firstDayOfWeek = { Calendar.SUNDAY },
    )

    // The state is shared WhileSubscribed, as the screen collects it.
    private fun TestScope.collected(viewModel: TrendsViewModel): TrendsViewModel {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        return viewModel
    }

    private suspend fun press(day: Long, weight: Int = 1, button: Long = 1) {
        dao.insert(TapEvent(timestamp = at(day), weight = weight, buttonId = button))
    }

    private fun at(day: Long) = day * DAY + 12 * HOUR

    private companion object {
        val UTC: TimeZone = TimeZone.getTimeZone("UTC")
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
        const val TODAY = 20_741L // Thu Oct 15, 2026
    }
}
