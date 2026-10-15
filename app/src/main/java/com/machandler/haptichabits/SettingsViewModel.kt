package com.machandler.haptichabits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.machandler.haptichabits.data.AppSettings
import com.machandler.haptichabits.data.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val store: SettingsStore) : ViewModel() {

    /** The stored settings, or null until loaded so the switches never flash their defaults. */
    val settings: StateFlow<AppSettings?> = store.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setVibrateOnPositive(enabled: Boolean) {
        viewModelScope.launch { store.setVibrateOnPositive(enabled) }
    }

    fun setVibrateOnNegative(enabled: Boolean) {
        viewModelScope.launch { store.setVibrateOnNegative(enabled) }
    }

    fun setInvertColors(enabled: Boolean) {
        viewModelScope.launch { store.setInvertColors(enabled) }
    }
}
