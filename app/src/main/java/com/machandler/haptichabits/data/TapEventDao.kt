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

    /** Emits the sum of all tap weights, and again whenever the log changes. 0 when empty. */
    @Query("SELECT COALESCE(SUM(weight), 0) FROM tap_events")
    fun observeTotal(): Flow<Long>

    /**
     * Emits every entry, newest first, with its button's name, and again whenever the log or a
     * button changes. Entries are in id (insertion) order, the app's one canonical order: ids
     * only ever grow (AUTOINCREMENT), whereas timestamps jump when the phone's clock changes.
     */
    @Query(
        "SELECT e.id, e.timestamp, e.weight, b.name AS buttonName FROM tap_events e " +
            "LEFT JOIN buttons b ON b.id = e.buttonId ORDER BY e.id DESC",
    )
    fun observeHistory(): Flow<List<HistoryEntry>>
}
