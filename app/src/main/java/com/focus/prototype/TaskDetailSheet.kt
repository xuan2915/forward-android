package com.focus.prototype

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TaskDetailSheet(
    task: Task,
    db: Database,
    timer: FocusTimer,
    startEditing: Boolean = false,
    onDismiss: () -> Unit,
    onFocus: () -> Unit,
    onAnalytics: () -> Unit,
    onChanged: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var visible by remember(task.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(task.id) { visible = true }
    fun dismiss(after: () -> Unit = onDismiss) {
        if (!visible) return
        visible = false
        scope.launch {
            delay(AppMotion.Standard.toLong())
            after()
        }
    }
    BackHandler(enabled = true) { dismiss() }
    var checking by remember(task.id) { mutableStateOf(false) }
    var selfCheck by remember(task.id) { mutableStateOf(false) }
    var revoking by remember(task.id) { mutableStateOf(false) }
    var resetting by remember(task.id) { mutableStateOf(false) }
    val active = timer.taskId == task.id && timer.phase == "work"
    val paused = timer.taskId == task.id && timer.phase == "paused"
    val elapsed = db.elapsed(task)
    val completionMode = if (task.mode == "timer") "计时" else task.mode
    val canComplete = selfCheck && elapsed >= task.minutes * 60_000L && (task.mode == "行动型" || task.evidence.isNotBlank())

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(AppMotion.Short)),
        exit = fadeOut(tween(AppMotion.Short)),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.56f)).clickable { dismiss() })
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                enter = slideInVertically(spring(dampingRatio = AppMotion.Damping, stiffness = AppMotion.Stiffness)) { it } + fadeIn(tween(AppMotion.Standard)),
                exit = slideOutVertically(tween(AppMotion.Standard)) { it } + fadeOut(tween(AppMotion.Short)),
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.92f)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                        .navigationBarsPadding().imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp).padding(bottom = 22.dp)
                        .animateContentSize(motionSizeSpec()),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(16.dp), contentAlignment = Alignment.Center) {
                        Surface(Modifier.width(36.dp).height(4.dp), shape = RoundedCornerShape(50), color = AppColors.Muted) {}
                    }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(if (task.required) "必做任务" else "选做任务", style = MaterialTheme.typography.labelLarge, color = AppColors.Muted)
                    Text(task.title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                }
                StatusPill(when {
                    task.done -> "已完成"
                    active -> "专注中"
                    paused -> "已暂停"
                    else -> "待开始"
                }, active = task.done || active)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(if (task.minutes > 0) "${focusDuration(elapsed)} / ${task.minutes} 分钟" else focusDuration(elapsed), style = MaterialTheme.typography.titleMedium)
                    Text("专注记录", style = MaterialTheme.typography.labelSmall, color = AppColors.Muted)
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(completionMode, style = MaterialTheme.typography.titleMedium)
                    Text("完成方式", style = MaterialTheme.typography.labelSmall, color = AppColors.Muted)
                }
            }
            TextButton(onClick = { dismiss(onAnalytics) }, modifier = Modifier.align(Alignment.End)) {
                Text("查看成长统计")
            }
            if (task.checklist.isNotBlank()) {
                QuietSurface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Text("完成标准", style = MaterialTheme.typography.labelLarge, color = AppColors.Muted)
                    Text(task.checklist, style = MaterialTheme.typography.bodyLarge)
                }
            }

            if (task.done && (task.evidence.isNotBlank() || db.attachments(task).isNotEmpty())) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("历史完成证明", style = MaterialTheme.typography.titleMedium)
                    ProofActions(task, db, onChanged)
                }
            }

            if (task.done) {
                if (task.required && (task.rewardMinutes>0 || task.rewardGames>0)) {
                    Text("本项奖励已自动到账："+buildList{if(task.rewardMinutes>0)add("${task.rewardMinutes} 分钟");if(task.rewardGames>0)add("${task.rewardGames} 局")}.joinToString(" · "),style=MaterialTheme.typography.bodyMedium,color=AppColors.Muted)
                } else if (!task.required && !task.claimed) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("领取一项奖励", style = MaterialTheme.typography.titleMedium)
                        Text("每项选做奖励限领一次", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (task.rewardMinutes > 0) SecondaryAction("${task.rewardMinutes} 分钟", { db.claim(task, "video"); onChanged() }, enabled = db.eligible())
                            if (task.rewardGames > 0) SecondaryAction("${task.rewardGames} 局", { db.claim(task, "game"); onChanged() }, enabled = db.eligible())
                        }
                        if (!db.eligible()) Text("完成今日必做任务后可领取", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                    }
                } else if (task.claimed) {
                    Text("奖励已领取", style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted)
                }
                SecondaryAction("撤销误完成", { revoking = true }, Modifier.fillMaxWidth())
            } else {
                PrimaryAction(
                    when { active -> "暂停专注"; paused -> "继续专注"; else -> "开始专注" },
                    onClick = {
                        when {
                            active -> timer.pause()
                            paused -> timer.resume(task)
                            else -> { if (timer.phase != "ready") timer.finish(); timer.begin(task) }
                        }
                        onChanged()
                        if (!active) dismiss(onFocus)
                    },
                    Modifier.fillMaxWidth(),
                    icon = { PlayPauseMorph(if (active) 1f else 0f) },
                )

                if (elapsed > 0) SecondaryAction("重新计时", { resetting = true }, Modifier.fillMaxWidth())

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("完成证明", style = MaterialTheme.typography.titleMedium)
                    Text(if(db.setting("proof_no_save_${task.id}")=="true") "仅提交证明 · 不在本机保存" else "照片和附件保存在本机，用于完成自检", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                    ProofActions(task, db, onChanged)
                }

                androidx.compose.material3.HorizontalDivider(color = AppColors.Border)
                TextButton(onClick = { checking = !checking }) { Text(if (checking) "收起完成确认" else "完成自检") }
                AnimatedVisibility(
                    checking,
                    enter = expandVertically(tween(motionMillis())) + fadeIn(tween(motionMillis())),
                    exit = shrinkVertically(tween(motionMillis())) + fadeOut(tween(motionMillis())),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth().toggleable(value = selfCheck, onValueChange = { selfCheck = it }), verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(selfCheck, onCheckedChange = null)
                            Text("我已按预设条件完成${if (task.mode == "掌握型") "独立练习或解释" else ""}")
                        }
                        Button(enabled = canComplete, onClick = {
                            db.complete(task)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            checking = false
                            onChanged()
                        }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("确认完成任务") }
                        Text(
                            when {
                                elapsed < task.minutes * 60_000L -> "还需专注 ${focusDuration((task.minutes * 60_000L - elapsed).coerceAtLeast(0))}"
                                task.mode != "行动型" && task.evidence.isBlank() -> "请先添加照片或附件证明"
                                else -> "请确认已达到预设完成标准"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.Muted,
                        )
                    }
                }

                Text("计划调整", style = MaterialTheme.typography.titleMedium)
                TaskChanges(task, db, onChanged, startEditing)
            }
        }
    }
    }
    }

    if (resetting) AlertDialog(
        onDismissRequest = { resetting = false },
        title = { Text("重新开始这次专注？") },
        text = { Text("已计入的专注时长将清零 · 完成证明保留") },
        confirmButton = { TextButton(onClick = { db.restart(task); resetting = false; onChanged() }) { Text("清零计时") } },
        dismissButton = { TextButton(onClick = { resetting = false }) { Text("取消") } },
    )
    if (revoking) AlertDialog(
        onDismissRequest = { revoking = false },
        title = { Text("撤销任务完成？") },
        text = { Text("将按可用余额冲回奖励 · 已使用额度不追回") },
        confirmButton = { TextButton(onClick = { db.revoke(task); revoking = false; onChanged() }) { Text("确认撤销") } },
        dismissButton = { TextButton(onClick = { revoking = false }) { Text("取消") } },
    )
}
