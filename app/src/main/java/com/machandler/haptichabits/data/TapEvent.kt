package com.machandler.haptichabits.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One press of a habit button. The running total is the sum of all weights. */
@Entity(
    tableName = "tap_events",
    // Buttons are archived, never deleted, so every event's button can always be looked up.
    foreignKeys = [ForeignKey(entity = HabitButton::class, parentColumns = ["id"], childColumns = ["buttonId"])],
    // For History (newest first) and per-button Trends.
    indices = [Index("buttonId"), Index("timestamp")],
)
data class TapEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Wall-clock time of the tap, in epoch milliseconds. */
    val timestamp: Long,
    /** The pressed button's value at the time of the press; later edits to the button don't change it. */
    val weight: Int,
    /** The button that was pressed. Null for entries that don't come from a button (e.g. a future reset). */
    val buttonId: Long? = null,
)
