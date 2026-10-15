package com.machandler.haptichabits.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

// DataStore Preferences runs on the plain JVM, so this needs no Robolectric; a real file in a
// temporary folder stands in for the app's settings file.
class SettingsStoreTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val file: File by lazy { File(tempFolder.root, "settings.preferences_pb") }
    private val jobs = mutableListOf<Job>()

    /** Opens the settings file; each call is a fresh DataStore, like a new app process. */
    private fun openStore(): SettingsStore {
        val job = Job()
        jobs += job
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { file },
        )
        return SettingsStore(dataStore)
    }

    @After
    fun tearDown() {
        jobs.forEach { it.cancel() }
    }

    @Test
    fun bothSwitchesAreOnByDefault() = runTest {
        assertEquals(AppSettings(vibrateOnPositive = true, vibrateOnNegative = true), openStore().settings.first())
    }

    @Test
    fun switchesAreStoredIndependently() = runTest {
        val store = openStore()

        store.setVibrateOnPositive(false)
        assertEquals(AppSettings(vibrateOnPositive = false, vibrateOnNegative = true), store.settings.first())

        store.setVibrateOnNegative(false)
        store.setVibrateOnPositive(true)
        assertEquals(AppSettings(vibrateOnPositive = true, vibrateOnNegative = false), store.settings.first())
    }

    @Test
    fun colorsAreNotInvertedByDefault() = runTest {
        assertEquals(false, openStore().settings.first().invertColors)
    }

    @Test
    fun invertColorsIsStoredWithoutTouchingTheVibrationSwitches() = runTest {
        val store = openStore()
        store.setVibrateOnNegative(false)

        store.setInvertColors(true)
        assertEquals(
            AppSettings(vibrateOnPositive = true, vibrateOnNegative = false, invertColors = true),
            store.settings.first(),
        )

        store.setInvertColors(false)
        assertEquals(AppSettings(vibrateOnPositive = true, vibrateOnNegative = false), store.settings.first())
    }

    @Test
    fun settingsSurviveReopeningTheFile() = runTest {
        val store = openStore()
        store.setVibrateOnPositive(false)
        store.setVibrateOnNegative(false)
        store.setInvertColors(true)
        // Only one DataStore may have the file open at a time, as in the app.
        jobs.last().cancelAndJoin()

        assertEquals(
            AppSettings(vibrateOnPositive = false, vibrateOnNegative = false, invertColors = true),
            openStore().settings.first(),
        )
    }
}
