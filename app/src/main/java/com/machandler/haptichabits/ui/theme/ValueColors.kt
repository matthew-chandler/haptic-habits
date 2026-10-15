package com.machandler.haptichabits.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * The single source of truth for coloring values: button values, changes in the total (the sum
 * flash), History rows, and Trends' charts and calendar. By default positive is green and
 * negative red; the "Invert colors" setting swaps them.
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

/**
 * The green and red for one theme (light or dark), and [empty], the neutral grey of a Trends
 * calendar day with no presses. Fixed, so dynamic color doesn't change them.
 */
@Immutable
data class ValuePalette(val green: HueColors, val red: HueColors, val empty: Color) {
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
    empty = Color(0xFFE8E8EC),
)
val DarkValuePalette = ValuePalette(
    green = HueColors(color = Color(0xFF8DD68A), onColor = Color(0xFF00390A)),
    red = HueColors(color = Color(0xFFFFB4AB), onColor = Color(0xFF690005)),
    empty = Color(0xFF33313A),
)

/** Provided by [HapticHabitsTheme] to match its light/dark choice. */
val LocalValuePalette = staticCompositionLocalOf { LightValuePalette }

/** The colors for [value] in the current theme, or null for zero (use the theme's own colors). */
@Composable
@ReadOnlyComposable
fun valueColors(value: Long, invertColors: Boolean): HueColors? =
    valueHue(value, invertColors)?.let { LocalValuePalette.current[it] }

/**
 * The fill of a Trends calendar day at intensity [level] (0 = no presses, 1-4 = more and more):
 * [empty] for 0, then [hue] mixed into [empty] from 40% to 100%. Even level 1 differs clearly from
 * an empty day, in hue and in contrast (checked in `ValueColorsTest`), in both themes.
 */
fun intensityColor(hue: HueColors, empty: Color, level: Int): Color =
    if (level <= 0) empty else lerp(empty, hue.color, INTENSITY_FRACTIONS[level.coerceAtMost(4) - 1])

private val INTENSITY_FRACTIONS = floatArrayOf(0.4f, 0.6f, 0.8f, 1f)
