package com.machandler.haptichabits

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.RequiresApi

/**
 * Plays a short, firm vibration to confirm a +1 tap.
 *
 * This deliberately uses [Vibrator] instead of Compose/View haptic feedback: the latter is
 * silenced when the system "touch feedback" setting is off, and this should fire regardless.
 * For the same reason the vibration is tagged with media usage; an untagged short vibration
 * is treated as touch feedback by Android 13+ and would be silenced by that setting too.
 */
class TapHaptics(context: Context) {
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
            else -> @Suppress("DEPRECATION") vibrator.vibrate(FALLBACK_DURATION_MS)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun effect(): VibrationEffect =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Tuned per device by the manufacturer; falls back to a waveform if unsupported.
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        } else {
            VibrationEffect.createOneShot(FALLBACK_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        }

    private companion object {
        const val FALLBACK_DURATION_MS = 40L
    }
}
