package com.machandler.haptichabits

import kotlin.math.abs

/** What a button's name and value may be, shared by the editor dialog and [ButtonsViewModel]. */
object ButtonRules {
    const val MAX_NAME_LENGTH = 40

    /** Values run from -[MAX_ABS_VALUE] to [MAX_ABS_VALUE], excluding 0. */
    const val MAX_ABS_VALUE = 999

    /** The name as it will be stored (trimmed), or null if it is blank or too long. */
    fun cleanName(input: String): String? =
        input.trim().takeIf { it.isNotEmpty() && it.length <= MAX_NAME_LENGTH }

    /** The value [input] stands for, or null unless it is a non-zero whole number within range. */
    fun parseValue(input: String): Int? =
        input.trim().toIntOrNull()?.takeIf { it != 0 && abs(it) <= MAX_ABS_VALUE }

    /**
     * Why [input] isn't a valid value, or null if it is valid or still being typed (empty or a
     * lone "-"), where an error would only nag.
     */
    fun valueError(input: String): String? {
        val text = input.trim()
        if (text.isEmpty() || text == "-") return null
        val number = text.toIntOrNull() ?: return "Enter a whole number"
        return when {
            number == 0 -> "Can't be 0"
            abs(number) > MAX_ABS_VALUE -> "Must be between -$MAX_ABS_VALUE and $MAX_ABS_VALUE"
            else -> null
        }
    }

    /** Whether [input] may be typed into the value field: an optional leading "-" and up to 3 digits. */
    fun isTypeableValue(input: String): Boolean = VALUE_INPUT.matches(input)

    /** Flips the sign of the value being typed, e.g. "5" -> "-5" -> "5", "" -> "-". */
    fun toggleSign(input: String): String = if (input.startsWith("-")) input.drop(1) else "-$input"

    /** How a value is shown: with an explicit sign, e.g. "+3" or "-2". */
    fun formatValue(value: Int): String = if (value > 0) "+$value" else value.toString()

    private val VALUE_INPUT = Regex("""-?\d{0,${MAX_ABS_VALUE.toString().length}}""")
}
