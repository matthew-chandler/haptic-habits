package com.machandler.haptichabits

import android.text.format.DateUtils
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

// Date math for Trends. Every date here is a "day": a calendar day in the phone's time zone,
// counted from 1970-01-01 (see localDay in HistoryScreen.kt). Working in whole days makes the
// buckets immune to DST (a 23- or 25-hour day is still one day) and needs no java.time, which
// minSdk 24 doesn't have without desugaring. Timestamps become days once, through the zone's
// offset at that instant; after that it's plain arithmetic, plus a UTC Calendar for months.

/** How Trends groups days: each point on a chart is one day, week or month. */
enum class Granularity(
    val label: String,
    /** Singular noun for captions, e.g. "presses per week". */
    val noun: String,
    /** How many buckets the charts show at most, ending with the current one. */
    val windowSize: Int,
) {
    Day("Day", "day", 30),
    Week("Week", "week", 26),
    Month("Month", "month", 24),
}

/** The day of the week of [day], as a `Calendar` constant (`SUNDAY` = 1 .. `SATURDAY` = 7). */
fun dayOfWeek(day: Long): Int =
    // 1970-01-01, day 0, was a Thursday.
    Math.floorMod(day + 4, 7L).toInt() + 1

/** The first day of the week containing [day], for weeks that start on [firstDayOfWeek]. */
fun weekStart(day: Long, firstDayOfWeek: Int): Long =
    day - Math.floorMod(dayOfWeek(day) - firstDayOfWeek, 7)

/** The first day of the month containing [day]. */
fun monthStart(day: Long): Long = day - (dayOfMonth(day) - 1)

/** The day of the month (1-31) of [day]. */
fun dayOfMonth(day: Long): Int =
    GregorianCalendar(UTC).apply { timeInMillis = day * DateUtils.DAY_IN_MILLIS }.get(Calendar.DAY_OF_MONTH)

/** The first day of the day, week or month containing [day]. Buckets are keyed by it. */
fun bucketStart(day: Long, granularity: Granularity, firstDayOfWeek: Int): Long = when (granularity) {
    Granularity.Day -> day
    Granularity.Week -> weekStart(day, firstDayOfWeek)
    Granularity.Month -> monthStart(day)
}

/** The bucket after the one starting on [start]. */
fun nextBucket(start: Long, granularity: Granularity): Long = when (granularity) {
    Granularity.Day -> start + 1
    Granularity.Week -> start + 7
    // No month is longer than 31 days, so this lands in the next month.
    Granularity.Month -> monthStart(start + 31)
}

/** The bucket before the one starting on [start]. */
fun previousBucket(start: Long, granularity: Granularity): Long = when (granularity) {
    Granularity.Day -> start - 1
    Granularity.Week -> start - 7
    Granularity.Month -> monthStart(start - 1)
}

/**
 * The buckets the charts show, oldest first: the last [Granularity.windowSize] buckets up to and
 * including the one containing [today], but none before the bucket of [firstDay] (the earliest
 * entry), so a new log doesn't start with a long empty stretch. [firstDay] null (no entries)
 * gives the full window.
 */
fun windowBuckets(today: Long, granularity: Granularity, firstDayOfWeek: Int, firstDay: Long?): List<Long> {
    val end = bucketStart(today, granularity, firstDayOfWeek)
    var start = end
    repeat(granularity.windowSize - 1) { start = previousBucket(start, granularity) }
    if (firstDay != null) {
        // A first entry "in the future" (the clock was ahead) still leaves the current bucket.
        start = maxOf(start, minOf(bucketStart(firstDay, granularity, firstDayOfWeek), end))
    }
    return generateSequence(start) { nextBucket(it, granularity) }.takeWhile { it <= end }.toList()
}

/**
 * A moment on [day] in [zone] (around midday), for formatting the day with `DateUtils`, which
 * formats in the phone's zone. Midday keeps it inside the day even when the zone's offset changes
 * around it.
 */
fun middayMillis(day: Long, zone: TimeZone = TimeZone.getDefault()): Long {
    val utcMidday = day * DateUtils.DAY_IN_MILLIS + DateUtils.DAY_IN_MILLIS / 2
    return utcMidday - zone.getOffset(utcMidday)
}

private val UTC: TimeZone = TimeZone.getTimeZone("UTC")
