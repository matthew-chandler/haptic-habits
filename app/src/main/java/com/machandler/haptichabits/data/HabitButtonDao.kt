package com.machandler.haptichabits.data

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitButtonDao {
    /** Emits the buttons that haven't been deleted, in display order, and again on every change. */
    @Query("SELECT * FROM buttons WHERE archived = 0 ORDER BY position, id")
    fun observeActive(): Flow<List<HabitButton>>

    /** Adds a button after all existing ones (archived included) and returns its id. */
    @Query(
        "INSERT INTO buttons (name, weight, position, archived) " +
            "VALUES (:name, :weight, (SELECT COALESCE(MAX(position), -1) + 1 FROM buttons), 0)",
    )
    suspend fun add(name: String, weight: Int): Long

    /** Renames a button and/or changes its value. Past events keep the value they were recorded with. */
    @Query("UPDATE buttons SET name = :name, weight = :weight WHERE id = :id")
    suspend fun update(id: Long, name: String, weight: Int): Int

    /** "Deletes" a button: it disappears from the app, but past events keep pointing at it. */
    @Query("UPDATE buttons SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long): Int

    /**
     * Emits the buttons Trends can show, with how often each was pressed: every active button
     * (in display order), then every deleted one that was pressed at least once. Only presses
     * count (`type = TAP`); resets have no button anyway. One grouped query using the index on
     * `tap_events.buttonId`.
     */
    @Query(
        "SELECT b.id, b.name, b.weight, b.archived, COUNT(e.id) AS presses FROM buttons b " +
            "LEFT JOIN tap_events e ON e.buttonId = b.id AND e.type = ${EntryType.TAP} " +
            "GROUP BY b.id HAVING b.archived = 0 OR COUNT(e.id) > 0 " +
            "ORDER BY b.archived, b.position, b.id",
    )
    fun observeTrendButtons(): Flow<List<TrendButton>>
}
