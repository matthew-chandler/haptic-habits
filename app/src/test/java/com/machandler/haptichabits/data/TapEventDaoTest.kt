package com.machandler.haptichabits.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.machandler.haptichabits.withRunningSums
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TapEventDaoTest {
    private lateinit var db: HabitDatabase
    private lateinit var dao: TapEventDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HabitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.tapEventDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun totalIsZeroWhenEmpty() = runTest {
        assertEquals(0L, dao.observeTotal().first())
    }

    @Test
    fun totalSumsWeightsOfAllTaps() = runTest {
        dao.insert(TapEvent(timestamp = 1_000, weight = 1))
        dao.insert(TapEvent(timestamp = 2_000, weight = 1))
        dao.insert(TapEvent(timestamp = 3_000, weight = 1))

        assertEquals(3L, dao.observeTotal().first())
    }

    @Test
    fun totalHandlesLargerAndNegativeWeights() = runTest {
        dao.insert(TapEvent(timestamp = 1_000, weight = 5))
        dao.insert(TapEvent(timestamp = 2_000, weight = -2))

        assertEquals(3L, dao.observeTotal().first())
    }

    @Test
    fun insertAssignsDistinctIds() = runTest {
        val first = dao.insert(TapEvent(timestamp = 1_000, weight = 1))
        val second = dao.insert(TapEvent(timestamp = 1_000, weight = 1))

        assertNotEquals(first, second)
    }

    @Test
    fun deleteByIdRemovesOnlyThatTap() = runTest {
        dao.insert(TapEvent(timestamp = 1_000, weight = 1))
        val second = dao.insert(TapEvent(timestamp = 2_000, weight = 5))
        dao.insert(TapEvent(timestamp = 3_000, weight = 1))

        assertEquals(1, dao.deleteById(second))
        assertEquals(2L, dao.observeTotal().first())
    }

    @Test
    fun deleteByIdReturnsZeroWhenAlreadyGone() = runTest {
        val id = dao.insert(TapEvent(timestamp = 1_000, weight = 1))
        dao.deleteById(id)

        assertEquals(0, dao.deleteById(id))
        assertEquals(0L, dao.observeTotal().first())
    }

    @Test
    fun insertStoresTheButtonId() = runTest {
        val button = db.habitButtonDao().add("Snack", 3)
        val id = dao.insert(TapEvent(timestamp = 1_000, weight = 3, buttonId = button))

        val stored = db.query("SELECT buttonId FROM tap_events WHERE id = ?", arrayOf(id)).use { c ->
            c.moveToFirst()
            c.getLong(0)
        }
        assertEquals(button, stored)
    }

    @Test
    fun insertRejectsAButtonIdThatDoesNotExist() {
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { dao.insert(TapEvent(timestamp = 1_000, weight = 1, buttonId = 999)) }
        }
    }

    @Test
    fun historyIsEmptyWhenThereAreNoEntries() = runTest {
        assertEquals(emptyList<HistoryEntry>(), dao.observeHistory().first())
    }

    @Test
    fun historyIsNewestFirstByInsertionOrderNotTimestamp() = runTest {
        val button = db.habitButtonDao().add("Snack", 3)
        val first = dao.insert(TapEvent(timestamp = 5_000, weight = 3, buttonId = button))
        // The phone's clock was set back before the next presses.
        val second = dao.insert(TapEvent(timestamp = 1_000, weight = 3, buttonId = button))
        val third = dao.insert(TapEvent(timestamp = 3_000, weight = 3, buttonId = button))

        assertEquals(listOf(third, second, first), dao.observeHistory().first().map { it.id })
    }

    @Test
    fun historyHasEachEntrysOwnValueAndTime() = runTest {
        val button = db.habitButtonDao().add("Snack", 3)
        val id = dao.insert(TapEvent(timestamp = 1_234, weight = 3, buttonId = button))
        db.habitButtonDao().update(button, "Snack", 7) // Later edits don't change past entries.

        assertEquals(listOf(HistoryEntry(id, 1_234, 3, "Snack")), dao.observeHistory().first())
    }

    @Test
    fun historyNamesTheButtonEvenAfterItIsRenamedOrDeleted() = runTest {
        val buttons = db.habitButtonDao()
        val snack = buttons.add("Snack", 3)
        val walk = buttons.add("Walk", -2)
        dao.insert(TapEvent(timestamp = 1_000, weight = 3, buttonId = snack))
        dao.insert(TapEvent(timestamp = 2_000, weight = -2, buttonId = walk))
        buttons.archive(snack)
        buttons.update(walk, "Long walk", -2)

        assertEquals(listOf("Long walk", "Snack"), dao.observeHistory().first().map { it.buttonName })
    }

    @Test
    fun historyEntryWithoutAButtonHasNoName() = runTest {
        dao.insert(TapEvent(timestamp = 1_000, weight = 1, buttonId = null))

        assertNull(dao.observeHistory().first().single().buttonName)
    }

    @Test
    fun historyLeavesOutDeletedEntries() = runTest {
        val first = dao.insert(TapEvent(timestamp = 1_000, weight = 1))
        val second = dao.insert(TapEvent(timestamp = 2_000, weight = 5))
        val third = dao.insert(TapEvent(timestamp = 3_000, weight = 1))

        dao.deleteById(second)

        assertEquals(listOf(third, first), dao.observeHistory().first().map { it.id })
    }

    @Test
    fun newestRunningSumMatchesTheTotalForManyEntries() = runTest {
        db.runInTransaction {
            repeat(5_000) {
                db.openHelper.writableDatabase.execSQL(
                    "INSERT INTO tap_events (timestamp, weight) VALUES (?, ?)",
                    arrayOf<Any>(it.toLong(), if (it % 4 == 0) -7 else 3),
                )
            }
        }

        val rows = withRunningSums(dao.observeHistory().first())

        assertEquals(5_000, rows.size)
        assertEquals(dao.observeTotal().first(), rows.first().sumAfter)
        assertEquals(-7L, rows.last().sumAfter)
    }

    @Test
    fun totalIsZeroRightAfterAReset() = runTest {
        dao.insert(TapEvent(timestamp = 1_000, weight = 5))
        dao.insert(TapEvent(timestamp = 2_000, weight = -2))

        dao.insert(TapEvent.reset(timestamp = 3_000))

        assertEquals(0L, dao.observeTotal().first())
    }

    @Test
    fun totalCountsOnlyEntriesAfterTheLatestReset() = runTest {
        dao.insert(TapEvent(timestamp = 1_000, weight = 5))
        dao.insert(TapEvent.reset(timestamp = 2_000))
        dao.insert(TapEvent(timestamp = 3_000, weight = 3))
        dao.insert(TapEvent.reset(timestamp = 4_000))
        dao.insert(TapEvent(timestamp = 5_000, weight = 2))
        dao.insert(TapEvent(timestamp = 6_000, weight = -7))

        assertEquals(-5L, dao.observeTotal().first())
    }

    @Test
    fun latestResetIsTheNewestByIdNotByTimestamp() = runTest {
        dao.insert(TapEvent(timestamp = 5_000, weight = 4))
        // The phone's clock was set back: this reset is newer than the tap despite its timestamp.
        dao.insert(TapEvent.reset(timestamp = 1_000))
        dao.insert(TapEvent(timestamp = 500, weight = 1))

        assertEquals(1L, dao.observeTotal().first())
    }

    @Test
    fun deletingTheLatestResetBringsBackTheTotalWithoutIt() = runTest {
        dao.insert(TapEvent(timestamp = 1_000, weight = 5))
        dao.insert(TapEvent.reset(timestamp = 2_000))
        dao.insert(TapEvent(timestamp = 3_000, weight = 3))
        val latest = dao.insert(TapEvent.reset(timestamp = 4_000))
        dao.insert(TapEvent(timestamp = 5_000, weight = 2))

        dao.deleteById(latest)

        // Back to counting from the earlier reset: 3 + 2.
        assertEquals(5L, dao.observeTotal().first())
    }

    @Test
    fun deletingTheOnlyResetBringsBackEverything() = runTest {
        dao.insert(TapEvent(timestamp = 1_000, weight = 5))
        val reset = dao.insert(TapEvent.reset(timestamp = 2_000))

        dao.deleteById(reset)

        assertEquals(5L, dao.observeTotal().first())
    }

    @Test
    fun deletingEntriesBeforeTheLatestResetDoesNotChangeTheTotal() = runTest {
        val tap = dao.insert(TapEvent(timestamp = 1_000, weight = 5))
        val olderReset = dao.insert(TapEvent.reset(timestamp = 2_000))
        dao.insert(TapEvent.reset(timestamp = 3_000))
        dao.insert(TapEvent(timestamp = 4_000, weight = 2))

        dao.deleteById(tap)
        assertEquals(2L, dao.observeTotal().first())
        dao.deleteById(olderReset)
        assertEquals(2L, dao.observeTotal().first())
    }

    @Test
    fun resetIsStoredWithItsTypeNoButtonAndNoWeight() = runTest {
        val id = dao.insert(TapEvent.reset(timestamp = 1_234))

        val stored = db.query("SELECT type, buttonId, weight FROM tap_events WHERE id = ?", arrayOf(id)).use { c ->
            c.moveToFirst()
            Triple(c.getInt(0), c.isNull(1), c.getInt(2))
        }
        assertEquals(Triple(EntryType.RESET, true, 0), stored)
    }

    @Test
    fun tapsAreStoredAsTapsByDefault() = runTest {
        val id = dao.insert(TapEvent(timestamp = 1_000, weight = 1))

        val type = db.query("SELECT type FROM tap_events WHERE id = ?", arrayOf(id)).use { c ->
            c.moveToFirst()
            c.getInt(0)
        }
        assertEquals(EntryType.TAP, type)
    }

    @Test
    fun historyMarksResets() = runTest {
        val button = db.habitButtonDao().add("Snack", 3)
        val tap = dao.insert(TapEvent(timestamp = 1_000, weight = 3, buttonId = button))
        val reset = dao.insert(TapEvent.reset(timestamp = 2_000))

        assertEquals(
            listOf(
                HistoryEntry(reset, 2_000, 0, null, EntryType.RESET),
                HistoryEntry(tap, 1_000, 3, "Snack", EntryType.TAP),
            ),
            dao.observeHistory().first(),
        )
        assertEquals(listOf(true, false), dao.observeHistory().first().map { it.isReset })
    }

    @Test
    fun newestRunningSumMatchesTheTotalWithManyResets() = runTest {
        db.runInTransaction {
            repeat(5_000) {
                db.openHelper.writableDatabase.execSQL(
                    "INSERT INTO tap_events (timestamp, weight, type) VALUES (?, ?, ?)",
                    if (it % 997 == 500) {
                        arrayOf<Any>(it.toLong(), 0, EntryType.RESET)
                    } else {
                        arrayOf<Any>(it.toLong(), if (it % 4 == 0) -7 else 3, EntryType.TAP)
                    },
                )
            }
        }

        val rows = withRunningSums(dao.observeHistory().first())
        val total = dao.observeTotal().first()

        assertEquals(5_000, rows.size)
        assertEquals(total, rows.first().sumAfter)
        assertEquals(0L, rows.first { it.entry.isReset }.sumAfter)
        assertTrue(total != 0L)
    }

    @Test
    fun totalQueryFindsTheLatestResetThroughTheTypeIndex() {
        val plan = db.query(
            "EXPLAIN QUERY PLAN SELECT COALESCE(SUM(weight), 0) FROM tap_events WHERE id > " +
                "COALESCE((SELECT MAX(id) FROM tap_events WHERE type = ${EntryType.RESET}), 0)",
            null,
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("detail"))) } }

        assertTrue(plan.toString(), plan.any { "index_tap_events_type" in it })
    }

    @Test
    fun trendEntriesAreEmptyWhenThereAreNoEntries() = runTest {
        assertEquals(emptyList<TrendEntry>(), dao.observeTrendEntries().first())
    }

    @Test
    fun trendEntriesAreOldestFirstByIdNotTimestampWithResets() = runTest {
        val button = db.habitButtonDao().add("Snack", 3)
        val first = dao.insert(TapEvent(timestamp = 5_000, weight = 3, buttonId = button))
        // The phone's clock was set back before the reset and the next press.
        val reset = dao.insert(TapEvent.reset(timestamp = 1_000))
        val third = dao.insert(TapEvent(timestamp = 3_000, weight = -2, buttonId = button))

        assertEquals(
            listOf(
                TrendEntry(first, 5_000, 3, button, EntryType.TAP),
                TrendEntry(reset, 1_000, 0, null, EntryType.RESET),
                TrendEntry(third, 3_000, -2, button, EntryType.TAP),
            ),
            dao.observeTrendEntries().first(),
        )
    }

    @Test
    fun trendEntriesLoadManyRowsAndEndOnTheTotal() = runTest {
        db.runInTransaction {
            repeat(20_000) {
                db.openHelper.writableDatabase.execSQL(
                    "INSERT INTO tap_events (timestamp, weight, type) VALUES (?, ?, ?)",
                    if (it % 1_999 == 1_000) {
                        arrayOf<Any>(it * 60_000L, 0, EntryType.RESET)
                    } else {
                        arrayOf<Any>(it * 60_000L, if (it % 4 == 0) -7 else 3, EntryType.TAP)
                    },
                )
            }
        }

        val entries = dao.observeTrendEntries().first()
        // The same running total as History and home: restart at 0 at each reset, in id order.
        val total = entries.fold(0L) { sum, e -> if (e.isReset) 0 else sum + e.weight }

        assertEquals(20_000, entries.size)
        assertEquals(entries.map { it.id }.sorted(), entries.map { it.id })
        assertEquals(dao.observeTotal().first(), total)
    }
}
