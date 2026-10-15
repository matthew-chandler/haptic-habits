package com.machandler.haptichabits.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TapEventDao {
    @Insert
    suspend fun insert(event: TapEvent): Long

    /** Deletes one event. Returns the number of rows deleted: 1, or 0 if it was already gone. */
    @Query("DELETE FROM tap_events WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    /**
     * Emits the total, and again whenever the log changes: the sum of the weights of the entries
     * after the latest reset (the reset with the highest id, per the canonical id order), or of
     * every entry if there is none. 0 when empty or right after a reset. Deleting the latest reset
     * brings back the total from before it; deleting anything older than it changes nothing.
     *
     * Plain aggregates only (no window functions), so it runs on the SQLite of every supported
     * Android version. The index on `type` finds the latest reset without a scan, and the rest is
     * a range scan on the id.
     */
    @Query(
        "SELECT COALESCE(SUM(weight), 0) FROM tap_events WHERE id > " +
            "COALESCE((SELECT MAX(id) FROM tap_events WHERE type = ${EntryType.RESET}), 0)",
    )
    fun observeTotal(): Flow<Long>

    /**
     * Emits every entry, newest first, with its button's name, and again whenever the log or a
     * button changes. Entries are in id (insertion) order, the app's one canonical order: ids
     * only ever grow (AUTOINCREMENT), whereas timestamps jump when the phone's clock changes.
     */
    @Query(
        "SELECT e.id, e.timestamp, e.weight, b.name AS buttonName, e.type FROM tap_events e " +
            "LEFT JOIN buttons b ON b.id = e.buttonId ORDER BY e.id DESC",
    )
    fun observeHistory(): Flow<List<HistoryEntry>>
}
