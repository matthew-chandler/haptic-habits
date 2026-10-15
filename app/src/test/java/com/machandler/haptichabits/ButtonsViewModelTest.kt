package com.machandler.haptichabits

import com.machandler.haptichabits.data.HabitButton
import com.machandler.haptichabits.data.HabitButtonDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ButtonsViewModelTest {
    private lateinit var dao: FakeHabitButtonDao
    private lateinit var viewModel: ButtonsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        dao = FakeHabitButtonDao()
        viewModel = ButtonsViewModel(dao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun showsTheActiveButtons() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.buttons.collect {} }

        assertEquals(listOf("+1"), viewModel.buttons.value?.map { it.name })
    }

    @Test
    fun addStoresTheTrimmedNameAndParsedValue() = runTest {
        assertTrue(viewModel.add("  Snack  ", "-12"))
        advanceUntilIdle()

        assertEquals("Snack" to -12, dao.active().last().let { it.name to it.weight })
    }

    @Test
    fun addRejectsInvalidInputAndChangesNothing() = runTest {
        val tooLong = "x".repeat(ButtonRules.MAX_NAME_LENGTH + 1)
        listOf("" to "1", "   " to "1", tooLong to "1", "Snack" to "0", "Snack" to "", "Snack" to "-", "Snack" to "1000")
            .forEach { (name, value) -> assertFalse("$name / $value", viewModel.add(name, value)) }
        advanceUntilIdle()

        assertEquals(listOf("+1"), dao.active().map { it.name })
    }

    @Test
    fun updateRenamesAndRevalues() = runTest {
        assertTrue(viewModel.update(1, " Nail biting ", "5"))
        advanceUntilIdle()

        assertEquals(HabitButton(id = 1, name = "Nail biting", weight = 5, position = 0), dao.active().single())
    }

    @Test
    fun updateRejectsInvalidInput() = runTest {
        assertFalse(viewModel.update(1, "Nail biting", "0"))
        assertFalse(viewModel.update(1, "", "5"))
        advanceUntilIdle()

        assertEquals(HabitButton(id = 1, name = "+1", weight = 1, position = 0), dao.active().single())
    }

    @Test
    fun deleteArchivesTheButton() = runTest {
        viewModel.delete(1)
        advanceUntilIdle()

        assertEquals(emptyList<HabitButton>(), dao.active())
        assertEquals(listOf(1L), dao.archivedIds())
    }
}

/** In-memory stand-in for the Room DAO, starting like a fresh install with the "+1" button. */
private class FakeHabitButtonDao : HabitButtonDao {
    private val buttons = MutableStateFlow(listOf(HabitButton(id = 1, name = "+1", weight = 1, position = 0)))

    override fun observeActive(): Flow<List<HabitButton>> =
        buttons.map { list -> list.filterNot { it.archived }.sortedWith(compareBy({ it.position }, { it.id })) }

    override suspend fun add(name: String, weight: Int): Long {
        val id = (buttons.value.maxOfOrNull { it.id } ?: 0) + 1
        val position = (buttons.value.maxOfOrNull { it.position } ?: -1) + 1
        buttons.update { it + HabitButton(id = id, name = name, weight = weight, position = position) }
        return id
    }

    override suspend fun update(id: Long, name: String, weight: Int): Int = change(id) { it.copy(name = name, weight = weight) }

    override suspend fun archive(id: Long): Int = change(id) { it.copy(archived = true) }

    private fun change(id: Long, transform: (HabitButton) -> HabitButton): Int {
        val found = buttons.value.any { it.id == id }
        buttons.update { list -> list.map { if (it.id == id) transform(it) else it } }
        return if (found) 1 else 0
    }

    fun active(): List<HabitButton> = buttons.value.filterNot { it.archived }

    fun archivedIds(): List<Long> = buttons.value.filter { it.archived }.map { it.id }
}
