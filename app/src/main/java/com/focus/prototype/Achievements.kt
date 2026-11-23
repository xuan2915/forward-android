package com.focus.prototype

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

internal data class Achievement(
    val id: String,
    val category: String,
    val title: String,
    val condition: String,
    val progress: Int,
    val target: Int,
    val earnedDate: String?,
) {
    val earned get() = progress >= target
    val started get() = progress > 0 && !earned
}

internal fun achievementList(db: Database): List<Achievement> {
    val completedDates = mutableListOf<String>()
    db.readableDatabase.rawQuery(
        "SELECT day FROM tasks WHERE done=1 AND origin!='sleep_system' AND id NOT IN (SELECT source_id FROM task_moves) ORDER BY day,rowid",
        null,
    ).use { while (it.moveToNext()) completedDates.add(it.getString(0)) }
    val focusByDay = linkedMapOf<String, Long>()
    db.readableDatabase.rawQuery(
        "SELECT day,COALESCE(SUM(elapsed),0) FROM tasks WHERE origin!='sleep_system' AND id NOT IN (SELECT source_id FROM task_moves) GROUP BY day ORDER BY day",
        null,
    ).use { while (it.moveToNext()) focusByDay[it.getString(0)] = it.getLong(1) }
    db.runningTasks().filter { it.origin != "sleep_system" }.forEach { task -> focusByDay[task.date] = (focusByDay[task.date] ?: 0) + (db.elapsed(task) - task.elapsed).coerceAtLeast(0) }
    val focusMinutes = focusByDay.values.sum() / 60000
    fun taskDate(target: Int) = completedDates.getOrNull(target - 1)
    fun focusDate(target: Int): String? {
        var sum = 0L
        for ((date, millis) in focusByDay) {
            sum += millis
            if (sum >= target * 60000L) return date
        }
        return null
    }
    fun streakDate(target: Int): String? {
        var run = 0
        for ((date, state) in db.history().sortedBy { it.first }) {
            when (state) {
                "完成" -> { run++; if (run >= target) return date }
                "未完成" -> run = 0
                else -> Unit
            }
        }
        return null
    }
    val streak = db.bestStreak()
    return listOf(
        Achievement("task.first", "任务积累", "首项达成", "完成第一项任务", completedDates.size.coerceAtMost(1), 1, taskDate(1)),
        Achievement("task.ten", "任务积累", "十项达成", "累计完成 10 项任务", completedDates.size.coerceAtMost(10), 10, taskDate(10)),
        Achievement("task.fifty", "任务积累", "五十项达成", "累计完成 50 项任务", completedDates.size.coerceAtMost(50), 50, taskDate(50)),
        Achievement("streak.three", "连续记录", "连续三日", "连续三个自律日完成目标", streak.coerceAtMost(3), 3, streakDate(3)),
        Achievement("streak.seven", "连续记录", "连续一周", "连续七个自律日完成目标", streak.coerceAtMost(7), 7, streakDate(7)),
        Achievement("streak.thirty", "连续记录", "连续整月", "连续三十个自律日完成目标", streak.coerceAtMost(30), 30, streakDate(30)),
        Achievement("focus.hour", "专注积累", "专注一小时", "累计有效专注 60 分钟", focusMinutes.toInt().coerceAtMost(60), 60, focusDate(60)),
        Achievement("focus.ten_hours", "专注积累", "专注十小时", "累计有效专注 10 小时", focusMinutes.toInt().coerceAtMost(600), 600, focusDate(600)),
    )
}

/** Seed old achievements as already seen so an upgrade does not replay past unlocks. */
internal fun initializeAchievementNotices(db: Database) {
    if (db.setting("achievement_notice_baseline_v1") == "true") return
    achievementList(db).filter { it.earned }.forEach { db.setSetting("achievement_seen_${it.id}", "true") }
    db.setSetting("achievement_notice_baseline_v1", "true")
}

internal fun nextUnseenAchievement(db: Database): Achievement? = achievementList(db)
    .firstOrNull { it.earned && db.setting("achievement_seen_${it.id}") != "true" }

@Composable
internal fun AchievementPreview(db: Database, refreshKey: Int, onAll: () -> Unit) {
    val items = remember(refreshKey) { achievementList(db) }
    val visible = items.filter { it.earned || it.progress > 0 }
        .sortedWith(compareBy<Achievement> { if (it.earned) 0 else 1 }.thenBy { it.target - it.progress })
        .take(3).ifEmpty { items.take(3) }
    var selected by remember { mutableStateOf<Achievement?>(null) }
    GritAnalyticsHeader("成长成就", icon = R.drawable.grit_flag)
    QuietSurface {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("已获得 ${items.count { it.earned }} / ${items.size}", style = MaterialTheme.typography.labelMedium, color = AppColors.Muted, modifier = Modifier.weight(1f))
            TextButton(onClick = onAll) { Text("查看全部成就 ›") }
        }
        visible.forEach { AchievementCard(it) { selected = it } }
    }
    selected?.let { AchievementDetailDialog(it, db) { selected = null } }
}

@Composable
internal fun AchievementsPage(db: Database, refreshKey: Int, onBack: () -> Unit) {
    val items = remember(refreshKey) { achievementList(db) }
    var selected by remember { mutableStateOf<Achievement?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = AppSpace.Page).padding(top = AppSpace.XLarge, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(AppSpace.Large),
    ) {
        TextButton(onClick = onBack) { Text("‹ 返回成长") }
        PageHeading("成长记录", "全部成就")
        Text("已获得 ${items.count { it.earned }} 项 · 共 ${items.size} 项", style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted)
        listOf("任务积累", "连续记录", "专注积累").forEach { category ->
            val group = items.filter { it.category == category }
            GritAnalyticsHeader(category, icon = when (category) {
                "连续记录" -> R.drawable.category_streak
                "专注积累" -> R.drawable.category_focus
                else -> R.drawable.category_task
            })
            listOf("已获得" to group.filter { it.earned }, "进行中" to group.filter { it.started }, "未开始" to group.filter { !it.earned && !it.started })
                .filter { it.second.isNotEmpty() }
                .forEach { (label, entries) ->
                    Text(label, style = MaterialTheme.typography.labelMedium, color = AppColors.Muted)
                    entries.forEach { AchievementCard(it) { selected = it } }
                }
        }
    }
    selected?.let { AchievementDetailDialog(it, db) { selected = null } }
}

@Composable
internal fun AchievementProgressBar(
    progress: Float,
    started: Boolean,
    modifier: Modifier = Modifier,
) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        modifier
            .height(5.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(AppColors.Raised)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f)
            }
    ) {
        if (clamped > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(clamped)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (started) AppColors.Accent else AppColors.Border)
            )
        }
    }
}

@Composable
private fun AchievementCard(item: Achievement, onClick: () -> Unit) {
    val progress by animateIntAsState(item.progress, label = "achievement-progress-${item.id}")
    val status = when { item.earned -> "已获得"; else -> "$progress / ${item.target}" }
    QuietSurface(
        Modifier.animateContentSize().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AchievementMedal(item, Modifier.size(66.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall)
                    Text(status, style = MaterialTheme.typography.labelSmall, color = if (item.earned) AppColors.Accent else AppColors.Muted)
                }
                if (item.earned) Text("获得日期 · ${item.earnedDate ?: "记录已同步"}", style = MaterialTheme.typography.labelSmall, color = AppColors.Muted)
                else AchievementProgressBar(
                    progress = progress.toFloat() / item.target,
                    started = item.started,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

internal fun achievementDetailTitle(item: Achievement, evidence: AchievementEvidence): String =
    if (evidence is AchievementEvidence.Hidden) "隐藏成就" else item.title

internal fun achievementDetailNextStage(
    item: Achievement,
    evidence: AchievementEvidence,
): Pair<String, String>? = if (evidence is AchievementEvidence.Hidden) null else nextStageOf(item.id)

@Composable
private fun AchievementMedal(
    item: Achievement,
    modifier: Modifier = Modifier,
    size: Dp = 66.dp,
    accessibilityLabel: String = item.title,
) {
    val resource = when (item.id) {
        "task.first" -> R.drawable.achievement_first
        "task.ten" -> R.drawable.achievement_ten
        "task.fifty" -> R.drawable.achievement_fifty
        "streak.three" -> R.drawable.achievement_three
        "streak.seven" -> R.drawable.achievement_seven
        "streak.thirty" -> R.drawable.achievement_thirty
        "focus.hour" -> R.drawable.achievement_hour
        "focus.ten_hours" -> R.drawable.achievement_ten_hours
        else -> return
    }
    val accent = when (item.id.substringBefore('.')) {
        "streak" -> AppColors.Warning
        "focus" -> AppColors.Accent
        else -> AppColors.Reward
    }
    val tint = when {
        item.earned -> accent
        item.started -> accent.copy(alpha = .74f)
        else -> AppColors.Muted.copy(alpha = .55f)
    }
    Surface(
        modifier.size(size), shape = RoundedCornerShape(size * .25f),
        color = tint.copy(alpha = .09f),
        border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = .36f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(
                androidx.compose.ui.res.painterResource(resource), accessibilityLabel,
                Modifier.size(size * .60f), tint = tint,
            )
        }
    }
}

@Composable
internal fun AchievementDetailDialog(item: Achievement, db: Database, onDismiss: () -> Unit) {
    val evidence = remember(item.id, item.earned, item.progress) {
        loadAchievementEvidence(db, item)
    }
    val displayTitle = achievementDetailTitle(item, evidence)
    val nextStage = remember(item.id, evidence is AchievementEvidence.Hidden) {
        achievementDetailNextStage(item, evidence)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            shape = RoundedCornerShape(16.dp),
            color = AppColors.Surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Border),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Top Header: Medal + Title + Category + Condition
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AchievementMedal(item, Modifier.size(64.dp), accessibilityLabel = displayTitle)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(item.category, style = MaterialTheme.typography.labelMedium, color = AppColors.Muted)
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (evidence !is AchievementEvidence.Hidden) {
                            Text(
                                text = item.condition,
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.Muted,
                            )
                        }
                    }
                }

                // Progress / Earned status
                if (evidence !is AchievementEvidence.Hidden) {
                    if (item.earned) {
                        Text(
                            text = "已达成 · ${item.earnedDate?.let { "解锁于 $it" } ?: "记录已同步"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = AppColors.Accent,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "当前进度 · ${item.progress} / ${item.target}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AppColors.Muted,
                                )
                                val remaining = (item.target - item.progress).coerceAtLeast(0)
                                if (remaining > 0) {
                                    Text(
                                        text = "还需 $remaining",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppColors.Muted,
                                    )
                                }
                            }
                            AchievementProgressBar(
                                progress = item.progress.toFloat() / item.target,
                                started = item.started,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }

                    // Next stage if defined
                    nextStage?.let { (nextTitle, nextDesc) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AppColors.Raised,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("下一阶段", style = MaterialTheme.typography.labelSmall, color = AppColors.Accent)
                                Text("$nextTitle · $nextDesc", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                            }
                        }
                    }
                }

                Hairline()

                // Evidence List Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when (evidence) {
                        is AchievementEvidence.TaskList -> {
                            Text(
                                text = "贡献任务 (${evidence.tasks.size}/${item.target})",
                                style = MaterialTheme.typography.labelMedium,
                                color = AppColors.Text,
                            )
                            if (evidence.tasks.isEmpty()) {
                                Text("暂无完成任务记录", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                            } else {
                                evidence.tasks.forEach { task ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        Text("✓", color = AppColors.Accent, style = MaterialTheme.typography.bodySmall)
                                        Column(Modifier.weight(1f)) {
                                            Text(task.title, style = MaterialTheme.typography.bodyMedium)
                                            val timeDetail = if (task.completedAt > 0L) {
                                                val dt = Instant.ofEpochMilli(task.completedAt).atZone(ZoneId.systemDefault()).toLocalDateTime()
                                                val dateStr = String.format("%04d-%02d-%02d", dt.year, dt.monthValue, dt.dayOfMonth)
                                                val timeStr = String.format("%02d:%02d", dt.hour, dt.minute)
                                                val crossDay = if (dateStr != task.date) " · 实际完成 $dateStr $timeStr" else " · $timeStr"
                                                "计划 ${task.date}$crossDay"
                                            } else {
                                                "计划 ${task.date} · 完成时间未记录"
                                            }
                                            val focusDetail = if (task.elapsedKnown && task.elapsed > 0) " · 专注 ${task.elapsed / 60000} 分钟" else ""
                                            Text("$timeDetail$focusDetail", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                                        }
                                    }
                                }
                            }
                        }
                        is AchievementEvidence.DateList -> {
                            Text(
                                text = "连续达标日 (${evidence.dates.size}/${item.target})",
                                style = MaterialTheme.typography.labelMedium,
                                color = AppColors.Text,
                            )
                            if (evidence.dates.isEmpty()) {
                                Text("暂无连续达标日期记录", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                            } else {
                                evidence.dates.forEach { date ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("●", color = AppColors.Warning, style = MaterialTheme.typography.bodySmall)
                                        Text(date, style = MaterialTheme.typography.bodyMedium)
                                        Text("自律日达标", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                                    }
                                }
                            }
                        }
                        is AchievementEvidence.FocusList -> {
                            Text(
                                text = "专注分布 (${evidence.entries.size} 天)",
                                style = MaterialTheme.typography.labelMedium,
                                color = AppColors.Text,
                            )
                            if (evidence.entries.isEmpty()) {
                                Text("暂无专注记录", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                            } else {
                                evidence.entries.forEach { focus ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("●", color = AppColors.Accent, style = MaterialTheme.typography.bodySmall)
                                        Text(focus.date, style = MaterialTheme.typography.bodyMedium)
                                        Text("有效专注 ${focus.minutes} 分钟", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                                    }
                                }
                            }
                        }
                        is AchievementEvidence.Hidden -> {
                        }
                    }
                }

                Hairline()

                // Bottom Action: Return to origin
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.semantics { contentDescription = "返回原处" },
                    ) {
                        Text("返回", color = AppColors.Text)
                    }
                }
            }
        }
    }
}

@Composable
internal fun AchievementUnlockDialog(item: Achievement, onDismiss: () -> Unit, onViewAll: () -> Unit) {
    val reduced = LocalReducedMotion.current
    val card = remember(item.id) { Animatable(if (reduced) 1f else 0f) }
    val seal = remember(item.id) { Animatable(if (reduced) 1f else 0f) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    LaunchedEffect(item.id, reduced) {
        if (reduced) {
            card.snapTo(1f)
            seal.snapTo(1f)
        } else {
            kotlinx.coroutines.coroutineScope {
                launch { card.animateTo(1f, tween(210, easing = AppMotion.Ease)) }
                launch { seal.animateTo(1f, tween(280, delayMillis = 85, easing = AppMotion.Ease)) }
            }
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().graphicsLayer {
                alpha = card.value
                translationY = (1f - card.value) * 14.dp.toPx()
            },
            shape = MaterialTheme.shapes.medium,
            color = AppColors.Surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Border),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(item.category, style = MaterialTheme.typography.labelMedium, color = AppColors.Reward)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AchievementMedal(item, Modifier.graphicsLayer {
                        alpha = seal.value
                        scaleX = .78f + .22f * seal.value
                        scaleY = scaleX
                    }, 72.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleLarge)
                        Text(item.condition, style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted)
                    }
                }
                item.earnedDate?.let {
                    Text("解锁于 $it", style = MaterialTheme.typography.labelMedium, color = AppColors.Muted)
                }
                Hairline()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onViewAll) { Text("查看成就") }
                    TextButton(onClick = onDismiss) { Text("收下") }
                }
            }
        }
    }
}
