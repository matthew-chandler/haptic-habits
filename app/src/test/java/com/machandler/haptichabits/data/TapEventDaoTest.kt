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
}
