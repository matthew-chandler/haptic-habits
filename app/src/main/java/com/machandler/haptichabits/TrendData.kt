package com.machandler.haptichabits

import com.machandler.haptichabits.data.TrendEntry
import java.util.TimeZone

/**
 * What Trends needs from the log, by local day. Built in one pass over the whole log, oldest first
 * by id (the canonical order), each time the log changes; every chart is then cut from it without
 * touching the database again.
 */
class TrendLog(
    /** The time zone the days are in. */
    val zone: TimeZone,
    /** For each day with entries: the total right after the newest (highest id) entry dated that day. */
    val dayTotals: Map<Long, DayTotal>,
    /** The days with at least one reset. */
    val resetDays: Set<Long>,
    /** Presses (resets excluded) per button id, then per day. */
    val dailyPresses: Map<Long, Map<Long, Int>>,
    /** The earliest day of any entry, by timestamp; null when the log is empty. */
    val firstDay: Long?,
) {
    val isEmpty: Boolean get() = firstDay == null
}

/** The total right after entry [lastId], the newest entry (by id) of its day. */
data class DayTotal(val lastId: Long, val total: Long)

/**
 * One pass over [oldestFirst] (in id order, as `TapEventDao.observeTrendEntries` returns it): the
 * running total, restarting at 0 at each reset exactly like History and the home total, recorded
 * per day, plus the presses per button per day. Each entry is put in the day of its timestamp in
 * [zone]; the order of the sum never depends on timestamps, so clock changes can't reorder it.
 */
fun buildTrendLog(oldestFirst: List<TrendEntry>, zone: TimeZone): TrendLog {
    val dayTotals = HashMap<Long, DayTotal>()
    val resetDays = HashSet<Long>()
    val dailyPresses = HashMap<Long, HashMap<Long, Int>>()
    var firstDay: Long? = null
    var sum = 0L
    for (entry in oldestFirst) {
        val day = localDay(entry.timestamp, zone)
        if (firstDay == null || day < firstDay) firstDay = day
        if (entry.isReset) {
            sum = 0
            resetDays += day
        } else {
            sum += entry.weight
            // Only presses count, and only presses have a button; a reset never counts as one.
            entry.buttonId?.let { button ->
                val days = dailyPresses.getOrPut(button) { HashMap() }
                days[day] = (days[day] ?: 0) + 1
            }
        }
        // Ids only grow, so the last write for a day is its newest entry.
        dayTotals[day] = DayTotal(entry.id, sum)
    }
    return TrendLog(zone, dayTotals, resetDays, dailyPresses, firstDay)
}

/**
 * One chart's data: a value for each bucket in [buckets] (bucket start days, oldest first), and
 * the indices of the buckets to mark (those with a reset).
 */
data class TrendSeries(
    val buckets: List<Long>,
    val values: List<Long>,
    val markers: Set<Int> = emptySet(),
)

/**
 * The total at the end of each of [buckets]: the total right after the newest entry (by id) dated
 * in that bucket. A bucket without entries carries the total over from the bucket before it (the
 * first carries over from the latest bucket before the window, or 0). One point per bucket keeps
 * the chart readable however many entries there are, and a clock jump only moves entries between
 * buckets, never reorders the sum. Buckets that contain a reset are marked.
 */
fun totalSeries(log: TrendLog, buckets: List<Long>, granularity: Granularity, firstDayOfWeek: Int): TrendSeries {
    if (buckets.isEmpty()) return TrendSeries(emptyList(), emptyList())
    val first = buckets.first()
    val byBucket = HashMap<Long, DayTotal>()
    var carried: Pair<Long, DayTotal>? = null // the latest bucket before the window, and its total
    for ((day, dayTotal) in log.dayTotals) {
        val bucket = bucketStart(day, granularity, firstDayOfWeek)
        val newest = byBucket[bucket]
        if (newest == null || dayTotal.lastId > newest.lastId) byBucket[bucket] = dayTotal
    }
    for ((bucket, dayTotal) in byBucket) {
        if (bucket < first && (carried == null || bucket > carried.first)) carried = bucket to dayTotal
    }
    var total = carried?.second?.total ?: 0L
    val values = buckets.map { bucket ->
        byBucket[bucket]?.let { total = it.total }
        total
    }
    val resetBuckets = log.resetDays.mapTo(HashSet()) { bucketStart(it, granularity, firstDayOfWeek) }
    val markers = buckets.indices.filterTo(HashSet()) { buckets[it] in resetBuckets }
    return TrendSeries(buckets, values, markers)
}

/** How many times a button was pressed in each of [buckets], from its [dailyPresses] (null: never). */
fun pressSeries(
    dailyPresses: Map<Long, Int>?,
    buckets: List<Long>,
    granularity: Granularity,
    firstDayOfWeek: Int,
): TrendSeries {
    val index = buckets.withIndex().associate { (i, bucket) -> bucket to i }
    val counts = LongArray(buckets.size)
    dailyPresses?.forEach { (day, presses) ->
        index[bucketStart(day, granularity, firstDayOfWeek)]?.let { counts[it] += presses.toLong() }
    }
    return TrendSeries(buckets, counts.toList())
}

/**
 * The y-axis gridlines for values from [min] to [max]: round numbers (1, 2 or 5 times a power of
 * ten apart, never less than 1), about [maxIntervals] intervals (rounding the ends outwards can
 * add up to two), always including 0 so the
 * chart's baseline is honest. The first and last ticks are the axis bounds. All-zero (or no)
 * data gives 0 and 1.
 */
fun axisTicks(min: Long, max: Long, maxIntervals: Int = 4): List<Long> {
    val low = minOf(min, 0L)
    val high = maxOf(max, 0L).let { if (it == low) low + 1 else it }
    val step = niceStep((high - low).toDouble() / maxIntervals)
    val start = Math.floorDiv(low, step) * step
    val end = -Math.floorDiv(-high, step) * step
    return generateSequence(start) { it + step }.takeWhile { it <= end }.toList()
}

/** The smallest of 1, 2, 5, 10, 20, 50, ... that is at least [atLeast]. */
private fun niceStep(atLeast: Double): Long {
    var magnitude = 1L
    while (true) {
        for (multiple in longArrayOf(1, 2, 5)) {
            if (multiple * magnitude >= atLeast) return multiple * magnitude
        }
        magnitude *= 10
    }
}

/**
 * A spoken summary of a chart, for its contentDescription: what it shows, the range of dates, the
 * lowest, highest and latest values, and [extra] (e.g. how many resets it includes).
 * [label] names a bucket by its start day.
 */
fun chartSummary(what: String, series: TrendSeries, label: (Long) -> String, extra: String? = null): String {
    val values = series.values
    val text = when (values.size) {
        0 -> "$what: no data."
        1 -> "$what, ${label(series.buckets.single())}: ${values.single()}."
        else -> "$what, ${label(series.buckets.first())} to ${label(series.buckets.last())}: " +
            "lowest ${values.min()}, highest ${values.max()}, latest ${values.last()}."
    }
    return if (extra != null) "$text $extra" else text
}
