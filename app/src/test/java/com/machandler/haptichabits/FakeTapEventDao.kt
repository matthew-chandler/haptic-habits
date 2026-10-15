package com.machandler.haptichabits

import com.machandler.haptichabits.data.HistoryEntry
import com.machandler.haptichabits.data.TapEvent
import com.machandler.haptichabits.data.TapEventDao
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory stand-in for the Room DAO, so ViewModel logic can be tested without a database. */
class FakeTapEventDao(
    /** Button names by id, for [observeHistory] (the real DAO joins the buttons table). */
    private val buttonNames: Map<Long, String> = emptyMap(),
) : TapEventDao {
    private val events = MutableStateFlow<List<TapEvent>>(emptyList())
    private var nextId = 1L
    val deletedIds = mutableListOf<Long>()

    /** When set, the next insert suspends until it completes. */
    var insertGate: CompletableDeferred<Unit>? = null

    override suspend fun insert(event: TapEvent): Long {
        insertGate?.also { insertGate = null }?.await()
        val id = nextId++
        events.update { it + event.copy(id = id) }
        return id
    }

    override suspend fun deleteById(id: Long): Int {
        val before = events.value.size
        events.update { list -> list.filterNot { it.id == id } }
        val deleted = before - events.value.size
        if (deleted > 0) deletedIds += id
        return deleted
    }

    override fun observeTotal(): Flow<Long> = events.map { list -> list.sumOf { it.weight.toLong() } }

    override fun observeHistory(): Flow<List<HistoryEntry>> = events.map { list ->
        list.sortedByDescending { it.id }
            .map { HistoryEntry(it.id, it.timestamp, it.weight, it.buttonId?.let(buttonNames::get)) }
    }

    fun ids(): List<Long> = events.value.map { it.id }

    fun events(): List<TapEvent> = events.value

    fun sum(): Long = events.value.sumOf { it.weight.toLong() }
}
