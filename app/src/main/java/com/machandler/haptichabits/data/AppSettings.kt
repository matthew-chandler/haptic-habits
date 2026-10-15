package com.machandler.haptichabits.data

/** The user's preferences, as stored by [SettingsStore]. The defaults apply until changed. */
data class AppSettings(
    val vibrateOnPositive: Boolean = true,
    val vibrateOnNegative: Boolean = true,
) {
    /** Whether a press of a button worth [value] vibrates: the switch for its sign decides. */
    fun vibratesFor(value: Int): Boolean = when {
        value > 0 -> vibrateOnPositive
        value < 0 -> vibrateOnNegative
        else -> false
    }
}
