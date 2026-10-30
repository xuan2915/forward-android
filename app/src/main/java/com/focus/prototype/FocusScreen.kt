package com.focus.prototype

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        phase == "break" || phase == "paused" && pausedFrom == "break" -> 1f - remaining.toFloat() / mode.breakMillis.coerceAtLeast(1L)
        else -> 1f - remaining.toFloat() / mode.workMillis.coerceAtLeast(1L)
    }.coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(fraction, tween(AppMotion.Standard), label = "focus-progress")
    val running = phase == "work" || phase == "break"
    val activeTask = phase == "work" || phase == "paused" && pausedFrom == "work"

    Column(Modifier.fillMaxWidth().padding(top = AppSpace.Large, bottom = AppSpace.XLarge), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            PageHeading("专注时间", "把注意力收回来。")
            StatusPill(if (phase == "work") "正在专注" else if (phase == "break") "正在休息" else "本地计时", active = running)
        }
        Spacer(Modifier.height(AppSpace.Large))

        if (phase == "ready") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusMode.entries.forEach { item ->
                    FilterChip(
                        selected = item == mode,
                        onClick = { timer.selectMode(item); onChanged() },
                        label = { Text(item.title) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = AppColors.Surface,
                            labelColor = AppColors.Muted,
                            selectedContainerColor = AppColors.AccentWash,
                            selectedLabelColor = AppColors.Accent,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        FocusDial(
            Modifier.size(286.dp),
            animatedFraction,
            timer.elapsed(),
            freeMode,
            phase == "break" || phase == "paused" && pausedFrom == "break",
            time,
            if (phase == "break" || phase == "paused" && pausedFrom == "break") "休息" else if (phase == "work" || activeTask) "专注" else "准备开始",
        )
        Spacer(Modifier.height(24.dp))
        Text(
            when {
                phase == "work" -> "保持节奏。"
                phase == "break" -> "休息一下，回来继续。"
                phase == "paused" -> "从这里接着做。"
                timer.notice.isNotBlank() -> timer.notice
                else -> "先做这一件事。"
            },
            style = MaterialTheme.typography.titleMedium,
            color = AppColors.Text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        if (selected != null) {
            Box {
                TextButton(enabled = phase == "ready", onClick = { chooseTask = true }) {
                    Text(selected.title + if (selected.minutes > 0) " · ${selected.minutes} 分钟目标" else "", color = AppColors.Muted)
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

        Spacer(Modifier.height(22.dp))
        when (phase) {
            "ready" -> PrimaryAction("开始专注", { selected?.let(timer::begin); onChanged() }, Modifier.fillMaxWidth(), selected != null)
            "work" -> {
                PrimaryAction("暂停", { timer.pause(); onChanged() }, Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecondaryAction("休息一下", { timer.takeBreak(); onChanged() }, Modifier.weight(1f))
                    SecondaryAction("结束", { timer.finish(); onChanged() }, Modifier.weight(1f))
                }
            }
            "break" -> {
                PrimaryAction("暂停休息", { timer.pause(); onChanged() }, Modifier.fillMaxWidth())
                SecondaryAction("结束休息", { timer.finish(); onChanged() }, Modifier.fillMaxWidth())
            }
            "paused" -> {
                PrimaryAction("继续", { if(pausedFrom=="break")timer.resumeBreak() else selected?.let(timer::resume); onChanged() }, Modifier.fillMaxWidth(), selected != null || pausedFrom == "break")
                SecondaryAction("结束本次", { timer.finish(); onChanged() }, Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            when {
                phase == "break" -> "休息时间不会计入任务专注时长"
                activeTask && selected != null -> "专注时长已累计至「${selected.title}」"
                selected == null -> "先在今日任务中安排一项必做任务"
                else -> "完成后，专注时间会记录到这项任务"
            },
            color = AppColors.Quiet,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        if (selected == null) {
            Spacer(Modifier.height(12.dp))
            SecondaryAction("前往今日任务", onTasks)
        }
    }
}

@Composable
private fun FocusDial(
    modifier: Modifier,
    fraction: Float,
    elapsed: Long,
    free: Boolean,
    rest: Boolean,
    time: String,
    label: String,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(286.dp).padding(5.dp)) {
            val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            drawArc(AppColors.Raised, -90f, 360f, false, style = stroke)
            if (free) {
                val second = (elapsed / 1000L % 60L).toFloat()
                drawArc(AppColors.Accent, -90f + second * 6f, 12f, false, style = stroke)
            } else {
                drawArc(if (rest) AppColors.Muted else AppColors.Accent, -90f, 360f * fraction, false, style = stroke)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = AppColors.Muted)
            Spacer(Modifier.height(6.dp))
            Text(time, style = MaterialTheme.typography.displayLarge.copy(fontSize = 58.sp, fontWeight = FontWeight.Light), color = AppColors.Text, maxLines = 1)
            Text(if (free && !rest) "自由计时" else if (rest) "呼吸片刻" else "专注时间", style = MaterialTheme.typography.bodySmall, color = AppColors.Quiet)
        }
    }
}
