package com.machandler.haptichabits

import com.machandler.haptichabits.data.HistoryEntry
import com.machandler.haptichabits.data.TapEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val dao = FakeTapEventDao(buttonNames = mapOf(1L to "+1", 2L to "Walk"))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun runningSumsOfNothingIsNothing() {
        assertEquals(emptyList<HistoryRow>(), withRunningSums(emptyList()))
    }

    @Test
    fun eachRowGetsTheTotalUpToAndIncludingIt() {
        val rows = withRunningSums(listOf(entry(id = 3, weight = 5), entry(id = 2, weight = -3), entry(id = 1, weight = 1)))

        assertEquals(listOf(3L to 3L, 2L to -2L, 1L to 1L), rows.map { it.entry.id to it.sumAfter })
    }

    @Test
    fun sumsFollowTheListOrderNotTheTimestamps() {
        // The clock went back between the two presses: the newer entry has the older timestamp.
        val rows = withRunningSums(listOf(entry(id = 2, weight = 5, timestamp = 1_000), entry(id = 1, weight = 1, timestamp = 9_000)))

        assertEquals(listOf(6L, 1L), rows.map { it.sumAfter })
    }

    @Test
    fun rowsShowEveryEntryNewestFirstWithItsButtonNameAndSum() = runTest {
        dao.insert(TapEvent(timestamp = 1_000, weight = 1, buttonId = 1))
        dao.insert(TapEvent(timestamp = 2_000, weight = -3, buttonId = 2))
        dao.insert(TapEvent(timestamp = 3_000, weight = 1, buttonId = 1))
        val viewModel = collected(HistoryViewModel(dao, UnconfinedTestDispatcher(testScheduler)))

        assertEquals(
            listOf(
                HistoryRow(HistoryEntry(3, 3_000, 1, "+1"), sumAfter = -1),
                HistoryRow(HistoryEntry(2, 2_000, -3, "Walk"), sumAfter = -2),
                HistoryRow(HistoryEntry(1, 1_000, 1, "+1"), sumAfter = 1),
            ),
            viewModel.rows.value,
        )
    }

    @Test
    fun deletingAnEntryRecomputesTheSumsOfLaterRowsOnly() = runTest {
        dao.insert(TapEvent(timestamp = 1_000, weight = 1, buttonId = 1))
        val walk = dao.insert(TapEvent(timestamp = 2_000, weight = -3, buttonId = 2))
        dao.insert(TapEvent(timestamp = 3_000, weight = 1, buttonId = 1))
        dao.insert(TapEvent(timestamp = 4_000, weight = 1, buttonId = 1))
        val viewModel = collected(HistoryViewModel(dao, UnconfinedTestDispatcher(testScheduler)))
        assertEquals(listOf(4L to 0L, 3L to -1L, 2L to -2L, 1L to 1L), viewModel.rows.value?.map { it.entry.id to it.sumAfter })

        dao.deleteById(walk)
        advanceUntilIdle()

        assertEquals(listOf(4L to 3L, 3L to 2L, 1L to 1L), viewModel.rows.value?.map { it.entry.id to it.sumAfter })
    }

    @Test
    fun emptyLogGivesNoRows() = runTest {
        val viewModel = collected(HistoryViewModel(dao, UnconfinedTestDispatcher(testScheduler)))

        assertEquals(emptyList<HistoryRow>(), viewModel.rows.value)
    }

    @Test
    fun newestRowsSumIsTheTotal() = runTest {
        repeat(500) { dao.insert(TapEvent(timestamp = it.toLong(), weight = if (it % 3 == 0) -2 else 5, buttonId = 1)) }
        val viewModel = collected(HistoryViewModel(dao, UnconfinedTestDispatcher(testScheduler)))

        assertEquals(dao.sum(), viewModel.rows.value?.first()?.sumAfter)
    }

    // The rows are shared WhileSubscribed, as the screen collects them.
    private fun TestScope.collected(viewModel: HistoryViewModel): HistoryViewModel {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.rows.collect {} }
        return viewModel
    }

    private fun entry(id: Long, weight: Int, timestamp: Long = id * 1_000) =
        HistoryEntry(id = id, timestamp = timestamp, weight = weight, buttonName = "+1")
}
