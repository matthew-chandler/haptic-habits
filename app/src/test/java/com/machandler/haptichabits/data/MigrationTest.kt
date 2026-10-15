package com.machandler.haptichabits.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.machandler.haptichabits.data.HabitDatabase.Companion.setUp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Upgrades real version-1 databases, built from the committed `schemas/.../1.json`, by opening
 * them the way the app does. Room runs the migrations and then validates the resulting tables,
 * columns, foreign keys and indices against the current entities (the ones `2.json` was exported
 * from), failing the open if anything differs.
 *
 * (Room's MigrationTestHelper isn't used because it reads the schemas from the app's assets, and
 * under Robolectric that would mean shipping them inside the debug APK.)
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private lateinit var context: Context
    private var db: HabitDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB_NAME)
    }

    @After
    fun tearDown() {
        db?.close()
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun migrate1To2KeepsEveryTapAndAttachesItToTheDefaultButton() = runTest {
        createVersion1Database {
            // Gaps in the ids, as left behind by undo.
            execSQL("INSERT INTO tap_events (id, timestamp, weight) VALUES (1, 1000, 1), (2, 2000, 1), (5, 5000, 1)")
        }

        val db = openWithAppSetup()

        val taps = db.query("SELECT id, timestamp, weight, buttonId FROM tap_events ORDER BY id", null).use { c ->
            buildList { while (c.moveToNext()) add(listOf(c.getLong(0), c.getLong(1), c.getLong(2), c.getLong(3))) }
        }
        assertEquals(
            listOf(
                listOf(1L, 1000L, 1L, DEFAULT_BUTTON_ID),
                listOf(2L, 2000L, 1L, DEFAULT_BUTTON_ID),
                listOf(5L, 5000L, 1L, DEFAULT_BUTTON_ID),
            ),
            taps,
        )
        assertEquals(3L, db.tapEventDao().observeTotal().first())
        // Exactly one button, the default: the fresh-install seeding must not also run on upgrade.
        val buttons = db.query("SELECT id, name, weight, position, archived FROM buttons", null).use { c ->
            buildList { while (c.moveToNext()) add(listOf(c.getLong(0), c.getString(1), c.getInt(2), c.getInt(3), c.getInt(4))) }
        }
        assertEquals(listOf(listOf(DEFAULT_BUTTON_ID, "+1", 1, 0, 0)), buttons)
    }

    @Test
    fun migratedDatabaseKeepsWorking() = runTest {
        createVersion1Database {
            execSQL("INSERT INTO tap_events (id, timestamp, weight) VALUES (1, 1000, 1), (2, 2000, 1), (3, 3000, 1)")
            // Undo deleted the newest row; its id must not be handed out again.
            execSQL("INSERT INTO tap_events (id, timestamp, weight) VALUES (4, 4000, 1)")
            execSQL("DELETE FROM tap_events WHERE id = 4")
        }

        val db = openWithAppSetup()

        assertEquals(
            listOf(HabitButton(id = DEFAULT_BUTTON_ID, name = "+1", weight = 1, position = 0)),
            db.habitButtonDao().observeActive().first(),
        )
        val newId = db.tapEventDao().insert(TapEvent(timestamp = 6000, weight = 1, buttonId = DEFAULT_BUTTON_ID))
        assertTrue("ids keep counting up after the migration", newId > 4)
        assertEquals(4L, db.tapEventDao().observeTotal().first())
        // New buttons go after the default one.
        db.habitButtonDao().add("Snack", 3)
        assertEquals(listOf("+1", "Snack"), db.habitButtonDao().observeActive().first().map { it.name })
    }

    @Test
    fun migrateEmpty1To2GivesJustTheDefaultButton() = runTest {
        createVersion1Database {}

        val db = openWithAppSetup()

        assertEquals(0L, db.tapEventDao().observeTotal().first())
        assertEquals(listOf("+1"), db.habitButtonDao().observeActive().first().map { it.name })
    }

    /**
     * Creates the test database exactly as version 1 of the app left it: the tables, indices and
     * Room bookkeeping from the exported 1.json, and SQLite's user_version set to 1.
     */
    private fun createVersion1Database(fill: SQLiteDatabase.() -> Unit) {
        // Unit tests run with the module directory as the working directory.
        val schema = JSONObject(File(SCHEMAS_DIR, "1.json").readText()).getJSONObject("database")
        assertEquals(1, schema.getInt("version"))
        val file = context.getDatabasePath(DB_NAME)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace(TABLE_NAME_PLACEHOLDER, table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace(TABLE_NAME_PLACEHOLDER, table))
                }
            }
            val setupQueries = schema.getJSONArray("setupQueries")
            for (i in 0 until setupQueries.length()) db.execSQL(setupQueries.getString(i))
            db.version = 1
            db.fill()
        }
    }

    /** Opens the test database the way the app does, so its migrations, callbacks and validation run. */
    private fun openWithAppSetup(): HabitDatabase =
        Room.databaseBuilder(context, HabitDatabase::class.java, DB_NAME)
            .setUp()
            .allowMainThreadQueries()
            .build()
            .also { db = it }

    private companion object {
        const val DB_NAME = "migration-test.db"
        const val SCHEMAS_DIR = "schemas/com.machandler.haptichabits.data.HabitDatabase"
        const val TABLE_NAME_PLACEHOLDER = "\${TABLE_NAME}"
    }
}
