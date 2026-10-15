package com.machandler.haptichabits

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.sp
import com.machandler.haptichabits.ui.theme.LocalValuePalette
import com.machandler.haptichabits.ui.theme.ValueHue
import com.machandler.haptichabits.ui.theme.valueHue

/**
 * Decides when the total flashes: whenever the observed total changes, except for the first value
 * seen. A fresh watcher is made each time the home screen is shown, so launching the app, rotating,
 * or coming back from Settings never flashes; only changes made while watching do (presses and
 * Undo alike, since both simply change the total).
 */
class TotalChangeWatcher {
    private var last: Long? = null

    /** Feeds the latest [total]; returns how much it changed (up > 0, down < 0), or null for no flash. */
    fun onTotal(total: Long?): Long? {
        if (total == null) return null
        val previous = last
        last = total
        return if (previous == null || previous == total) null else total - previous
    }
}

/**
 * The large total. When it changes it flashes green (up) or red (down), swapped by [invertColors],
 * then fades back to the normal text color. A new change restarts the flash in its own color, so
 * rapid presses never stack.
 */
@Composable
fun FlashingTotal(total: Long?, invertColors: Boolean, modifier: Modifier = Modifier) {
    val watcher = remember { TotalChangeWatcher() }
    // 1 = fully the flash color, 0 = the normal text color.
    val flash = remember { Animatable(0f) }
    var flashHue by remember { mutableStateOf<ValueHue?>(null) }
    LaunchedEffect(total) {
        val hue = watcher.onTotal(total)?.let { valueHue(it, invertColors) }
        if (hue != null) {
            flashHue = hue
            flash.snapTo(1f)
        }
        // Also finishes a fade that a newer value interrupted without flashing itself.
        flash.animateTo(0f, tween(FLASH_FADE_MS, delayMillis = FLASH_HOLD_MS, easing = FastOutSlowInEasing))
    }
    val normal = LocalContentColor.current
    val flashColor = flashHue?.let { LocalValuePalette.current[it].color } ?: normal
    Text(
        // Blank while loading so a stale 0 never shows on launch.
        text = total?.toString() ?: "",
        color = lerp(normal, flashColor, flash.value),
        style = MaterialTheme.typography.displayLarge.copy(fontSize = 96.sp, lineHeight = 104.sp),
        maxLines = 1,
        modifier = modifier,
    )
}

// The flash holds its full color briefly, then eases back: about half a second in all.
private const val FLASH_HOLD_MS = 120
private const val FLASH_FADE_MS = 400
