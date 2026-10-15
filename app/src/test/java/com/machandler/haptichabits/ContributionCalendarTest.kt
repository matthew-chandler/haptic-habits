package com.machandler.haptichabits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** The contribution calendar's grid, intensity levels and labels. */
class ContributionCalendarTest {
    @Test
    fun gridIs53WeeksEndingWithTheCurrentWeek() {
        val calendar = contributionCalendar(OCT_15_2026, Calendar.SUNDAY, dailyPresses = null)

        assertEquals(53, calendar.weeks)
        assertEquals(OCT_12_2025, calendar.firstDay) // a Sunday, 52 weeks before Sun Oct 11, 2026
        assertEquals(OCT_12_2025, calendar.dayAt(0, 0))
        assertEquals(OCT_12_2025 + 6, calendar.dayAt(0, 6))
        assertEquals(OCT_11_2026, calendar.dayAt(52, 0))
        assertEquals(OCT_15_2026, calendar.dayAt(52, 4)) // Thursday
    }

    @Test
    fun daysAfterTodayAndOutsideTheGridAreNotCells() {
        val calendar = contributionCalendar(OCT_15_2026, Calendar.SUNDAY, dailyPresses = null)

        assertNull(calendar.dayAt(52, 5)) // Friday, tomorrow
        assertNull(calendar.dayAt(52, 6))
        assertNull(calendar.dayAt(53, 0))
        assertNull(calendar.dayAt(-1, 0))
        assertNull(calendar.dayAt(0, 7))
        assertNull(calendar.dayAt(0, -1))
    }

    @Test
    fun rowsFollowTheLocalesFirstDayOfWeek() {
        val calendar = contributionCalendar(OCT_15_2026, Calendar.MONDAY, dailyPresses = null)

        assertEquals(OCT_12_2025 + 1, calendar.firstDay) // Mon Oct 13, 2025
        assertEquals(Calendar.MONDAY, dayOfWeek(calendar.dayAt(10, 0)!!))
        assertEquals(OCT_15_2026, calendar.dayAt(52, 3))
        assertNull(calendar.dayAt(52, 4))
        // The labeled rows name the 2nd, 4th and 6th weekdays.
        assertEquals(listOf(Calendar.MONDAY, Calendar.WEDNESDAY, Calendar.FRIDAY), LABELED_ROWS.map { rowWeekday(it, Calendar.SUNDAY) })
        assertEquals(listOf(Calendar.TUESDAY, Calendar.THURSDAY, Calendar.SATURDAY), LABELED_ROWS.map { rowWeekday(it, Calendar.MONDAY) })
        assertEquals(listOf(Calendar.SUNDAY, Calendar.TUESDAY, Calendar.THURSDAY), LABELED_ROWS.map { rowWeekday(it, Calendar.SATURDAY) })
    }

    @Test
    fun countsAreLimitedToTheDaysShown() {
        val daily = mapOf(
            OCT_15_2026 to 3,
            OCT_15_2026 - 1 to 1,
            OCT_12_2025 to 2, // the first day shown
            OCT_12_2025 - 1 to 50, // before the grid
            OCT_15_2026 + 1 to 50, // "tomorrow" (the clock was ahead)
        )

        val calendar = contributionCalendar(OCT_15_2026, Calendar.SUNDAY, daily)

        assertEquals(3, calendar.count(OCT_15_2026))
        assertEquals(0, calendar.count(OCT_15_2026 - 2))
        assertEquals(3, calendar.maxCount)
        assertEquals(6, calendar.totalPresses)
        assertEquals(3, calendar.activeDays)
    }

    @Test
    fun emptyCalendarHasNoPressesAndAllLevelsZero() {
        val calendar = contributionCalendar(OCT_15_2026, Calendar.SUNDAY, emptyMap())

        assertEquals(0, calendar.maxCount)
        assertEquals(0, calendar.totalPresses)
        assertEquals(0, calendar.level(OCT_15_2026))
    }

    @Test
    fun levelsAreQuartersOfTheBusiestDay() {
        assertEquals(0, intensityLevel(0, 8))
        assertEquals(1, intensityLevel(1, 8))
        assertEquals(1, intensityLevel(2, 8))
        assertEquals(2, intensityLevel(3, 8))
        assertEquals(2, intensityLevel(4, 8))
        assertEquals(3, intensityLevel(6, 8))
        assertEquals(4, intensityLevel(7, 8))
        assertEquals(4, intensityLevel(8, 8))
        // A single press is never invisible, however busy the busiest day.
        assertEquals(1, intensityLevel(1, 1_000))
        // When every day has the same count, those days are the busiest.
        assertEquals(4, intensityLevel(1, 1))
        assertEquals(0, intensityLevel(0, 0))
    }

    @Test
    fun monthNamesSitOverTheWeekWhereEachMonthStarts() {
        val labels = contributionCalendar(OCT_15_2026, Calendar.SUNDAY, null).monthLabels

        // Nov 1, 2025 (a Saturday) is in the 3rd column; too close to label the first column too.
        assertEquals(MonthLabel(2, NOV_1_2025), labels.first())
        assertEquals(MonthLabel(50, OCT_1_2026), labels.last())
        assertEquals(12, labels.size)
        assertEquals(labels.map { monthStart(it.monthStart) }, labels.map { it.monthStart })
        assertEquals(labels.sortedBy { it.column }, labels)
        labels.zipWithNext { a, b -> assertTrue("$a and $b too close", b.column - a.column >= 4) }
    }

    @Test
    fun theFirstColumnIsLabeledWhenTheNextMonthIsFarEnough() {
        // Tue Oct 6, 2026: the grid starts Sun Oct 5, 2025, and Nov 1 is in the 4th column.
        val labels = contributionCalendar(OCT_15_2026 - 9, Calendar.SUNDAY, null).monthLabels

        assertEquals(MonthLabel(0, OCT_1_2025), labels[0])
        assertEquals(MonthLabel(3, NOV_1_2025), labels[1])
    }

    @Test
    fun aMonthStartingAfterTodayIsNotLabeledYet() {
        // Sun Nov 29, 2026: Tue Dec 1 is in this week's column, but still ahead.
        val labels = contributionCalendar(NOV_1_2026 + 28, Calendar.SUNDAY, null).monthLabels

        assertEquals(MonthLabel(52, NOV_1_2026 + 30), contributionCalendar(NOV_1_2026 + 30, Calendar.SUNDAY, null).monthLabels.last())
        assertEquals(NOV_1_2026, labels.last().monthStart)
    }

    private companion object {
        const val OCT_15_2026 = 20_741L // a Thursday
        const val OCT_11_2026 = 20_737L // a Sunday
        const val OCT_1_2026 = 20_727L
        const val OCT_12_2025 = 20_373L // a Sunday
        const val OCT_1_2025 = 20_362L
        const val NOV_1_2025 = 20_393L
        const val NOV_1_2026 = 20_758L // a Sunday
    }
}
