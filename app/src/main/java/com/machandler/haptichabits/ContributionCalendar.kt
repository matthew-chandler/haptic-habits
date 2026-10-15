package com.machandler.haptichabits

/**
 * A GitHub-style calendar of one button's presses: a column per week (oldest at the left, the
 * current week at the right) and a row per day of the week, starting with the locale's first day
 * of the week. Days after [today] in the last column are left out.
 */
class ContributionCalendar(
    /** The first day of the first (leftmost) column. */
    val firstDay: Long,
    val today: Long,
    val weeks: Int,
    val firstDayOfWeek: Int,
    /** Presses per day, only for days shown. */
    val counts: Map<Long, Int>,
) {
    /** The most presses on one shown day; 0 when there are none. */
    val maxCount: Int = counts.values.maxOrNull() ?: 0

    /** All presses shown. */
    val totalPresses: Int = counts.values.sum()

    /** Days shown with at least one press. */
    val activeDays: Int = counts.count { it.value > 0 }

    /** The day in [column] (0 = oldest week) and [row] (0 = first day of the week), or null after today. */
    fun dayAt(column: Int, row: Int): Long? {
        if (column !in 0 until weeks || row !in 0 until 7) return null
        return (firstDay + 7L * column + row).takeIf { it <= today }
    }

    fun count(day: Long): Int = counts[day] ?: 0

    /** 0 (no presses) to 4 (the busiest days), for the cell's color. */
    fun level(day: Long): Int = intensityLevel(count(day), maxCount)

    /** Which columns get a month name above them. */
    val monthLabels: List<MonthLabel> = monthLabels(firstDay, weeks, today)
}

/** A month name above [column], naming the month that starts on [monthStart]. */
data class MonthLabel(val column: Int, val monthStart: Long)

/**
 * The calendar for [dailyPresses] (presses per day, null for none): [weeks] columns, roughly a
 * year with the default 53, ending with the week that contains [today].
 */
fun contributionCalendar(
    today: Long,
    firstDayOfWeek: Int,
    dailyPresses: Map<Long, Int>?,
    weeks: Int = CALENDAR_WEEKS,
): ContributionCalendar {
    val firstDay = weekStart(today, firstDayOfWeek) - 7L * (weeks - 1)
    val shown = dailyPresses.orEmpty().filter { (day, count) -> day in firstDay..today && count > 0 }
    return ContributionCalendar(firstDay, today, weeks, firstDayOfWeek, shown)
}

/** 53 weeks: a full year whatever the day of the week, like GitHub's. */
const val CALENDAR_WEEKS = 53

/**
 * How dark a day's cell is: 0 for no presses, otherwise 1-4 by quarters of the busiest day
 * ([max]), so the busiest days are always level 4 and a single press is never invisible.
 */
fun intensityLevel(count: Int, max: Int): Int = when {
    count <= 0 || max <= 0 -> 0
    else -> ((4L * count + max - 1) / max).toInt().coerceIn(1, 4)
}

/**
 * Month names above the calendar: over each column whose week contains the 1st of a month (up to
 * [today]). The first column also gets its own month when the next name is at least 3 columns
 * away, so the oldest weeks are labeled without two names overlapping.
 */
fun monthLabels(firstDay: Long, weeks: Int, today: Long): List<MonthLabel> {
    val labels = mutableListOf<MonthLabel>()
    for (column in 0 until weeks) {
        val start = firstDay + 7L * column
        // The month of the column's last shown day began inside the column.
        val first = monthStart(minOf(start + 6, today))
        if (first >= start) labels += MonthLabel(column, first)
    }
    if ((labels.firstOrNull()?.column ?: weeks) >= 3) {
        labels.add(0, MonthLabel(0, monthStart(firstDay)))
    }
    return labels
}

/** The weekday (a `Calendar` constant) shown in [row] of the calendar, for weeks starting on [firstDayOfWeek]. */
fun rowWeekday(row: Int, firstDayOfWeek: Int): Int = (firstDayOfWeek - 1 + row) % 7 + 1

/** The rows that get a weekday name, like GitHub's: the 2nd, 4th and 6th. */
val LABELED_ROWS = listOf(1, 3, 5)
