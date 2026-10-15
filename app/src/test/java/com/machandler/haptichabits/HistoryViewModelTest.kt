package com.machandler.haptichabits

import com.machandler.haptichabits.data.EntryType
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
    fun sumsRestartAtZeroAtEachReset() {
        val rows = withRunningSums(
            listOf(
                entry(id = 6, weight = 2),
                reset(id = 5),
                entry(id = 4, weight = -1),
                entry(id = 3, weight = 4),
                reset(id = 2),
                entry(id = 1, weight = 5),
            ),
        )

        assertEquals(listOf(2L, 0L, 3L, 4L, 0L, 5L), rows.map { it.sumAfter })
    }

    @Test
    fun aResetsSumBeforeIsTheTotalItCleared() {
        val rows = withRunningSums(listOf(reset(id = 4), entry(id = 3, weight = -9), reset(id = 2), entry(id = 1, weight = 5)))

        assertEquals(listOf(-9L, 0L, 5L, 0L), rows.map { it.sumBefore })
        assertEquals(listOf(0L, -9L, 0L, 5L), rows.map { it.sumAfter })
    }

    @Test
    fun aResetAsTheOldestEntryClearsNothing() {
        val rows = withRunningSums(listOf(entry(id = 2, weight = 3), reset(id = 1)))

        assertEquals(listOf(0L to 3L, 0L to 0L), rows.map { it.sumBefore to it.sumAfter })
    }

    @Test
    fun deletingAResetRecomputesTheLaterSumsFromBeforeIt() = runTest {
        dao.insert(TapEvent(timestamp = 1_000, weight = 5, buttonId = 1))
        val reset = dao.insert(TapEvent.reset(timestamp = 2_000))
        dao.insert(TapEvent(timestamp = 3_000, weight = 1, buttonId = 1))
        val viewModel = collected(HistoryViewModel(dao, UnconfinedTestDispatcher(testScheduler)))
        assertEquals(listOf(3L to 1L, 2L to 0L, 1L to 5L), viewModel.rows.value?.map { it.entry.id to it.sumAfter })
        assertEquals(true, viewModel.rows.value?.get(1)?.entry?.isReset)

        dao.deleteById(reset)
        advanceUntilIdle()

        assertEquals(listOf(3L to 6L, 1L to 5L), viewModel.rows.value?.map { it.entry.id to it.sumAfter })
    }

    @Test
    fun deletingAnEntryBeforeAResetChangesThatResetsClearedAmountOnly() = runTest {
        val tap = dao.insert(TapEvent(timestamp = 1_000, weight = 5, buttonId = 1))
        dao.insert(TapEvent(timestamp = 1_500, weight = 2, buttonId = 1))
        dao.insert(TapEvent.reset(timestamp = 2_000))
        dao.insert(TapEvent(timestamp = 3_000, weight = 1, buttonId = 1))
        val viewModel = collected(HistoryViewModel(dao, UnconfinedTestDispatcher(testScheduler)))
        assertEquals(7L, viewModel.rows.value?.get(1)?.sumBefore)

        dao.deleteById(tap)
        advanceUntilIdle()

        assertEquals(listOf(1L, 0L, 2L), viewModel.rows.value?.map { it.sumAfter })
        assertEquals(2L, viewModel.rows.value?.get(1)?.sumBefore)
    }

    @Test
    fun newestRowsSumIsTheTotalWithResets() = runTest {
        repeat(500) {
            if (it % 97 == 50) {
                dao.insert(TapEvent.reset(timestamp = it.toLong()))
            } else {
                dao.insert(TapEvent(timestamp = it.toLong(), weight = if (it % 3 == 0) -2 else 5, buttonId = 1))
            }
        }
        val viewModel = collected(HistoryViewModel(dao, UnconfinedTestDispatcher(testScheduler)))

        assertEquals(dao.sum(), viewModel.rows.value?.first()?.sumAfter)
    }

    @Test
    fun rowsShowEveryEntryNewestFirstWithItsButtonNameAndSum() = runTest {
        dao.insert(TapEvent(timestamp = 1_000, weight = 1, buttonId = 1))
        dao.insert(TapEvent(timestamp = 2_000, weight = -3, buttonId = 2))
        dao.insert(TapEvent(timestamp = 3_000, weight = 1, buttonId = 1))
        val viewModel = collected(HistoryViewModel(dao, UnconfinedTestDispatcher(testScheduler)))

        assertEquals(
            listOf(
                HistoryRow(HistoryEntry(3, 3_000, 1, "+1"), sumBefore = -2, sumAfter = -1),
                HistoryRow(HistoryEntry(2, 2_000, -3, "Walk"), sumBefore = 1, sumAfter = -2),
                HistoryRow(HistoryEntry(1, 1_000, 1, "+1"), sumBefore = 0, sumAfter = 1),
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

    private fun reset(id: Long) =
        HistoryEntry(id = id, timestamp = id * 1_000, weight = 0, buttonName = null, type = EntryType.RESET)
}
