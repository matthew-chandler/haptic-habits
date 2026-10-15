package com.machandler.haptichabits

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.machandler.haptichabits.data.AppSettings
import com.machandler.haptichabits.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = SettingsViewModel(SettingsStore(InMemoryDataStore()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun showsDefaultsOnceLoaded() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.settings.collect {} }

        assertEquals(AppSettings(vibrateOnPositive = true, vibrateOnNegative = true), viewModel.settings.value)
    }

    @Test
    fun switchesUpdateTheStoredSettings() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.settings.collect {} }

        viewModel.setVibrateOnPositive(false)
        advanceUntilIdle()
        assertEquals(AppSettings(vibrateOnPositive = false, vibrateOnNegative = true), viewModel.settings.value)

        viewModel.setVibrateOnNegative(false)
        advanceUntilIdle()
        assertEquals(AppSettings(vibrateOnPositive = false, vibrateOnNegative = false), viewModel.settings.value)
    }
}

/** In-memory stand-in for the DataStore file, so ViewModel logic can be tested without disk. */
private class InMemoryDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}
