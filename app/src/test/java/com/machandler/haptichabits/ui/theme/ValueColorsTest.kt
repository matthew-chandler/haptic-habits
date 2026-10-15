package com.machandler.haptichabits.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValueColorsTest {
    @Test
    fun positiveIsGreenAndNegativeRedByDefault() {
        assertEquals(ValueHue.Green, valueHue(1, invertColors = false))
        assertEquals(ValueHue.Green, valueHue(999, invertColors = false))
        assertEquals(ValueHue.Red, valueHue(-1, invertColors = false))
        assertEquals(ValueHue.Red, valueHue(-999, invertColors = false))
    }

    @Test
    fun invertingSwapsTheHues() {
        assertEquals(ValueHue.Red, valueHue(1, invertColors = true))
        assertEquals(ValueHue.Green, valueHue(-1, invertColors = true))
    }

    @Test
    fun zeroHasNoHue() {
        assertNull(valueHue(0, invertColors = false))
        assertNull(valueHue(0, invertColors = true))
    }

    @Test
    fun worksForChangesBeyondTheIntRange() {
        // The total (and so a change in it) is a Long.
        assertEquals(ValueHue.Green, valueHue(Long.MAX_VALUE, invertColors = false))
        assertEquals(ValueHue.Red, valueHue(Long.MIN_VALUE, invertColors = false))
    }

    @Test
    fun paletteLooksUpEachHue() {
        assertEquals(LightValuePalette.green, LightValuePalette[ValueHue.Green])
        assertEquals(LightValuePalette.red, LightValuePalette[ValueHue.Red])
        assertEquals(DarkValuePalette.green, DarkValuePalette[ValueHue.Green])
        assertEquals(DarkValuePalette.red, DarkValuePalette[ValueHue.Red])
    }

    @Test
    fun labelsAreReadableOnTheButtons() {
        for (hue in listOf(LightValuePalette.green, LightValuePalette.red, DarkValuePalette.green, DarkValuePalette.red)) {
            assertReadable(hue.onColor, on = hue.color)
        }
    }

    @Test
    fun flashColorsAreReadableOnTheScreenBackground() {
        // Material 3's baseline backgrounds; dynamic-color backgrounds are similarly near white or
        // near black.
        val lightBackground = Color(0xFFFEF7FF)
        val darkBackground = Color(0xFF141218)
        assertReadable(LightValuePalette.green.color, on = lightBackground)
        assertReadable(LightValuePalette.red.color, on = lightBackground)
        assertReadable(DarkValuePalette.green.color, on = darkBackground)
        assertReadable(DarkValuePalette.red.color, on = darkBackground)
    }

    @Test
    fun calendarLevelsRunFromEmptyToTheFullHue() {
        for (palette in listOf(LightValuePalette, DarkValuePalette)) {
            for (hue in listOf(palette.green, palette.red)) {
                assertEquals(palette.empty, intensityColor(hue, palette.empty, 0))
                assertEquals(hue.color, intensityColor(hue, palette.empty, 4))
            }
        }
    }

    @Test
    fun calendarDaysWithPressesStandOutFromEmptyOnesInBothThemes() {
        for (palette in listOf(LightValuePalette, DarkValuePalette)) {
            for (hue in listOf(palette.green, palette.red)) {
                val levels = (0..4).map { intensityColor(hue, palette.empty, it) }
                // Even one press is clearly not empty (besides having a hue at all)...
                assertTrue("level 1 vs empty: ${contrast(levels[1], levels[0])}", contrast(levels[1], levels[0]) >= 1.5f)
                // ...and each level is further from empty than the one before.
                levels.zipWithNext { a, b ->
                    assertTrue("$a then $b", contrast(b, palette.empty) > contrast(a, palette.empty))
                }
            }
        }
    }

    @Test
    fun emptyCalendarDaysAreVisibleOnTheBackground() {
        assertTrue(contrast(LightValuePalette.empty, Color(0xFFFEF7FF)) >= 1.1f)
        assertTrue(contrast(DarkValuePalette.empty, Color(0xFF141218)) >= 1.1f)
    }

    private fun contrast(a: Color, b: Color): Float {
        val lighter = maxOf(a.luminance(), b.luminance())
        val darker = minOf(a.luminance(), b.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    /** WCAG AA for normal text: a contrast ratio of at least 4.5:1. */
    private fun assertReadable(text: Color, on: Color) {
        val lighter = maxOf(text.luminance(), on.luminance())
        val darker = minOf(text.luminance(), on.luminance())
        val ratio = (lighter + 0.05f) / (darker + 0.05f)
        assertTrue("contrast of $text on $on is only $ratio", ratio >= 4.5f)
    }
}
