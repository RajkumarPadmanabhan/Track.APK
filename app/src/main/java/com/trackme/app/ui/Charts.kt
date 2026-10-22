package com.trackme.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

val chartColors = listOf(
    Color(0xFFFF4D6D), Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFFF59E0B),
    Color(0xFF8B5CF6), Color(0xFF06B6D4), Color(0xFFEC4899), Color(0xFF84CC16),
    Color(0xFF94A3B8),
)

/** Vertical bar chart for historical metrics (e.g. date -> count). */
@Composable
fun SimpleBarChart(
    metrics: List<Pair<String, Long>>,
    modifier: Modifier = Modifier,
    barColor: Color = Color(0xFFFF4D6D)
) {
    if (metrics.isEmpty()) return
    val max = (metrics.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(1L)
    val min = metrics.minOfOrNull { it.second } ?: 0L
    val range = (max - min).coerceAtLeast(1L)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        metrics.forEach { (label, value) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.weight(1f)
            ) {
                val heightFraction = if (max == min) 0.5f else (0.2f + 0.8f * (value - min).toFloat() / range.toFloat())
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .fillMaxHeight(heightFraction.coerceIn(0.1f, 1f))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(barColor)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    fontSize = 10.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Donut chart + legend for language distribution. */
@Composable
fun LanguageDonut(data: List<Pair<String, Int>>) {
    val total = data.sumOf { it.second }.coerceAtLeast(1)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Canvas(Modifier.size(130.dp)) {
            var start = -90f
            val stroke = 28.dp.toPx()
            data.forEachIndexed { i, (_, count) ->
                val sweep = 360f * count / total
                drawArc(
                    color = chartColors[i % chartColors.size],
                    startAngle = start,
                    sweepAngle = sweep - 1.5f,
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke),
                )
                start += sweep
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            data.forEachIndexed { i, (lang, count) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(chartColors[i % chartColors.size])
                    )
                    Text(
                        "  $lang  ${count * 100 / total}%",
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Horizontal bar chart: label, bar, value. */
@Composable
fun HBarChart(items: List<Pair<String, Int>>, color: Color = chartColors[0]) {
    val max = (items.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (label, value) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    modifier = Modifier.width(96.dp),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(value.toFloat() / max)
                            .clip(RoundedCornerShape(6.dp))
                            .background(color)
                    )
                }
                Text("  $value", fontSize = 12.sp, modifier = Modifier.width(48.dp))
            }
        }
    }
}

/**
 * Contribution-style heatmap: one column per week, rows Monday to Sunday,
 * today in the last column. Cells after today are not drawn.
 */
@Composable
fun ActivityHeatmap(counts: Map<String, Int>, weeks: Int = 13) {
    val grid = remember(counts, weeks) { buildGrid(counts, weeks) }
    val max = (grid.flatten().filterNotNull().maxOrNull() ?: 1).coerceAtLeast(1)
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val levels = listOf(Color(0xFF6B2A3A), Color(0xFFB8405A), Color(0xFFFF4D6D), Color(0xFFFFB3C1))

    Canvas(Modifier.fillMaxWidth().aspectRatio(weeks / 7f)) {
        val gap = 3.dp.toPx()
        val cell = (size.width - gap * (weeks - 1)) / weeks
        for (c in 0 until weeks) {
            for (r in 0 until 7) {
                val v = grid[c][r] ?: continue
                val color = if (v == 0) {
                    empty
                } else {
                    val idx = (Math.ceil(v.toDouble() / max * 4).toInt() - 1).coerceIn(0, 3)
                    levels[idx]
                }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(c * (cell + gap), r * (cell + gap)),
                    size = Size(cell, cell),
                    cornerRadius = CornerRadius(cell * 0.3f),
                )
            }
        }
    }
}

/** Builds [weeks] columns of 7 rows (Mon..Sun). Future days are null. */
private fun buildGrid(counts: Map<String, Int>, weeks: Int): List<List<Int?>> {
    val utc = TimeZone.getDefault() // local days, matching how activity is stored
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = utc }
    val today = Calendar.getInstance(utc)
    val todayRow = (today.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday = 0
    val start = (today.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, -((weeks - 1) * 7 + todayRow))
    }
    val todayKey = fmt.format(today.time)
    return List(weeks) { c ->
        List(7) { r ->
            val d = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, c * 7 + r) }
            val key = fmt.format(d.time)
            if (key > todayKey) null else counts[key] ?: 0
        }
    }
}

/**
 * Contribution heatmap in the "streak" style: weekday letters on the left (M T W T F S S),
 * one column per week, newest week on the right. Four pink levels, dark cells for empty days.
 */
@Composable
fun StreakHeatmap(counts: Map<String, Int>, modifier: Modifier = Modifier, weeks: Int = 18) {
    val grid = remember(counts, weeks) { buildGrid(counts, weeks) }
    val max = (grid.flatten().filterNotNull().maxOrNull() ?: 1).coerceAtLeast(1)
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Color(0xFFB98A93), fontSize = 10.sp)
    val levels = listOf(Color(0xFFFFD1DA), Color(0xFFFF9DB3), Color(0xFFFF5C7C), Color(0xFFE8264F))
    val empty = Color(0xFF4B232B)
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val labelW = 18.dp
        val gap = 3.dp
        val cell = (maxWidth - labelW - gap * (weeks - 1)) / weeks
        val totalHeight = cell * 7 + gap * 6

        Canvas(Modifier.fillMaxWidth().height(totalHeight)) {
            val g = gap.toPx()
            val lw = labelW.toPx()
            val c = (size.width - lw - g * (weeks - 1)) / weeks
            for (r in 0 until 7) {
                val layout = measurer.measure(labels[r], labelStyle)
                drawText(layout, topLeft = Offset(0f, r * (c + g) + (c - layout.size.height) / 2f))
            }
            for (col in 0 until weeks) {
                for (r in 0 until 7) {
                    val v = grid[col][r] ?: continue
                    val color = if (v == 0) {
                        empty
                    } else {
                        val idx = (Math.ceil(v.toDouble() / max * 4).toInt() - 1).coerceIn(0, 3)
                        levels[idx]
                    }
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(lw + col * (c + g), r * (c + g)),
                        size = Size(c, c),
                        cornerRadius = CornerRadius(c * 0.3f),
                    )
                }
            }
        }
    }
}

/** The first day shown by a heatmap with [weeks] columns, as yyyy-MM-dd. */
fun heatmapStartKey(weeks: Int): String {
    val today = Calendar.getInstance()
    val todayRow = (today.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val start = (today.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, -((weeks - 1) * 7 + todayRow))
    }
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(start.time)
}

/**
 * Full-year contribution graph (53 weeks) like the one on a GitHub profile: month names on top,
 * Mon / Wed / Fri on the left, "Less ... More" legend underneath. Cells have a fixed size, so the
 * graph scrolls sideways and opens on the most recent weeks.
 */
@Composable
fun YearHeatmap(counts: Map<String, Int>, modifier: Modifier = Modifier, weeks: Int = 53) {
    val grid = remember(counts, weeks) { buildGrid(counts, weeks) }
    val max = (grid.flatten().filterNotNull().maxOrNull() ?: 1).coerceAtLeast(1)
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Color(0xFFD7A9B3), fontSize = 11.sp)
    val levels = listOf(Color(0xFFFFD1DA), Color(0xFFFF9DB3), Color(0xFFFF5C7C), Color(0xFFE8264F))
    val empty = Color(0xFF4B232B)
    val cell = 13.dp
    val gap = 3.dp
    val monthH = 18.dp
    val labelW = 34.dp
    val gridH = cell * 7 + gap * 6
    val scroll = rememberScrollState()
    LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }

    // Month name above the first column of each month
    val months = remember(weeks) {
        val cal = Calendar.getInstance()
        val todayRow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        cal.add(Calendar.DAY_OF_YEAR, -((weeks - 1) * 7 + todayRow))
        val fmt = SimpleDateFormat("MMM", Locale.getDefault())
        var last = -1
        List(weeks) { c ->
            val d = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, c * 7) }
            val m = d.get(Calendar.MONTH)
            if (m != last) { last = m; fmt.format(d.time) } else null
        }
    }

    Column(modifier.fillMaxWidth()) {
        Row {
            Canvas(Modifier.width(labelW).height(monthH + gridH)) {
                val rowH = (cell + gap).toPx()
                listOf(0 to "Mon", 2 to "Wed", 4 to "Fri").forEach { (r, t) ->
                    val l = measurer.measure(t, labelStyle)
                    drawText(l, topLeft = Offset(0f, monthH.toPx() + r * rowH + (cell.toPx() - l.size.height) / 2f))
                }
            }
            Canvas(
                Modifier
                    .weight(1f)
                    .height(monthH + gridH)
                    .horizontalScroll(scroll)
                    .width((cell + gap) * weeks)
            ) {
                val step = (cell + gap).toPx()
                val c = cell.toPx()
                val top = monthH.toPx()
                months.forEachIndexed { col, name ->
                    if (name != null && col <= weeks - 3) {
                        drawText(measurer.measure(name, labelStyle), topLeft = Offset(col * step, 0f))
                    }
                }
                for (col in 0 until weeks) {
                    for (r in 0 until 7) {
                        val v = grid[col][r] ?: continue
                        val color = if (v == 0) empty else {
                            levels[(Math.ceil(v.toDouble() / max * 4).toInt() - 1).coerceIn(0, 3)]
                        }
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(col * step, top + r * step),
                            size = Size(c, c),
                            cornerRadius = CornerRadius(c * 0.22f),
                        )
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            Text("Less", fontSize = 11.sp, color = Color(0xFFD7A9B3))
            Spacer(Modifier.width(6.dp))
            (listOf(empty) + levels).forEach {
                Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(it))
                Spacer(Modifier.width(3.dp))
            }
            Spacer(Modifier.width(3.dp))
            Text("More", fontSize = 11.sp, color = Color(0xFFD7A9B3))
        }
    }
}
