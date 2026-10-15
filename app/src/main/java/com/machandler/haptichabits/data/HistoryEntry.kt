package com.machandler.haptichabits.data

/** One entry of History as loaded from the database: an event plus the name of its button. */
data class HistoryEntry(
    val id: Long,
    /** Wall-clock time of the entry, in epoch milliseconds. Shown, but never used for ordering. */
    val timestamp: Long,
    /** The value the entry added to the total (the button's value at press time); 0 for a reset. */
    val weight: Int,
    /** The button's current name (archived buttons included), or null for an entry with no button. */
    val buttonName: String?,
    /** [EntryType.TAP] or [EntryType.RESET]. */
    val type: Int = EntryType.TAP,
) {
    val isReset: Boolean get() = type == EntryType.RESET
}
