package com.machandler.haptichabits.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

// One DataStore per process for this file (DataStore requires that). Settings are only
// preferences, so a corrupt file is replaced by the defaults rather than failing forever.
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** Reads and writes [AppSettings], persisted with Jetpack DataStore (Preferences). */
class SettingsStore(private val dataStore: DataStore<Preferences>) {

    /** Emits the current settings, and again whenever they change. */
    val settings: Flow<AppSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            AppSettings(
                vibrateOnPositive = prefs[VIBRATE_ON_POSITIVE] ?: DEFAULTS.vibrateOnPositive,
                vibrateOnNegative = prefs[VIBRATE_ON_NEGATIVE] ?: DEFAULTS.vibrateOnNegative,
            )
        }
        .distinctUntilChanged()

    suspend fun setVibrateOnPositive(enabled: Boolean) {
        dataStore.edit { it[VIBRATE_ON_POSITIVE] = enabled }
    }

    suspend fun setVibrateOnNegative(enabled: Boolean) {
        dataStore.edit { it[VIBRATE_ON_NEGATIVE] = enabled }
    }

    companion object {
        private val DEFAULTS = AppSettings()

        // These key names are what is stored on disk; never rename them.
        private val VIBRATE_ON_POSITIVE = booleanPreferencesKey("vibrate_on_positive")
        private val VIBRATE_ON_NEGATIVE = booleanPreferencesKey("vibrate_on_negative")

        /** The app's settings, backed by the single on-disk settings file. */
        fun from(context: Context): SettingsStore = SettingsStore(context.applicationContext.settingsDataStore)
    }
}
