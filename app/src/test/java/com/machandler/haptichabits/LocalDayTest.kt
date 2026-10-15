package com.machandler.haptichabits

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

/** History's day headers group rows by [localDay]: the calendar day in the phone's time zone. */
class LocalDayTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val losAngeles = TimeZone.getTimeZone("America/Los_Angeles")

    @Test
    fun countsDaysFromTheEpoch() {
        assertEquals(0L, localDay(0, utc))
        assertEquals(0L, localDay(DAY - 1, utc))
        assertEquals(1L, localDay(DAY, utc))
    }

    @Test
    fun usesTheLocalCalendarDay() {
        // 2026-10-15 03:00 UTC (day 20741) is still the evening of Oct 14 in Los Angeles.
        val utcMorning = 20_741 * DAY + 3 * HOUR
        assertEquals(20_741L, localDay(utcMorning, utc))
        assertEquals(20_740L, localDay(utcMorning, losAngeles))
    }

    @Test
    fun beforeTheEpochRoundsDown() {
        assertEquals(-1L, localDay(-1, utc))
    }

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
    }
}
