package com.machandler.haptichabits

import android.content.Context
import android.os.VibrationAttributes
import android.os.Vibrator
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowVibrator

@RunWith(RobolectricTestRunner::class)
class TapVibrationTest {
    private lateinit var context: Context
    private lateinit var vibrator: ShadowVibrator

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        vibrator = shadowOf(context.getSystemService(Vibrator::class.java))
    }

    @Test
    fun playVibrates() {
        vibrator.setHasVibrator(true)

        TapVibration(context).play()

        assertTrue(vibrator.isVibrating)
    }

    @Test
    fun playIsTwoFullStrengthBuzzesMuchLongerThanATick() {
        vibrator.setHasVibrator(true)

        TapVibration(context).play()

        assertArrayEquals(TapVibration.TIMINGS_MS, vibrator.pattern)
        assertEquals(TapVibration.NO_REPEAT, vibrator.repeat)
        val amplitudes = TapVibration.AMPLITUDES
        assertEquals(TapVibration.TIMINGS_MS.size, amplitudes.size)
        val buzzes = TapVibration.TIMINGS_MS.indices.filter { amplitudes[it] > 0 }
        assertEquals(2, buzzes.size)
        buzzes.forEach { i ->
            assertEquals(255, amplitudes[i])
            assertTrue("each buzz should be far longer than a ~40 ms tick", TapVibration.TIMINGS_MS[i] >= 150)
        }
    }

    @Test
    fun playUsesMediaUsageSoTouchFeedbackSettingDoesNotSilenceIt() {
        vibrator.setHasVibrator(true)

        TapVibration(context).play()

        val attributes = vibrator.vibrationAttributesFromLastVibration as VibrationAttributes
        assertEquals(VibrationAttributes.USAGE_MEDIA, attributes.usage)
    }

    @Test
    fun playDoesNothingWithoutVibrator() {
        vibrator.setHasVibrator(false)

        TapVibration(context).play()

        assertFalse(vibrator.isVibrating)
    }
}
