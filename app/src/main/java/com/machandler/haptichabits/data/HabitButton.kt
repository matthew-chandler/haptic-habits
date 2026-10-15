package com.machandler.haptichabits.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A habit button on the home screen. Each press records a [TapEvent] that copies this button's
 * current [weight], so editing a button never changes past events.
 */
@Entity(tableName = "buttons")
data class HabitButton(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Added to the total on each press: non-zero, and negative for positive habits. */
    val weight: Int,
    /** Display order on the home screen and in the editor, ascending (ties broken by id). */
    val position: Int,
    /**
     * Set when the user deletes the button. Archived buttons are hidden everywhere, but the row
     * is kept so past events still point at a name (for History and Trends).
     */
    val archived: Boolean = false,
)
