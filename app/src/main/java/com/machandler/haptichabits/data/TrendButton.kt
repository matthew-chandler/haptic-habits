package com.machandler.haptichabits.data

/** A button Trends can show: every active button, and each deleted (archived) one that has presses. */
data class TrendButton(
    val id: Long,
    /** The button's current name. */
    val name: String,
    /** The button's current value; its sign picks the button's color. */
    val weight: Int,
    /** Deleted by the user: hidden everywhere else, kept here because it has history. */
    val archived: Boolean,
    /** How many times it was pressed, ever (resets don't count). */
    val presses: Int,
)
