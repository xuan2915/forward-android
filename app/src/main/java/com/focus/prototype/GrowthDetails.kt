package com.focus.prototype

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private data class DayStats(val date: LocalDate, val completed: Int, val total: Int, val focusMinutes: Long)
private data class AuditEntry(val date: String, val detail: String)
internal data class WeeklyCompletion(val title: String, val completedAt: Long, val elapsed: Long, val elapsedKnown: Boolean)
private enum class GrowthSection(val label: String) { CALENDAR("日历"), TREND("趋势"), MILESTONES("里程碑") }

@Composable
fun GrowthDetails(db: Database, refreshKey: Int = 0, onAllAchievements: () -> Unit = {}, onHistoricalTask:(Task)->Unit = {}, onOpenDailyCard: (String) -> Unit = {}) {
    val today = LocalDate.parse(db.day())
    val history = db.history(requireKnownTypes = false).toMap()
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

    // This composable is hosted by a LazyColumn item. Keep every section in one
    // vertical layout; otherwise Compose places its sibling roots in the same slot.
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GrowthSection.entries.forEach { item ->
                FilterChip(
                    selected = section == item,
                    onClick = { section = item },
                    label = { Text(item.label, maxLines = 1) },
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
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (fadeIn(tween(transitionDuration)) + slideInHorizontally(tween(transitionDuration)) { it / 18 * direction }) togetherWith
                    (fadeOut(tween(transitionDuration)) + slideOutHorizontally(tween(transitionDuration)) { -it / 22 * direction })
            },
            label = "growth-section",
        ) { visibleSection ->
            when (visibleSection) {
                GrowthSection.CALENDAR -> CalendarSection(db, today, history, selectedDate, calendarState, scope, refreshKey, { selectedDate = it }, onHistoricalTask, onOpenDailyCard)
                GrowthSection.TREND -> TrendSection(db, today, refreshKey, history) { selectedDate = it; section = GrowthSection.CALENDAR }
                GrowthSection.MILESTONES -> MilestoneSection(db, refreshKey, onAllAchievements)
            }
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
    refreshKey: Int,
    onDateSelected: (LocalDate) -> Unit,
    onHistoricalTask:(Task)->Unit,
    onOpenDailyCard: (String) -> Unit = {},
) {
    val visibleMonth = calendarState.firstVisibleMonth.yearMonth
    val monthDays = (1..visibleMonth.lengthOfMonth()).map { visibleMonth.atDay(it) }
    val completed = monthDays.count { history[it.toString()] == "完成" }
    val rested = monthDays.count { history[it.toString()] == "休息" }
    val missed = monthDays.count { history[it.toString()] == "未完成" }

    // Calendar grid, date controls, and selected-day detail are separate vertical
    // blocks so the variable-height detail can never be painted over the month.
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GritAnalyticsHeader("计划日历", icon = R.drawable.grit_calendar)
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
                        listOf(completed to AppColors.Accent, rested to AppColors.Border, missed to AppColors.Missed).forEach { (count, color) ->
                            if (count > 0) {
                                val sweep = 360f * count / total
                                drawArc(color, start, sweep, false, style = Stroke(5.dp.toPx(), cap = StrokeCap.Butt))
                                start += sweep
                            }
                        }
                        if (completed + rested + missed == 0) drawArc(AppColors.Raised, -90f, 360f, false, style = Stroke(5.dp.toPx()))
                    }
                    Text(completed.toString(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("本月必做达标", style = MaterialTheme.typography.labelMedium, color = AppColors.Muted)
                    Text("$completed 天达标 · $rested 天休息", style = MaterialTheme.typography.bodySmall)
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

        val visibleDate = selectedDate
        val selectedState = history[visibleDate.toString()]
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            GritAnalyticsHeader("${visibleDate.monthValue}月${visibleDate.dayOfMonth}日", icon = R.drawable.grit_analytics) {
                StatusPill(selectedState ?: "未安排", active = selectedState == "完成")
            }
            QuietSurface {
                val selectedTasks = db.tasks(visibleDate.toString())
                if (selectedTasks.isEmpty()) {
                    Text("这一天还没有任务记录", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                } else selectedTasks.forEachIndexed { index, task ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable(enabled = !visibleDate.isAfter(today), onClick = { onHistoricalTask(task) })
                            .semantics(mergeDescendants = true) {}
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CompletionMark(task.done)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(task.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = AppColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val elapsedMs = db.elapsed(task)
                            val elapsedStr = if (!task.elapsedKnown) "专注时长未知" else "实际\u00A0${formatDurationMs(elapsedMs)}"
                            val targetStr = if (task.minutes > 0) "目标\u00A0${formatDurationMinutes(task.minutes)}" else ""
                            val durationText = when {
                                !task.elapsedKnown && task.minutes > 0 -> "专注时长未知 / 目标\u00A0${formatDurationMinutes(task.minutes)}"
                                !task.elapsedKnown -> "专注时长未知"
                                targetStr.isNotEmpty() -> "$elapsedStr / $targetStr"
                                else -> elapsedStr
                            }
                            val completedAt = task.completedAt.takeIf { task.done && it > 0L }?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime() }
                            val completionSuffix = if (task.done) {
                                completedAt?.let { actual ->
                                    val timeStr = actual.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                                    if (actual.toLocalDate().toString() != task.date) " · 跨日补记完成于\u00A0${actual.monthValue}月${actual.dayOfMonth}日\u00A0$timeStr" else " · 完成于\u00A0$timeStr"
                                } ?: " · 完成日期未记录"
                            } else ""
                            Text(durationText + completionSuffix, style = MaterialTheme.typography.labelSmall, color = AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(if (task.done) "完成" else "未完成", style = MaterialTheme.typography.labelSmall, color = if (task.done) AppColors.Accent else AppColors.Muted)
                            Spacer(Modifier.height(4.dp))
                            if (task.done) {
                                Text("+100\u00A0XP", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp), color = AppColors.Accent)
                            }
                            if (!visibleDate.isAfter(today)) {
                                Text(if (task.done) "详情" else if (visibleDate.isBefore(today)) "补记" else "查看", style = MaterialTheme.typography.labelSmall, color = AppColors.Text)
                            }
                        }
                    }
                    if (index < selectedTasks.lastIndex) Hairline()
                }
            }
        }

        AuditLogSection(db, refreshKey)

        val dayCard = remember(visibleDate, refreshKey) { DailyCards.load(db, visibleDate.toString()) }
        if (dayCard != null) {
            QuietSurface(
                modifier = Modifier.clickable { onOpenDailyCard(visibleDate.toString()) },
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.grit_flag),
                            contentDescription = null,
                            tint = AppColors.Reward,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("查看当日纪念", style = MaterialTheme.typography.titleSmall, color = AppColors.Text)
                            Text("${visibleDate.monthValue}月${visibleDate.dayOfMonth}日 · 历史纪念卡", style = MaterialTheme.typography.labelSmall, color = AppColors.Muted)
                        }
                    }
                    Text("›", style = MaterialTheme.typography.titleMedium, color = AppColors.Muted)
                }
            }
        }
    }
}

@Composable
private fun AuditLogSection(db: Database, refreshKey: Int) {
    val records = remember(db, refreshKey) {
        db.readableDatabase.rawQuery(
            "SELECT day,detail FROM audit WHERE detail LIKE '计划%' OR detail LIKE '任务日期%' OR detail LIKE '接收延期%' OR detail LIKE '紧急解锁%' OR detail='恢复保护' OR detail LIKE '睡眠%' OR detail LIKE '预设休息%' ORDER BY created DESC LIMIT 12",
            null,
        ).use { cursor ->
            buildList {
                if (cursor.moveToFirst()) do { add(AuditEntry(cursor.getString(0), readableAudit(cursor.getString(1)))) } while (cursor.moveToNext())
            }
        }
    }
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<AuditEntry?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("记录与例外", style = MaterialTheme.typography.titleMedium, color = AppColors.Text)
                    Text(if (records.isEmpty()) "暂无计划调整或保护记录" else "${records.size} 条计划与保护记录", style = MaterialTheme.typography.labelSmall, color = AppColors.Muted)
                }
                Text(if (expanded) "收起" else "查看", style = MaterialTheme.typography.labelLarge, color = AppColors.Accent)
            }
        }
        AnimatedVisibility(expanded) {
            QuietSurface {
                if (records.isEmpty()) Text("计划调整、睡眠例外和应用保护记录会显示在这里", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                records.forEach { record ->
                    Text(
                        "${record.date}  ·  ${record.detail}",
                        modifier = Modifier.fillMaxWidth().clickable { selected = record }.padding(vertical = 4.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.Muted,
                    )
                }
            }
        }
    }
    selected?.let { record ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("记录详情") },
            text = { Text("${record.date} · ${record.detail}") },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("完成") } },
        )
    }
}

@Composable
private fun TrendSection(db: Database, today: LocalDate, refreshKey: Int, history: Map<String, String>, onDateSelected: (LocalDate) -> Unit) {
    val weekState = rememberWeeklyReviewState(today)
    val weeklyCompletions = remember(db, refreshKey, weekState.weekStart) {
        completedTasksBetween(db, weekState.weekStart, weekState.weekEndExclusive)
    }
    var expanded by remember(weekState.weekStart) { mutableStateOf(false) }
    val visibleCompletions = if (expanded || weeklyCompletions.size <= 5) weeklyCompletions else weeklyCompletions.take(5)

    val stats = (6 downTo 0).map { offset -> dailyStats(db, today.minusDays(offset.toLong())) }
    val total = stats.sumOf { it.total }
    val done = stats.sumOf { it.completed }
    val focusMinutes = stats.sumOf { it.focusMinutes }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GritAnalyticsHeader("近 7 天", icon = R.drawable.grit_analytics)
        QuietSurface {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${percent(done, total)}%", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Light)
                    Text("计划完成率 · $done / $total", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(formatMinutes(focusMinutes), style = MaterialTheme.typography.titleLarge)
                    Text("有效专注", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                }
            }
            if (total > 0) DailyCompletionBars(stats)
            else Text("最近 7 天还没有任务记录", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
        }

        GritAnalyticsHeader("周回顾", icon = R.drawable.grit_analytics) {
            StatusPill("${weeklyCompletions.size} 项", active = weeklyCompletions.isNotEmpty())
        }
        QuietSurface {
            WeeklyReviewNavRow(weekState)
            if (weeklyCompletions.isEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("暂无完成记录", style = MaterialTheme.typography.bodyMedium, color = AppColors.Text)
            } else {
                Spacer(Modifier.height(4.dp))
                visibleCompletions.forEachIndexed { index, completion ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CompletionMark(true)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(completion.title, style = MaterialTheme.typography.bodyMedium, color = AppColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                weeklyCompletionDetail(completion, ZoneId.systemDefault()),
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.Muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (index < visibleCompletions.lastIndex) Hairline()
                }

                if (weeklyCompletions.size > 5) {
                    Hairline()
                    TextButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    ) {
                        Text(
                            if (expanded) "收起" else "展开全部 ${weeklyCompletions.size} 项",
                            style = MaterialTheme.typography.labelLarge,
                            color = AppColors.Accent,
                        )
                    }
                }
            }
        }

        GritAnalyticsHeader("过去一年 · 计划日", icon = R.drawable.grit_heat)
        GritWeeklyHeatMap(today, history, onDateSelected)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CalendarLegend(AppColors.Accent, "完成")
            CalendarLegend(AppColors.Border, "休息")
            CalendarLegend(AppColors.Missed, "未完成")
            CalendarLegend(MaterialTheme.colorScheme.surfaceContainerHigh, "无必做")
        }
    }
}

@Composable
private fun DailyCompletionBars(stats: List<DayStats>) {
    Row(
        Modifier.fillMaxWidth().height(132.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        stats.forEach { stat ->
            val rate = if (stat.total == 0) 0f else (stat.completed.toFloat() / stat.total).coerceIn(0f, 1f)
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (stat.total == 0) "—" else "${stat.completed}/${stat.total}", style = MaterialTheme.typography.labelSmall, color = AppColors.Muted, maxLines = 1, softWrap = false)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    if (rate > 0f) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(rate).clip(RoundedCornerShape(6.dp)).background(AppColors.Accent))
                }
                Spacer(Modifier.height(4.dp))
                Text("${stat.date.monthValue}/${stat.date.dayOfMonth}", style = MaterialTheme.typography.labelSmall, color = AppColors.Quiet, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun MilestoneSection(db: Database, refreshKey: Int, onAllAchievements: () -> Unit) {
    Column {
        AchievementPreview(db, refreshKey, onAllAchievements)
    }
}

private fun readableAudit(detail: String): String = detail
    .replace("计划变更：", "计划调整：")
    .replace("必做=true", "必做").replace("必做=false", "选做")
    .replace("删除=true", "已删除本次任务").replace("删除=false", "")
    .replace("/", " · ")
    .replace(Regex("\\s+"), " ")
    .trim()

@Composable
private fun CalendarLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
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
        "SELECT COUNT(*),COALESCE(SUM(done),0),COALESCE(SUM(elapsed),0) FROM tasks WHERE day>=? AND day<=? AND id NOT IN (SELECT source_id FROM task_moves)",
        arrayOf(from.toString(), to.toString()),
    ).use { cursor ->
        cursor.moveToFirst()
        TaskStats(cursor.getInt(1), cursor.getInt(0), cursor.getLong(2) / 60000)
    }

internal fun completedTasksBetween(db: Database, start: LocalDate, endExclusive: LocalDate): List<WeeklyCompletion> {
    val zone = ZoneId.systemDefault()
    val from = start.atStartOfDay(zone).toInstant().toEpochMilli()
    val until = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
    return db.readableDatabase.rawQuery(
        "SELECT id,title,completed_at,elapsed FROM tasks WHERE done=1 AND completed_at>=? AND completed_at<? AND id NOT IN (SELECT source_id FROM task_moves) ORDER BY completed_at DESC,rowid DESC",
        arrayOf(from.toString(), until.toString()),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(WeeklyCompletion(
                    title = cursor.getString(1),
                    completedAt = cursor.getLong(2),
                    elapsed = if (cursor.isNull(3)) 0L else cursor.getLong(3),
                    elapsedKnown = !cursor.isNull(3),
                ))
            }
        }
    }
}

private fun weeklyCompletionDetail(completion: WeeklyCompletion, zone: ZoneId): String {
    val completedAt = Instant.ofEpochMilli(completion.completedAt).atZone(zone)
    val weekday = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[completedAt.dayOfWeek.value - 1]
    val time = completedAt.format(DateTimeFormatter.ofPattern("HH:mm"))
    val focus = when {
        !completion.elapsedKnown -> "专注时长未知"
        completion.elapsed > 0L -> "专注 ${focusDuration(completion.elapsed)}"
        else -> "未记录专注时长"
    }
    return "${completedAt.monthValue}/${completedAt.dayOfMonth} $weekday $time · $focus"
}

private fun percent(done: Int, total: Int) = if (total == 0) 0 else done * 100 / total

private fun formatMinutes(minutes: Long): String = when {
    minutes < 60 -> "$minutes 分钟"
    else -> "${minutes / 60} 小时 ${minutes % 60} 分"
}
