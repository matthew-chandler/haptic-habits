package com.machandler.haptichabits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TotalChangeWatcherTest {
    private val watcher = TotalChangeWatcher()

    @Test
    fun firstValueNeverFlashes() {
        // Launch: blank while loading, then the stored total appears.
        assertNull(watcher.onTotal(null))
        assertNull(watcher.onTotal(42))
    }

    @Test
    fun firstValueWithoutALoadingStepNeverFlashes() {
        // Coming back from Settings: the total is already known, so it appears at once.
        assertNull(watcher.onTotal(42))
    }

    @Test
    fun reportsEachChangeWithItsDirection() {
        watcher.onTotal(10)

        assertEquals(1L, watcher.onTotal(11))
        assertEquals(3L, watcher.onTotal(14))
        assertEquals(-3L, watcher.onTotal(11)) // e.g. Undo
        assertEquals(-2L, watcher.onTotal(9)) // e.g. a negative button
    }

    @Test
    fun sameValueAgainDoesNotFlash() {
        watcher.onTotal(5)

        assertNull(watcher.onTotal(5))
        assertEquals(1L, watcher.onTotal(6))
    }

    @Test
    fun nullAfterLoadingIsIgnoredAndTheNextChangeIsMeasuredFromTheLastValue() {
        watcher.onTotal(5)

        assertNull(watcher.onTotal(null))
        assertEquals(2L, watcher.onTotal(7))
    }

    @Test
    fun crossingZeroKeepsTheDirection() {
        watcher.onTotal(1)

        assertEquals(-2L, watcher.onTotal(-1))
        assertEquals(1L, watcher.onTotal(0))
    }

    @Test
    fun aNewWatcherStartsOverWithoutFlashing() {
        watcher.onTotal(5)
        watcher.onTotal(6)

        // The home screen makes a new watcher each time it is shown.
        val next = TotalChangeWatcher()
        assertNull(next.onTotal(6))
        assertEquals(-1L, next.onTotal(5))
    }
}
