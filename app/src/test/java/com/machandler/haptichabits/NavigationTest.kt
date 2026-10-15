package com.machandler.haptichabits

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Runs the real activity, so keep this to a single test: the database and settings file are
// process-wide singletons that would otherwise carry over between tests.
@RunWith(RobolectricTestRunner::class)
class NavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun gearOpensSettingsWhereSwitchesToggleButtonsAndHistoryOpenAndBackReturnsHome() {
        // A fresh install starts with the default button.
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("+1").assertIsDisplayed() }.isSuccess }
        compose.onNodeWithText("+1").performClick()
        compose.waitUntil(5_000) { isShown("Undo") }

        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitUntil(5_000) { positiveSwitch().isOn() }
        compose.onNodeWithText("Vibrate on negative presses").assertIsDisplayed().assertIsOn()
        compose.onNodeWithText("Invert colors").assertIsDisplayed().assertIsOff()

        positiveSwitch().performClick()
        // The write goes through DataStore off the main thread, so wait for it to come back.
        compose.waitUntil(5_000) { !positiveSwitch().isOn() }
        compose.onNodeWithText("Vibrate on negative presses").assertIsOn()

        compose.onNodeWithText("Buttons").performClick()
        compose.onNodeWithText("Add button").assertIsDisplayed()
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("+1").assertIsDisplayed() }.isSuccess }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Vibrate on positive presses").assertIsDisplayed()

        // History lists the press; deleting it there also takes it out of this session's undo.
        compose.onNodeWithText("History").performClick()
        compose.waitUntil(5_000) { isShown("Total 1") }
        compose.onNodeWithContentDescription("Delete +1", substring = true).performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.waitUntil(5_000) { isShown("No entries yet", substring = true) }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Vibrate on positive presses").assertIsDisplayed()

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("+1").assertIsDisplayed()
        compose.onNodeWithText("0").assertIsDisplayed()
        compose.onAllNodesWithText("Undo").assertCountEquals(0)
    }

    private fun isShown(text: String, substring: Boolean = false): Boolean =
        runCatching { compose.onNodeWithText(text, substring = substring).assertIsDisplayed() }.isSuccess

    private fun positiveSwitch() = compose.onNodeWithText("Vibrate on positive presses")

    private fun SemanticsNodeInteraction.isOn(): Boolean =
        runCatching { assertIsOn() }.isSuccess
}
