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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
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
        "SELECT day FROM tasks WHERE done=1 AND id NOT IN (SELECT source_id FROM task_moves) ORDER BY day,rowid",
        null,
    ).use { while (it.moveToNext()) completedDates.add(it.getString(0)) }
    val focusByDay = linkedMapOf<String, Long>()
    db.readableDatabase.rawQuery(
        "SELECT day,COALESCE(SUM(elapsed),0) FROM tasks WHERE id NOT IN (SELECT source_id FROM task_moves) GROUP BY day ORDER BY day",
        null,
    ).use { while (it.moveToNext()) focusByDay[it.getString(0)] = it.getLong(1) }
    db.runningTasks().forEach { task -> focusByDay[task.date] = (focusByDay[task.date] ?: 0) + (db.elapsed(task) - task.elapsed).coerceAtLeast(0) }
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
        var previous: LocalDate? = null
        for ((date, state) in db.history().sortedBy { it.first }) {
            val current = LocalDate.parse(date)
            if (previous != null && current != previous!!.plusDays(1)) run = 0
            when (state) {
                "完成" -> { run++; if (run >= target) return date }
                "休息" -> Unit
                else -> run = 0
            }
            previous = current
        }
        return null
    }
    val streak = db.bestStreak()
    return listOf(
        Achievement("task.first", "任务积累", "第一步", "完成第一项任务", completedDates.size.coerceAtMost(1), 1, taskDate(1)),
        Achievement("task.ten", "任务积累", "十项有成", "累计完成 10 项任务", completedDates.size.coerceAtMost(10), 10, taskDate(10)),
        Achievement("task.fifty", "任务积累", "积少成多", "累计完成 50 项任务", completedDates.size.coerceAtMost(50), 50, taskDate(50)),
        Achievement("streak.three", "连续记录", "三日有恒", "连续三个自律日完成目标", streak.coerceAtMost(3), 3, streakDate(3)),
        Achievement("streak.seven", "连续记录", "一周不辍", "连续七个自律日完成目标", streak.coerceAtMost(7), 7, streakDate(7)),
        Achievement("streak.thirty", "连续记录", "一月有恒", "连续三十个自律日完成目标", streak.coerceAtMost(30), 30, streakDate(30)),
        Achievement("focus.hour", "专注积累", "初次专注", "累计有效专注 60 分钟", focusMinutes.toInt().coerceAtMost(60), 60, focusDate(60)),
        Achievement("focus.ten_hours", "专注积累", "积时成寸", "累计有效专注 10 小时", focusMinutes.toInt().coerceAtMost(600), 600, focusDate(600)),
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
            TextButton(onClick = onAll) { Text("查看全部成就") }
        }
        visible.forEach { AchievementCard(it) { selected = it } }
    }
    selected?.let { AchievementDetailDialog(it) { selected = null } }
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
        PageHeading("真实记录 · 持续积累", "全部成就")
        Text("已获得 ${items.count { it.earned }} 项 · 共 ${items.size} 项", style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted)
        listOf("任务积累", "连续记录", "专注积累").forEach { category ->
            val group = items.filter { it.category == category }
            GritAnalyticsHeader(category, icon = when (category) {
                "连续记录" -> R.drawable.grit_flag
                "专注积累" -> R.drawable.grit_sprint
                else -> R.drawable.grit_chart
            })
            listOf("已获得" to group.filter { it.earned }, "进行中" to group.filter { it.started }, "未开始" to group.filter { !it.earned && !it.started })
                .filter { it.second.isNotEmpty() }
                .forEach { (label, entries) ->
                    Text(label, style = MaterialTheme.typography.labelMedium, color = AppColors.Muted)
                    entries.forEach { AchievementCard(it) { selected = it } }
                }
        }
    }
    selected?.let { AchievementDetailDialog(it) { selected = null } }
}

@Composable
private fun AchievementCard(item: Achievement, onClick: () -> Unit) {
    val progress by animateIntAsState(item.progress, label = "achievement-progress-${item.id}")
    val status = when { item.earned -> "已获得"; item.started -> "进行中 · $progress / ${item.target}"; else -> "未开始 · 0 / ${item.target}" }
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
                Text(item.condition, style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                if (item.earned) Text("获得日期 · ${item.earnedDate ?: "记录已同步"}", style = MaterialTheme.typography.labelSmall, color = AppColors.Muted)
                else LinearProgressIndicator(
                    progress = { (progress.toFloat() / item.target).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (item.started) AppColors.Accent else AppColors.Border,
                    trackColor = AppColors.Raised,
                )
            }
        }
    }
}

@Composable
private fun AchievementMedal(item: Achievement, modifier: Modifier = Modifier, size: Dp = 66.dp) {
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
                androidx.compose.ui.res.painterResource(resource), item.title,
                Modifier.size(size * .60f), tint = tint,
            )
        }
    }
}

@Composable
private fun AchievementDetailDialog(item: Achievement, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { AchievementMedal(item, Modifier.size(104.dp)) },
        title = { Text(item.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.condition, color = AppColors.Muted)
                if (item.earned) Text("已获得 · ${item.earnedDate ?: "记录已同步"}", color = AppColors.Accent)
                else {
                    Text("当前进度 · ${item.progress} / ${item.target}", color = AppColors.Muted)
                    LinearProgressIndicator(progress = { (item.progress.toFloat() / item.target).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = if (item.started) AppColors.Accent else AppColors.Border, trackColor = AppColors.Raised)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
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
