package com.machandler.haptichabits.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
}
