package com.machandler.haptichabits

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/** Trends' buckets: days, weeks and months in local time. */
class TrendDatesTest {
    @Test
    fun dayOfWeekMatchesTheCalendar() {
        assertEquals(Calendar.THURSDAY, dayOfWeek(0)) // 1970-01-01
        assertEquals(Calendar.THURSDAY, dayOfWeek(OCT_15_2026))
        assertEquals(Calendar.SUNDAY, dayOfWeek(OCT_11_2026))
        assertEquals(Calendar.WEDNESDAY, dayOfWeek(-1)) // 1969-12-31
        // Every day of a few years agrees with java.util.Calendar.
        for (day in 19_000L..20_500L) {
            val calendar = GregorianCalendar(UTC).apply { timeInMillis = day * DAY }
            assertEquals("day $day", calendar.get(Calendar.DAY_OF_WEEK), dayOfWeek(day))
        }
    }

    @Test
    fun weeksStartOnTheLocalesFirstDay() {
        // Thu Oct 15, 2026.
        assertEquals(OCT_11_2026, weekStart(OCT_15_2026, Calendar.SUNDAY)) // US
        assertEquals(OCT_11_2026 + 1, weekStart(OCT_15_2026, Calendar.MONDAY)) // most of Europe
        assertEquals(OCT_11_2026 - 1, weekStart(OCT_15_2026, Calendar.SATURDAY)) // e.g. Egypt
        // The first day of the week starts its own week.
        assertEquals(OCT_11_2026, weekStart(OCT_11_2026, Calendar.SUNDAY))
        assertEquals(OCT_11_2026 - 6, weekStart(OCT_11_2026, Calendar.MONDAY))
    }

    @Test
    fun weeksSpanTheTurnOfTheYear() {
        // Thu Jan 1, 2026 is in the week of Mon Dec 29, 2025 (or Sun Dec 28).
        assertEquals(DEC_29_2025, weekStart(JAN_1_2026, Calendar.MONDAY))
        assertEquals(DEC_29_2025 - 1, weekStart(JAN_1_2026, Calendar.SUNDAY))
    }

    @Test
    fun monthStartsOnTheFirst() {
        assertEquals(OCT_1_2026, monthStart(OCT_15_2026))
        assertEquals(OCT_1_2026, monthStart(OCT_1_2026))
        assertEquals(FEB_29_2024 - 28, monthStart(FEB_29_2024)) // leap day
        assertEquals(FEB_29_2024 + 1, monthStart(FEB_29_2024 + 1)) // Mar 1, 2024
        assertEquals(29, dayOfMonth(FEB_29_2024))
        assertEquals(1, dayOfMonth(JAN_1_2026))
    }

    @Test
    fun bucketsStepByDayWeekAndMonth() {
        assertEquals(OCT_15_2026 + 1, nextBucket(OCT_15_2026, Granularity.Day))
        assertEquals(OCT_15_2026 - 1, previousBucket(OCT_15_2026, Granularity.Day))
        assertEquals(OCT_11_2026 + 7, nextBucket(OCT_11_2026, Granularity.Week))
        assertEquals(OCT_11_2026 - 7, previousBucket(OCT_11_2026, Granularity.Week))
        assertEquals(OCT_1_2026 + 31, nextBucket(OCT_1_2026, Granularity.Month)) // Nov 1
        assertEquals(OCT_1_2026 - 30, previousBucket(OCT_1_2026, Granularity.Month)) // Sep 1
        // February, December -> January.
        assertEquals(FEB_29_2024 + 1, nextBucket(FEB_29_2024 - 28, Granularity.Month))
        assertEquals(JAN_1_2026, nextBucket(JAN_1_2026 - 31, Granularity.Month))
        assertEquals(JAN_1_2026 - 31, previousBucket(JAN_1_2026, Granularity.Month))
    }

    @Test
    fun bucketStartPicksTheGranularity() {
        assertEquals(OCT_15_2026, bucketStart(OCT_15_2026, Granularity.Day, Calendar.SUNDAY))
        assertEquals(OCT_11_2026, bucketStart(OCT_15_2026, Granularity.Week, Calendar.SUNDAY))
        assertEquals(OCT_1_2026, bucketStart(OCT_15_2026, Granularity.Month, Calendar.SUNDAY))
    }

    @Test
    fun windowIsTheLastBucketsEndingWithTheCurrentOne() {
        val days = windowBuckets(OCT_15_2026, Granularity.Day, Calendar.SUNDAY, firstDay = 0)
        assertEquals(30, days.size)
        assertEquals(OCT_15_2026 - 29, days.first())
        assertEquals(OCT_15_2026, days.last())

        val weeks = windowBuckets(OCT_15_2026, Granularity.Week, Calendar.MONDAY, firstDay = 0)
        assertEquals(26, weeks.size)
        assertEquals(OCT_11_2026 + 1, weeks.last())
        assertEquals((1 until 26).map { 7L }, weeks.zipWithNext { a, b -> b - a })

        val months = windowBuckets(OCT_15_2026, Granularity.Month, Calendar.SUNDAY, firstDay = 0)
        assertEquals(24, months.size)
        assertEquals(OCT_1_2026, months.last())
        assertEquals(20_028L, months.first()) // Nov 1, 2024
        assertEquals(months.map { monthStart(it) }, months)
    }

    @Test
    fun windowStartsNoEarlierThanTheFirstEntry() {
        assertEquals(
            listOf(OCT_15_2026 - 2, OCT_15_2026 - 1, OCT_15_2026),
            windowBuckets(OCT_15_2026, Granularity.Day, Calendar.SUNDAY, firstDay = OCT_15_2026 - 2),
        )
        // A first entry this week: just this week.
        assertEquals(listOf(OCT_11_2026), windowBuckets(OCT_15_2026, Granularity.Week, Calendar.SUNDAY, firstDay = OCT_11_2026 + 1))
        // A first entry "in the future" (the clock was ahead) still shows the current bucket.
        assertEquals(listOf(OCT_15_2026), windowBuckets(OCT_15_2026, Granularity.Day, Calendar.SUNDAY, firstDay = OCT_15_2026 + 9))
        // No entries: the full window.
        assertEquals(30, windowBuckets(OCT_15_2026, Granularity.Day, Calendar.SUNDAY, firstDay = null).size)
    }

    @Test
    fun springForwardDayIsOneDayAndInTheRightWeek() {
        // Sun Mar 8, 2026: Los Angeles skips 02:00-03:00, so the day is 23 hours long.
        val la = TimeZone.getTimeZone("America/Los_Angeles")
        assertEquals(MAR_8_2026 - 1, localDay(at(la, 2026, Calendar.MARCH, 7, 23, 30), la))
        assertEquals(MAR_8_2026, localDay(at(la, 2026, Calendar.MARCH, 8, 0, 30), la))
        assertEquals(MAR_8_2026, localDay(at(la, 2026, Calendar.MARCH, 8, 3, 30), la))
        assertEquals(MAR_8_2026, localDay(at(la, 2026, Calendar.MARCH, 8, 23, 30), la))
        assertEquals(MAR_8_2026 + 1, localDay(at(la, 2026, Calendar.MARCH, 9, 0, 30), la))
        // Sunday-first weeks start that day; Monday-first ones end that day.
        assertEquals(MAR_8_2026, weekStart(localDay(at(la, 2026, Calendar.MARCH, 8, 23, 30), la), Calendar.SUNDAY))
        assertEquals(MAR_8_2026 - 6, weekStart(localDay(at(la, 2026, Calendar.MARCH, 8, 23, 30), la), Calendar.MONDAY))
    }

    @Test
    fun fallBackDayIsOneDayEvenWithTheRepeatedHour() {
        // Sun Nov 1, 2026: Los Angeles repeats 01:00-02:00, so the day is 25 hours long.
        val la = TimeZone.getTimeZone("America/Los_Angeles")
        val firstOneThirty = at(la, 2026, Calendar.NOVEMBER, 1, 1, 30)
        val secondOneThirty = firstOneThirty + HOUR
        assertEquals(NOV_1_2026, localDay(at(la, 2026, Calendar.NOVEMBER, 1, 0, 0), la))
        assertEquals(NOV_1_2026, localDay(firstOneThirty, la))
        assertEquals(NOV_1_2026, localDay(secondOneThirty, la))
        assertEquals(NOV_1_2026, localDay(at(la, 2026, Calendar.NOVEMBER, 1, 23, 59), la))
        assertEquals(NOV_1_2026 + 1, localDay(at(la, 2026, Calendar.NOVEMBER, 2, 0, 0), la))
        assertEquals(NOV_1_2026, monthStart(NOV_1_2026))
    }

    @Test
    fun middayMillisFallsOnThatDayInTheZone() {
        val zones = listOf("UTC", "America/Los_Angeles", "Europe/London", "Pacific/Kiritimati", "Pacific/Pago_Pago", "Asia/Kolkata")
        for (id in zones) {
            val zone = TimeZone.getTimeZone(id)
            for (day in listOf(MAR_8_2026 - 1, MAR_8_2026, MAR_8_2026 + 1, NOV_1_2026, OCT_15_2026, FEB_29_2024)) {
                assertEquals("$id day $day", day, localDay(middayMillis(day, zone), zone))
            }
        }
    }

    private fun at(zone: TimeZone, year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        GregorianCalendar(zone).apply {
            clear()
            set(year, month, day, hour, minute)
        }.timeInMillis

    private companion object {
        val UTC: TimeZone = TimeZone.getTimeZone("UTC")
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
        const val OCT_15_2026 = 20_741L // a Thursday
        const val OCT_11_2026 = 20_737L // a Sunday
        const val OCT_1_2026 = 20_727L
        const val JAN_1_2026 = 20_454L
        const val DEC_29_2025 = 20_451L // a Monday
        const val FEB_29_2024 = 19_782L
        const val MAR_8_2026 = 20_520L
        const val NOV_1_2026 = 20_758L
    }
}
