package com.machandler.haptichabits.data

/**
 * One entry of the log as Trends loads it: just what the charts need, without the button's name.
 * Loaded oldest first by id, the canonical order, so a single pass gives the running total.
 */
data class TrendEntry(
    val id: Long,
    /** Wall-clock time of the entry, in epoch milliseconds. Only used to put it in a day. */
    val timestamp: Long,
    /** The value the entry added to the total; 0 for a reset. */
    val weight: Int,
    /** The pressed button, or null for an entry with no button (a reset). */
    val buttonId: Long?,
    /** [EntryType.TAP] or [EntryType.RESET]. */
    val type: Int = EntryType.TAP,
) {
    val isReset: Boolean get() = type == EntryType.RESET
}
