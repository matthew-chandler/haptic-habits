package com.machandler.haptichabits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.machandler.haptichabits.data.HabitButton
import com.machandler.haptichabits.data.HabitButtonDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the Buttons screen in Settings: add, rename, change the value of, and delete buttons. */
class ButtonsViewModel(private val dao: HabitButtonDao) : ViewModel() {

    /** The active buttons in display order, or null until loaded. */
    val buttons: StateFlow<List<HabitButton>?> = dao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Adds a button at the end. Returns false, and changes nothing, if the input isn't valid. */
    fun add(name: String, value: String): Boolean {
        val cleanName = ButtonRules.cleanName(name) ?: return false
        val weight = ButtonRules.parseValue(value) ?: return false
        viewModelScope.launch { dao.add(cleanName, weight) }
        return true
    }

    /**
     * Renames a button and sets its value. Returns false, and changes nothing, if the input isn't
     * valid. Presses already recorded keep the value they had.
     */
    fun update(id: Long, name: String, value: String): Boolean {
        val cleanName = ButtonRules.cleanName(name) ?: return false
        val weight = ButtonRules.parseValue(value) ?: return false
        viewModelScope.launch { dao.update(id, cleanName, weight) }
        return true
    }

    /** Removes a button from the app. Its past presses stay in the total (it is archived, not deleted). */
    fun delete(id: Long) {
        viewModelScope.launch { dao.archive(id) }
    }
}
