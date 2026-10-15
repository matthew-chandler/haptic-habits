package com.machandler.haptichabits.ui.theme

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The theme hands out the palette that matches light or dark, with or without dynamic color. */
@RunWith(RobolectricTestRunner::class)
class ValueColorsThemeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun lightThemeUsesTheLightPalette() {
        assertEquals(LightValuePalette.green, colorsFor(1, darkTheme = false, dynamicColor = true))
    }

    @Test
    fun lightThemeWithoutDynamicColorUsesTheLightPalette() {
        assertEquals(LightValuePalette.red, colorsFor(-1, darkTheme = false, dynamicColor = false))
    }

    @Test
    fun darkThemeUsesTheDarkPalette() {
        assertEquals(DarkValuePalette.green, colorsFor(1, darkTheme = true, dynamicColor = true))
    }

    @Test
    fun darkThemeWithoutDynamicColorUsesTheDarkPalette() {
        assertEquals(DarkValuePalette.red, colorsFor(-1, darkTheme = true, dynamicColor = false))
    }

    @Test
    fun invertingSwapsTheColors() {
        assertEquals(DarkValuePalette.red, colorsFor(5, darkTheme = true, invertColors = true))
    }

    @Test
    fun zeroUsesTheThemeColors() {
        assertNull(colorsFor(0, darkTheme = false))
    }

    // setContent may only be called once per test, so each test asks for one value.
    private fun colorsFor(
        value: Long,
        darkTheme: Boolean,
        dynamicColor: Boolean = true,
        invertColors: Boolean = false,
    ): HueColors? {
        var result: HueColors? = null
        compose.setContent {
            HapticHabitsTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                result = valueColors(value, invertColors)
            }
        }
        compose.waitForIdle()
        return result
    }
}
