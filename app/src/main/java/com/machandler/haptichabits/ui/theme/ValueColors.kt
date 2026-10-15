package com.machandler.haptichabits.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The single source of truth for coloring values: button values, changes in the total (the sum
 * flash), and later History rows. By default positive is green and negative red; the
 * "Invert colors" setting swaps them.
 */
enum class ValueHue { Green, Red }

/** The hue for [value] (a button's value, or how much the total changed), or null for zero. */
fun valueHue(value: Long, invertColors: Boolean): ValueHue? = when {
    value == 0L -> null
    (value > 0) != invertColors -> ValueHue.Green
    else -> ValueHue.Red
}

/**
 * One hue in the current theme. [color] fills a shape (a button) or colors text drawn straight
 * on the screen background (the flashing total); [onColor] is for text on top of [color].
 * Both pairs keep at least 4.5:1 contrast.
 */
@Immutable
data class HueColors(val color: Color, val onColor: Color)

/** The green and red for one theme (light or dark). Fixed, so dynamic color doesn't change them. */
@Immutable
data class ValuePalette(val green: HueColors, val red: HueColors) {
    operator fun get(hue: ValueHue): HueColors = when (hue) {
        ValueHue.Green -> green
        ValueHue.Red -> red
    }
}

// Light: muted tone-40 green/red with white text, like Material's primary/error. Dark: tone-80
// pastels with dark text, like Material's dark-theme filled buttons; they stand out on the dark
// background and read well as text on it.
val LightValuePalette = ValuePalette(
    green = HueColors(color = Color(0xFF2E6B30), onColor = Color(0xFFFFFFFF)),
    red = HueColors(color = Color(0xFFB3261E), onColor = Color(0xFFFFFFFF)),
)
val DarkValuePalette = ValuePalette(
    green = HueColors(color = Color(0xFF8DD68A), onColor = Color(0xFF00390A)),
    red = HueColors(color = Color(0xFFFFB4AB), onColor = Color(0xFF690005)),
)

/** Provided by [HapticHabitsTheme] to match its light/dark choice. */
val LocalValuePalette = staticCompositionLocalOf { LightValuePalette }

/** The colors for [value] in the current theme, or null for zero (use the theme's own colors). */
@Composable
@ReadOnlyComposable
fun valueColors(value: Long, invertColors: Boolean): HueColors? =
    valueHue(value, invertColors)?.let { LocalValuePalette.current[it] }
