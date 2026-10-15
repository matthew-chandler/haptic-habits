package com.machandler.haptichabits

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.machandler.haptichabits.data.EntryType
import com.machandler.haptichabits.data.TrendButton
import com.machandler.haptichabits.data.TrendEntry
import com.machandler.haptichabits.ui.theme.HapticHabitsTheme
import com.machandler.haptichabits.ui.theme.LocalValuePalette
import com.machandler.haptichabits.ui.theme.intensityColor
import com.machandler.haptichabits.ui.theme.valueColors
import java.util.Calendar
import java.util.TimeZone

/**
 * Settings -> Trends, one scrolling page:
 * - a Day / Week / Month switch for the two line charts;
 * - the total over time (at the end of each bucket; resets drop it to 0 and are marked);
 * - a button picker (active buttons, then deleted ones that have presses), that button's presses
 *   per bucket, and its calendar of presses per day over the last year.
 *
 * [state] is null while loading. The press chart and calendar are in the button's value color
 * (green/red, swapped by [invertColors]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(
    state: TrendsState?,
    invertColors: Boolean,
    onGranularityChange: (Granularity) -> Unit,
    onSelectButton: (id: Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Trends") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            state == null -> Unit
            !state.hasEntries -> Text(
                text = "No entries yet. Press a button on the home screen and your trends show up here.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp),
            )
            else -> Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            ) {
                TrendsContent(state, invertColors, onGranularityChange, onSelectButton)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrendsContent(
    state: TrendsState,
    invertColors: Boolean,
    onGranularityChange: (Granularity) -> Unit,
    onSelectButton: (id: Long) -> Unit,
) {
    val context = LocalContext.current
    val granularity = state.granularity
    val noun = granularity.noun

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Granularity.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == granularity,
                onClick = { onGranularityChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = Granularity.entries.size),
            ) { Text(option.label) }
        }
    }

    SectionTitle("Total over time")
    val resets = state.totals.markers.size
    Caption(
        "The total at the end of each $noun. A reset brings it back to 0" +
            if (resets > 0) "; dashed lines mark the ${noun}s with one." else ".",
    )
    LineChart(
        series = state.totals,
        color = MaterialTheme.colorScheme.onSurface,
        startLabel = bucketLabel(context, state.totals.buckets.first(), granularity),
        endLabel = bucketLabel(context, state.totals.buckets.last(), granularity),
        description = chartSummary(
            "Total at the end of each $noun",
            state.totals,
            label = { bucketLabel(context, it, granularity, long = true) },
            extra = if (resets > 0) "${pluralize(resets, noun)} with a reset." else null,
        ),
    )

    SectionTitle("Per button")
    val selected = state.selected
    if (selected == null || state.presses == null || state.calendar == null) {
        Caption("No buttons to show. Add one in Settings, then Buttons.")
        return
    }
    ButtonPicker(buttons = state.buttons, selected = selected, invertColors = invertColors, onSelect = onSelectButton)
    val hue = valueColors(selected.weight.toLong(), invertColors)
    val lineColor = hue?.color ?: MaterialTheme.colorScheme.primary

    Caption("Presses of “${selected.name}” per $noun. Resets don't count.")
    LineChart(
        series = state.presses,
        color = lineColor,
        startLabel = bucketLabel(context, state.presses.buckets.first(), granularity),
        endLabel = bucketLabel(context, state.presses.buckets.last(), granularity),
        description = chartSummary(
            "Presses of ${selected.name} per $noun",
            state.presses,
            label = { bucketLabel(context, it, granularity, long = true) },
        ),
    )

    val calendar = state.calendar
    Caption("Presses per day over the last year.")
    val palette = LocalValuePalette.current
    val levelColors = remember(hue, palette) {
        (0..4).map { level -> hue?.let { intensityColor(it, palette.empty, level) } ?: palette.empty }
    }
    // The tapped day, cleared when the button changes; saveable so it survives rotation.
    var selectedDay by rememberSaveable(selected.id) { mutableStateOf<Long?>(null) }
    CalendarView(
        calendar = calendar,
        colors = levelColors,
        monthName = { formatDay(context, it, DateUtils.FORMAT_NO_MONTH_DAY or DateUtils.FORMAT_NO_YEAR) },
        selectedDay = selectedDay,
        onSelectDay = { selectedDay = it },
        description = calendarSummary(selected.name, calendar) { formatDay(context, it, DateUtils.FORMAT_SHOW_YEAR) },
        modifier = Modifier.padding(top = 8.dp),
    )
    CalendarLegend(colors = levelColors, modifier = Modifier.padding(top = 8.dp))
    Text(
        text = selectedDay?.let { day ->
            val date = formatDay(context, day, DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY or DateUtils.FORMAT_SHOW_YEAR)
            "$date: ${pluralize(calendar.count(day), "press", "presses")}"
        } ?: "Tap a day to see how many presses it had.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = 24.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

/**
 * The button whose presses are shown, and a menu of the others: active buttons first, then
 * deleted ones that have presses, marked "(deleted)".
 */
@Composable
private fun ButtonPicker(
    buttons: List<TrendButton>,
    selected: TrendButton,
    invertColors: Boolean,
    onSelect: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(bottom = 8.dp)) {
        OutlinedButton(onClick = { expanded = true }) {
            Text(
                text = buttonLabel(selected),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = ButtonRules.formatValue(selected.weight),
                color = valueColors(selected.weight.toLong(), invertColors)?.color ?: Color.Unspecified,
            )
            Icon(painter = painterResource(R.drawable.ic_arrow_drop_down), contentDescription = "Choose a button")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (button in buttons) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(buttonLabel(button), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = "${ButtonRules.formatValue(button.weight)} · ${pluralize(button.presses, "press", "presses")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onSelect(button.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** The button's name, marked "(deleted)" if it was deleted. */
fun buttonLabel(button: TrendButton): String = if (button.archived) "${button.name} (deleted)" else button.name

/** "1 day", "3 days"; "1 press", "3 presses". */
fun pluralize(n: Int, singular: String, plural: String = "${singular}s"): String = "$n ${if (n == 1) singular else plural}"

/**
 * A spoken summary of the calendar: how many presses on how many days, and the most in a day.
 * [label] names a day.
 */
fun calendarSummary(name: String, calendar: ContributionCalendar, label: (Long) -> String): String {
    val range = "${label(calendar.firstDay)} to ${label(calendar.today)}"
    val what = "Calendar of $name presses per day, $range"
    return if (calendar.totalPresses == 0) {
        "$what: no presses."
    } else {
        "$what: ${pluralize(calendar.totalPresses, "press", "presses")} on ${pluralize(calendar.activeDays, "day")}, " +
            "at most ${calendar.maxCount} in a day."
    }
}

/**
 * Names the bucket starting on [day]: "Oct 15" for a day or a week (its first day), "Oct 2026"
 * for a month. [long] (for spoken summaries) says "week of" for weeks. The year shows when it
 * isn't the current one.
 */
private fun bucketLabel(context: Context, day: Long, granularity: Granularity, long: Boolean = false): String =
    when (granularity) {
        Granularity.Day -> formatDay(context, day)
        Granularity.Week -> if (long) "week of ${formatDay(context, day)}" else formatDay(context, day)
        Granularity.Month -> formatDay(context, day, DateUtils.FORMAT_NO_MONTH_DAY or DateUtils.FORMAT_SHOW_YEAR)
    }

/** [day] as a short date in the phone's locale ("Oct 15"), plus any extra `DateUtils` [flags]. */
private fun formatDay(context: Context, day: Long, flags: Int = 0): String =
    DateUtils.formatDateTime(
        context,
        middayMillis(day),
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or flags,
    )

@Preview(showBackground = true)
@Composable
fun TrendsScreenPreview() {
    val zone = TimeZone.getDefault()
    val now = System.currentTimeMillis()
    val hour = DateUtils.HOUR_IN_MILLIS
    val entries = (1L..400L).map { i ->
        if (i == 250L) {
            TrendEntry(i, now - (400 - i) * 9 * hour, 0, null, EntryType.RESET)
        } else {
            TrendEntry(i, now - (400 - i) * 9 * hour, if (i % 3 == 0L) -2 else 1, if (i % 3 == 0L) 2 else 1)
        }
    }
    val log = buildTrendLog(entries, zone)
    HapticHabitsTheme {
        TrendsScreen(
            state = trendsState(
                log = log,
                buttons = listOf(
                    TrendButton(1, "+1", 1, archived = false, presses = 266),
                    TrendButton(2, "Went for a walk", -2, archived = true, presses = 133),
                ),
                granularity = Granularity.Day,
                selectedId = null,
                today = localDay(now, zone),
                firstDayOfWeek = Calendar.getInstance().firstDayOfWeek,
            ),
            invertColors = false,
            onGranularityChange = {},
            onSelectButton = {},
            onBack = {},
        )
    }
}
