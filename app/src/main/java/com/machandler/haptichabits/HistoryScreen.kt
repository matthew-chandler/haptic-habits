package com.machandler.haptichabits

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.machandler.haptichabits.data.HistoryEntry
import com.machandler.haptichabits.ui.theme.HapticHabitsTheme
import com.machandler.haptichabits.ui.theme.valueColors
import java.util.TimeZone
import kotlin.math.abs

/**
 * Settings -> History: every entry, newest first, under a header for each day. A row shows the
 * time, the button's name, the entry's value (colored like the buttons) and the total right
 * after it. The trash icon deletes the entry, after a confirmation.
 *
 * [rows] is null while loading.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    rows: List<HistoryRow>?,
    invertColors: Boolean,
    onDelete: (id: Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The entry whose delete confirmation is open; saveable so rotating the phone keeps it open.
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    // For the "Today" / "Yesterday" headers; fixed while the screen is open.
    val today = remember { localDay(System.currentTimeMillis()) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            rows == null -> Unit
            rows.isEmpty() -> Text(
                text = "No entries yet. Every press on the home screen shows up here.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            ) {
                itemsIndexed(rows, key = { _, row -> row.entry.id }) { index, row ->
                    // A row starts a new day when the one above it (newer) is from another day.
                    // Compared row by row, so a clock change only adds a header, never misplaces one.
                    val day = localDay(row.entry.timestamp)
                    val startsDay = index == 0 || localDay(rows[index - 1].entry.timestamp) != day
                    Column {
                        if (startsDay) {
                            if (index > 0) HorizontalDivider()
                            DayHeader(day = day, today = today, timestamp = row.entry.timestamp)
                        }
                        HistoryRowView(row = row, invertColors = invertColors, onDelete = { deletingId = row.entry.id })
                    }
                }
            }
        }
    }

    deletingId?.let { id ->
        // Gone (e.g. undone meanwhile) means there is nothing left to confirm.
        rows?.find { it.entry.id == id }?.let { row ->
            val entry = row.entry
            val context = LocalContext.current
            AlertDialog(
                onDismissRequest = { deletingId = null },
                title = { Text("Delete entry?") },
                text = {
                    val direction = if (entry.weight > 0) "down" else "up"
                    Text(
                        "“${displayName(entry)}” (${ButtonRules.formatValue(entry.weight)}) from " +
                            "${formatDateAndTime(context, entry.timestamp)} will be deleted. " +
                            "The total goes $direction by ${abs(entry.weight)}.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        onDelete(entry.id)
                        deletingId = null
                    }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { deletingId = null }) { Text("Cancel") }
                },
            )
        }
    }
}

/** "Today", "Yesterday", or the date, e.g. "Wed, Oct 14, 2026" (in the phone's locale). */
@Composable
private fun DayHeader(day: Long, today: Long, timestamp: Long) {
    val context = LocalContext.current
    val text = when (day) {
        today -> "Today"
        today - 1 -> "Yesterday"
        else -> DateUtils.formatDateTime(
            context,
            timestamp,
            DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or
                DateUtils.FORMAT_ABBREV_WEEKDAY or DateUtils.FORMAT_ABBREV_MONTH,
        )
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

/** One entry: name over time on the left; value over the total after it, then trash, on the right. */
@Composable
private fun HistoryRowView(row: HistoryRow, invertColors: Boolean, onDelete: () -> Unit) {
    val entry = row.entry
    val context = LocalContext.current
    val time = DateUtils.formatDateTime(context, entry.timestamp, DateUtils.FORMAT_SHOW_TIME)
    ListItem(
        headlineContent = { Text(displayName(entry), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(time) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                    Text(
                        text = ButtonRules.formatValue(entry.weight),
                        style = MaterialTheme.typography.titleMedium,
                        color = valueColors(entry.weight.toLong(), invertColors)?.color ?: Color.Unspecified,
                    )
                    Text(text = "Total ${row.sumAfter}", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = "Delete ${displayName(entry)} at $time",
                    )
                }
            }
        },
    )
}

/** The entry's button name; every entry comes from a button for now. */
private fun displayName(entry: HistoryEntry): String = entry.buttonName ?: "(no button)"

private fun formatDateAndTime(context: Context, timestamp: Long): String =
    DateUtils.formatDateTime(
        context,
        timestamp,
        DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or
            DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_WEEKDAY or DateUtils.FORMAT_ABBREV_MONTH,
    )

/** The calendar day [timestamp] falls on in [zone], as days since 1970-01-01 there. */
fun localDay(timestamp: Long, zone: TimeZone = TimeZone.getDefault()): Long =
    (timestamp + zone.getOffset(timestamp)).floorDiv(DateUtils.DAY_IN_MILLIS)

@Preview(showBackground = true)
@Composable
fun HistoryScreenPreview() {
    val now = System.currentTimeMillis()
    val day = DateUtils.DAY_IN_MILLIS
    HapticHabitsTheme {
        HistoryScreen(
            rows = withRunningSums(
                listOf(
                    HistoryEntry(id = 4, timestamp = now, weight = 1, buttonName = "+1"),
                    HistoryEntry(id = 3, timestamp = now - 60_000, weight = -2, buttonName = "Went for a walk"),
                    HistoryEntry(id = 2, timestamp = now - day, weight = 5, buttonName = "Doomscrolling on the phone in bed at night"),
                    HistoryEntry(id = 1, timestamp = now - 3 * day, weight = 1, buttonName = "+1"),
                ),
            ),
            invertColors = false,
            onDelete = {},
            onBack = {},
        )
    }
}
