package com.machandler.haptichabits

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import java.text.DateFormatSymbols

// Trends' charts, drawn with Canvas (no chart library). Each Canvas carries a contentDescription
// that summarises it, since what it draws isn't in the semantics tree.

/**
 * A line chart of [series], one point per bucket, evenly spaced: y-axis gridlines at round
 * numbers (always including 0) with their values on the left, the first and last bucket's dates
 * ([startLabel], [endLabel]) under the ends, and a dashed vertical line at each marked bucket
 * (a reset). Every point gets a dot, so a single point still shows; an all-zero series is a flat
 * line on the 0 gridline.
 */
@Composable
fun LineChart(
    series: TrendSeries,
    color: Color,
    startLabel: String,
    endLabel: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val baselineColor = MaterialTheme.colorScheme.outline
    val values = series.values
    val ticks = remember(values) { axisTicks(values.minOrNull() ?: 0, values.maxOrNull() ?: 0) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .semantics { contentDescription = description },
    ) {
        val tickLabels = ticks.map { textMeasurer.measure(it.toString(), labelStyle) }
        val start = textMeasurer.measure(startLabel, labelStyle)
        val end = textMeasurer.measure(endLabel, labelStyle)
        val labelGap = 6.dp.toPx()
        val dotRadius = 3.dp.toPx()

        val left = tickLabels.maxOf { it.size.width } + labelGap
        val right = size.width - dotRadius
        val top = tickLabels.last().size.height / 2f
        val bottom = size.height - start.size.height - labelGap
        val low = ticks.first()
        val high = ticks.last()
        fun y(value: Long) = bottom - (value - low).toFloat() / (high - low) * (bottom - top)
        fun x(index: Int) = if (values.size <= 1) (left + right) / 2 else left + index * (right - left) / (values.size - 1)

        ticks.forEachIndexed { i, tick ->
            val lineY = y(tick)
            drawLine(
                color = if (tick == 0L) baselineColor else gridColor,
                start = Offset(left, lineY),
                end = Offset(right, lineY),
                strokeWidth = 1.dp.toPx(),
            )
            val label = tickLabels[i]
            drawText(label, topLeft = Offset(left - labelGap - label.size.width, lineY - label.size.height / 2f))
        }
        for (index in series.markers) {
            drawLine(
                color = baselineColor,
                start = Offset(x(index), top),
                end = Offset(x(index), bottom),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
        }
        if (values.size > 1) {
            val path = Path()
            values.forEachIndexed { i, value ->
                if (i == 0) path.moveTo(x(i), y(value)) else path.lineTo(x(i), y(value))
            }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        values.forEachIndexed { i, value -> drawCircle(color, radius = dotRadius, center = Offset(x(i), y(value))) }

        val labelY = bottom + labelGap
        if (values.size <= 1) {
            drawText(start, topLeft = Offset((left + right - start.size.width) / 2, labelY))
        } else {
            drawText(start, topLeft = Offset(left, labelY))
            // Right-aligned under the last point, unless the two would overlap.
            if (left + start.size.width + labelGap < size.width - end.size.width) {
                drawText(end, topLeft = Offset(size.width - end.size.width, labelY))
            }
        }
    }
}

/**
 * The contribution calendar: a column per week, a row per weekday, with month names above and a
 * few weekday names at the left (which stay put while the weeks scroll). It starts scrolled to
 * the newest week. Each day is [colors] at its intensity level (0 = no presses); tapping a day
 * calls [onSelectDay], and [selectedDay] gets an outline.
 */
@Composable
fun CalendarView(
    calendar: ContributionCalendar,
    colors: List<Color>,
    monthName: (monthStart: Long) -> String,
    selectedDay: Long?,
    onSelectDay: (Long) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val outlineColor = MaterialTheme.colorScheme.onSurface
    val weekdayNames = remember { DateFormatSymbols.getInstance().shortWeekdays }
    val monthNames = remember(calendar) { calendar.monthLabels.map { monthName(it.monthStart) } }
    val currentOnSelectDay by rememberUpdatedState(onSelectDay)
    val scroll = rememberScrollState()
    // Once laid out (and whenever the width changes), show the newest week at the right.
    LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }

    Row(modifier = modifier) {
        Column(modifier = Modifier.padding(end = 4.dp)) {
            Spacer(Modifier.height(MONTH_ROW))
            for (row in 0 until 7) {
                Box(modifier = Modifier.height(CELL_PITCH), contentAlignment = Alignment.CenterStart) {
                    if (row in LABELED_ROWS) {
                        Text(
                            text = weekdayNames[rowWeekday(row, calendar.firstDayOfWeek)],
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            softWrap = false,
                        )
                    }
                }
            }
        }
        Box(modifier = Modifier.horizontalScroll(scroll)) {
            Canvas(
                modifier = Modifier
                    .size(width = CELL_PITCH * calendar.weeks, height = MONTH_ROW + CELL_PITCH * 7)
                    .semantics { contentDescription = description }
                    .pointerInput(calendar) {
                        detectTapGestures { offset ->
                            val column = (offset.x / CELL_PITCH.toPx()).toInt()
                            val rowY = offset.y - MONTH_ROW.toPx()
                            val row = if (rowY < 0) -1 else (rowY / CELL_PITCH.toPx()).toInt()
                            calendar.dayAt(column, row)?.let(currentOnSelectDay)
                        }
                    },
            ) {
                val pitch = CELL_PITCH.toPx()
                val cell = CELL_SIZE.toPx()
                val header = MONTH_ROW.toPx()
                val corner = CornerRadius(2.dp.toPx())
                calendar.monthLabels.forEachIndexed { i, label ->
                    drawText(textMeasurer.measure(monthNames[i], labelStyle), topLeft = Offset(label.column * pitch, 0f))
                }
                for (column in 0 until calendar.weeks) {
                    for (row in 0 until 7) {
                        val day = calendar.dayAt(column, row) ?: continue
                        val topLeft = Offset(column * pitch, header + row * pitch)
                        drawRoundRect(colors[calendar.level(day)], topLeft = topLeft, size = Size(cell, cell), cornerRadius = corner)
                        if (day == selectedDay) {
                            drawRoundRect(
                                outlineColor,
                                topLeft = topLeft,
                                size = Size(cell, cell),
                                cornerRadius = corner,
                                style = Stroke(width = 1.5.dp.toPx()),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "Less [] [] [] [] [] More": the calendar's colors from no presses to the busiest days. */
@Composable
fun CalendarLegend(colors: List<Color>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Less", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (color in colors) {
            Box(Modifier.size(CELL_SIZE).background(color, RoundedCornerShape(2.dp)))
        }
        Text("More", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val CELL_SIZE = 12.dp
private val CELL_PITCH = 15.dp // cell plus the gap after it
private val MONTH_ROW = 16.dp
