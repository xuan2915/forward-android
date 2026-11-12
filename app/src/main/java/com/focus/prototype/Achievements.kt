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
    val medalMetal = when (item.category) {
        "连续记录" -> AppColors.Warning
        "专注积累" -> AppColors.Accent
        else -> AppColors.Reward
    }
    val tint by animateColorAsState(
        targetValue = when {
            item.earned -> medalMetal
            item.started -> medalMetal.copy(alpha = 0.66f)
            else -> AppColors.Muted.copy(alpha = 0.42f)
        },
        label = "medal-color-${item.id}",
    )
    val progress by animateFloatAsState((item.progress.toFloat() / item.target).coerceIn(0f, 1f), label = "medal-progress-${item.id}")
    val stateLabel = when { item.earned -> "已获得"; item.started -> "进行中"; else -> "未解锁" }
    Canvas(
        modifier.size(size).semantics { contentDescription = "${item.title}勋章，$stateLabel" },
    ) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val min = this.size.minDimension
        val color = tint
        val radius = min * .40f
        val body = Path().apply {
            when (item.category) {
                "任务积累" -> {
                    val corner = radius * .32f
                    moveTo(cx - radius + corner, cy - radius)
                    lineTo(cx + radius, cy - radius)
                    lineTo(cx + radius, cy + radius - corner)
                    lineTo(cx + radius - corner, cy + radius)
                    lineTo(cx - radius, cy + radius)
                    lineTo(cx - radius, cy - radius + corner)
                    close()
                }
                "连续记录" -> {
                    moveTo(cx - radius, cy - radius)
                    lineTo(cx + radius, cy - radius)
                    lineTo(cx + radius, cy + radius * .1f)
                    quadraticTo(cx + radius, cy + radius * .62f, cx, cy + radius)
                    quadraticTo(cx - radius, cy + radius * .62f, cx - radius, cy + radius * .1f)
                    close()
                }
                else -> addOval(androidx.compose.ui.geometry.Rect(cx-radius, cy-radius, cx+radius, cy+radius))
            }
        }
        drawPath(body, AppColors.Background)
        drawPath(body, color.copy(alpha = if (item.earned) .19f else .07f))
        drawPath(body, color, style = Stroke(min * .026f))
        if (item.earned) {
            val edgeY = cy - radius * .72f
            drawLine(color.copy(alpha = .65f), Offset(cx-radius*.29f, edgeY),
                Offset(cx+radius*.29f, edgeY), min*.013f, cap = StrokeCap.Round)
        } else if (item.started) {
            drawLine(color, Offset(cx-radius*.6f, cy+radius*.70f),
                Offset(cx-radius*.6f+radius*1.2f*progress, cy+radius*.70f), min*.018f, cap = StrokeCap.Round)
        }
        when (item.id) {
            "task.first" -> drawCheckMedal(cx, cy - min * .025f, min, color)
            "task.ten" -> drawListMedal(cx, cy - min * .03f, min, color)
            "task.fifty" -> drawLaurelMedal(cx, cy - min * .02f, min, color)
            "streak.three" -> drawFlameMedal(cx, cy - min * .02f, min, color)
            "streak.seven" -> drawCalendarFlameMedal(cx, cy - min * .02f, min, color)
            "streak.thirty" -> drawStarMedal(cx, cy - min * .02f, min, color)
            "focus.hour" -> drawClockMedal(cx, cy - min * .02f, min, color)
            else -> drawHourglassMedal(cx, cy - min * .02f, min, color)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCheckMedal(cx: Float, cy: Float, d: Float, c: Color) {
    val path = Path().apply { moveTo(cx - d * .16f, cy); lineTo(cx - d * .04f, cy + d * .12f); lineTo(cx + d * .19f, cy - d * .15f) }
    drawPath(path, c, style = Stroke(d * .075f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawListMedal(cx: Float, cy: Float, d: Float, c: Color) {
    for (i in 0..2) {
        val y = cy + (i - 1) * d * .14f
        drawCircle(c, d * .027f, Offset(cx - d * .15f, y))
        drawLine(c, Offset(cx - d * .06f, y), Offset(cx + d * .16f, y), d * .045f, cap = StrokeCap.Round)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLaurelMedal(cx: Float, cy: Float, d: Float, c: Color) {
    val star = starPath(cx, cy, d * .17f, d * .075f)
    drawPath(star, c)
    for (side in listOf(-1f, 1f)) for (i in 0..2) {
        val x = cx + side * d * (.22f - i * .035f)
        val y = cy + d * (i - 1) * .105f
        drawOval(c, topLeft = Offset(x - d * .035f, y - d * .055f), size = androidx.compose.ui.geometry.Size(d * .07f, d * .11f))
    }
    drawArc(c, 205f, 130f, false, topLeft = Offset(cx - d * .28f, cy - d * .16f), size = androidx.compose.ui.geometry.Size(d * .56f, d * .34f), style = Stroke(d * .025f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFlameMedal(cx: Float, cy: Float, d: Float, c: Color) {
    val flame = Path().apply {
        moveTo(cx, cy + d * .22f); cubicTo(cx - d * .21f, cy + d * .08f, cx - d * .04f, cy - d * .04f, cx - d * .08f, cy - d * .23f)
        cubicTo(cx + d * .18f, cy - d * .12f, cx + d * .18f, cy + d * .02f, cx + d * .12f, cy + d * .08f)
        cubicTo(cx + d * .22f, cy + d * .04f, cx + d * .23f, cy + d * .18f, cx, cy + d * .22f)
        close()
    }
    drawPath(flame, c)
    drawCircle(AppColors.AccentInk.copy(alpha = .8f), d * .055f, Offset(cx, cy + d * .10f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCalendarFlameMedal(cx: Float, cy: Float, d: Float, c: Color) {
    val left = cx - d * .22f; val top = cy - d * .17f; val width = d * .44f; val height = d * .34f
    drawRoundRect(c, Offset(left, top), androidx.compose.ui.geometry.Size(width, height), androidx.compose.ui.geometry.CornerRadius(d * .045f), style = Stroke(d * .045f))
    drawLine(c, Offset(left, top + d * .09f), Offset(left + width, top + d * .09f), d * .035f)
    drawLine(c, Offset(cx - d * .11f, top - d * .035f), Offset(cx - d * .11f, top + d * .035f), d * .045f, cap = StrokeCap.Round)
    drawLine(c, Offset(cx + d * .11f, top - d * .035f), Offset(cx + d * .11f, top + d * .035f), d * .045f, cap = StrokeCap.Round)
    drawCircle(c, d * .035f, Offset(cx - d * .10f, cy + d * .02f)); drawCircle(c, d * .035f, Offset(cx + d * .10f, cy + d * .02f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStarMedal(cx: Float, cy: Float, d: Float, c: Color) {
    drawPath(starPath(cx, cy, d * .23f, d * .105f), c)
    drawCircle(AppColors.AccentInk.copy(alpha = .8f), d * .045f, Offset(cx, cy))
}

private fun starPath(cx: Float, cy: Float, outer: Float, inner: Float): Path = Path().apply {
    for (index in 0 until 10) {
        val angle = Math.toRadians(-90.0 + index * 36.0)
        val radius = if (index % 2 == 0) outer else inner
        val x = cx + kotlin.math.cos(angle).toFloat() * radius
        val y = cy + kotlin.math.sin(angle).toFloat() * radius
        if (index == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawClockMedal(cx: Float, cy: Float, d: Float, c: Color) {
    drawCircle(c, d * .205f, Offset(cx, cy), style = Stroke(d * .045f))
    drawLine(c, Offset(cx, cy), Offset(cx, cy - d * .12f), d * .045f, cap = StrokeCap.Round)
    drawLine(c, Offset(cx, cy), Offset(cx + d * .12f, cy + d * .06f), d * .045f, cap = StrokeCap.Round)
    drawCircle(c, d * .035f, Offset(cx, cy))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHourglassMedal(cx: Float, cy: Float, d: Float, c: Color) {
    val p = Path().apply {
        moveTo(cx - d * .17f, cy - d * .2f); lineTo(cx + d * .17f, cy - d * .2f); lineTo(cx + d * .04f, cy - d * .02f)
        lineTo(cx + d * .17f, cy + d * .18f); lineTo(cx - d * .17f, cy + d * .18f); lineTo(cx - d * .04f, cy - d * .02f); close()
    }
    drawPath(p, c, style = Stroke(d * .04f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    drawLine(c, Offset(cx - d * .08f, cy + d * .12f), Offset(cx + d * .08f, cy + d * .12f), d * .035f, cap = StrokeCap.Round)
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
            card.animateTo(1f, tween(200, easing = AppMotion.Ease))
            seal.animateTo(1f, tween(330, easing = AppMotion.Ease))
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
