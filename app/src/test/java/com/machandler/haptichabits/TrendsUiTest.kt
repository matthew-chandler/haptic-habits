package com.machandler.haptichabits

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.machandler.haptichabits.data.EntryType
import com.machandler.haptichabits.data.TrendButton
import com.machandler.haptichabits.data.TrendEntry
import com.machandler.haptichabits.ui.theme.HapticHabitsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar
import java.util.TimeZone

/**
 * Settings -> Trends with a real [TrendLog] and [trendsState] behind it, and the granularity and
 * button selection held in the test as the ViewModel would hold them.
 */
@RunWith(RobolectricTestRunner::class)
class TrendsUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = TimeZone.getDefault()
    private val now = System.currentTimeMillis()
    private val today = localDay(now, zone)
    private val day = 24 * 3_600_000L

    private val buttons = listOf(
        TrendButton(1, "Snack", 3, archived = false, presses = 3),
        TrendButton(2, "Walk", -2, archived = false, presses = 1),
        TrendButton(9, "Nail biting", 1, archived = true, presses = 2),
    )
    private val entries = listOf(
        TrendEntry(1, now - 2 * day, 3, 1),
        TrendEntry(2, now - 2 * day, 1, 9),
        TrendEntry(3, now - day, 0, null, EntryType.RESET),
        TrendEntry(4, now - day, 3, 1),
        TrendEntry(5, now, 3, 1),
        TrendEntry(6, now, -2, 2),
        TrendEntry(7, now, 1, 9),
    )

    @Test
    fun emptyLogSaysSo() {
        show(emptyList())

        compose.onNodeWithText("No entries yet", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("Total over time").assertCountEquals(0)
    }

    @Test
    fun nothingShowsWhileLoading() {
        compose.setContent {
            HapticHabitsTheme {
                TrendsScreen(state = null, invertColors = false, onGranularityChange = {}, onSelectButton = {}, onBack = {})
            }
        }

        compose.onNodeWithText("Trends").assertIsDisplayed()
        compose.onAllNodesWithText("No entries yet", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Total over time").assertCountEquals(0)
    }

    @Test
    fun withDataShowsTheTotalThePressesAndTheCalendar() {
        show(entries)

        compose.onNodeWithText("Total over time").assertIsDisplayed()
        // Three days: 4, then 0 + 3 after the reset, then 3 + 3 - 2 + 1.
        compose.onNodeWithContentDescription("Total at the end of each day", substring = true)
            .assertDescriptionContains("lowest 3, highest 5, latest 5. 1 day with a reset.")
        compose.onNodeWithText("Day").assertIsSelected()
        compose.onNodeWithText("Snack").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Presses of Snack per day", substring = true)
            .performScrollTo()
            .assertDescriptionContains("lowest 1, highest 1, latest 1.")
        compose.onNodeWithContentDescription("Calendar of Snack presses per day", substring = true)
            .performScrollTo()
            .assertDescriptionContains("3 presses on 3 days, at most 1 in a day.")
        compose.onNodeWithText("Tap a day to see how many presses it had.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun switchingGranularityRedrawsBothCharts() {
        show(entries)

        compose.onNodeWithText("Week").performClick()

        compose.onNodeWithText("Week").assertIsSelected()
        compose.onNodeWithContentDescription("Total at the end of each week", substring = true).assertExists()
        compose.onNodeWithContentDescription("Presses of Snack per week", substring = true).assertExists()

        compose.onNodeWithText("Month").performClick()

        // Everything is in this month: a single point.
        compose.onNodeWithContentDescription("Total at the end of each month", substring = true)
            .assertDescriptionContains(": 5. 1 month with a reset.")
        compose.onNodeWithText("The total at the end of each month", substring = true).assertExists()
    }

    @Test
    fun theButtonPickerListsDeletedButtonsAndSwitchesTheCharts() {
        show(entries)

        compose.onNodeWithContentDescription("Choose a button").performScrollTo().performClick()
        compose.onNodeWithText("Walk").assertIsDisplayed()
        compose.onNodeWithText("Nail biting (deleted)").assertIsDisplayed()
        compose.onNodeWithText("+1 · 2 presses").assertIsDisplayed()
        compose.onNodeWithText("Nail biting (deleted)").performClick()

        compose.onNodeWithText("Nail biting (deleted)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Presses of Nail biting per day", substring = true)
            .assertDescriptionContains("highest 1, latest 1.")
        compose.onNodeWithContentDescription("Calendar of Nail biting presses per day", substring = true)
            .performScrollTo()
            .assertDescriptionContains("2 presses on 2 days")
    }

    @Test
    fun tappingTodayInTheCalendarShowsItsCount() {
        show(entries)
        // Scrolled into view through the legend below it: scrolling to the calendar itself would
        // also scroll it sideways to its oldest weeks, away from today.
        compose.onNodeWithText("Less").performScrollTo()
        val calendar = compose.onNodeWithContentDescription("Calendar of Snack", substring = true)
        val row = Math.floorMod(dayOfWeek(today) - Calendar.getInstance().firstDayOfWeek, 7)

        calendar.performTouchInput {
            // The centre of today's cell, in the newest (rightmost) column, which is showing: the
            // touch area is the calendar's visible part. 15 dp per cell (12 dp + gap), below the
            // 16 dp row of month names.
            click(Offset(right - (15 - 6).dp.toPx(), (16 + row * 15 + 6).dp.toPx()))
        }

        compose.onNodeWithText(": 1 press", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun noButtonsToShowSaysSo() {
        show(entries, buttons = emptyList())

        compose.onNodeWithText("Total over time").assertIsDisplayed()
        compose.onNodeWithText("No buttons to show", substring = true).performScrollTo().assertIsDisplayed()
    }

    private fun show(entries: List<TrendEntry>, buttons: List<TrendButton> = this.buttons) {
        val log = buildTrendLog(entries, zone)
        compose.setContent {
            var granularity by remember { mutableStateOf(Granularity.Day) }
            var selectedId by remember { mutableStateOf<Long?>(null) }
            HapticHabitsTheme {
                TrendsScreen(
                    state = trendsState(log, buttons, granularity, selectedId, today, Calendar.getInstance().firstDayOfWeek),
                    invertColors = false,
                    onGranularityChange = { granularity = it },
                    onSelectButton = { selectedId = it },
                    onBack = {},
                )
            }
        }
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDescriptionContains(text: String) = apply {
        val descriptions = fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription]
        assertEquals("“$text” in $descriptions", true, descriptions.any { text in it })
    }
}
