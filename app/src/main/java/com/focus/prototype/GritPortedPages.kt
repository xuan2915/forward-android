/*
 * Copyright (C) 2026 Shubham Gorai
 * This file is derived from Grit shared/ui's TaskList, TaskCard,
 * AnalyticsPage, StartStats, WeeklyHeatMap, CalendarMap and WeeklyActivity.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Self Discipline adaptations keep the original page order and component
 * proportions while binding them to local task templates and dated records.
 */
package com.focus.prototype

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
internal fun GritTaskListPage(
    modifier: Modifier = Modifier,
    date: String,
    tasks: List<Task>,
    continuing: List<Task>,
    filter: Int,
    confirmed: Boolean,
    onFilter: (Int) -> Unit,
    onAdd: () -> Unit,
    onPlan: () -> Unit,
    onSettings: () -> Unit,
    onDetails: (Task) -> Unit,
    onEdit: (Task) -> Unit,
    onMove: (Task) -> Unit,
    onDelete: (Task) -> Unit,
) {
    val completed = tasks.count(Task::done)
    val filteredTasks = when (filter) {
        1 -> tasks.filter(Task::required)
        2 -> tasks.filterNot(Task::required)
        else -> tasks
    }
    Column(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(onClick = onSettings) {
                    Icon(painterResource(R.drawable.grit_settings), contentDescription = "设置")
                }
                FilledIconButton(onClick = onAdd) {
                    Icon(painterResource(R.drawable.grit_add), contentDescription = "添加任务")
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "今日任务",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineLarge.copy(fontFamily = gritFontEmphasis()),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$completed/${tasks.size} 已完成",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = gritFontRounded()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onPlan) { Text(if (confirmed) "今日计划" else "确认计划") }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            content = {
                listOf(0 to "全部", 1 to "必做", 2 to "选做").forEach { (value, label) ->
                    GritFilterButton(value == filter, label) { onFilter(value) }
                }
            },
        )

        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                contentPadding = PaddingValues(top = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (continuing.isNotEmpty()) {
                    item {
                        Text(
                            "跨日专注 · 记录归属开始当天",
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    itemsIndexed(continuing, key = { _, task -> "continue-${task.id}" }) { index, task ->
                        GritTaskListCard(task, gritTaskShape(index, continuing.size), onDetails, onEdit, onMove, onDelete)
                    }
                }
                if (filteredTasks.isEmpty()) {
                    item {
                        Text(
                            if (tasks.isEmpty()) "今天还没有任务，添加一项要完成的事" else "这个分类下还没有任务",
                            modifier = Modifier.fillMaxWidth().padding(top = 150.dp, start = 12.dp, end = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    itemsIndexed(filteredTasks, key = { _, task -> task.id }) { index, task ->
                        GritTaskListCard(task, gritTaskShape(index, filteredTasks.size), onDetails, onEdit, onMove, onDelete)
                    }
                }
            }
            androidx.compose.material3.FloatingActionButton(
                onClick = onAdd,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(painterResource(R.drawable.grit_add), contentDescription = "添加任务")
            }
        }
    }
}

@Composable
private fun GritFilterButton(selected: Boolean, text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(text, Modifier.padding(horizontal = 18.dp, vertical = 10.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GritTaskListCard(
    task: Task,
    shape: Shape,
    onDetails: (Task) -> Unit,
    onEdit: (Task) -> Unit,
    onMove: (Task) -> Unit,
    onDelete: (Task) -> Unit,
) {
    var menuExpanded by remember(task.id) { mutableStateOf(false) }
    val cardContent by animateColorAsState(
        if (task.done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSecondaryContainer,
        label = "grit-card-content",
    )
    val cardBackground by animateColorAsState(
        if (task.done) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.secondaryContainer,
        label = "grit-card-background",
    )
    Card(
        modifier = Modifier
            .semantics {
                role = Role.Button
                onClick(label = "查看任务详情") { onDetails(task); true }
                onLongClick(label = "打开任务操作") { menuExpanded = true; true }
            }
            .pointerInput(task.id) {
                val menuAreaPx = 56.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.position.x < size.width - menuAreaPx) {
                        var tracking = true
                        while (tracking) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val dx = change.position.x - down.position.x
                            val dy = change.position.y - down.position.y
                            if (dx * dx + dy * dy > viewConfiguration.touchSlop * viewConfiguration.touchSlop) {
                                tracking = false
                            } else if (!change.pressed) {
                                val heldMillis = change.uptimeMillis - down.uptimeMillis
                                if (heldMillis >= viewConfiguration.longPressTimeoutMillis) {
                                    menuExpanded = true
                                } else {
                                    onDetails(task)
                                }
                                tracking = false
                            }
                        }
                    }
                }
            }
            .animateContentSize(motionSizeSpec()),
        colors = CardDefaults.cardColors(containerColor = cardBackground, contentColor = cardContent),
        shape = shape,
    ) {
        // Grit TaskCard row adapted to dated self-discipline tasks. Completion
        // remains a detail-sheet action so evidence and frozen-plan rules stay
        // in force; the row keeps Grit's status, title, supporting line, and
        // trailing action hierarchy.
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CompletionMark(task.done)
            Column(
                Modifier.weight(1f).padding(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    task.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
                )
                val target = if (task.minutes > 0) "${task.minutes} 分钟" else "自由专注"
                val reward = buildList {
                    if (task.rewardMinutes > 0) add("+${task.rewardMinutes} 分钟")
                    if (task.rewardGames > 0) add("+${task.rewardGames} 局")
                }.joinToString(" · ")
                Text(
                    buildString {
                        append(if (task.required) "必做" else "选做")
                        append(" · ")
                        append(target)
                        if (reward.isNotEmpty()) append(" · 完成可得 $reward")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(40.dp).semantics { contentDescription = "任务操作" }) {
                    Text("⋮", fontSize = 24.sp, color = cardContent, maxLines = 1)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    if (!task.done) DropdownMenuItem(text = { Text("编辑任务") }, onClick = { menuExpanded = false; onEdit(task) })
                    if (!task.done) DropdownMenuItem(text = { Text("调整日期") }, onClick = { menuExpanded = false; onMove(task) })
                    DropdownMenuItem(text = { Text("查看详情") }, onClick = { menuExpanded = false; onDetails(task) })
                    if (!task.done) DropdownMenuItem(text = { Text("删除任务") }, onClick = { menuExpanded = false; onDelete(task) })
                }
            }
        }
    }
}

private fun gritHabitShape(index: Int, count: Int, completed: Boolean): Shape = gritTaskShape(index, count, completed)

@Composable
internal fun GritHabitAnalyticsPage(db: Database, taskId: String, refreshKey: Int, modifier: Modifier = Modifier, onBack: () -> Unit) {
    val data = remember(taskId, refreshKey) { gritHabitData(db, taskId) }
    if (data == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack) { Text("返回成长") }
            Text("这项任务已不在历史记录中", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    var selectedDate by remember(taskId) { mutableStateOf(LocalDate.parse(db.day())) }
    val today = LocalDate.parse(db.day())
    val calendarState = rememberCalendarState(
        startMonth = YearMonth.from(data.startDate),
        endMonth = YearMonth.from(today),
        firstVisibleMonth = YearMonth.from(today),
        firstDayOfWeek = DayOfWeek.MONDAY,
    )
    val scope = rememberCoroutineScope()
    LaunchedEffect(selectedDate) {
        val month = YearMonth.from(selectedDate)
        if (month != calendarState.firstVisibleMonth.yearMonth) calendarState.animateScrollToMonth(month)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalIconButton(onClick = onBack) { Icon(painterResource(R.drawable.grit_arrow_back), contentDescription = "返回") }
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            data.title,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = gritFontEmphasis()),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            data.description,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp),
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = gritFontRounded()),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(20.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { GritStartStats(data) }
            item {
                GritAnalyticsHeader("每周进度", R.drawable.grit_calendar) {
                    TextButton(onClick = { selectedDate = today }) { Text("今天") }
                }
                GritWeeklyHeatMap(today, data.heatMap) { selectedDate = it }
                val selected = data.days.firstOrNull { it.date == selectedDate }
                Text(
                    "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日 · ${when { selected?.done == true -> "已完成"; selected != null -> "未完成"; else -> "未安排" }}${selected?.let { " · 专注 ${focusDuration(it.elapsed)}" }.orEmpty()}",
                    modifier = Modifier.padding(start = 16.dp, top = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                GritAnalyticsHeader("成长日历", R.drawable.grit_calendar) {
                    Row {
                        IconButton(onClick = { scope.launch { calendarState.animateScrollToMonth(calendarState.firstVisibleMonth.yearMonth.minusMonths(1)) } }) {
                            Text("‹", fontSize = 24.sp)
                        }
                        IconButton(onClick = { scope.launch { calendarState.animateScrollToMonth(YearMonth.from(today)) } }) {
                            Icon(painterResource(R.drawable.grit_calendar), contentDescription = "回到本月", modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { scope.launch { calendarState.animateScrollToMonth(calendarState.firstVisibleMonth.yearMonth.plusMonths(1)) } }, enabled = calendarState.firstVisibleMonth.yearMonth < YearMonth.from(today)) {
                            Text("›", fontSize = 24.sp)
                        }
                    }
                }
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(vertical = 12.dp)) {
                        HorizontalCalendar(
                            state = calendarState,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            monthHeader = { month ->
                                Text(
                                    "${month.yearMonth.year}年${month.yearMonth.monthValue}月",
                                    Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 8.dp),
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = gritFontRounded()),
                                )
                            },
                            dayContent = { day -> GritHabitCalendarDay(day, today, selectedDate, data.heatMap) { selectedDate = it } },
                        )
                    }
                }
            }
            item { GritWeeklyActivity(data, today) }
        }
    }
}

@Composable
private fun GritStartStats(data: GritHabitData) {
    val progress by animateFloatAsState(data.consistency, animationSpec = tween(motionMillis(AppMotion.Growth), easing = AppMotion.Ease), label = "grit-consistency")
    Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = 380.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                strokeCap = StrokeCap.Round,
                strokeWidth = 36.dp,
                modifier = Modifier.size(200.dp),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${(progress * 100).roundToInt()}%", style = MaterialTheme.typography.headlineSmall.copy(fontFamily = gritFontRounded(), color = MaterialTheme.colorScheme.primary))
                Text("完成率", style = MaterialTheme.typography.titleSmall.copy(fontFamily = gritFontRounded()))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            GritStatTile("开始记录", data.startDate.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)), R.drawable.grit_flag, MaterialTheme.colorScheme.primaryContainer)
            GritStatTile("最长连续", data.bestStreak.toString(), R.drawable.grit_heat, MaterialTheme.colorScheme.primaryContainer)
            val fraction by animateFloatAsState(
                if (data.bestStreak == 0) 0f else (data.currentStreak.toFloat() / data.bestStreak).coerceIn(0f, 1f),
                animationSpec = tween(600),
                label = "grit-current-streak",
            )
            GritStatTile("当前连续", data.currentStreak.toString(), R.drawable.grit_sprint, MaterialTheme.colorScheme.primaryContainer, fraction)
        }
    }
}

@Composable
internal fun GritStatTile(label: String, value: String, icon: Int, color: Color, fraction: Float = 1f) {
    Row(
        Modifier.fillMaxWidth().background(color, RoundedCornerShape(20.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.fillMaxWidth().background(
                Brush.horizontalGradient(0f to color, fraction to color, fraction to Color.Transparent, 1f to Color.Transparent),
                RoundedCornerShape(20.dp),
            ).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(Modifier.background(MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 3.dp, vertical = 14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium.copy(fontFamily = gritFontRounded(), color = MaterialTheme.colorScheme.onPrimaryContainer), maxLines = 1)
                Text(value, style = MaterialTheme.typography.titleMedium.copy(fontFamily = gritFontRounded(), color = MaterialTheme.colorScheme.onPrimaryContainer), maxLines = 1, modifier = Modifier.basicMarquee())
            }
        }
    }
}

@Composable
private fun GritHabitCalendarDay(day: CalendarDay, today: LocalDate, selected: LocalDate, history: Map<String, String>, onClick: (LocalDate) -> Unit) {
    val state = history[day.date.toString()]
    val inside = day.position == DayPosition.MonthDate
    val fill = when (state) {
        "完成" -> MaterialTheme.colorScheme.primary
        "休息" -> AppColors.Border
        "未完成" -> AppColors.Missed
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).padding(3.dp).clip(CircleShape)
            .background(if (inside) fill else Color.Transparent)
            .then(if (inside) Modifier.clickable { onClick(day.date) } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = gritFontRounded()),
            color = when {
                !inside -> Color.Transparent
                state == "完成" -> MaterialTheme.colorScheme.onPrimary
                state == "未完成" -> AppColors.MissedInk
                state == "休息" -> AppColors.Muted
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        if (inside && (day.date == selected || day.date == today)) {
            Box(Modifier.fillMaxSize().padding(2.dp).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape))
        }
    }
}

@Composable
private fun GritWeeklyActivity(data: GritHabitData, today: LocalDate) {
    var period by remember(data.taskId) { mutableIntStateOf(8) }
    val firstMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val weeklyCounts = remember(data.days, today) {
        (51 downTo 0).map { week ->
            val start = firstMonday.minusWeeks(week.toLong())
            data.days.count { it.done && it.date >= start && it.date < start.plusWeeks(1) }.toDouble()
        }
    }
    val max = weeklyCounts.maxOrNull()?.coerceAtLeast(1.0)?.toFloat() ?: 1f
    val visible = weeklyCounts.takeLast(period)
    val average = if (visible.isEmpty()) 0 else (visible.dropLast(1).ifEmpty { visible }.average()).roundToInt()
    GritAnalyticsHeader("每周完成", R.drawable.grit_chart)
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(8 to "2月", 16 to "4月", 32 to "8月", 52 to "1年").forEach { (weeks, label) ->
                    val selected = period == weeks
                    Surface(
                        onClick = { period = weeks },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    ) { Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.labelMedium) } }
                }
            }
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                visible.forEach { count ->
                    val height by animateFloatAsState((count.toFloat() / max * 112f).coerceAtLeast(4f), animationSpec = tween(450), label = "grit-week-bar")
                    Box(
                        Modifier.weight(1f).height(height.dp).clip(CircleShape)
                            .background(if (count == max.toDouble()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary),
                    )
                }
            }
            Text(
                "$average 次每周（平均）",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = gritFontRounded()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
