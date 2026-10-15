package com.machandler.haptichabits

import com.machandler.haptichabits.data.EntryType
import com.machandler.haptichabits.data.TrendEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/** The one-pass log condensation and the series cut from it. Days are in UTC unless noted. */
class TrendDataTest {
    private var nextId = 1L

    @Test
    fun emptyLogHasNothing() {
        val log = buildTrendLog(emptyList(), UTC)

        assertTrue(log.isEmpty)
        assertNull(log.firstDay)
        val series = totalSeries(log, listOf(D, D + 1), Granularity.Day, Calendar.SUNDAY)
        assertEquals(listOf(0L, 0L), series.values)
        assertEquals(emptySet<Int>(), series.markers)
    }

    @Test
    fun eachDayKeepsTheTotalAfterItsLastEntry() {
        val log = buildTrendLog(
            listOf(tap(D, 9, weight = 5), tap(D, 18, weight = -2), tap(D + 2, 8, weight = 4)),
            UTC,
        )

        assertEquals(D, log.firstDay)
        assertEquals(3L, log.dayTotals.getValue(D).total)
        assertEquals(7L, log.dayTotals.getValue(D + 2).total)
        assertEquals(
            listOf(3L, 3L, 7L, 7L),
            totalSeries(log, (D..D + 3).toList(), Granularity.Day, Calendar.SUNDAY).values,
        )
    }

    @Test
    fun resetsDropTheTotalToZeroAndAreMarked() {
        val log = buildTrendLog(
            listOf(
                tap(D, 9, weight = 5),
                reset(D + 1, 10),
                tap(D + 2, 9, weight = 2),
                tap(D + 2, 10, weight = 2),
                reset(D + 2, 11),
                tap(D + 2, 12, weight = 1),
            ),
            UTC,
        )

        val series = totalSeries(log, (D..D + 3).toList(), Granularity.Day, Calendar.SUNDAY)
        // Day 3 ends at 1: its reset cleared 4, then one more press.
        assertEquals(listOf(5L, 0L, 1L, 1L), series.values)
        assertEquals(setOf(1, 2), series.markers)
        assertEquals(setOf(D + 1, D + 2), log.resetDays)
    }

    @Test
    fun aResetAsTheLastEntryEndsTheDayAtZero() {
        val log = buildTrendLog(listOf(tap(D, 9, weight = 5), reset(D, 10)), UTC)

        assertEquals(listOf(0L), totalSeries(log, listOf(D), Granularity.Day, Calendar.SUNDAY).values)
    }

    @Test
    fun theWindowCarriesOverTheTotalFromBeforeIt() {
        val log = buildTrendLog(listOf(tap(D, 9, weight = 5), tap(D + 1, 9, weight = 3), tap(D + 5, 9, weight = 1)), UTC)

        assertEquals(
            listOf(8L, 8L, 9L),
            totalSeries(log, listOf(D + 3, D + 4, D + 5), Granularity.Day, Calendar.SUNDAY).values,
        )
    }

    @Test
    fun theSumFollowsIdOrderWhenTheClockJumps() {
        // Pressed on Wed and Fri, then the clock was set back to Wed for a third press.
        val log = buildTrendLog(
            listOf(tap(OCT_14, 9, weight = 5), tap(OCT_14 + 2, 9, weight = 3), tap(OCT_14, 20, weight = 1)),
            UTC,
        )

        // Wed ends with the newest entry dated then (id 3, total 9); Fri's newest is id 2 (8).
        assertEquals(DayTotal(3, 9), log.dayTotals[OCT_14])
        assertEquals(DayTotal(2, 8), log.dayTotals[OCT_14 + 2])
        assertEquals(
            listOf(9L, 9L, 8L),
            totalSeries(log, (OCT_14..OCT_14 + 2).toList(), Granularity.Day, Calendar.SUNDAY).values,
        )
        // The week holding all three ends with the newest by id, which is the real total.
        assertEquals(listOf(9L), totalSeries(log, listOf(OCT_14 - 3), Granularity.Week, Calendar.SUNDAY).values)
    }

    @Test
    fun weeksAndMonthsEndWithTheirNewestEntry() {
        // Wed Oct 14 and Sat Oct 17, 2026 (one Sunday-first week), then Mon Oct 19.
        val log = buildTrendLog(
            listOf(tap(OCT_14, 9, weight = 2), tap(OCT_14 + 3, 9, weight = 2), tap(OCT_14 + 5, 9, weight = 10)),
            UTC,
        )

        val sundayWeeks = listOf(OCT_14 - 3, OCT_14 + 4)
        assertEquals(listOf(4L, 14L), totalSeries(log, sundayWeeks, Granularity.Week, Calendar.SUNDAY).values)
        // Monday-first: Oct 12-18 and Oct 19-25, same split.
        assertEquals(listOf(4L, 14L), totalSeries(log, listOf(OCT_14 - 2, OCT_14 + 5), Granularity.Week, Calendar.MONDAY).values)
        // Saturday-first: Oct 10-16 holds only the first press.
        assertEquals(listOf(2L, 14L), totalSeries(log, listOf(OCT_14 - 4, OCT_14 + 3), Granularity.Week, Calendar.SATURDAY).values)
        assertEquals(listOf(14L), totalSeries(log, listOf(monthStart(OCT_14)), Granularity.Month, Calendar.SUNDAY).values)
    }

    @Test
    fun weeklyResetMarkersCoverTheWholeWeek() {
        val log = buildTrendLog(listOf(tap(OCT_14, 9, weight = 2), reset(OCT_14 + 1, 9)), UTC)

        val series = totalSeries(log, listOf(OCT_14 - 10, OCT_14 - 3), Granularity.Week, Calendar.SUNDAY)
        assertEquals(setOf(1), series.markers)
        assertEquals(listOf(0L, 0L), series.values)
    }

    @Test
    fun pressesCountTapsPerButtonPerDayButNeverResets() {
        val log = buildTrendLog(
            listOf(
                tap(D, 9, weight = 5, button = 1),
                tap(D, 10, weight = 5, button = 1),
                tap(D, 11, weight = -2, button = 2),
                reset(D, 12),
                tap(D + 1, 9, weight = 5, button = 1),
            ),
            UTC,
        )

        assertEquals(mapOf(D to 2, D + 1 to 1), log.dailyPresses[1])
        assertEquals(mapOf(D to 1), log.dailyPresses[2])
        assertEquals(setOf(1L, 2L), log.dailyPresses.keys)
    }

    @Test
    fun pressSeriesSumsDaysIntoBucketsInTheWindow() {
        val daily = mapOf(OCT_14 to 2, OCT_14 + 3 to 4, OCT_14 + 5 to 1, OCT_14 - 100 to 9)

        assertEquals(
            listOf(0L, 2L, 0L, 0L, 4L, 0L, 1L),
            pressSeries(daily, (OCT_14 - 1..OCT_14 + 5).toList(), Granularity.Day, Calendar.SUNDAY).values,
        )
        assertEquals(
            listOf(6L, 1L),
            pressSeries(daily, listOf(OCT_14 - 3, OCT_14 + 4), Granularity.Week, Calendar.SUNDAY).values,
        )
        assertEquals(listOf(7L), pressSeries(daily, listOf(monthStart(OCT_14)), Granularity.Month, Calendar.SUNDAY).values)
        // Never pressed: all zero.
        assertEquals(listOf(0L, 0L), pressSeries(null, listOf(D, D + 1), Granularity.Day, Calendar.SUNDAY).values)
    }

    @Test
    fun pressesAreCountedInTheLocalDayAcrossDst() {
        val la = TimeZone.getTimeZone("America/Los_Angeles")
        fun at(day: Int, hour: Int, minute: Int) =
            GregorianCalendar(la).apply { clear(); set(2026, Calendar.MARCH, day, hour, minute) }.timeInMillis
        val log = buildTrendLog(
            listOf(
                TrendEntry(1, at(7, 23, 30), 1, 1),
                TrendEntry(2, at(8, 0, 30), 1, 1),
                TrendEntry(3, at(8, 23, 30), 1, 1), // the 23-hour day
                TrendEntry(4, at(9, 0, 30), 1, 1),
            ),
            la,
        )

        assertEquals(mapOf(MAR_8 - 1 to 1, MAR_8 to 2, MAR_8 + 1 to 1), log.dailyPresses[1])
        assertEquals(
            listOf(1L, 3L),
            pressSeries(log.dailyPresses[1], listOf(MAR_8 - 7, MAR_8), Granularity.Week, Calendar.SUNDAY).values,
        )
    }

    @Test
    fun manyEntriesEndOnTheSameTotalAsTheCanonicalRunningSum() {
        val entries = (1L..50_000L).map { i ->
            val timestamp = i * 7 * 60_000L // every 7 minutes, about 243 days
            if (i % 4_999 == 0L) {
                TrendEntry(i, timestamp, 0, null, EntryType.RESET)
            } else {
                TrendEntry(i, timestamp, if (i % 3 == 0L) -2 else 5, i % 4)
            }
        }
        val expected = entries.fold(0L) { sum, e -> if (e.isReset) 0 else sum + e.weight }

        val log = buildTrendLog(entries, UTC)
        val today = localDay(entries.last().timestamp, UTC)
        for (granularity in Granularity.entries) {
            val buckets = windowBuckets(today, granularity, Calendar.SUNDAY, log.firstDay)
            assertEquals(granularity.name, expected, totalSeries(log, buckets, granularity, Calendar.SUNDAY).values.last())
        }
        assertEquals(entries.count { !it.isReset }, log.dailyPresses.values.sumOf { it.values.sum() })
    }

    @Test
    fun axisTicksAreRoundAndIncludeZero() {
        assertEquals(listOf(0L, 1L), axisTicks(0, 0))
        assertEquals(listOf(0L, 1L), axisTicks(1, 1))
        assertEquals(listOf(0L, 2L, 4L, 6L, 8L), axisTicks(0, 7))
        assertEquals(listOf(-20L, 0L, 20L, 40L, 60L), axisTicks(-3, 42))
        assertEquals(listOf(-6L, -4L, -2L, 0L), axisTicks(-5, -1))
        assertEquals(listOf(0L, 5_000L, 10_000L, 15_000L), axisTicks(0, 12_345))
        assertEquals(listOf(0L, 1L, 2L, 3L), axisTicks(3, 3))
    }

    @Test
    fun axisTicksCoverTheDataWithFewLines() {
        for ((min, max) in listOf(0L to 1L, -1L to 1L, 0L to 99L, -250L to 1_000L, 7L to 7L, -999_999L to 3L)) {
            val ticks = axisTicks(min, max)
            assertTrue("$min..$max: $ticks", ticks.first() <= minOf(min, 0) && ticks.last() >= maxOf(max, 0))
            assertTrue("$min..$max: $ticks", 0L in ticks)
            assertTrue("$min..$max: $ticks", ticks.size in 2..7)
        }
    }

    @Test
    fun chartSummarySaysRangeLowHighAndLatest() {
        val label = { day: Long -> "day$day" }

        assertEquals("Total: no data.", chartSummary("Total", TrendSeries(emptyList(), emptyList()), label))
        assertEquals("Total, day5: 3.", chartSummary("Total", TrendSeries(listOf(5), listOf(3)), label))
        assertEquals(
            "Total, day1 to day3: lowest -2, highest 7, latest 0. 1 day with a reset.",
            chartSummary("Total", TrendSeries(listOf(1, 2, 3), listOf(7, -2, 0)), label, extra = "1 day with a reset."),
        )
    }

    private fun tap(day: Long, hour: Int, weight: Int, button: Long = 1) =
        TrendEntry(nextId++, day * DAY + hour * HOUR, weight, button)

    private fun reset(day: Long, hour: Int) =
        TrendEntry(nextId++, day * DAY + hour * HOUR, 0, null, EntryType.RESET)

    private companion object {
        val UTC: TimeZone = TimeZone.getTimeZone("UTC")
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
        const val D = 20_000L
        const val OCT_14 = 20_740L // Wed Oct 14, 2026
        const val MAR_8 = 20_520L // Sun Mar 8, 2026, the US spring-forward day
    }
}
