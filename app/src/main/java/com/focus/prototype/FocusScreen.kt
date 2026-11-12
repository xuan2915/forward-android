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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
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

/** The clock supplies the progress; the vessel only renders it. */
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
        freeMode -> 1f - kotlin.math.abs(1f - (elapsed % 120_000L) / 60_000f)
        isRest -> 1f - remaining.toFloat() / mode.breakMillis.coerceAtLeast(1L)
        else -> 1f - remaining.toFloat() / mode.workMillis.coerceAtLeast(1L)
    }.coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        fraction,
        tween(motionMillis(850), easing = androidx.compose.animation.core.LinearEasing),
        label = "focus-vessel",
    )
    val phaseColor by animateColorAsState(
        targetValue = if (isRest) MaterialTheme.colorScheme.secondary else AppColors.Accent,
        animationSpec = tween(motionMillis(420), easing = AppMotion.Ease),
        label = "focus-phase-color",
    )
    val running = phase == "work" || phase == "break"
    val activeTask = phase == "work" || phase == "paused" && pausedFrom == "work"
    val status = when {
        phase == "break" && timer.notice == "本轮完成" -> "本轮完成"
        phase == "ready" && timer.notice == "休息结束" -> "休息结束"
        phase == "work" -> "专注中"
        phase == "break" -> "休息中"
        phase == "paused" -> "已暂停"
        else -> "准备专注"
    }

    Column(
        Modifier.fillMaxWidth().animateContentSize(motionSizeSpec()).padding(top = AppSpace.Medium, bottom = AppSpace.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpace.Large),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("专注", style = MaterialTheme.typography.headlineSmall)
            StatusPill(status, active = running)
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
        shape = MaterialTheme.shapes.large,
        color = AppColors.Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Border),
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(duration, style = MaterialTheme.typography.labelMedium, color = AppColors.Muted)
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val digitSize = with(LocalDensity.current) { (maxWidth / if (time.length > 5) 5.3f else 4.3f).toSp() }
                Text(
                    time,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Light,
                        fontSize = digitSize, lineHeight = digitSize * 1.12f, letterSpacing = (-2).sp,
                    ),
                    maxLines = 1, softWrap = false,
                )
            }
            FocusVessel(fraction, free, tint)
            Text(
                when {
                    phase == "paused" -> "已暂停"
                    free -> "自由计时"
                    rest -> "休息进度 ${(fraction * 100).toInt()}%"
                    else -> "本轮进度 ${(fraction * 100).toInt()}%"
                },
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.Muted,
            )
        }
    }
}

@Composable
private fun FocusVessel(fraction: Float, free: Boolean, tint: Color) {
    Canvas(Modifier.size(width = 90.dp, height = 104.dp)) {
        val x = size.width
        val y = size.height
        val outline = Path().apply {
            moveTo(.16f * x, .09f * y)
            lineTo(.84f * x, .09f * y)
            cubicTo(.84f*x, .32f*y, .66f*x, .37f*y, .54f*x, .48f*y)
            cubicTo(.50f*x, .52f*y, .84f*x, .63f*y, .84f*x, .91f*y)
            lineTo(.16f * x, .91f * y)
            cubicTo(.16f*x, .63f*y, .50f*x, .52f*y, .46f*x, .48f*y)
            cubicTo(.34f*x, .37f*y, .16f*x, .32f*y, .16f*x, .09f*y)
            close()
        }
        drawPath(outline, AppColors.Background)
        clipPath(outline) {
            val bottom = y * (.91f - .37f * fraction)
            val top = y * (.13f + .36f * fraction)
            if (!free) drawRect(tint.copy(alpha = .55f), Offset(0f, top), Size(x, (y * .49f - top).coerceAtLeast(0f)))
            if (fraction > 0f) {
                val sand = Path().apply {
                    moveTo(0f, bottom + y * .05f)
                    quadraticTo(x * .46f, bottom - y * .05f, x, bottom + y * .035f)
                    lineTo(x, y)
                    lineTo(0f, y)
                    close()
                }
                drawPath(sand, tint)
                for (grain in 0..5) {
                    val grainY = y * (.51f + (grain * .068f + fraction * 2f) % .35f)
                    if (grainY < bottom) drawCircle(tint.copy(alpha = .55f), 1.dp.toPx(), Offset(x * .50f, grainY))
                }
            }
        }
        drawPath(outline, AppColors.Muted.copy(alpha = .58f), style = Stroke(1.15.dp.toPx()))
        for (edge in listOf(.055f, .915f)) {
            drawRoundRect(AppColors.Border, Offset(x * .10f, y * edge),
                Size(x * .80f, y * .03f), CornerRadius(2.dp.toPx()))
        }
    }
}

private fun FocusMode.durationLabel(): String = when (this) {
    FocusMode.POMODORO -> "25 分钟"
    FocusMode.DEEP -> "50 分钟"
    FocusMode.FREE -> "不限时"
}
