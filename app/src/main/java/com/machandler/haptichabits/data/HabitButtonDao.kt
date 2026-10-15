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
}
