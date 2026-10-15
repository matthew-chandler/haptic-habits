package com.machandler.haptichabits

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.RequiresApi

/**
 * Plays a full-phone vibration, like an incoming notification, to confirm a press: two firm
 * buzzes at full strength, so it can be felt clearly without looking.
 *
 * This deliberately uses [Vibrator] instead of Compose/View haptic feedback: the latter is
 * silenced when the system "touch feedback" setting is off, and this should fire regardless.
 * For the same reason the vibration is tagged with media usage; an untagged vibration can be
 * treated as touch feedback by Android 13+ and silenced by that setting too.
 */
class TapVibration(context: Context) {
    private val vibrator: Vibrator? = context.getSystemService(Vibrator::class.java)

    fun play() {
        val vibrator = vibrator?.takeIf { it.hasVibrator() } ?: return
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> vibrator.vibrate(
                effect(),
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA),
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> @Suppress("DEPRECATION") vibrator.vibrate(
                effect(),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build(),
            )
            // No amplitude control before API 26: the same on/off pattern at the default strength.
            else -> @Suppress("DEPRECATION") vibrator.vibrate(TIMINGS_MS, NO_REPEAT)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun effect(): VibrationEffect = VibrationEffect.createWaveform(TIMINGS_MS, AMPLITUDES, NO_REPEAT)

    internal companion object {
        /** Alternating off/on durations, starting with an (empty) initial pause: buzz, gap, buzz. */
        val TIMINGS_MS = longArrayOf(0, 200, 120, 200)

        /** Amplitude for each segment of [TIMINGS_MS]; 255 is full strength. */
        val AMPLITUDES = intArrayOf(0, 255, 0, 255)

        const val NO_REPEAT = -1
    }
}
