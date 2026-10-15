package com.machandler.haptichabits.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.machandler.haptichabits.data.HabitDatabase.Companion.setUp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HabitButtonDaoTest {
    private lateinit var db: HabitDatabase
    private lateinit var dao: HabitButtonDao
    private lateinit var tapDao: TapEventDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Built with the app's setup, so it starts like a fresh install: with the default button.
        db = Room.inMemoryDatabaseBuilder(context, HabitDatabase::class.java)
            .setUp()
            .allowMainThreadQueries()
            .build()
        dao = db.habitButtonDao()
        tapDao = db.tapEventDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun freshInstallStartsWithTheDefaultPlusOneButton() = runTest {
        assertEquals(
            listOf(HabitButton(id = DEFAULT_BUTTON_ID, name = "+1", weight = 1, position = 0)),
            dao.observeActive().first(),
        )
    }

    @Test
    fun addAppendsButtonsInOrder() = runTest {
        val snack = dao.add("Snack", 3)
        val walk = dao.add("Walk", -2)

        val buttons = dao.observeActive().first()
        assertEquals(listOf(DEFAULT_BUTTON_ID, snack, walk), buttons.map { it.id })
        assertEquals(listOf(0, 1, 2), buttons.map { it.position })
        assertEquals(HabitButton(id = walk, name = "Walk", weight = -2, position = 2), buttons.last())
    }

    @Test
    fun addGoesAfterDeletedButtonsToo() = runTest {
        val snack = dao.add("Snack", 3)
        dao.archive(snack)

        val walk = dao.add("Walk", -2)

        assertEquals(2, dao.observeActive().first().single { it.id == walk }.position)
    }

    @Test
    fun updateRenamesAndRevaluesWithoutChangingPastTaps() = runTest {
        tapDao.insert(TapEvent(timestamp = 1_000, weight = 1, buttonId = DEFAULT_BUTTON_ID))

        assertEquals(1, dao.update(DEFAULT_BUTTON_ID, "Nail biting", 5))

        assertEquals(
            HabitButton(id = DEFAULT_BUTTON_ID, name = "Nail biting", weight = 5, position = 0),
            dao.observeActive().first().single(),
        )
        assertEquals(1L, tapDao.observeTotal().first())
    }

    @Test
    fun archiveHidesTheButtonButKeepsItAndItsTaps() = runTest {
        val snack = dao.add("Snack", 3)
        tapDao.insert(TapEvent(timestamp = 1_000, weight = 3, buttonId = snack))

        assertEquals(1, dao.archive(snack))

        assertEquals(listOf(DEFAULT_BUTTON_ID), dao.observeActive().first().map { it.id })
        assertEquals(3L, tapDao.observeTotal().first())
        // The row is still there with its name, for History and Trends.
        val name = db.query("SELECT name FROM buttons WHERE id = ?", arrayOf(snack)).use { c ->
            c.moveToFirst()
            c.getString(0)
        }
        assertEquals("Snack", name)
    }

    @Test
    fun deletingEveryButtonLeavesNoneActive() = runTest {
        dao.archive(DEFAULT_BUTTON_ID)

        assertEquals(emptyList<HabitButton>(), dao.observeActive().first())
    }
}
