package com.focus.prototype

import android.os.SystemClock
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.BoxWithConstraints
import kotlinx.coroutines.delay

/** The clock supplies the progress; the vessel only renders it. */
@Composable
fun FocusScreen(db: Database, timer: FocusTimer, tasks: List<Task>, onTasks: () -> Unit, onChanged: () -> Unit) {
    val selected = db.task(timer.taskId)?.takeUnless { it.done }
        ?: tasks.firstOrNull { !it.done && it.runningSince > 0 }
        ?: tasks.firstOrNull { !it.done && it.required }
        ?: tasks.firstOrNull { !it.done }
    val phase = timer.phase
    val mode = timer.mode
    var displayTick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(phase) {
        if (phase == "work" || phase == "break") {
            while (true) {
                delay(1_000L)
                displayTick++
            }
        }
    }
    val remaining = remember(displayTick, phase) { timer.remaining() }
    val elapsed = remember(displayTick, phase, mode) { timer.elapsed() }
    val sampledAt = SystemClock.elapsedRealtime()
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
    val freeSand = freeMode && !isRest
    val sandDuration = when { isRest -> mode.breakMillis; freeSand -> 120_000L; else -> mode.workMillis }
    val sandElapsed = when {
        phase == "ready" -> 0L
        freeSand -> elapsed
        else -> (sandDuration - remaining).coerceAtLeast(0L)
    }
    val sand = SandClock(sandElapsed, sandDuration, sampledAt, looping = freeSand)
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
            sand = sand,
            free = freeSand,
            rest = isRest,
            tint = phaseColor,
            duration = when {
                isRest -> "本次休息"
                freeMode -> "自由计时"
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
private fun FocusTimerStage(time: String, phase: String, sand: SandClock, free: Boolean, rest: Boolean, tint: Color, duration: String) {
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
            FocusHourglass(
                clock = sand,
                running = phase == "work" || phase == "break",
                settling = phase == "paused",
                free = free,
                rest = rest,
                tint = tint,
            )
            val percent = (sand.progressAt(sand.sampledAt, running = false) * 100).toInt()
            Text(
                when {
                    phase == "paused" -> "已暂停"
                    free -> "自由计时"
                    rest -> "休息进度 ${percent}%"
                    else -> "本轮进度 ${percent}%"
                },
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.Muted,
            )
        }
    }
}

private fun FocusMode.durationLabel(): String = when (this) {
    FocusMode.POMODORO -> "25 分钟"
    FocusMode.DEEP -> "50 分钟"
    FocusMode.FREE -> "不限时"
}
