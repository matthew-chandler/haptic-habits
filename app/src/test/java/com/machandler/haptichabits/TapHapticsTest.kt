package com.machandler.haptichabits

import android.content.Context
import android.os.VibrationAttributes
import android.os.Vibrator
import androidx.test.core.app.ApplicationProvider
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
class TapHapticsTest {
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

        TapHaptics(context).play()

        assertTrue(vibrator.isVibrating)
    }

    @Test
    fun playUsesMediaUsageSoTouchFeedbackSettingDoesNotSilenceIt() {
        vibrator.setHasVibrator(true)

        TapHaptics(context).play()

        val attributes = vibrator.vibrationAttributesFromLastVibration as VibrationAttributes
        assertEquals(VibrationAttributes.USAGE_MEDIA, attributes.usage)
    }

    @Test
    fun playDoesNothingWithoutVibrator() {
        vibrator.setHasVibrator(false)

        TapHaptics(context).play()

        assertFalse(vibrator.isVibrating)
    }
}
