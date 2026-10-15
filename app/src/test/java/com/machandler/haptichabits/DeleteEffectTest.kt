package com.machandler.haptichabits

import com.machandler.haptichabits.data.EntryType
import com.machandler.haptichabits.data.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Test

/** The last sentence of History's delete confirmation: how the current (home) total changes. */
class DeleteEffectTest {

    @Test
    fun deletingAPositiveTapWithNoResetsTakesItsValueOff() {
        val rows = rows(tap(id = 2, weight = 3), tap(id = 1, weight = 1))

        assertEquals("The total goes down by 3.", deleteEffect(rows, 0))
        assertEquals("The total goes down by 1.", deleteEffect(rows, 1))
    }

    @Test
    fun deletingANegativeTapPutsItsValueBack() {
        val rows = rows(tap(id = 1, weight = -2))

        assertEquals("The total goes up by 2.", deleteEffect(rows, 0))
    }

    @Test
    fun deletingATapAfterTheLatestResetChangesTheTotal() {
        val rows = rows(tap(id = 3, weight = 4), reset(id = 2), tap(id = 1, weight = 9))

        assertEquals("The total goes down by 4.", deleteEffect(rows, 0))
    }

    @Test
    fun deletingATapBeforeTheLatestResetLeavesTheTotal() {
        val rows = rows(tap(id = 3, weight = 4), reset(id = 2), tap(id = 1, weight = 9))

        assertEquals("It's older than the latest reset, so the current total doesn't change.", deleteEffect(rows, 2))
    }

    @Test
    fun deletingAnOlderResetLeavesTheTotal() {
        val rows = rows(reset(id = 4), tap(id = 3, weight = 2), reset(id = 2), tap(id = 1, weight = 9))

        assertEquals("It's older than the latest reset, so the current total doesn't change.", deleteEffect(rows, 2))
    }

    @Test
    fun deletingTheLatestResetWithNothingAfterItGoesBackToTheClearedTotal() {
        val rows = rows(reset(id = 3), tap(id = 2, weight = 5), tap(id = 1, weight = 7))

        assertEquals("The total goes back to 12.", deleteEffect(rows, 0))
    }

    @Test
    fun deletingTheLatestResetOfANegativeTotalGoesBackToIt() {
        val rows = rows(reset(id = 2), tap(id = 1, weight = -4))

        assertEquals("The total goes back to -4.", deleteEffect(rows, 0))
    }

    @Test
    fun deletingTheLatestResetWithEntriesAfterItAddsBackWhatItCleared() {
        val rows = rows(tap(id = 4, weight = 2), reset(id = 3), tap(id = 2, weight = 5), tap(id = 1, weight = 7))

        assertEquals("The total goes up by 12, to 14.", deleteEffect(rows, 1))
    }

    @Test
    fun deletingTheLatestResetOfANegativeTotalWithEntriesAfterItTakesItOff() {
        val rows = rows(tap(id = 3, weight = 1), reset(id = 2), tap(id = 1, weight = -6))

        assertEquals("The total goes down by 6, to -5.", deleteEffect(rows, 1))
    }

    @Test
    fun deletingTheLatestResetCountsOnlyBackToTheResetBeforeIt() {
        val rows = rows(reset(id = 4), tap(id = 3, weight = 2), reset(id = 2), tap(id = 1, weight = 9))

        assertEquals("The total goes back to 2.", deleteEffect(rows, 0))
    }

    @Test
    fun deletingAResetThatClearedNothingLeavesTheTotal() {
        val rows = rows(tap(id = 3, weight = 1), reset(id = 2), reset(id = 1))

        assertEquals("The current total doesn't change.", deleteEffect(rows, 1))
    }

    private fun rows(vararg newestFirst: HistoryEntry) = withRunningSums(newestFirst.toList())

    private fun tap(id: Long, weight: Int) =
        HistoryEntry(id = id, timestamp = id * 1_000, weight = weight, buttonName = "+1")

    private fun reset(id: Long) =
        HistoryEntry(id = id, timestamp = id * 1_000, weight = 0, buttonName = null, type = EntryType.RESET)
}
