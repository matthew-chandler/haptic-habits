package com.machandler.haptichabits

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.machandler.haptichabits.data.HabitButton
import com.machandler.haptichabits.ui.theme.HapticHabitsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The home screen's buttons and the Settings -> Buttons editor, with their state faked. */
@RunWith(RobolectricTestRunner::class)
class ButtonsUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val added = mutableListOf<Pair<String, String>>()
    private val updated = mutableListOf<Triple<Long, String, String>>()
    private val deleted = mutableListOf<Long>()

    @Test
    fun homeShowsEachButtonWithItsValueAndPressesTheOneTapped() {
        val pressed = mutableListOf<HabitButton>()
        compose.setContent {
            HapticHabitsTheme {
                CounterScreen(
                    total = 4,
                    buttons = listOf(PLUS_ONE, SNACK),
                    onPress = { pressed += it },
                    canUndo = false,
                    onUndo = {},
                    onOpenSettings = {},
                )
            }
        }

        compose.onNodeWithText("+1").assertIsDisplayed()
        compose.onNodeWithText("Snack").assertIsDisplayed()
        compose.onNodeWithText("+3").assertIsDisplayed()
        compose.onNodeWithText("Snack").performClick()
        compose.onNodeWithText("+1").performClick()

        assertEquals(listOf(SNACK, PLUS_ONE), pressed)
    }

    @Test
    fun homeWithNoButtonsPointsToSettings() {
        compose.setContent {
            HapticHabitsTheme {
                CounterScreen(total = 4, buttons = emptyList(), onPress = {}, canUndo = false, onUndo = {}, onOpenSettings = {})
            }
        }

        compose.onNodeWithText("No buttons", substring = true).assertIsDisplayed()
    }

    @Test
    fun addDialogSavesOnlyOnceTheNameIsFilledIn() {
        showEditor(listOf(PLUS_ONE))

        compose.onNodeWithText("Add button").performClick()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Name").performTextInput("Walk")
        compose.onNodeWithText("Save").assertIsEnabled()
        // The value starts at 1; the sign button makes it negative without a minus key.
        compose.onNodeWithText("+/−").performClick()
        compose.onNodeWithText("Save").performClick()

        assertEquals(listOf("Walk" to "-1"), added)
        compose.onNodeWithText("New button").assertDoesNotExist()
    }

    @Test
    fun zeroIsNotAValidValue() {
        showEditor(listOf(PLUS_ONE))

        compose.onNodeWithText("Add button").performClick()
        compose.onNodeWithText("Name").performTextInput("Walk")
        compose.onNodeWithText("Value").performTextReplacement("0")

        compose.onNodeWithText("Can't be 0").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun tappingARowEditsThatButton() {
        showEditor(listOf(PLUS_ONE, SNACK))

        compose.onNodeWithText("Snack").performClick()
        compose.onNodeWithText("Edit button").assertIsDisplayed()
        compose.onNodeWithText("Value").performTextReplacement("7")
        compose.onNodeWithText("Save").performClick()

        assertEquals(listOf(Triple(SNACK.id, "Snack", "7")), updated)
    }

    @Test
    fun deleteAsksForConfirmationFirst() {
        showEditor(listOf(PLUS_ONE, SNACK))

        compose.onNodeWithContentDescription("Delete Snack").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(emptyList<Long>(), deleted)

        compose.onNodeWithContentDescription("Delete Snack").performClick()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(listOf(SNACK.id), deleted)
    }

    private fun showEditor(buttons: List<HabitButton>) {
        compose.setContent {
            HapticHabitsTheme {
                ButtonsScreen(
                    buttons = buttons,
                    onAdd = { name, value -> added += name to value; true },
                    onUpdate = { id, name, value -> updated += Triple(id, name, value); true },
                    onDelete = { deleted += it },
                    onBack = {},
                )
            }
        }
    }

    private companion object {
        val PLUS_ONE = HabitButton(id = 1, name = "+1", weight = 1, position = 0)
        val SNACK = HabitButton(id = 2, name = "Snack", weight = 3, position = 1)
    }
}
