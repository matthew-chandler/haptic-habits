package com.machandler.haptichabits

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
    fun gearOpensSettingsWhereSwitchesToggleAndBackReturnsHome() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitUntil(5_000) { positiveSwitch().isOn() }
        compose.onNodeWithText("Vibrate on negative presses").assertIsDisplayed().assertIsOn()

        positiveSwitch().performClick()
        // The write goes through DataStore off the main thread, so wait for it to come back.
        compose.waitUntil(5_000) { !positiveSwitch().isOn() }
        compose.onNodeWithText("Vibrate on negative presses").assertIsOn()

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("+1").assertIsDisplayed()
    }

    private fun positiveSwitch() = compose.onNodeWithText("Vibrate on positive presses")

    private fun SemanticsNodeInteraction.isOn(): Boolean =
        runCatching { assertIsOn() }.isSuccess
}
