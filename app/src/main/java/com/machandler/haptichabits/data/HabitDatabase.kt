package com.machandler.haptichabits.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

// Every version bump needs an exported schema and a real Migration; never fall back to
// destructive migration, the tap history is the user's data.
@Database(entities = [TapEvent::class, HabitButton::class], version = 3, exportSchema = true)
abstract class HabitDatabase : RoomDatabase() {
    abstract fun tapEventDao(): TapEventDao
    abstract fun habitButtonDao(): HabitButtonDao

    companion object {
        @Volatile
        private var instance: HabitDatabase? = null

        fun getInstance(context: Context): HabitDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HabitDatabase::class.java,
                    "haptic_habits.db",
                ).setUp().build().also { instance = it }
            }

        /**
         * Everything the app's database needs besides its schema: the migrations, and the default
         * button on a fresh install. Tests build their databases with this too.
         */
        fun RoomDatabase.Builder<HabitDatabase>.setUp(): RoomDatabase.Builder<HabitDatabase> =
            addMigrations(*ALL_MIGRATIONS).addCallback(SeedDefaultButton)
    }
}

/**
 * Gives a fresh install the default "+1" button. Upgraded installs get the same button from
 * [MIGRATION_1_2] instead, since onCreate only runs when the database file is first created.
 */
private object SeedDefaultButton : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        db.execSQL(INSERT_DEFAULT_BUTTON_SQL)
    }
}
