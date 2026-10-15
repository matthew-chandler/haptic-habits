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
 * Upgrades real old-version databases, built from the committed `schemas/.../<version>.json`, by
 * opening them the way the app does. Room runs every migration from that version to the current
 * one and then validates the resulting tables, columns, foreign keys and indices against the
 * current entities (the ones the newest JSON was exported from), failing the open if anything
 * differs. So the version-1 tests below run the whole 1 -> 2 -> 3 chain.
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
        createDatabase(version = 1) {
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
        createDatabase(version = 1) {
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
        createDatabase(version = 1) {}

        val db = openWithAppSetup()

        assertEquals(0L, db.tapEventDao().observeTotal().first())
        assertEquals(listOf("+1"), db.habitButtonDao().observeActive().first().map { it.name })
    }

    @Test
    fun migrate2To3KeepsEveryEntryAsATapAndTheTotal() = runTest {
        createDatabase(version = 2) {
            execSQL(INSERT_DEFAULT_BUTTON_SQL)
            execSQL("INSERT INTO buttons (id, name, weight, position, archived) VALUES (2, 'Walk', -2, 1, 1)")
            // Gaps in the ids (undo, History deletes), an archived button, and a row with no button.
            execSQL(
                "INSERT INTO tap_events (id, timestamp, weight, buttonId) VALUES " +
                    "(1, 1000, 1, 1), (3, 3000, -2, 2), (4, 4000, 5, NULL), (7, 7000, 1, 1)",
            )
        }

        val db = openWithAppSetup()

        val rows = db.query("SELECT id, timestamp, weight, buttonId, type FROM tap_events ORDER BY id", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(listOf(c.getLong(0), c.getLong(1), c.getLong(2), if (c.isNull(3)) null else c.getLong(3), c.getLong(4)))
                }
            }
        }
        val tap = EntryType.TAP.toLong()
        assertEquals(
            listOf(
                listOf(1L, 1000L, 1L, 1L, tap),
                listOf(3L, 3000L, -2L, 2L, tap),
                listOf(4L, 4000L, 5L, null, tap),
                listOf(7L, 7000L, 1L, 1L, tap),
            ),
            rows,
        )
        assertEquals(5L, db.tapEventDao().observeTotal().first())
        assertEquals(
            listOf("+1", null, "Walk", "+1"),
            db.tapEventDao().observeHistory().first().map { it.buttonName },
        )
        assertTrue(db.tapEventDao().observeHistory().first().none { it.isReset })
        // Buttons are untouched, and the fresh-install seeding didn't run again.
        assertEquals(listOf("+1"), db.habitButtonDao().observeActive().first().map { it.name })
    }

    @Test
    fun databaseMigratedFrom2CanResetAndUndoTheReset() = runTest {
        createDatabase(version = 2) {
            execSQL(INSERT_DEFAULT_BUTTON_SQL)
            execSQL("INSERT INTO tap_events (id, timestamp, weight, buttonId) VALUES (1, 1000, 1, 1), (2, 2000, 1, 1)")
            // A deleted newest row; its id must not be handed out again.
            execSQL("INSERT INTO tap_events (id, timestamp, weight, buttonId) VALUES (3, 3000, 1, 1)")
            execSQL("DELETE FROM tap_events WHERE id = 3")
        }

        val db = openWithAppSetup()
        val dao = db.tapEventDao()

        val reset = dao.insert(TapEvent.reset(timestamp = 4000))
        assertTrue("ids keep counting up after the migration", reset > 3)
        assertEquals(0L, dao.observeTotal().first())
        dao.insert(TapEvent(timestamp = 5000, weight = 1, buttonId = DEFAULT_BUTTON_ID))
        assertEquals(1L, dao.observeTotal().first())

        dao.deleteById(reset)

        assertEquals(3L, dao.observeTotal().first())
    }

    @Test
    fun migrateEmpty2To3() = runTest {
        createDatabase(version = 2) { execSQL(INSERT_DEFAULT_BUTTON_SQL) }

        val db = openWithAppSetup()

        assertEquals(0L, db.tapEventDao().observeTotal().first())
        assertEquals(emptyList<HistoryEntry>(), db.tapEventDao().observeHistory().first())
    }

    @Test
    fun migrate1To3ChainKeepsEveryTapAsATapOfTheDefaultButton() = runTest {
        createDatabase(version = 1) {
            execSQL("INSERT INTO tap_events (id, timestamp, weight) VALUES (1, 1000, 1), (2, 2000, 1), (6, 6000, 1)")
        }

        val db = openWithAppSetup()

        val history = db.tapEventDao().observeHistory().first()
        assertEquals(
            listOf(
                HistoryEntry(6, 6000, 1, "+1", EntryType.TAP),
                HistoryEntry(2, 2000, 1, "+1", EntryType.TAP),
                HistoryEntry(1, 1000, 1, "+1", EntryType.TAP),
            ),
            history,
        )
        assertEquals(3L, db.tapEventDao().observeTotal().first())
        assertEquals(3, db.openHelper.readableDatabase.version)
    }

    /**
     * Creates the test database exactly as [version] of the app left it: the tables, indices and
     * Room bookkeeping from the exported `<version>.json`, and SQLite's user_version set to it.
     * Only what the schema has: e.g. version 2's default button came from a callback, so a test
     * that wants it inserts it in [fill].
     */
    private fun createDatabase(version: Int, fill: SQLiteDatabase.() -> Unit) {
        // Unit tests run with the module directory as the working directory.
        val schema = JSONObject(File(SCHEMAS_DIR, "$version.json").readText()).getJSONObject("database")
        assertEquals(version, schema.getInt("version"))
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
            db.version = version
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
