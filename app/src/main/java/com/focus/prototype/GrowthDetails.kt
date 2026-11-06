package com.focus.prototype

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
private enum class GrowthSection(val label: String) { CALENDAR("日历"), TREND("趋势"), MILESTONES("里程碑") }

@Composable
fun GrowthDetails(db: Database,refreshKey:Int=0,onAllAchievements:()->Unit={}) {
    val today = LocalDate.parse(db.day())
    val history = db.history().toMap()
    var selectedDate by remember(today) { mutableStateOf(today) }
    var section by remember { mutableStateOf(GrowthSection.CALENDAR) }
    val calendarState = rememberCalendarState(
        startMonth = YearMonth.of(2019, 1),
        endMonth = YearMonth.from(today.plusYears(2)),
        firstVisibleMonth = YearMonth.from(today),
        firstDayOfWeek = DayOfWeek.MONDAY,
    )
    val scope = rememberCoroutineScope()
    LaunchedEffect(selectedDate) {
        val month = YearMonth.from(selectedDate)
        if (month != calendarState.firstVisibleMonth.yearMonth) calendarState.animateScrollToMonth(month)
    }

    GritAnalyticsHeader("过去一年", icon = R.drawable.grit_heat)
    GritWeeklyHeatMap(today, history) { selectedDate = it; section = GrowthSection.CALENDAR }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        CalendarLegend(AppColors.Accent, "完成")
        CalendarLegend(AppColors.Border, "休息")
        CalendarLegend(AppColors.Warning.copy(alpha = .75f), "未完成")
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GrowthSection.entries.forEach { item ->
            FilterChip(
                selected = section == item,
                onClick = { section = item },
                label = { Text(item.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.weight(1f),
            )
        }
    }

    val transitionDuration = motionMillis(AppMotion.Short)
    AnimatedContent(
        targetState = section,
        transitionSpec = { fadeIn(tween(transitionDuration)) togetherWith fadeOut(tween(transitionDuration)) },
        label = "growth-section",
    ) { visibleSection ->
        when (visibleSection) {
            GrowthSection.CALENDAR -> CalendarSection(db, today, history, selectedDate, calendarState, scope) { selectedDate = it }
            GrowthSection.TREND -> TrendSection(db, today)
            GrowthSection.MILESTONES -> MilestoneSection(db,refreshKey,onAllAchievements)
        }
    }
}

@Composable
private fun CalendarSection(
    db: Database,
    today: LocalDate,
    history: Map<String, String>,
    selectedDate: LocalDate,
    calendarState: com.kizitonwose.calendar.compose.CalendarState,
    scope: kotlinx.coroutines.CoroutineScope,
    onDateSelected: (LocalDate) -> Unit,
) {
    val visibleMonth = calendarState.firstVisibleMonth.yearMonth
    val monthDays = (1..visibleMonth.lengthOfMonth()).map { visibleMonth.atDay(it) }
    val completed = monthDays.count { history[it.toString()] == "完成" }
    val rested = monthDays.count { history[it.toString()] == "休息" }
    val missed = monthDays.count { history[it.toString()] == "未完成" }

    GritAnalyticsHeader("成长日历", icon = R.drawable.grit_calendar)
    QuietSurface {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { scope.launch { calendarState.animateScrollToMonth(visibleMonth.minusMonths(1)) } }) { Text("‹", color = AppColors.Muted, fontSize = 24.sp) }
            Text("${visibleMonth.year} 年 ${visibleMonth.monthValue} 月", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { scope.launch { calendarState.animateScrollToMonth(visibleMonth.plusMonths(1)) } }) { Text("›", color = AppColors.Muted, fontSize = 24.sp) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                    val total = visibleMonth.lengthOfMonth().toFloat()
                    var start = -90f
                    listOf(completed to AppColors.Accent, rested to AppColors.Border, missed to AppColors.Warning.copy(alpha = .8f)).forEach { (count, color) ->
                        if (count > 0) {
                            val sweep = 360f * count / total
                            drawArc(color, start, sweep, false, style = Stroke(5.dp.toPx()))
                            start += sweep
                        }
                    }
                    if (completed + rested + missed == 0) drawArc(AppColors.Raised, -90f, 360f, false, style = Stroke(5.dp.toPx()))
                }
                Text(completed.toString(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("本月完成", style = MaterialTheme.typography.labelMedium, color = AppColors.Muted)
                Text("$completed 天完成 · $rested 天休息", style = MaterialTheme.typography.bodySmall)
                Text("$missed 天未完成", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
            }
        }
        HorizontalCalendar(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium),
            state = calendarState,
            dayContent = { day -> GritCalendarDay(day, today, selectedDate, history) { onDateSelected(it) } },
            monthHeader = {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                        Text(it, Modifier.weight(1f), color = AppColors.Quiet, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
        )
    }

    val selectedState = history[selectedDate.toString()]
    GritAnalyticsHeader("${selectedDate.monthValue}月${selectedDate.dayOfMonth}日", icon = R.drawable.grit_analytics) {
        StatusPill(selectedState ?: "未记录", active = selectedState == "完成")
    }
    QuietSurface {
        val selectedTasks = if (selectedState != null) db.tasks(selectedDate.toString()) else emptyList()
        if (selectedTasks.isEmpty()) Text("这一天还没有任务记录。", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
        selectedTasks.forEachIndexed { index, task ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompletionMark(task.done)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(task.title, style = MaterialTheme.typography.bodyMedium, color = AppColors.Text, maxLines = 1)
                    Text(if (task.minutes > 0) "${focusDuration(db.elapsed(task))} / 目标 ${task.minutes} 分钟" else "专注 ${focusDuration(db.elapsed(task))}", style = MaterialTheme.typography.labelSmall, color = AppColors.Muted)
                }
                Text(if (task.done) "完成" else "未完成", style = MaterialTheme.typography.labelSmall, color = AppColors.Muted)
            }
            if (index < selectedTasks.lastIndex) Hairline()
        }
        val review = if (selectedState != null) db.readReview(selectedDate.toString()).filter { it.isNotBlank() } else emptyList()
        if (review.isNotEmpty()) {
            Hairline()
            Text("当日总结", style = MaterialTheme.typography.labelLarge, color = AppColors.Muted)
            review.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = AppColors.Text) }
        }
    }
}

@Composable
private fun TrendSection(db: Database, today: LocalDate) {
    val stats = (6 downTo 0).map { offset -> dailyStats(db, today.minusDays(offset.toLong())) }
    val total = stats.sumOf { it.total }
    val done = stats.sumOf { it.completed }
    val focusMinutes = stats.sumOf { it.focusMinutes }
    GritAnalyticsHeader("近 7 天", icon = R.drawable.grit_analytics)
    QuietSurface {
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
                    modifier = Modifier.fillMaxWidth().height(154.dp),
                    runInitialAnimation = false,
                    isZoomEnabled = false,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                stats.forEach { Text("${it.date.dayOfMonth}", style = MaterialTheme.typography.labelSmall, color = AppColors.Quiet) }
            }
        } else Text("完成任务后，这里会显示真实的每日完成趋势。", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
    }

    GritAnalyticsHeader("近 30 天", icon = R.drawable.grit_chart)
    val monthStats = taskStats(db, today.minusDays(29), today)
    QuietSurface {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("${percent(monthStats.completed, monthStats.total)}%", style = MaterialTheme.typography.headlineSmall)
                Text("任务完成率", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(formatMinutes(monthStats.focusMinutes), style = MaterialTheme.typography.titleLarge)
                Text("有效专注时长", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
            }
        }
    }
}

@Composable
private fun MilestoneSection(db: Database,refreshKey:Int,onAllAchievements:()->Unit) {
    AchievementPreview(db,refreshKey,onAllAchievements)
    GritAnalyticsHeader("计划与例外", icon = R.drawable.grit_calendar)
    QuietSurface {
        val audits = db.readableDatabase.rawQuery("SELECT day,detail FROM audit ORDER BY created DESC LIMIT 12", null)
        audits.use { cursor ->
            if (!cursor.moveToFirst()) Text("计划变更与紧急解锁会显示在这里。", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
            else do {
                val day = LocalDate.parse(cursor.getString(0))
                Text("${day.monthValue}月${day.dayOfMonth}日  ·  ${cursor.getString(1)}", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
            } while (cursor.moveToNext())
        }
    }
}

@Composable
private fun CalendarLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = AppColors.Muted)
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
