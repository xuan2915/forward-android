// CalendarMap layout adapted from Grit, Copyright (C) 2026 Shubham Gorai, GPL-3.0-or-later.
package com.focus.prototype

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.line.lineSpec
import com.patrykandpatrick.vico.compose.style.ChartStyle
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entriesOf
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch

private data class DayStats(val date: LocalDate, val completed: Int, val total: Int, val focusMinutes: Long)

@Composable
fun GrowthDetails(db: Database) {
    val today = LocalDate.parse(db.day())
    val history = db.history().toMap()
    var selectedDate by remember(today) { mutableStateOf(today) }
    val calendarState = rememberCalendarState(
        startMonth = YearMonth.of(2019, 1),
        endMonth = YearMonth.from(today.plusYears(2)),
        firstVisibleMonth = YearMonth.from(today),
        firstDayOfWeek = DayOfWeek.MONDAY,
    )
    val scope = rememberCoroutineScope()
    val visibleMonth = calendarState.firstVisibleMonth.yearMonth
    val monthDays = (1..visibleMonth.lengthOfMonth()).map { visibleMonth.atDay(it) }
    val completed = monthDays.count { history[it.toString()] == "完成" }
    val rested = monthDays.count { history[it.toString()] == "休息" }
    val missed = monthDays.count { history[it.toString()] == "未完成" }
    val stats = (6 downTo 0).map { offset -> dailyStats(db, today.minusDays(offset.toLong())) }

    GritAnalyticsHeader("成长日历")
    QuietSurface {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { scope.launch { calendarState.animateScrollToMonth(visibleMonth.minusMonths(1)) } }) { Text("‹", color = AppColors.Muted, fontSize = 24.sp) }
            Text("${visibleMonth.year} 年 ${visibleMonth.monthValue} 月", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { scope.launch { calendarState.animateScrollToMonth(visibleMonth.plusMonths(1)) } }) { Text("›", color = AppColors.Muted, fontSize = 24.sp) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                    val total = visibleMonth.lengthOfMonth().toFloat()
                    var start = -90f
                    listOf(completed to AppColors.Accent, rested to AppColors.Border, missed to AppColors.Warning).forEach { (count, color) ->
                        if (count > 0) {
                            val sweep = 360f * count / total
                            drawArc(color, start, sweep, false, style = Stroke(4.dp.toPx()))
                            start += sweep
                        }
                    }
                    if (completed + rested + missed == 0) drawArc(AppColors.Raised, -90f, 360f, false, style = Stroke(4.dp.toPx()))
                }
                Text(completed.toString(), fontSize = 22.sp, fontWeight = FontWeight.Light)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("本月记录", style = MaterialTheme.typography.labelMedium, color = AppColors.Muted)
                Text("完成 $completed 天   休息 $rested 天", style = MaterialTheme.typography.bodySmall, color = AppColors.Text)
                Text("未完成 $missed 天", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
            }
        }
        HorizontalCalendar(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer,MaterialTheme.shapes.medium).animateContentSize(animationSpec=motionSizeSpec()),
            state = calendarState,
            dayContent = { day -> GritCalendarDay(day, today, selectedDate, history) { selectedDate = it } },
            monthHeader = {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                        Text(it, Modifier.weight(1f), color = AppColors.Quiet, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CalendarLegend(AppColors.Accent, "完成")
            CalendarLegend(AppColors.Border, "休息")
            CalendarLegend(AppColors.Warning.copy(alpha = .75f), "未完成")
        }
    }

    val selectedState = history[selectedDate.toString()]
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${selectedDate.monthValue}月${selectedDate.dayOfMonth}日", style = MaterialTheme.typography.titleMedium)
        StatusPill(selectedState ?: "未记录", active = selectedState == "完成")
    }
    val selectedTasks = if (selectedState != null) db.tasks(selectedDate.toString()) else emptyList()
    selectedTasks.forEach { task ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (task.done) "✓" else "·", color = if (task.done) AppColors.Accent else AppColors.Muted, fontSize = 18.sp)
            Text(task.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = AppColors.Text)
            Text(if (task.done) "已完成" else "未完成", style = MaterialTheme.typography.labelSmall, color = AppColors.Muted)
        }
    }
    if (selectedState == null) Text("这一天还没有任务记录。", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
    val review = if (selectedState != null) db.readReview(selectedDate.toString()).filter { it.isNotBlank() } else emptyList()
    if (review.isNotEmpty()) Text(review.joinToString("\n"), style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)

    GritAnalyticsHeader("近 7 天")
    QuietSurface {
        val total = stats.sumOf { it.total }
        val done = stats.sumOf { it.completed }
        val focusMinutes = stats.sumOf { it.focusMinutes }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("$done / $total", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Light)
                Text("任务完成", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(formatMinutes(focusMinutes), style = MaterialTheme.typography.titleLarge)
                Text("有效专注", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
            }
        }
        if (total > 0) {
            val points = stats.mapIndexed { index, stat -> index to stat.completed }
            val producer = remember(points) { ChartEntryModelProducer(entriesOf(*points.toTypedArray())) }
            ProvideChartStyle(ChartStyle.fromColors(AppColors.Muted, AppColors.Border, AppColors.Border, listOf(AppColors.Accent), AppColors.Background)) {
                Chart(
                    chart = lineChart(lines = listOf(lineSpec(lineColor = AppColors.Accent, lineThickness = 2.5.dp, lineBackgroundShader = null))),
                    chartModelProducer = producer,
                    modifier = Modifier.fillMaxWidth().height(142.dp),
                    runInitialAnimation = false,
                    isZoomEnabled = false,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                stats.forEach { Text("${it.date.dayOfMonth}", style = MaterialTheme.typography.labelSmall, color = AppColors.Quiet) }
            }
        } else {
            Text("完成任务后，这里会显示真实的每日完成趋势。", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
        }
    }

    GritAnalyticsHeader("近 30 天")
    val monthStats = taskStats(db, today.minusDays(29), today)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${percent(monthStats.completed, monthStats.total)}%", style = MaterialTheme.typography.titleLarge)
            Text("任务完成率", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(formatMinutes(monthStats.focusMinutes), style = MaterialTheme.typography.titleLarge)
            Text("有效专注时长", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
        }
    }

    val lifetime = db.readableDatabase.rawQuery("SELECT COALESCE(SUM(done),0),COALESCE(SUM(elapsed),0) FROM tasks", null).use { cursor ->
        cursor.moveToFirst(); cursor.getInt(0) to cursor.getLong(1)
    }
    val bestStreak = db.bestStreak()
    val milestones = listOf(
        Triple("迈出第一步", "完成任务 ${lifetime.first.coerceAtMost(1)} / 1 项", lifetime.first >= 1),
        Triple("十次坚持", "完成任务 ${lifetime.first.coerceAtMost(10)} / 10 项", lifetime.first >= 10),
        Triple("一周节奏", "最长连续 $bestStreak / 7 天", bestStreak >= 7),
        Triple("专注十小时", "累计专注 ${(lifetime.second / 60000).coerceAtMost(600)} / 600 分钟", lifetime.second >= 600 * 60000L),
    )
    var expanded by remember { mutableStateOf(false) }
    GritAnalyticsHeader("成长里程碑") { TextButton(onClick={expanded=!expanded}) { Text(if(expanded) "收起" else "展开") } }
    androidx.compose.animation.AnimatedVisibility(expanded,enter=androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(motionMillis())),exit=androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(motionMillis()))) { Column {
    Text("每一步都来自真实的任务与专注记录。", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
    milestones.forEachIndexed { index, (title, detail, earned) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = AppColors.Text)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
            }
            StatusPill(if (earned) "已达成" else "进行中", active = earned)
        }
        if (index < milestones.lastIndex) Hairline()
    }

    } }
    GritAnalyticsHeader("计划与例外记录")
    val audits = db.readableDatabase.rawQuery("SELECT day,detail FROM audit ORDER BY created DESC LIMIT 12", null)
    audits.use { cursor ->
        if (!cursor.moveToFirst()) Text("计划变更与紧急解锁会显示在这里。", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
        else do {
            val day = LocalDate.parse(cursor.getString(0))
            Text("${day.monthValue}月${day.dayOfMonth}日  ·  ${cursor.getString(1)}", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
        } while (cursor.moveToNext())
    }
}

@Composable
private fun CalendarCell(day: CalendarDay, today: LocalDate, selected: LocalDate, history: Map<String, String>, onSelected: (LocalDate) -> Unit) {
    val date = day.date
    val state = history[date.toString()]
    val inside = day.position == DayPosition.MonthDate
    val fill = when (state) {
        "完成" -> AppColors.Accent
        "休息" -> AppColors.Raised
        "未完成" -> Color(0xFF382A28)
        else -> Color.Transparent
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).padding(3.dp).clip(CircleShape).background(fill)
            .then(if (date == selected || date == today) Modifier.border(1.dp, if (date == selected) AppColors.Text else AppColors.Accent.copy(alpha = .7f), CircleShape) else Modifier)
            .clickable(enabled = inside) { onSelected(date) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = when {
                !inside -> Color.Transparent
                state == "完成" -> AppColors.AccentInk
                else -> AppColors.Text
            },
        )
    }
}

@Composable
private fun CalendarLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = AppColors.Muted)
    }
}

private fun dailyStats(db: Database, day: LocalDate): DayStats {
    val stats = taskStats(db, day, day)
    return DayStats(day, stats.completed, stats.total, stats.focusMinutes)
}

private data class TaskStats(val completed: Int, val total: Int, val focusMinutes: Long)

private fun taskStats(db: Database, from: LocalDate, to: LocalDate): TaskStats =
    db.readableDatabase.rawQuery(
        "SELECT COUNT(*),COALESCE(SUM(done),0),COALESCE(SUM(elapsed),0) FROM tasks WHERE day>=? AND day<=?",
        arrayOf(from.toString(), to.toString()),
    ).use { cursor ->
        cursor.moveToFirst()
        TaskStats(cursor.getInt(1), cursor.getInt(0), cursor.getLong(2) / 60000)
    }

private fun percent(done: Int, total: Int) = if (total == 0) 0 else done * 100 / total

private fun formatMinutes(minutes: Long): String = when {
    minutes < 60 -> "$minutes 分钟"
    else -> "${minutes / 60} 小时 ${minutes % 60} 分"
}
