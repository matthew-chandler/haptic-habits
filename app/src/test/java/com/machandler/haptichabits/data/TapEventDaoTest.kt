package com.machandler.haptichabits.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
}
