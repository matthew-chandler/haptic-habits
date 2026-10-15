package com.machandler.haptichabits.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TapEventDao {
    @Insert
    suspend fun insert(event: TapEvent): Long

    /** Emits the sum of all tap weights, and again whenever a tap is added. 0 when empty. */
    @Query("SELECT COALESCE(SUM(weight), 0) FROM tap_events")
    fun observeTotal(): Flow<Long>
}
