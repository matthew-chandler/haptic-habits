package com.machandler.haptichabits

import android.text.format.DateUtils
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.machandler.haptichabits.data.HistoryEntry
import com.machandler.haptichabits.ui.theme.HapticHabitsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Settings -> History, with its rows faked. */
@RunWith(RobolectricTestRunner::class)
class HistoryUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val deleted = mutableListOf<Long>()
    private val now = System.currentTimeMillis()

    @Test
    fun emptyHistorySaysSo() {
        show(emptyList())

        compose.onNodeWithText("No entries yet", substring = true).assertIsDisplayed()
    }

    @Test
    fun rowsShowNameValueAndTheTotalAfterThem() {
        show(
            listOf(
                HistoryEntry(id = 2, timestamp = now, weight = -2, buttonName = "Walk"),
                HistoryEntry(id = 1, timestamp = now, weight = 3, buttonName = "Snack"),
            ),
        )

        compose.onNodeWithText("Walk").assertIsDisplayed()
        compose.onNodeWithText("-2").assertIsDisplayed()
        compose.onNodeWithText("Total 1").assertIsDisplayed()
        compose.onNodeWithText("Snack").assertIsDisplayed()
        compose.onNodeWithText("+3").assertIsDisplayed()
        compose.onNodeWithText("Total 3").assertIsDisplayed()
    }

    @Test
    fun rowsAreGroupedUnderOneHeaderPerDay() {
        val yesterday = now - DateUtils.DAY_IN_MILLIS
        show(
            listOf(
                HistoryEntry(id = 4, timestamp = now, weight = 1, buttonName = "+1"),
                HistoryEntry(id = 3, timestamp = now, weight = 1, buttonName = "+1"),
                HistoryEntry(id = 2, timestamp = yesterday, weight = 1, buttonName = "+1"),
                HistoryEntry(id = 1, timestamp = yesterday, weight = 1, buttonName = "+1"),
            ),
        )

        compose.onAllNodesWithText("Today").assertCountEquals(1)
        compose.onAllNodesWithText("Yesterday").assertCountEquals(1)
    }

    @Test
    fun entryWithoutAButtonStillShows() {
        show(listOf(HistoryEntry(id = 1, timestamp = now, weight = 1, buttonName = null)))

        compose.onNodeWithText("(no button)").assertIsDisplayed()
    }

    @Test
    fun deleteAsksFirstSayingWhatGoesThenDeletesThatEntry() {
        show(
            listOf(
                HistoryEntry(id = 7, timestamp = now, weight = 3, buttonName = "Snack"),
                HistoryEntry(id = 6, timestamp = now, weight = 1, buttonName = "+1"),
            ),
        )

        compose.onAllNodesWithContentDescription("Delete Snack", substring = true)[0].performClick()

        compose.onNodeWithText("Delete entry?").assertIsDisplayed()
        compose.onNodeWithText("“Snack” (+3) from", substring = true).assertIsDisplayed()
        compose.onNodeWithText("The total goes down by 3.", substring = true).assertIsDisplayed()
        assertEquals(emptyList<Long>(), deleted)

        compose.onNodeWithText("Delete").performClick()

        assertEquals(listOf(7L), deleted)
        compose.onAllNodesWithText("Delete entry?").assertCountEquals(0)
    }

    @Test
    fun deletingANegativeEntrySaysTheTotalGoesUp() {
        show(listOf(HistoryEntry(id = 1, timestamp = now, weight = -2, buttonName = "Walk")))

        compose.onAllNodesWithContentDescription("Delete Walk", substring = true)[0].performClick()

        compose.onNodeWithText("The total goes up by 2.", substring = true).assertIsDisplayed()
    }

    @Test
    fun cancelDeletesNothing() {
        show(listOf(HistoryEntry(id = 1, timestamp = now, weight = 3, buttonName = "Snack")))

        compose.onAllNodesWithContentDescription("Delete Snack", substring = true)[0].performClick()
        compose.onNodeWithText("Cancel").performClick()

        assertEquals(emptyList<Long>(), deleted)
        compose.onAllNodesWithText("Delete entry?").assertCountEquals(0)
    }

    private fun show(newestFirst: List<HistoryEntry>) {
        compose.setContent {
            HapticHabitsTheme {
                HistoryScreen(
                    rows = withRunningSums(newestFirst),
                    invertColors = false,
                    onDelete = { deleted += it },
                    onBack = {},
                )
            }
        }
    }
}
