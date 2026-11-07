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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
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
    val animatedFraction by animateFloatAsState(fraction, tween(motionMillis(1000),easing=AppMotion.Ease), label = "focus-progress")
    val running = phase == "work" || phase == "break"
    val activeTask = phase == "work" || phase == "paused" && pausedFrom == "work"

    Column(Modifier.fillMaxWidth().animateContentSize(motionSizeSpec()).padding(top = AppSpace.Medium, bottom = AppSpace.Large), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            PageHeading("专注 · 今日任务", "专注时间")
            StatusPill(when (phase) { "work" -> "专注中"; "break" -> "休息中"; "paused" -> "已暂停"; else -> "准备专注" }, active = running)
        }
        Spacer(Modifier.height(AppSpace.Medium))

        if (phase == "ready") {
            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FocusMode.entries.forEach { item ->
                        val selectedMode = item == mode
                        Surface(
                            onClick = { timer.selectMode(item); onChanged() },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                            color = if (selectedMode) MaterialTheme.colorScheme.primary else Color.Transparent,
                            contentColor = if (selectedMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).height(42.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) { Text(item.title, style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        FocusDial(
            Modifier.size(252.dp),
            animatedFraction,
            timer.elapsed(),
            freeMode,
            phase == "break" || phase == "paused" && pausedFrom == "break",
            time,
            when { phase == "break" || phase == "paused" && pausedFrom == "break" -> "休息中"; phase == "paused" -> "已暂停"; phase == "work" -> "专注中"; else -> "准备专注" },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            when {
                phase == "work" -> "专注中"
                phase == "break" -> "休息一下"
                phase == "paused" -> "已暂停"
                timer.notice.isNotBlank() -> timer.notice
                else -> "准备专注"
            },
            style = MaterialTheme.typography.titleMedium,
            color = AppColors.Text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        if (selected != null) {
            Box(Modifier.fillMaxWidth()) {
                Surface(
                    onClick = { chooseTask = true },
                    enabled = phase == "ready",
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("当前任务", style = MaterialTheme.typography.labelSmall, color = AppColors.Muted)
                            Text(selected.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
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

        Spacer(Modifier.height(12.dp))
        val morph by animateFloatAsState(if(running) 1f else 0f,tween(motionMillis()),label="play-pause")
        PrimaryAction(when(phase){"work"->"暂停专注";"break"->"暂停休息";"paused"->if(pausedFrom=="break")"继续休息" else "继续专注";else->"开始专注"}, {
            when(phase){"work","break"->timer.pause();"paused"->if(pausedFrom=="break")timer.resumeBreak() else selected?.let(timer::resume);else->selected?.let(timer::begin)}
            onChanged()
        },Modifier.fillMaxWidth(),selected!=null || phase=="break" || pausedFrom=="break",icon={PlayPauseMorph(morph)})
        AnimatedVisibility(phase!="ready",enter=expandVertically(tween(motionMillis()))+fadeIn(),exit=shrinkVertically(tween(motionMillis()))+fadeOut()) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                if(phase=="work") SecondaryAction("休息一下",{timer.takeBreak();onChanged()},Modifier.weight(1f))
                SecondaryAction(if(phase=="break") "结束休息" else "结束本轮",{timer.finish();onChanged()},Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(10.dp))
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
        Canvas(Modifier.fillMaxSize().padding(5.dp)) {
            val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
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
            Text(time, style = MaterialTheme.typography.displayMedium.copy(fontSize = 46.sp, fontWeight = FontWeight.Light), color = AppColors.Text, maxLines = 1)
            Text(if (free && !rest) "自由计时" else if (rest) "呼吸片刻" else "专注时间", style = MaterialTheme.typography.bodySmall, color = AppColors.Quiet)
        }
    }
}
