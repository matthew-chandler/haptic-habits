package com.machandler.haptichabits.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** The id of the "+1" button every install starts with (fresh or upgraded from version 1). */
const val DEFAULT_BUTTON_ID = 1L

/** Inserts the default "+1" button (value 1, first in order). Shared by fresh installs and [MIGRATION_1_2]. */
internal const val INSERT_DEFAULT_BUTTON_SQL =
    "INSERT INTO buttons (id, name, weight, position, archived) VALUES ($DEFAULT_BUTTON_ID, '+1', 1, 0, 0)"

/**
 * Version 1 -> 2 (step 3, configurable buttons): adds the `buttons` table with the default "+1"
 * button, and gives `tap_events` a `buttonId`. Every existing tap was a +1 press, so all of them
 * are attached to the default button; ids, timestamps and weights are left untouched, so the
 * total is unchanged.
 *
 * The SQL mirrors `schemas/.../2.json` exactly (Room validates the result against it).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `buttons` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `weight` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                "`archived` INTEGER NOT NULL)",
        )
        db.execSQL(INSERT_DEFAULT_BUTTON_SQL)
        // SQLite allows adding a column with a REFERENCES clause as long as its default is NULL,
        // so the table doesn't need to be rebuilt.
        db.execSQL(
            "ALTER TABLE `tap_events` ADD COLUMN `buttonId` INTEGER " +
                "REFERENCES `buttons`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION",
        )
        db.execSQL("UPDATE `tap_events` SET `buttonId` = $DEFAULT_BUTTON_ID")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tap_events_buttonId` ON `tap_events` (`buttonId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tap_events_timestamp` ON `tap_events` (`timestamp`)")
    }
}

/** Every migration, oldest first. Add each new one here. */
internal val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
