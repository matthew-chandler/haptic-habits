package com.machandler.haptichabits.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One entry of the log: a press of a habit button, or a reset of the total to 0 (see [type]).
 * The total is the sum of the weights of the entries after the latest reset, in id order.
 */
@Entity(
    tableName = "tap_events",
    // Buttons are archived, never deleted, so every event's button can always be looked up.
    foreignKeys = [ForeignKey(entity = HabitButton::class, parentColumns = ["id"], childColumns = ["buttonId"])],
    // For History (newest first) and per-button Trends; `type` finds the latest reset for the total.
    indices = [Index("buttonId"), Index("timestamp"), Index("type")],
)
data class TapEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Wall-clock time of the entry, in epoch milliseconds. */
    val timestamp: Long,
    /** The pressed button's value at the time of the press; later edits to the button don't change it. 0 for a reset. */
    val weight: Int,
    /** The button that was pressed. Null for entries that don't come from a button (resets). */
    val buttonId: Long? = null,
    /** [EntryType.TAP] or [EntryType.RESET]. The default is what the 2 -> 3 migration gives existing rows. */
    @ColumnInfo(defaultValue = "${EntryType.TAP}")
    val type: Int = EntryType.TAP,
) {
    companion object {
        /** A reset at [timestamp]: no button and weight 0, so sums of weights (e.g. per button) never count it. */
        fun reset(timestamp: Long) = TapEvent(timestamp = timestamp, weight = 0, buttonId = null, type = EntryType.RESET)
    }
}

/** What an entry of `tap_events` is, stored in its `type` column. Never renumber these. */
object EntryType {
    /** A press of a button: adds its weight to the total. */
    const val TAP = 0

    /**
     * Brings the total to 0 without deleting anything: the total only counts entries after the
     * latest reset (highest id). Has no button and weight 0.
     */
    const val RESET = 1
}
