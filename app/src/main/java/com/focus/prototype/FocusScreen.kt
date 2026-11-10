package com.focus.prototype

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.BoxWithConstraints

/** Focus view: calm typography and a horizontal elapsed-time rail instead of a dial. */
@Composable
fun FocusScreen(db: Database, timer: FocusTimer, tasks: List<Task>, onTasks: () -> Unit, onChanged: () -> Unit) {
    val selected = db.task(timer.taskId)?.takeUnless { it.done }
        ?: tasks.firstOrNull { !it.done && it.runningSince > 0 }
        ?: tasks.firstOrNull { !it.done && it.required }
        ?: tasks.firstOrNull { !it.done }
    val phase = timer.phase
    val mode = timer.mode
    val remaining = timer.remaining()
    val elapsed = timer.elapsed()
    var chooseTask by remember { mutableStateOf(false) }
    val pausedFrom = db.setting("focus_paused_phase")
    val freeMode = mode == FocusMode.FREE
    val isRest = phase == "break" || phase == "paused" && pausedFrom == "break"
    val time = when {
        phase == "work" && freeMode -> formatFocusTime(elapsed)
        phase == "ready" && freeMode -> "00:00"
        phase == "paused" && pausedFrom == "work" && freeMode -> formatFocusTime(elapsed)
        phase == "ready" -> formatFocusTime(mode.workMillis)
        else -> formatFocusTime(remaining)
    }
    val fraction = when {
        phase == "ready" -> 0f
        freeMode -> 0f
        isRest -> 1f - remaining.toFloat() / mode.breakMillis.coerceAtLeast(1L)
        else -> 1f - remaining.toFloat() / mode.workMillis.coerceAtLeast(1L)
    }.coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        fraction,
        tween(motionMillis(1100), easing = AppMotion.Ease),
        label = "focus-timeline",
    )
    val phaseColor by animateColorAsState(
        targetValue = if (isRest) MaterialTheme.colorScheme.secondary else AppColors.Accent,
        animationSpec = tween(motionMillis(420), easing = AppMotion.Ease),
        label = "focus-phase-color",
    )
    val running = phase == "work" || phase == "break"
    val activeTask = phase == "work" || phase == "paused" && pausedFrom == "work"

    Column(
        Modifier.fillMaxWidth().animateContentSize(motionSizeSpec()).padding(top = AppSpace.Medium, bottom = AppSpace.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpace.Large),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            PageHeading("FOCUS · 今日任务", "专注时间")
            StatusPill(when (phase) { "work" -> "专注中"; "break" -> "休息中"; "paused" -> "已暂停"; else -> "准备专注" }, active = running)
        }

        if (phase == "ready") {
            Surface(shape = RoundedCornerShape(AppShape.Control), color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FocusMode.entries.forEach { item ->
                        val selectedMode = item == mode
                        Surface(
                            onClick = { timer.selectMode(item); onChanged() },
                            shape = RoundedCornerShape(13.dp),
                            color = if (selectedMode) MaterialTheme.colorScheme.primary else Color.Transparent,
                            contentColor = if (selectedMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).height(44.dp),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(item.title, style = MaterialTheme.typography.labelLarge)
                                Text(item.durationLabel(), style = MaterialTheme.typography.labelSmall, color = if (selectedMode) MaterialTheme.colorScheme.onPrimary.copy(alpha = .78f) else AppColors.Quiet)
                            }
                        }
                    }
                }
            }
        }

        FocusTimerStage(
            time = time,
            phase = phase,
            fraction = animatedFraction,
            free = freeMode,
            rest = isRest,
            tint = phaseColor,
            duration = when {
                freeMode -> "自由计时"
                isRest -> "本次休息"
                else -> mode.durationLabel()
            },
        )

        if (selected != null) {
            Box(Modifier.fillMaxWidth()) {
                Surface(
                    onClick = { chooseTask = true },
                    enabled = phase == "ready",
                    shape = RoundedCornerShape(AppShape.Card),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("当前任务", style = MaterialTheme.typography.labelSmall, color = AppColors.Muted)
                            Text(selected.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (phase == "ready") Text("更换", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                DropdownMenu(expanded = chooseTask, onDismissRequest = { chooseTask = false }) {
                    tasks.filter { !it.done }.forEach { task ->
                        DropdownMenuItem(
                            text = { Text(task.title, maxLines = 1) },
                            onClick = { timer.selectTask(task); chooseTask = false; onChanged() },
                        )
                    }
                }
            }
        } else {
            Text("今天没有待做任务", color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium)
        }

        val morph by animateFloatAsState(if (running) 1f else 0f, tween(motionMillis()), label = "play-pause")
        PrimaryAction(
            when (phase) { "work" -> "暂停专注"; "break" -> "暂停休息"; "paused" -> if (pausedFrom == "break") "继续休息" else "继续专注"; else -> "开始专注" },
            {
                when (phase) {
                    "work", "break" -> timer.pause()
                    "paused" -> if (pausedFrom == "break") timer.resumeBreak() else selected?.let(timer::resume)
                    else -> selected?.let(timer::begin)
                }
                onChanged()
            },
            Modifier.fillMaxWidth(),
            selected != null || phase == "break" || pausedFrom == "break",
            icon = { PlayPauseMorph(morph) },
        )
        AnimatedVisibility(
            phase != "ready",
            enter = expandVertically(tween(motionMillis())) + fadeIn(tween(motionMillis())),
            exit = shrinkVertically(tween(motionMillis())) + fadeOut(tween(motionMillis())),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (phase == "work") SecondaryAction("休息一下", { timer.takeBreak(); onChanged() }, Modifier.weight(1f))
                SecondaryAction(if (phase == "break") "结束休息" else "结束本轮", { timer.finish(); onChanged() }, Modifier.weight(1f))
            }
        }
        Text(
            when {
                phase == "break" -> "休息时间不计入任务专注时长"
                activeTask && selected != null -> "专注时间会计入「${selected.title}」"
                selected == null -> "先在今日任务中安排一项必做任务"
                else -> "完成后，专注时间会记录到这项任务"
            },
            color = AppColors.Quiet,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        if (selected == null) SecondaryAction("前往今日任务", onTasks)
    }
}

@Composable
private fun FocusTimerStage(time: String, phase: String, fraction: Float, free: Boolean, rest: Boolean, tint: Color, duration: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppShape.Panel),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(0.8.dp, AppColors.Border.copy(alpha = .48f)),
    ) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(duration.uppercase(), style = MaterialTheme.typography.labelSmall, color = AppColors.Muted, letterSpacing = 1.2.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(7.dp)) { drawCircle(tint.copy(alpha = if (phase == "work" || phase == "break") .95f else .45f)) }
                    Text(when (phase) { "work" -> "专注中"; "break" -> "休息中"; "paused" -> "已暂停"; else -> "准备专注" }, style = MaterialTheme.typography.labelMedium, color = AppColors.Muted)
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                val fontSize = with(LocalDensity.current) { (maxWidth * .205f).toSp() }
                val digitMotion = motionMillis(130)
                AnimatedContent(
                    targetState = time,
                    transitionSpec = {
                        (slideInVertically(tween(digitMotion, easing = AppMotion.Ease)) { it / 7 } + fadeIn(tween(digitMotion))) togetherWith
                            (slideOutVertically(tween(digitMotion, easing = AppMotion.Ease)) { -it / 8 } + fadeOut(tween(digitMotion)))
                    },
                    label = "focus-digits",
                ) { shown ->
                    Text(
                        shown,
                        style = MaterialTheme.typography.displayLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Light, fontSize = fontSize, letterSpacing = (-2).sp),
                        color = AppColors.Text,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (free) "已专注" else if (rest) "休息进度" else "本轮进度", style = MaterialTheme.typography.labelMedium, color = AppColors.Muted)
                Text(if (free) "计时中" else "${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = tint)
            }
            FocusTimeline(fraction, free, tint)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (free) "开始后累计" else if (rest) "放慢一点" else "开始", style = MaterialTheme.typography.labelSmall, color = AppColors.Quiet)
                Text(if (free) "不设时限" else if (rest) "恢复专注" else duration, style = MaterialTheme.typography.labelSmall, color = AppColors.Quiet)
            }
        }
    }
}

@Composable
private fun FocusTimeline(fraction: Float, free: Boolean, tint: Color) {
    val insetColor = MaterialTheme.colorScheme.surfaceContainerLow
    Canvas(Modifier.fillMaxWidth().height(22.dp)) {
        val centerY = size.height / 2f
        val radius = 5.dp.toPx()
        val left = radius
        val right = size.width - radius
        val trackHeight = 5.dp.toPx()
        drawRoundRect(
            color = AppColors.Raised,
            topLeft = Offset(left, centerY - trackHeight / 2f),
            size = androidx.compose.ui.geometry.Size(right - left, trackHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f),
        )
        if (!free && fraction > 0f) {
            val progressWidth = (right - left) * fraction.coerceIn(0f, 1f)
            drawRoundRect(
                color = tint,
                topLeft = Offset(left, centerY - trackHeight / 2f),
                size = androidx.compose.ui.geometry.Size(progressWidth.coerceAtLeast(trackHeight), trackHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f),
            )
        }
        if (!free) {
            for (mark in 1..3) {
                val x = left + (right - left) * mark / 4f
                drawLine(AppColors.Background.copy(alpha = .52f), Offset(x, centerY - 7.dp.toPx()), Offset(x, centerY + 7.dp.toPx()), 1.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        val markerX = if (free) right else left + (right - left) * fraction.coerceIn(0f, 1f)
        drawCircle(tint, radius = radius, center = Offset(markerX, centerY))
        drawCircle(insetColor, radius = 1.7.dp.toPx(), center = Offset(markerX, centerY))
    }
}

private fun FocusMode.durationLabel(): String = when (this) {
    FocusMode.POMODORO -> "25 分钟"
    FocusMode.DEEP -> "50 分钟"
    FocusMode.FREE -> "不限时"
}
