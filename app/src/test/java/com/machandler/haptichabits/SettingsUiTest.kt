package com.machandler.haptichabits

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.machandler.haptichabits.data.AppSettings
import com.machandler.haptichabits.ui.theme.HapticHabitsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Settings' "Reset total to 0" row and its confirmation, with the settings and total faked. */
@RunWith(RobolectricTestRunner::class)
class SettingsUiTest {
    @get:Rule
    val compose = createComposeRule()

    private var resets = 0
    private var trendsOpened = 0

    @Test
    fun trendsRowSitsAfterHistoryAndOpensTrends() {
        show(total = 3)

        val history = compose.onNodeWithText("History").fetchSemanticsNode().boundsInRoot
        val trends = compose.onNodeWithText("Trends").fetchSemanticsNode().boundsInRoot
        assertTrue(trends.top > history.top)
        compose.onNodeWithText("Trends").performClick()

        assertEquals(1, trendsOpened)
    }

    @Test
    fun resetAsksFirstExplainingWhatHappensThenResets() {
        show(total = 12)

        compose.onNodeWithText("Reset total to 0").performScrollTo().assertIsEnabled().performClick()

        compose.onNodeWithText("Reset total to 0?").assertIsDisplayed()
        compose.onNodeWithText("The total goes from 12 to 0.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Nothing is deleted: History keeps every entry", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Undo on the home screen takes it back", substring = true).assertIsDisplayed()
        compose.onNodeWithText("delete it from History", substring = true).assertIsDisplayed()
        assertEquals(0, resets)

        compose.onNodeWithText("Reset").performClick()

        assertEquals(1, resets)
        compose.onAllNodesWithText("Reset total to 0?").assertCountEquals(0)
    }

    @Test
    fun cancelDoesNotReset() {
        show(total = -4)

        compose.onNodeWithText("Reset total to 0").performScrollTo().performClick()
        compose.onNodeWithText("The total goes from -4 to 0.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()

        assertEquals(0, resets)
        compose.onAllNodesWithText("Reset total to 0?").assertCountEquals(0)
    }

    @Test
    fun resetIsDisabledWhenTheTotalIsAlreadyZero() {
        show(total = 0)

        compose.onNodeWithText("Reset total to 0").performScrollTo().assertIsNotEnabled().performClick()

        compose.onNodeWithText("The total is already 0").assertIsDisplayed()
        compose.onAllNodesWithText("Reset total to 0?").assertCountEquals(0)
        assertEquals(0, resets)
    }

    @Test
    fun resetWaitsForTheSettingsSoItNeverJumps() {
        show(total = 5, settings = null)

        compose.onAllNodesWithText("Reset total to 0").assertCountEquals(0)
    }

    private fun show(total: Long?, settings: AppSettings? = AppSettings()) {
        compose.setContent {
            HapticHabitsTheme {
                SettingsScreen(
                    settings = settings,
                    total = total,
                    onOpenButtons = {},
                    onOpenHistory = {},
                    onOpenTrends = { trendsOpened++ },
                    onVibrateOnPositiveChange = {},
                    onVibrateOnNegativeChange = {},
                    onInvertColorsChange = {},
                    onReset = { resets++ },
                    onBack = {},
                )
            }
        }
    }
}
