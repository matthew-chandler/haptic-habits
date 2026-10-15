package com.machandler.haptichabits.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One press of a habit button. The running total is the sum of all weights. */
@Entity(tableName = "tap_events")
data class TapEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Wall-clock time of the tap, in epoch milliseconds. */
    val timestamp: Long,
    val weight: Int,
)
