package com.machandler.haptichabits.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Every version bump needs an exported schema and a real Migration; never fall back to
// destructive migration, the tap history is the user's data.
@Database(entities = [TapEvent::class], version = 1, exportSchema = true)
abstract class HabitDatabase : RoomDatabase() {
    abstract fun tapEventDao(): TapEventDao

    companion object {
        @Volatile
        private var instance: HabitDatabase? = null

        fun getInstance(context: Context): HabitDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HabitDatabase::class.java,
                    "haptic_habits.db",
                ).build().also { instance = it }
            }
    }
}
