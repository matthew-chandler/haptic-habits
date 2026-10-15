package com.machandler.haptichabits.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {
    @Test
    fun defaultsVibrateForBothSigns() {
        assertTrue(AppSettings().vibratesFor(1))
        assertTrue(AppSettings().vibratesFor(-1))
    }

    @Test
    fun positiveSwitchControlsOnlyPositiveValues() {
        val settings = AppSettings(vibrateOnPositive = false, vibrateOnNegative = true)

        assertFalse(settings.vibratesFor(1))
        assertFalse(settings.vibratesFor(5))
        assertTrue(settings.vibratesFor(-1))
    }

    @Test
    fun negativeSwitchControlsOnlyNegativeValues() {
        val settings = AppSettings(vibrateOnPositive = true, vibrateOnNegative = false)

        assertTrue(settings.vibratesFor(1))
        assertFalse(settings.vibratesFor(-1))
        assertFalse(settings.vibratesFor(-5))
    }

    @Test
    fun zeroNeverVibrates() {
        assertFalse(AppSettings().vibratesFor(0))
    }
}
